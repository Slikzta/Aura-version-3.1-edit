package com.example.aura.core.tools

import com.example.aura.core.security.SecurityLevel
import java.io.File

/**
 * Sandboxed file system tool operating strictly within Aura's private internal storage.
 * Cannot escape to the host root file system.
 * Security: SENSITIVE (mutates local storage).
 */
class SandboxedFileTool : AuraTool {
    override val id: String = "sandboxed_file"
    override val name: String = "Sandboxed File Manager"
    override val description: String = "Reads, writes, lists, or deletes files inside Aura's secure private sandbox directory."
    override val category: ToolCategory = ToolCategory.FILE_STORAGE
    override val securityLevel: SecurityLevel = SecurityLevel.MEDIUM_RISK

    override val inputSchema: ToolSchema = ToolSchema(
        parameters = listOf(
            ToolParameter(
                name = "action",
                type = "string",
                description = "Operation to perform: 'read', 'write', 'list', or 'delete'",
                required = true,
                enumValues = listOf("read", "write", "list", "delete")
            ),
            ToolParameter(
                name = "file_name",
                type = "string",
                description = "Name of the target file (relative to sandbox)",
                required = false
            ),
            ToolParameter(
                name = "content",
                type = "string",
                description = "Content to write when action is 'write'",
                required = false
            )
        )
    )

    override suspend fun execute(
        context: ToolExecutionContext,
        arguments: Map<String, Any?>
    ): ToolExecutionResult {
        return try {
            val sandbox = context.sandboxDirectory
            if (!sandbox.exists()) {
                sandbox.mkdirs()
            }

            val action = arguments["action"]?.toString()?.lowercase() ?: "list"
            val fileName = arguments["file_name"]?.toString()?.replace(Regex("[/\\\\]"), "_")

            when (action) {
                "list" -> {
                    val files = sandbox.listFiles()?.map { "${it.name} (${it.length()} bytes)" } ?: emptyList()
                    val output = if (files.isEmpty()) "Sandbox is empty." else "Sandbox files:\n" + files.joinToString("\n")
                    ToolExecutionResult.Success(output = output, structuredData = mapOf("count" to files.size))
                }
                "write" -> {
                    if (fileName.isNullOrBlank()) {
                        return ToolExecutionResult.Failure("Missing 'file_name' for write operation.")
                    }
                    val content = arguments["content"]?.toString() ?: ""
                    val targetFile = File(sandbox, fileName)
                    targetFile.writeText(content)
                    ToolExecutionResult.Success("File '$fileName' saved (${content.length} characters).")
                }
                "read" -> {
                    if (fileName.isNullOrBlank()) {
                        return ToolExecutionResult.Failure("Missing 'file_name' for read operation.")
                    }
                    val targetFile = File(sandbox, fileName)
                    if (!targetFile.exists()) {
                        return ToolExecutionResult.Failure("File '$fileName' does not exist in sandbox.")
                    }
                    val content = targetFile.readText()
                    ToolExecutionResult.Success("Content of '$fileName':\n$content")
                }
                "delete" -> {
                    if (fileName.isNullOrBlank()) {
                        return ToolExecutionResult.Failure("Missing 'file_name' for delete operation.")
                    }
                    val targetFile = File(sandbox, fileName)
                    val deleted = targetFile.delete()
                    if (deleted) {
                        ToolExecutionResult.Success("File '$fileName' deleted.")
                    } else {
                        ToolExecutionResult.Failure("File '$fileName' could not be deleted or does not exist.")
                    }
                }
                else -> ToolExecutionResult.Failure("Unsupported action: $action")
            }
        } catch (e: Exception) {
            ToolExecutionResult.Failure("File tool error: ${e.message}", e)
        }
    }
}
