package fr.roadly.core.assistant.gemini

import fr.roadly.core.assistant.provider.AssistantError
import fr.roadly.core.assistant.provider.AssistantEvent
import fr.roadly.core.assistant.provider.Connection
import fr.roadly.core.assistant.provider.SessionConfig
import fr.roadly.core.assistant.provider.ToolResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class GeminiLiveProviderTest {
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<String>()
    private val provider = GeminiLiveProvider(Json { ignoreUnknownKeys = true })
    private val config = SessionConfig("test-key", "Règles", emptyList())

    @Before fun setUp() { server.start(); provider.endpoint = server.url("/ws").toString().replaceFirst("http", "ws") }
    @After fun tearDown() = server.shutdown()

    private fun serve(onText: (WebSocket, String) -> Unit, onOpen: (WebSocket) -> Unit = {}) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) = onOpen(webSocket)
            override fun onMessage(webSocket: WebSocket, text: String) { received += text; onText(webSocket, text) }
            // Répond à la fermeture du client, comme le vrai serveur ; sinon shutdown() attend 5 s la fin de la file.
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(1000, null) }
        }))
    }

    @Test fun `setup is sent with the key in the query and a tool call round-trips`() = runBlocking {
        serve(onText = { ws, text ->
            if ("\"setup\"" in text) {
                ws.send("""{"setupComplete":{}}""")
                ws.send("""{"toolCall":{"functionCalls":[{"id":"c1","name":"getArrivalTime","args":{}}]}}""")
            }
        })
        val open = provider.connect(config) as Connection.Open
        assertEquals("test-key", server.takeRequest().requestUrl!!.queryParameter("key"))
        val event = withTimeout(5_000) { open.session.events.first { it is AssistantEvent.ToolCalls } } as AssistantEvent.ToolCalls
        assertEquals("c1", event.calls.single().id)
        open.session.sendToolResults(listOf(ToolResult("c1", "getArrivalTime", buildJsonObject { put("status", "ok") })))
        withTimeout(5_000) { while (received.none { "toolResponse" in it }) kotlinx.coroutines.delay(20) }
        assertTrue(received.first().contains("models/gemini-3.8-live"))
        open.session.close()
    }

    @Test fun `server closing with an API key reason is reported as INVALID_KEY`() = runBlocking {
        serve(onText = { _, _ -> }, onOpen = { ws -> ws.close(1008, "API key not valid. Please pass a valid API key.") })
        assertEquals(Connection.Refused(AssistantError.INVALID_KEY), provider.connect(config))
    }

    @Test fun `no setupComplete within the timeout is TIMEOUT`() = runBlocking {
        provider.setupTimeoutMs = 300
        serve(onText = { _, _ -> })
        assertEquals(Connection.Refused(AssistantError.TIMEOUT), provider.connect(config))
    }

    @Test fun `close reasons and HTTP failures are classified`() {
        assertEquals(null, closeError(1000, ""))
        assertEquals(AssistantError.QUOTA, closeError(1011, "RESOURCE_EXHAUSTED: quota"))
        assertEquals(AssistantError.NETWORK, closeError(1006, "abnormal"))
        assertEquals(AssistantError.INVALID_KEY, failureError(RuntimeException(), 403))
        assertEquals(AssistantError.QUOTA, failureError(RuntimeException(), 429))
        assertEquals(AssistantError.TIMEOUT, failureError(java.net.SocketTimeoutException(), null))
        assertEquals(AssistantError.NETWORK, failureError(java.io.IOException(), null))
    }
}
