package com.example.aura.core.tools

import android.content.Context
import com.example.aura.core.security.SecurityLevel
import java.io.File

/**
 * Functional category of an agent skill/tool.
 */
enum class ToolCategory {
    SYSTEM,
    FILE_STORAGE,
    ANDROID_OS,
    LOCAL_MEMORY,
    WEB_ACCESS,
    COMMUNICATION,
    AUTOMATION,
    PRODUCTIVITY
}

/**
 * Execution state of a tool invocation.
 */
enum class ToolExecutionState {
    IDLE,
    VALIDATING_INPUT,
    CHECKING_PERMISSIONS,
    AWAITING_APPROVAL,
    EXECUTING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Execution context provided to tools, containing sandboxed directories, Android context,
 * and cancellation coordinates.
 */
data class ToolExecutionContext(
    val appContext: Context,
    val sandboxDirectory: File,
    val sessionId: String? = null
)

/**
 * Parameter definition for tool schemas.
 */
data class ToolParameter(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = false,
    val enumValues: List<String> = emptyList()
)

/**
 * Schema defining the input or output parameters of a tool.
 */
data class ToolSchema(
    val parameters: List<ToolParameter>
) {
    fun toJsonSchema(): String {
        val props = parameters.joinToString(",") { param ->
            "\"${param.name}\":{\"type\":\"${param.type}\",\"description\":\"${param.description}\"}"
        }
        val reqs = parameters.filter { it.required }.joinToString(",") { "\"${it.name}\"" }
        return "{\"type\":\"object\",\"properties\":{$props},\"required\":[$reqs]}"
    }

    /**
     * Validates that all required parameters are provided in the input map.
     */
    fun validateInput(input: Map<String, Any?>): Result<Unit> {
        for (param in parameters) {
            if (param.required && (!input.containsKey(param.name) || input[param.name] == null)) {
                return Result.failure(IllegalArgumentException("Missing required parameter: '${param.name}'"))
            }
        }
        return Result.success(Unit)
    }
}

/**
 * Result of a tool execution.
 */
sealed interface ToolExecutionResult {
    data class Success(
        val output: String,
        val structuredData: Map<String, Any?> = emptyMap()
    ) : ToolExecutionResult

    data class Failure(
        val errorMessage: String,
        val cause: Throwable? = null
    ) : ToolExecutionResult

    data class DeniedByPolicy(
        val reason: String
    ) : ToolExecutionResult

    data class Cancelled(
        val reason: String
    ) : ToolExecutionResult
}

/**
 * Universal interface for all Aura Skills and Tools.
 * Every tool defines its security classification, category, input/output schemas,
 * Android runtime permissions, and execution logic.
 */
interface AuraTool {
    /** Unique tool identifier (e.g., "web_access", "sandboxed_file", "notifications") */
    val id: String

    /** Human-readable tool title */
    val name: String

    /** Plain language description presented to LLMs for tool selection */
    val description: String

    /** Categorization */
    val category: ToolCategory

    /** Security classification governing whether execution requires user approval */
    val securityLevel: SecurityLevel

    /** Android runtime permissions required before this tool can run (e.g. RECORD_AUDIO, POST_NOTIFICATIONS) */
    val permissionRequirements: Set<String> get() = emptySet()

    /** Input parameter schema */
    val inputSchema: ToolSchema

    /** Output parameter schema */
    val outputSchema: ToolSchema get() = ToolSchema(listOf(ToolParameter("output", "string", "Result of operation")))

    /** Stage 1 backwards compatibility */
    val schema: ToolSchema get() = inputSchema

    /**
     * Executes the tool with the given parameters in a suspended context.
     * Implementations must support cooperative coroutine cancellation.
     */
    suspend fun execute(context: ToolExecutionContext, arguments: Map<String, Any?>): ToolExecutionResult
}
