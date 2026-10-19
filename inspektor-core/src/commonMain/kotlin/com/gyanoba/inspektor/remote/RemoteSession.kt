package com.gyanoba.inspektor.remote

import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.InspektorDataSourceImpl
import com.gyanoba.inspektor.data.OverrideRepository
import com.gyanoba.inspektor.data.TransactionChange
import com.gyanoba.inspektor.utils.InspektorLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

private const val TAG = "Inspektor remote"

/** How many transactions one page of a backfill or a `transactions.list` holds at most. */
private const val PAGE_SIZE = 200L

/** A request a client got wrong; answered with an error, the connection stays open. */
private class RequestException(val code: String, message: String) : Exception(message)

/**
 * One client connection (docs/REMOTE_PROTOCOL.md).
 *
 * The server speaks first with `hello`. After that the client sends requests, each answered with
 * a reply carrying its id, and after `subscribe` the session also pushes events: first every
 * stored transaction after the client's `afterId` (the backfill), then each write as it happens.
 * Replies and events share one connection, so every write goes through [writeLock].
 */
@OptIn(UnstableInspektorAPI::class)
internal class RemoteSession(
    private val connection: RemoteConnection,
    private val dataSource: InspektorDataSourceImpl,
    private val overrides: OverrideRepository,
    private val hello: suspend () -> JsonObject,
    parent: CoroutineScope,
) {
    private val scope = CoroutineScope(
        parent.coroutineContext + SupervisorJob(parent.coroutineContext[Job]),
    )
    private val writeLock = Mutex()
    private var subscription: Job? = null

    /** Runs the session until the client disconnects or breaks the protocol. */
    fun start(): Job = scope.launch {
        try {
            send(hello())
            val reader = FrameReader(connection)
            while (true) {
                val frame = reader.next() ?: break
                handle(frame)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RemoteProtocolException) {
            InspektorLog.info(TAG) { "Closed a connection that broke the protocol: ${e.message}" }
        } catch (e: Throwable) {
            InspektorLog.error(e, TAG) { "A remote-inspection connection failed" }
        } finally {
            close()
        }
    }

    fun close() {
        scope.cancel()
        connection.close()
    }

    // ── Requests ──────────────────────────────────────────────────────────

    private suspend fun handle(frame: JsonObject) {
        val id = (frame["id"] as? JsonPrimitive)?.longOrNull
        val method = (frame["method"] as? JsonPrimitive)?.contentOrNull
        // Only requests come from a client; anything else is from a newer revision.
        if (id == null || method == null) return
        val params = frame["params"] as? JsonObject ?: EmptyJsonObject
        val reply = try {
            resultMessage(id, dispatch(method, params))
        } catch (e: CancellationException) {
            throw e
        } catch (e: RequestException) {
            errorMessage(id, e.code, e.message ?: e.code)
        } catch (e: Throwable) {
            InspektorLog.error(e, TAG) { "\"$method\" failed" }
            errorMessage(id, "internal", e.message ?: e.toString())
        }
        send(reply)
    }

    private suspend fun dispatch(method: String, params: JsonObject): JsonElement =
        when (method) {
            "subscribe" -> {
                subscribe(
                    afterId = params.longParam("afterId"),
                    inlineBodyLimit = params.intParam("inlineBodyLimit") ?: 0,
                )
                EmptyJsonObject
            }

            "transactions.list" -> listTransactions(
                afterId = params.longParam("afterId") ?: 0,
                limit = (params.intParam("limit") ?: 100).coerceIn(1, PAGE_SIZE.toInt()),
            )

            "transactions.get" -> getTransactions(params)
            "body.get" -> getBody(params)
            "transactions.clear" -> {
                dataSource.deleteAllTransactions()
                EmptyJsonObject
            }

            "overrides.list" -> overridesJson(overrides.getAll())
            "overrides.put" -> putOverride(params)
            "overrides.delete" -> {
                val id = params.longParam("id")
                    ?: throw RequestException("invalid_params", "\"overrides.delete\" needs an id.")
                val doomed = overrides.getAll().firstOrNull { it.id == id }
                    ?: throw RequestException("not_found", "There is no override $id.")
                overrides.remove(doomed)
                EmptyJsonObject
            }

            "ping" -> EmptyJsonObject
            else -> throw RequestException("unknown_method", "Unknown method \"$method\".")
        }

    private suspend fun listTransactions(afterId: Long, limit: Int): JsonObject {
        val page = dataSource.transactionsAfter(afterId, limit + 1L)
        return buildJsonObject {
            put("transactions", jsonArrayOf(page.take(limit).mapNotNull { it.toRemoteJson() }))
            put("hasMore", page.size > limit)
        }
    }

    private suspend fun getTransactions(params: JsonObject): JsonObject {
        val ids = (params["ids"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
            ?: throw RequestException("invalid_params", "\"transactions.get\" needs ids.")
        val found = ids.mapNotNull { dataSource.getTransaction(it)?.toRemoteJson() }
        return buildJsonObject { put("transactions", jsonArrayOf(found)) }
    }

    private suspend fun getBody(params: JsonObject): JsonObject {
        val id = params.longParam("id")
            ?: throw RequestException("invalid_params", "\"body.get\" needs an id.")
        val part = BodyPart.fromWire((params["part"] as? JsonPrimitive)?.contentOrNull)
            ?: throw RequestException("invalid_params", "Unknown body part.")
        val transaction = dataSource.getTransaction(id)
            ?: throw RequestException("not_found", "There is no transaction $id.")
        val body = transaction.body(part)
            ?: throw RequestException("not_found", "Transaction $id has no ${part.wireName} body.")
        val payloadSize = when (part) {
            BodyPart.Request -> transaction.requestPayloadSize
            BodyPart.Response -> transaction.responsePayloadSize
            else -> null
        }
        return bodyResult(
            body = body,
            truncated = bodyState(body, payloadSize) == "truncated",
            offset = params.intParam("offset"),
            length = params.intParam("length"),
        )
    }

    private suspend fun putOverride(params: JsonObject): JsonElement {
        val override = try {
            overrideFromJson(params)
        } catch (e: SerializationException) {
            throw RequestException("invalid_params", "Not an override: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw RequestException("invalid_params", "Not an override: ${e.message}")
        }
        val stored = if (override.id == 0L) {
            overrides.add(override)
            overrides.getAll().maxByOrNull { it.id }
        } else {
            if (overrides.getAll().none { it.id == override.id }) {
                throw RequestException("not_found", "There is no override ${override.id}.")
            }
            overrides.update(override)
            overrides.getAll().firstOrNull { it.id == override.id }
        }
        return overrideJson(stored ?: override)
    }

    // ── Events ────────────────────────────────────────────────────────────

    /**
     * Starts pushing events, replacing any earlier subscription. Changes are collected from before
     * the backfill is read, so a write that lands during the backfill is sent too; a transaction
     * may then arrive twice, which a client treats as an update.
     */
    private fun subscribe(afterId: Long?, inlineBodyLimit: Int) {
        subscription?.cancel()
        subscription = scope.launch {
            try {
                pumpEvents(afterId, inlineBodyLimit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Usually the client went away mid-write; the reader sees the end and closes.
                InspektorLog.info(TAG) { "Stopped sending events: ${e.message}" }
            }
        }
    }

    private suspend fun pumpEvents(afterId: Long?, inlineBodyLimit: Int): Unit = coroutineScope {
        val changes = Channel<TransactionChange>(Channel.UNLIMITED)
        launch(start = CoroutineStart.UNDISPATCHED) {
            dataSource.changes.collect { changes.send(it) }
        }
        launch {
            overrides.updates.collect {
                send(eventMessage("overrides.changed", overridesJson(it)))
            }
        }

        var cursor = afterId ?: 0L
        while (true) {
            val page = dataSource.transactionsAfter(cursor, PAGE_SIZE)
            for (transaction in page) {
                val json = transaction.toRemoteJson(inlineBodyLimit) ?: continue
                send(eventMessage("transaction.updated", json))
            }
            if (page.size < PAGE_SIZE) break
            cursor = page.last().id
        }

        for (change in changes) {
            when (change) {
                is TransactionChange.Inserted -> sendTransaction(
                    change.id,
                    "transaction.started",
                    inlineBodyLimit,
                )

                is TransactionChange.Updated -> sendTransaction(
                    change.id,
                    "transaction.updated",
                    inlineBodyLimit,
                )

                TransactionChange.Removed -> if (dataSource.transactionCount() == 0L) {
                    send(eventMessage("transactions.cleared"))
                }
            }
        }
    }

    private suspend fun sendTransaction(id: Long, event: String, inlineBodyLimit: Int) {
        val json = dataSource.getTransaction(id)?.toRemoteJson(inlineBodyLimit) ?: return
        send(eventMessage(event, json))
    }

    private suspend fun send(message: JsonObject) {
        val frame = encodeFrame(message)
        writeLock.withLock { connection.write(frame) }
    }
}

/** A number param; ids travel as strings, so both forms are accepted. */
private fun JsonObject.longParam(key: String): Long? {
    val value = this[key] as? JsonPrimitive ?: return null
    return value.longOrNull ?: value.contentOrNull?.toLongOrNull()
}

private fun JsonObject.intParam(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
