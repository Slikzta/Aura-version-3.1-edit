package com.example.aura.core.tools

import com.example.aura.core.provider.ToolDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages registration, discovery, and toggling of agent tools and skills.
 */
class ToolRegistry {

    private val toolsMap = ConcurrentHashMap<String, AuraTool>()
    private val _enabledToolIds = MutableStateFlow<Set<String>>(emptySet())
    val enabledToolIds: StateFlow<Set<String>> = _enabledToolIds.asStateFlow()

    private val _toolsList = MutableStateFlow<List<AuraTool>>(emptyList())
    val toolsList: StateFlow<List<AuraTool>> = _toolsList.asStateFlow()

    fun registerTool(tool: AuraTool, enabledByDefault: Boolean = true) {
        toolsMap[tool.id] = tool
        _toolsList.value = toolsMap.values.toList()
        if (enabledByDefault) {
            _enabledToolIds.update { it + tool.id }
        }
    }

    fun getTool(idOrName: String): AuraTool? {
        return toolsMap[idOrName]
            ?: toolsMap.values.find {
                it.id.equals(idOrName, ignoreCase = true) ||
                it.name.equals(idOrName, ignoreCase = true)
            }
    }

    fun setToolEnabled(toolId: String, enabled: Boolean) {
        _enabledToolIds.update { set ->
            if (enabled) set + toolId else set - toolId
        }
    }

    fun getActiveTools(): List<AuraTool> {
        val enabled = _enabledToolIds.value
        return toolsMap.values.filter { enabled.contains(it.id) }
    }

    /**
     * Converts enabled tools into provider-agnostic [ToolDefinition]s for LLM prompt context.
     */
    fun toToolDefinitions(): List<ToolDefinition> {
        return getActiveTools().map { tool ->
            ToolDefinition(
                name = tool.id,
                description = tool.description,
                parametersJsonSchema = tool.schema.toJsonSchema()
            )
        }
    }
}
