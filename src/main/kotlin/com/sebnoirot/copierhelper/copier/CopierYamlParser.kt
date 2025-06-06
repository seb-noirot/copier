package com.sebnoirot.copierhelper.copier

import com.intellij.openapi.diagnostic.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * Parses Copier YAML configuration files.
 */
object CopierYamlParser {
    private val LOG = Logger.getInstance(CopierYamlParser::class.java)

    /**
     * Parse a Copier YAML configuration file.
     *
     * @param file The YAML file to parse
     * @return A map of variable names to their definitions
     */
    fun parseYaml(file: File): Map<String, TemplateVariable> {
        LOG.info("Parsing Copier YAML file: ${file.absolutePath}")

        val yaml = Yaml()
        val variables = mutableMapOf<String, TemplateVariable>()

        try {
            FileInputStream(file).use { inputStream ->
                val yamlData = yaml.load<Map<String, Any>>(inputStream)

                // First check for variables in dedicated sections
                val variablesSection = yamlData["_variables"] as? Map<String, Any>
                    ?: yamlData["variables"] as? Map<String, Any>

                // Process variables from dedicated section if found
                if (variablesSection != null) {
                    for ((name, definition) in variablesSection) {
                        val variable = parseVariable(name, definition)
                        variables[name] = variable
                    }
                } else {
                    // If no dedicated variables section, look for variables at root level
                    // Exclude keys that start with underscore as they are typically directives
                    for ((name, definition) in yamlData) {
                        if (!name.startsWith("_") && definition is Map<*, *>) {
                            // Check if this looks like a variable definition
                            // Either it has type/help/default/choices keys
                            // Or it's a simple key-value pair at the root
                            val isVariableDefinition = definition.containsKey("type") || 
                                                      definition.containsKey("help") || 
                                                      definition.containsKey("default") || 
                                                      definition.containsKey("choices")

                            if (isVariableDefinition) {
                                val variable = parseVariable(name, definition)
                                variables[name] = variable
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse YAML file: ${e.message}", e)
            throw e
        }

        LOG.info("Parsed ${variables.size} variables from YAML file")
        return variables
    }

    /**
     * Parse a variable definition.
     *
     * @param name The variable name
     * @param definition The variable definition from the YAML file
     * @return A TemplateVariable object
     */
    private fun parseVariable(name: String, definition: Any): TemplateVariable {
        return when (definition) {
            is Map<*, *> -> {
                // Complex variable definition with type, default, help, etc.
                val type = (definition["type"] as? String) ?: inferType(definition["default"])
                val defaultValue = definition["default"]?.toString() ?: ""
                val help = definition["help"] as? String ?: ""
                val choices = (definition["choices"] as? List<*>)?.map { it.toString() } ?: emptyList()

                TemplateVariable(
                    name = name,
                    type = type,
                    defaultValue = defaultValue,
                    help = help,
                    choices = choices
                )
            }
            else -> {
                // Simple variable definition with just a default value
                TemplateVariable(
                    name = name,
                    type = inferType(definition),
                    defaultValue = definition?.toString() ?: "",
                    help = "",
                    choices = emptyList()
                )
            }
        }
    }

    /**
     * Infer the type of a variable from its default value.
     *
     * @param value The default value
     * @return The inferred type
     */
    private fun inferType(value: Any?): String {
        return when (value) {
            is Boolean -> "bool"
            is Number -> "number"
            is String -> "str"
            is List<*> -> "list"
            else -> "str" // Default to string
        }
    }
}

/**
 * Represents a variable in a Copier template.
 */
data class TemplateVariable(
    val name: String,
    val type: String,
    val defaultValue: String,
    val help: String,
    val choices: List<String>
)
