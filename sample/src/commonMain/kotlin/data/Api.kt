package data

import Inspektor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.pipeline.Pipeline
import io.ktor.util.pipeline.PipelinePhase
import kotlinx.datetime.Clock
import models.Todo
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.measureTimedValue

object Api {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = LogLevel.BODY
        }
        install(Inspektor) { }
    }

    private const val BASE_URL = "https://jsonplaceholder.typicode.com"

    suspend fun getTodo(id: Int): Todo = client.get("$BASE_URL/todos/$id").body()

    suspend fun getTodos(): List<Todo> = client.get("$BASE_URL/todos").body()

}

fun HttpClient.addDebugInterceptors() {
    requestPipeline.items.forEach { phase ->
        requestPipeline.interceptTimed("Request", phase)
    }

    sendPipeline.items.forEach { phase ->
        sendPipeline.interceptTimed("Send", phase)
    }

    receivePipeline.items.forEach { phase ->
        receivePipeline.interceptTimed("Receive", phase)
    }

    responsePipeline.items.forEach { phase ->
        responsePipeline.interceptTimed("Response", phase)
    }
}


fun <A : Any, B : Any> Pipeline<A, B>.interceptTimed(name: String, phase: PipelinePhase) =
    intercept(phase) {
        debugPrint("➡\uFE0F $name ::: ${phase.name}") { "> ${Clock.System.now()}\t\t\t:: $it" }
        val (value, duration) = measureTimedValue {
            proceed()
        }
        debugPrint("⬅\uFE0F $name ::: ${phase.name}") { "<${duration.getDashes()} ${Clock.System.now()}\t\t\t::: $duration\n$value" }
    }


fun Duration.getDashes(step: Duration = 50.microseconds): String {
    val count = (this / step).toInt()
    return buildString { repeat(count) { append('-') } }
}

private inline fun debugPrint(tag: String, content: () -> Any?) =
    println("$tag\n${content()}")