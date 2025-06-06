package com.sebnoirot.copierhelper.update

import com.intellij.openapi.diagnostic.Logger
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

/**
 * Parses Copier answers YAML files (.copier-answers.yml).
 */
object CopierAnswersParser {
    private val LOG = Logger.getInstance(CopierAnswersParser::class.java)

    /**
     * Parse a Copier answers YAML file.
     *
     * @param file The YAML file to parse
     * @return A map of all answers
     */
    fun parseAnswers(file: File): Map<String, Any> {
        LOG.info("Parsing Copier answers file: ${file.absolutePath}")

        val yaml = Yaml()
        
        try {
            FileInputStream(file).use { inputStream ->
                @Suppress("UNCHECKED_CAST")
                return yaml.load(inputStream) as? Map<String, Any> ?: emptyMap()
            }
        } catch (e: Exception) {
            LOG.error("Failed to parse answers file: ${e.message}", e)
            return emptyMap()
        }
    }

    /**
     * Extract template source path from answers.
     *
     * @param answers The parsed answers map
     * @return The template source path or null if not found
     */
    fun extractSourcePath(answers: Map<String, Any>): String? {
        return answers["_src_path"] as? String
    }

    /**
     * Extract template version from answers.
     *
     * @param answers The parsed answers map
     * @return The template version or null if not found
     */
    fun extractVersion(answers: Map<String, Any>): String? {
        return answers["_commit"] as? String ?: answers["_version"] as? String
    }
}