import com.gyanoba.inspektor.ClientCallLogger
import com.gyanoba.inspektor.Inspektor
import com.gyanoba.inspektor.IsTest
import com.gyanoba.inspektor.LogLevel
import com.gyanoba.inspektor.UnstableInspektorAPI
import com.gyanoba.inspektor.data.HttpMethod
import com.gyanoba.inspektor.data.HttpRequest
import com.gyanoba.inspektor.data.Override
import com.gyanoba.inspektor.data.OverrideAction
import com.gyanoba.inspektor.data.OverrideRepositoryImpl
import com.gyanoba.inspektor.data.PathMatcher
import com.gyanoba.inspektor.data.setApplicationId
import com.gyanoba.inspektor.platform.NotificationManager
import io.github.xxfast.kstore.storeOf
import io.github.xxfast.kstore.extensions.plus
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.logging.LogLevel as KtorLogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.SIMPLE
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utils.KStoreInMemoryCodec
import utils.NoOpDataSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ResponseDeserializationTest {
    private val store by lazy {
        storeOf<List<Override>>(codec = KStoreInMemoryCodec())
    }

    init {
        IsTest = true
        @OptIn(UnstableInspektorAPI::class)
        setApplicationId("com.test.inspektor")
    }

    @Test
    fun `response body can still be deserialized with content negotiation enabled`() = runBlocking {
        store.reset()
        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"id":1,"userId":1,"title":"Test Todo","completed":false}""",
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(Logging) {
                logger = Logger.SIMPLE
                level = KtorLogLevel.BODY
            }

            install(Inspektor) {
                level = LogLevel.BODY
                dataSource = NoOpDataSource
                overrideRepository = OverrideRepositoryImpl(store)
                notificationManager = object : NotificationManager {
                    override fun notify(title: String, message: String) = Unit
                }
            }
        }

        val response = client.get("http://localhost/todo")
        val todo: Todo = response.body()

        response.call.attributes[ClientCallLogger].joinResponseLogged()

        assertEquals(Todo(id = 1, userId = 1, title = "Test Todo", completed = false), todo)
        assertEquals(
            "{" +
                "\"id\":1,\"userId\":1,\"title\":\"Test Todo\",\"completed\":false}",
            response.call.attributes[ClientCallLogger].transaction.responseBody
        )
    }

    @Test
    fun `overridden json response can still be deserialized with content negotiation enabled`() = runBlocking {
        store.reset()
        store.plus(
            Override(
                id = 1,
                type = HttpRequest(HttpMethod.Get),
                matchers = listOf(PathMatcher("/todo")),
                action = OverrideAction(
                    type = OverrideAction.Type.FixedResponse,
                    responseHeaders = mutableMapOf(
                        HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString())
                    ),
                    responseBody = """{"id":2,"userId":5,"title":"Overridden Todo","completed":true}"""
                )
            )
        )

        val client = HttpClient(MockEngine) {
            engine {
                addHandler {
                    respond(
                        content = """{"id":1,"userId":1,"title":"Original Todo","completed":false}""",
                        headers = headersOf(
                            HttpHeaders.ContentType,
                            ContentType.Application.Json.toString()
                        )
                    )
                }
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            install(Logging) {
                logger = Logger.SIMPLE
                level = KtorLogLevel.BODY
            }

            install(Inspektor) {
                level = LogLevel.BODY
                dataSource = NoOpDataSource
                overrideRepository = OverrideRepositoryImpl(store)
                notificationManager = object : NotificationManager {
                    override fun notify(title: String, message: String) = Unit
                }
            }
        }

        val response = client.get("http://localhost/todo")
        val todo: Todo = response.body()

        response.call.attributes[ClientCallLogger].joinResponseLogged()

        assertEquals(Todo(id = 2, userId = 5, title = "Overridden Todo", completed = true), todo)
        assertEquals(
            "{" +
                "\"id\":2,\"userId\":5,\"title\":\"Overridden Todo\",\"completed\":true}",
            response.call.attributes[ClientCallLogger].transaction.responseBody
        )
        assertEquals(
            "{" +
                "\"id\":1,\"userId\":1,\"title\":\"Original Todo\",\"completed\":false}",
            response.call.attributes[ClientCallLogger].transaction.originalResponseBody
        )
    }

    @Serializable
    private data class Todo(
        val id: Int,
        val userId: Int,
        val title: String,
        val completed: Boolean,
    )
}

