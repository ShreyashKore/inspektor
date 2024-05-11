package data

import inspektor
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import models.Todo

object Api {
    private val client = HttpClient {
        install(ContentNegotiation) {
            json()
        }
    }.apply {
        plugin(HttpSend).intercept(inspektor)
    }

    private const val BASE_URL = "https://jsonplaceholder.typicode.com"

    suspend fun getTodo(id: String): Todo = client.get("$BASE_URL/todos/$id").body()

    suspend fun getTodos(): List<Todo> = client.get("$BASE_URL/todos").body()

}