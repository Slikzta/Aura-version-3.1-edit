package com.example

import com.example.aura.core.provider.ChatMessage
import com.example.aura.core.provider.CompletionRequest
import com.example.aura.core.provider.MessageRole
import com.example.aura.core.provider.ModelProviderType
import com.example.aura.core.provider.ProviderAuthState
import com.example.aura.core.provider.ProviderConfig
import com.example.aura.core.provider.ProviderError
import com.example.aura.core.provider.StreamChunk
import com.example.aura.core.provider.ToolCallRequest
import com.example.aura.core.provider.ToolDefinition
import com.example.aura.core.provider.NetworkModelProvider
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NetworkModelProviderTest {

    @Test
    fun testAuthStateMissingCredentialsWhenKeyIsBlankOrUnconfigured() {
        val configUnconfigured = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "UNCONFIGURED"
        )
        val provider1 = NetworkModelProvider(configUnconfigured)
        assertTrue(provider1.authState is ProviderAuthState.MissingCredentials)

        val configBlank = configUnconfigured.copy(apiKey = "")
        val provider2 = NetworkModelProvider(configBlank)
        assertTrue(provider2.authState is ProviderAuthState.MissingCredentials)
    }

    @Test
    fun testAuthStateInvalidConfigurationWhenUrlIsBlank() {
        val configNoUrl = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "",
            apiKey = "sk-valid-key"
        )
        val provider = NetworkModelProvider(configNoUrl)
        assertTrue(provider.authState is ProviderAuthState.InvalidConfiguration)
    }

    @Test
    fun testAuthStateConfiguredWhenUrlAndKeyPresent() {
        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-live-key"
        )
        val provider = NetworkModelProvider(config)
        assertEquals(ProviderAuthState.Configured, provider.authState)
    }

    @Test
    fun testAuthStateConfiguredForLocalEndpointWithoutKey() {
        val config = ProviderConfig(
            id = "ollama_local",
            type = ModelProviderType.OLLAMA_LOCAL,
            displayName = "Ollama Local",
            endpointUrl = "http://10.0.2.2:11434/v1",
            apiKey = ""
        )
        val provider = NetworkModelProvider(config)
        assertEquals(ProviderAuthState.Configured, provider.authState)
    }

    @Test
    fun testStreamResponseThrowsAuthenticationErrorWhenUnconfigured() = runTest {
        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "UNCONFIGURED"
        )
        val provider = NetworkModelProvider(config)

        val request = CompletionRequest(
            messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hello"))
        )

        try {
            provider.streamResponse(request).toList()
            fail("Expected AuthenticationError to be thrown")
        } catch (e: ProviderError.AuthenticationError) {
            assertTrue(e.message.contains("API key is not configured"))
        }
    }

    @Test
    fun testStreamResponseParsesOpenAiSseToolCallsAndDone() = runTest {
        val ssePayload = """
            data: {"id":"chatcmpl-test","choices":[{"delta":{"role":"assistant","content":null,"tool_calls":[{"index":0,"id":"call_001","type":"function","function":{"name":"web_access","arguments":""}}]}}]}

            data: {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"{\"url\":\"https://example.com\"}"}}]}}]}

            data: {"choices":[{"delta":{},"finish_reason":"tool_calls"}]}

            data: [DONE]

        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ssePayload.toResponseBody("text/event-stream".toMediaType()))
                    .build()
            }
            .build()

        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-mock-key"
        )
        val provider = NetworkModelProvider(config, mockClient)

        val request = CompletionRequest(
            messages = listOf(ChatMessage(role = MessageRole.USER, content = "Inspect website")),
            availableTools = listOf(
                ToolDefinition("web_access", "Fetches web URLs", "{\"type\":\"object\"}")
            )
        )

        val chunks = provider.streamResponse(request).toList()

        val toolCallChunks = chunks.filterIsInstance<StreamChunk.ToolCallChunk>()
        assertEquals(1, toolCallChunks.size)
        val toolCall = toolCallChunks.first().toolCall
        assertEquals("call_001", toolCall.id)
        assertEquals("web_access", toolCall.name)
        assertEquals("{\"url\":\"https://example.com\"}", toolCall.argumentsJson)

        val doneChunks = chunks.filterIsInstance<StreamChunk.DoneChunk>()
        assertEquals(1, doneChunks.size)
        assertEquals("tool_calls", doneChunks.first().finishReason)
    }

    @Test
    fun testGenerateResponseParsesOpenAiJsonResponseWithToolCalls() = runTest {
        val jsonResponse = """
            {
              "id": "chatcmpl-123",
              "choices": [
                {
                  "index": 0,
                  "message": {
                    "role": "assistant",
                    "content": "Checking the link for you.",
                    "tool_calls": [
                      {
                        "id": "call_abc",
                        "type": "function",
                        "function": {
                          "name": "web_access",
                          "arguments": "{\"url\":\"https://example.com\"}"
                        }
                      }
                    ]
                  },
                  "finish_reason": "tool_calls"
                }
              ]
            }
        """.trimIndent()

        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(jsonResponse.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-test-key"
        )
        val provider = NetworkModelProvider(config, mockClient)

        val response = provider.generateResponse(
            CompletionRequest(
                messages = listOf(ChatMessage(role = MessageRole.USER, content = "Fetch site"))
            )
        )

        assertEquals("Checking the link for you.", response.message.content)
        assertEquals(1, response.message.toolCalls.size)
        assertEquals("call_abc", response.message.toolCalls[0].id)
        assertEquals("web_access", response.message.toolCalls[0].name)
        assertEquals("tool_calls", response.finishReason)
    }

    @Test
    fun testHttpError401ThrowsAuthenticationError() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("{\"error\":{\"message\":\"Incorrect API key provided\"}}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-invalid-key"
        )
        val provider = NetworkModelProvider(config, mockClient)

        try {
            provider.generateResponse(CompletionRequest(messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hi"))))
            fail("Expected AuthenticationError")
        } catch (e: ProviderError.AuthenticationError) {
            assertTrue(e.message.contains("401"))
        }
    }

    @Test
    fun testHttpError429ThrowsRateLimitError() = runTest {
        val mockClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(429)
                    .message("Too Many Requests")
                    .body("{\"error\":{\"message\":\"Rate limit exceeded\"}}".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()

        val config = ProviderConfig(
            id = "openai_provider",
            type = ModelProviderType.OPENAI,
            displayName = "OpenAI",
            endpointUrl = "https://api.openai.com/v1",
            apiKey = "sk-valid-key"
        )
        val provider = NetworkModelProvider(config, mockClient)

        try {
            provider.generateResponse(CompletionRequest(messages = listOf(ChatMessage(role = MessageRole.USER, content = "Hi"))))
            fail("Expected RateLimitError")
        } catch (e: ProviderError.RateLimitError) {
            assertTrue(e.message.contains("429"))
        }
    }
}
