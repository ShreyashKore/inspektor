package data

import Inspektor
import LogLevel
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import models.Todo

object Api {
    private val client = HttpClient {
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

    private const val BASE_URL = "https://jsonplaceholder.typicode.com"

    suspend fun getTodo(id: Int): Todo = client.get("$BASE_URL/todos/$id").body()

    suspend fun getTodos(): List<Todo> = client.get("$BASE_URL/todos").body()

}