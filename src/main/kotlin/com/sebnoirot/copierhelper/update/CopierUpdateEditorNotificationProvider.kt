package com.sebnoirot.copierhelper.update

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Key
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.sebnoirot.copierhelper.copier.CopierExecutor
import java.io.File
import java.util.function.Function
import javax.swing.JComponent

/**
 * Editor notification provider for Copier answers files.
 * Shows a notification panel when a .copier-answers.yml file is opened in the editor.
 */
class CopierUpdateEditorNotificationProvider : EditorNotificationProvider {
    private val LOG = Logger.getInstance(CopierUpdateEditorNotificationProvider::class.java)

    companion object {
        private val KEY = Key.create<EditorNotificationPanel>("copier.update.notification")
        private const val ANSWERS_FILE = ".copier-answers.yml"
    }

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<FileEditor, JComponent?>? {
        // Check if this is a .copier-answers.yml file
        if (file.name != ANSWERS_FILE) {
            return null
        }

        LOG.info("Creating notification panel for ${file.path}")

        try {
            // Use the CopierUpdateChecker service to check for updates
            val updateChecker = project.service<CopierUpdateChecker>()
            val updateInfo = updateChecker.checkForUpdates()

            if (updateInfo == null) {
                // No update available or couldn't check
                // Get the current version to display in the "up to date" panel
                val answersFile = File(file.path)
                val answers = CopierAnswersParser.parseAnswers(answersFile)
                val currentVersion = CopierAnswersParser.extractVersion(answers)

                if (currentVersion == null) {
                    LOG.info("Could not extract version from answers file")
                    val errorPanel = createErrorPanel("Could not check template version", "Missing version in answers file")
                    return Function { errorPanel }
                }

                LOG.info("No update available. Current version: $currentVersion")
                val upToDatePanel = createUpToDatePanel(currentVersion)
                return Function { upToDatePanel }
            } else {
                // Update available
                val (sourcePath, currentVersion, newestVersion) = updateInfo
                LOG.info("Update available: $newestVersion (current: $currentVersion)")
                val updatePanel = createUpdatePanel(project, sourcePath, currentVersion, newestVersion)
                return Function { updatePanel }
            }
        } catch (e: Exception) {
            LOG.error("Failed to check for updates: ${e.message}", e)
            val errorPanel = createErrorPanel("Could not check template version", e.message ?: "Unknown error")
            return Function { errorPanel }
        }
    }


    /**
     * Create a panel for when an update is available.
     */
    private fun createUpdatePanel(
        project: Project,
        sourcePath: String,
        currentVersion: String,
        newestVersion: String
    ): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text = "Copier template update available: $newestVersion (current: $currentVersion)"

        panel.createActionLabel("Update now") {
            // Run copier update in the project directory
            val basePath = project.basePath ?: return@createActionLabel

            // Run the update in a background task
            ProgressManager.getInstance().run(object : Task.Backgroundable(
                project,
                "Running Copier Update",
                true
            ) {
                override fun run(indicator: ProgressIndicator) {
                    try {
                        indicator.text = "Running Copier update..."

                        // Execute Copier update
                        CopierExecutor.update(project, basePath)

                        // Refresh the project directory in the IDE
                        val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath)
                        if (projectDir != null) {
                            ApplicationManager.getApplication().invokeLater {
                                VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
                            }
                        }

                        // Update the panel text on the EDT
                        ApplicationManager.getApplication().invokeLater {
                            panel.text = "Template updated successfully to $newestVersion"
                        }
                    } catch (e: Exception) {
                        LOG.error("Failed to update template: ${e.message}", e)

                        // Update the panel text on the EDT
                        ApplicationManager.getApplication().invokeLater {
                            panel.text = "Failed to update template: ${e.message}"
                        }
                    }
                }
            })
        }

        return panel
    }

    /**
     * Create a panel for when the template is up to date.
     */
    private fun createUpToDatePanel(currentVersion: String): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text = "Template is up to date (version: $currentVersion)"
        return panel
    }

    /**
     * Create a panel for when there is an error checking for updates.
     */
    private fun createErrorPanel(title: String, message: String): EditorNotificationPanel {
        val panel = EditorNotificationPanel()
        panel.text = "$title: $message"
        return panel
    }
}
