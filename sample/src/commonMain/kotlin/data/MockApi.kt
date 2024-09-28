package com.gyanoba.inspektor.sample.data

import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.LogLevel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import models.Todo

object MockApi {
    private val mockEngine = MockEngine { request ->
        when (request.url.encodedPath) {

            "/todo" -> when (request.method) {
                HttpMethod.Post -> respond(
                    content = """{"id": 1, "userId": 1, "title": "New Todo", "completed": false}""",
                    status = HttpStatusCode.Created,
                    headers = headersOf(
                        HttpHeaders.ContentType, ContentType.Application.Json.toString()
                    )
                )

                HttpMethod.Get -> respond(
                    content = todoListJson,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType, ContentType.Application.Json.toString()
                    )
                )

                else -> respondError(HttpStatusCode.BadRequest)
            }

            "/todo/1" -> respond(
                content = """{"id": 1, "userId": 1, "title": "Todo 1", "completed": false}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType, ContentType.Application.Json.toString()
                )
            )


            "/json" -> respond(
                content = sampleJson,
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType, ContentType.Application.Json.toString()
                )
            )

            "/text" -> respond(
                content = sampleText,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )

            "/html" -> respond(
                content = sampleHtml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )

            "/xml" -> respond(
                content = sampleXml,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )

            "/binary" -> respond(
                content = byteArrayOf(0x01, 0x02, 0x03, 0x04),
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.OctetStream.toString()
                )
            )

            "/status/success/200" -> respond(
                content = "Success",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )

            "/status/error/400" -> respondError(
                content = "Bad Request",
                status = HttpStatusCode.BadRequest,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )

            "/status/error/404" -> respondError(
                content = "Not Found",
                status = HttpStatusCode.NotFound,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )

            "/status/error/500" -> respondError(
                content = "Internal Server Error",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )

            "/no-response" -> throw Exception("No response")

            else -> respondError(HttpStatusCode.NotFound)
        }
    }

    private val client = HttpClient(mockEngine) {
        install(ContentNegotiation) {
            json()
        }
        install(Logging) {
            logger = Logger.SIMPLE
            level = io.ktor.client.plugins.logging.LogLevel.BODY
        }
        install(Inspektor) {
            level = LogLevel.BODY
        }
    }

    fun getTodoList(): List<Todo> = runBlocking {
        client.get("http://localhost/todo").body()
    }

    fun getTodo(): Todo = runBlocking {
        client.get("http://localhost/todo/1").body()

    }

    fun createTodo(): Todo = runBlocking {
        client.post("http://localhost/todo") {
            contentType(ContentType.Application.Json)
            setBody(
                Todo(
                    id = 1, userId = 1, title = "New Todo", completed = false
                )
            )
        }.body()

    }

    fun updateTodo(): Todo = runBlocking {
        client.put("http://localhost/todo/1") {
            contentType(ContentType.Application.Json)
            setBody(
                Todo(
                    id = 1, userId = 1, title = "Updated Todo", completed = true
                )
            )
        }.body()
    }

    fun deleteTodo() = runBlocking {
        client.delete("http://localhost/todo/1").status
    }

    fun patchTodo(): Todo = runBlocking {
        client.patch("http://localhost/todo/1") {
            contentType(ContentType.Application.Json)
            setBody(
                TextContent(
                    text = """{"title": "Partially Updated Todo"}""",
                    contentType = ContentType.Application.Json
                )
            )
        }.body()
    }

    fun getJsonResponse(): String = runBlocking {
        client.get("http://localhost/json").body()
    }

    fun getTextResponse(): String = runBlocking {
        client.get("http://localhost/text").body()
    }

    fun getHtmlResponse(): String = runBlocking {
        client.get("http://localhost/html").body()
    }

    fun getXmlResponse(): String = runBlocking {
        client.get("http://localhost/xml").body()
    }

    fun getBinaryResponse(): ByteArray = runBlocking {
        client.get("http://localhost/binary").body()
    }

    fun getSuccessResponse200(): String = runBlocking {
        client.get("http://localhost/status/success/200").body()
    }

    fun getErrorResponse400(): String = runBlocking {
        client.get("http://localhost/status/error/400").body()
    }

    fun getErrorResponse404(): String = runBlocking {
        client.get("http://localhost/status/error/404").body()
    }

    fun getErrorResponse500(): String = runBlocking {
        client.get("http://localhost/status/error/500").body()
    }

    fun getNoResponse() = runBlocking {
        try {
            val response: String = client.get("http://localhost/no-response").body()
        } catch (e: Exception) {
            println("No response received: ${e.message}")
        }
    }
}
