package com.sebnoirot.copierhelper.copier

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Clones a Copier template repository into a temporary directory.
 */
object TemplateCloner {
    private val LOG = Logger.getInstance(TemplateCloner::class.java)
    
    /**
     * Clone a template repository into a temporary directory.
     *
     * @param project The current project
     * @param templateUrl The URL of the template repository
     * @param version Optional version (tag or branch) to clone
     * @param indicator Optional progress indicator
     * @return The path to the cloned repository
     * @throws Exception If cloning fails
     */
    fun cloneTemplate(
        project: Project,
        templateUrl: String,
        version: String? = null,
        indicator: ProgressIndicator? = null
    ): File {
        indicator?.text = "Cloning template repository..."
        LOG.info("Cloning template repository: $templateUrl, version: $version")
        
        // Create temporary directory
        val tempDir = Files.createTempDirectory("copier-template-").toFile()
        tempDir.deleteOnExit()
        
        LOG.info("Created temporary directory: ${tempDir.absolutePath}")
        
        // Build git clone command
        val commandLine = GeneralCommandLine()
            .withExePath("git")
            .withParameters("clone", templateUrl, tempDir.absolutePath)
            .withCharset(StandardCharsets.UTF_8)
        
        // Add version parameter if specified
        if (version != null && version.isNotBlank() && !version.startsWith("---")) {
            commandLine.addParameters("--branch", version)
        }
        
        LOG.info("Command line: ${commandLine.commandLineString}")
        
        // Execute command
        val processHandler = CapturingProcessHandler(commandLine)
        val output = processHandler.runProcess(60_000) // 60 seconds timeout
        
        if (output.exitCode != 0) {
            val errorMessage = "Git clone failed with exit code ${output.exitCode}: ${output.stderr}"
            LOG.error(errorMessage)
            throw Exception(errorMessage)
        }
        
        LOG.info("Template repository cloned successfully to ${tempDir.absolutePath}")
        return tempDir
    }
    
    /**
     * Find the copier.yaml file in the cloned repository.
     *
     * @param repoDir The directory of the cloned repository
     * @return The copier.yaml file, or null if not found
     */
    fun findCopierYamlFile(repoDir: File): File? {
        // Check for copier.yaml or .copier.yaml
        val possibleNames = listOf("copier.yaml", ".copier.yaml", "copier.yml", ".copier.yml")
        
        for (name in possibleNames) {
            val file = File(repoDir, name)
            if (file.exists() && file.isFile) {
                LOG.info("Found Copier configuration file: ${file.absolutePath}")
                return file
            }
        }
        
        LOG.warn("No Copier configuration file found in ${repoDir.absolutePath}")
        return null
    }
}