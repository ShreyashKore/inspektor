//import io.ktor.client.engine.mock.MockEngine
//import io.ktor.client.engine.mock.respond
//import io.ktor.client.plugins.logging.Logging


class KtorTest {
//
//    private fun ktor(mockEngine: MockEngine) = HttpClient(mockEngine) {
//        install(ContentNegotiation) {
//            json()
//        }
//        install(Logging)
//    }.apply {
//
//        receivePipeline.items.forEach { phase ->
//            receivePipeline.interceptTimed("Receive", phase)
//        }
//
//        responsePipeline.items.forEach { phase ->
//            responsePipeline.interceptTimed("Response", phase)
//        }
//    }
//
//    private fun ktorLogAll(mockEngine: MockEngine) = HttpClient(mockEngine) {
//        install(ContentNegotiation) {
//            json()
//        }
//    }.apply {
//        addDebugInterceptors()
//    }
//
//    @Test
//    fun testPipelines() = runTest {
//        val responseBody = Response(12, "OK")
//        val ktor = ktor(MockEngine {
//            respond(
//                Json.encodeToString(responseBody),
//                headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))
//            )
//        })
//        val response = ktor.post("https://ktor.io") {
//            setBody(Todo(false, 12, "Todo", 12))
//            contentType(ContentType.Application.Json)
//        }
//        assertEquals(responseBody, response.body<Response>())
//    }
//}
//
//
//fun <T : Any, R : Any> Pipeline<T, R>.interceptTimed(name: String, phase: PipelinePhase) {
//    println("$name: $phase")
//    intercept(phase) {
//        val (_, t) = measureTimedValue {
//            proceed()
//        }
//        println("$name: $t")
//    }
}

//fun HttpClient.addDebugInterceptors() {
//    receivePipeline.items.forEach { phase ->
//        receivePipeline.interceptTimed("Receive", phase)
//    }
//
//    responsePipeline.items.forEach { phase ->
//        responsePipeline.interceptTimed("Response", phase)
//    }
//}