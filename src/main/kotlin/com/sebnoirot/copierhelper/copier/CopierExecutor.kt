package com.sebnoirot.copierhelper.copier

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.ProcessOutput
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files

/**
 * Executor for Copier CLI commands.
 */
object CopierExecutor {
    private val LOG = Logger.getInstance(CopierExecutor::class.java)

    /**
     * Execute Copier template with the given URL, version, and target folder.
     *
     * @param project The current project
     * @param templateUrl The URL of the Copier template
     * @param targetFolder The target folder where the template will be generated
     * @param version Optional version (tag or branch) to use
     * @param variables Optional map of variable names to values
     * @return The process output
     * @throws Exception If the execution fails
     */
    fun execute(
        project: Project, 
        templateUrl: String, 
        targetFolder: String,
        version: String? = null,
        variables: Map<String, String> = emptyMap()
    ): ProcessOutput {
        LOG.info("Executing Copier with template URL: $templateUrl, version: $version, and target folder: $targetFolder")
        LOG.info("Variables: $variables")

        // Create target directory if it doesn't exist
        val targetDir = File(targetFolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Check if this is an SSH Git URL (git@hostname:path/repo.git)
        val isSSHUrl = templateUrl.startsWith("git@") && templateUrl.contains(":")

        // Get the configured Copier path from settings
        val settings = com.sebnoirot.copierhelper.settings.CopierSettings.getInstance()
        val copierPath = settings.copierPath

        // Build command line
        val commandLine = GeneralCommandLine()
            .withExePath(copierPath) // Use the configured path
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(project.basePath)

        // For SSH URLs, we need to pass the URL and version separately
        // Otherwise, we can combine them with @version
        if (isSSHUrl && version != null && version.isNotBlank() && !version.startsWith("---")) {
            commandLine.withParameters("copy", templateUrl, targetFolder, "--vcs-ref", version)
        } else {
            // Build template URL with version if specified (for non-SSH URLs)
            val fullTemplateUrl = if (!isSSHUrl && version != null && version.isNotBlank() && 
                                     !version.startsWith("---")) {
                "$templateUrl@$version"
            } else {
                templateUrl
            }
            commandLine.withParameters("copy", fullTemplateUrl, targetFolder)
        }

        // Add data file if variables are provided
        var dataFile: File? = null
        if (variables.isNotEmpty()) {
            dataFile = createDataFile(variables)
            commandLine.addParameters("--data-file", dataFile.absolutePath)
        }

        LOG.info("Command line: ${commandLine.commandLineString}")

        try {
            // Execute command
            val processHandler = CapturingProcessHandler(commandLine)
            val output = processHandler.runProcess(60_000) // 60 seconds timeout

            if (output.exitCode != 0) {
                val errorMessage = "Copier execution failed with exit code ${output.exitCode}: ${output.stderr}"
                LOG.error(errorMessage)
                throw Exception(errorMessage)
            }

            LOG.info("Copier execution successful")
            return output
        } finally {
            // Clean up temporary data file
            dataFile?.delete()
        }
    }

    /**
     * Create a temporary YAML file with the variable values.
     *
     * @param variables Map of variable names to values
     * @return The temporary file
     */
    private fun createDataFile(variables: Map<String, String>): File {
        val tempFile = Files.createTempFile("copier-data-", ".yml").toFile()
        tempFile.deleteOnExit()

        LOG.info("Creating data file: ${tempFile.absolutePath}")

        // Convert variables to appropriate types
        val typedVariables = variables.mapValues { (_, value) ->
            when {
                value.equals("true", ignoreCase = true) -> true
                value.equals("false", ignoreCase = true) -> false
                value.toIntOrNull() != null -> value.toInt()
                value.toDoubleOrNull() != null -> value.toDouble()
                else -> value
            }
        }

        // Write variables to YAML file
        FileWriter(tempFile).use { writer ->
            val yaml = Yaml()
            yaml.dump(typedVariables, writer)
        }

        return tempFile
    }

    /**
     * Execute Copier update in the given directory.
     *
     * @param project The current project
     * @param directory The directory where to run the update
     * @return The process output
     * @throws Exception If the execution fails
     */
    fun update(project: Project, directory: String): ProcessOutput {
        LOG.info("Executing Copier update in directory: $directory")

        // Check if the repository has uncommitted changes
        if (GitVersionFetcher.hasUncommittedChanges(directory)) {
            val errorMessage = "Destination repository is dirty; cannot continue. Please commit or stash your local changes and retry."
            LOG.error(errorMessage)
            throw Exception(errorMessage)
        }

        // Get settings
        val settings = com.sebnoirot.copierhelper.settings.CopierSettings.getInstance()
        val conflictStrategy = settings.conflictStrategy
        val skipAnsweredQuestions = settings.skipAnsweredQuestions
        val updateToLatestVersion = settings.updateToLatestVersion
        val copierPath = settings.copierPath

        // Build command line
        val commandLine = GeneralCommandLine()
            .withExePath(copierPath) // Use the configured path
            .withCharset(StandardCharsets.UTF_8)
            .withWorkDirectory(directory)
            .withParameters("update", "--conflict", conflictStrategy)

        // Add --skip-answered flag if enabled
        if (skipAnsweredQuestions) {
            commandLine.addParameter("--skip-answered")
        }

        // Add --vcs-ref=HEAD flag if enabled
        if (updateToLatestVersion) {
            commandLine.addParameters("--vcs-ref", "HEAD")
        }

        LOG.info("Command line: ${commandLine.commandLineString}")

        // Execute command off the EDT using a pooled thread
        val processHandler = CapturingProcessHandler(commandLine)

        // Run the process in a pooled thread to avoid blocking the EDT
        val future = com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread<ProcessOutput> {
            val output = processHandler.runProcess(60_000) // 60 seconds timeout

            if (output.exitCode != 0) {
                val errorMessage = "Copier update failed with exit code ${output.exitCode}: ${output.stderr}"
                LOG.error(errorMessage)
                throw Exception(errorMessage)
            }

            LOG.info("Copier update successful")
            output
        }

        // Wait for the result
        return future.get()
    }
}
