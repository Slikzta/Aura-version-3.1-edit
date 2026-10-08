package com.example.aura.core.tools

import com.example.aura.core.security.SecurityLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Real HTTP URL fetcher tool for web investigation.
 * Enforces security boundaries: only allows valid HTTP/HTTPS URLs.
 * Risk: LOW_RISK for standard reads.
 */
class WebAccessTool(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) : AuraTool {

    override val id: String = "web_access"
    override val name: String = "Web Access & URL Fetcher"
    override val description: String = "Fetches plain text or documentation from a publicly accessible HTTP or HTTPS web URL."
    override val category: ToolCategory = ToolCategory.WEB_ACCESS
    override val securityLevel: SecurityLevel = SecurityLevel.LOW_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "url",
                type = "string",
                description = "The target HTTP/HTTPS web address to fetch",
                required = true
            ),
            ToolParameter(
                name = "max_characters",
                type = "integer",
                description = "Maximum text characters to return (default: 4000)",
                required = false
            )
        )
    )

    override val outputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter("status_code", "integer", "HTTP status code"),
            ToolParameter("content", "string", "Extracted text content from web page")
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult = withContext(Dispatchers.IO) {
        val url = arguments["url"]?.toString()?.trim()
            ?: return@withContext ToolExecutionResult.Failure("Missing required 'url' parameter.")

        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return@withContext ToolExecutionResult.Failure("Invalid URL scheme: only http:// and https:// addresses are allowed.")
        }

        val maxChars = (arguments["max_characters"] as? Number)?.toInt() ?: 4000

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Aura-Agent/2.0 (Android; Local-First)")
                .build()

            val response = httpClient.newCall(request).execute()
            val code = response.code
            val body = response.body?.string() ?: ""

            // Simple HTML tag stripper for text extraction
            val plainText = body.replace(Regex("<[^>]*>"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(maxChars)

            ToolExecutionResult.Success(
                output = "HTTP $code from $url:\n${plainText.ifBlank { "[Empty Body]" }}",
                structuredData = mapOf(
                    "status_code" to code,
                    "length" to plainText.length,
                    "url" to url
                )
            )
        } catch (e: Exception) {
            ToolExecutionResult.Failure("Web access failed for '$url': ${e.message}", e)
        }
    }
}
