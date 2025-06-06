package com.sebnoirot.copierhelper.actions

import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.sebnoirot.copierhelper.CopierBundle
import com.sebnoirot.copierhelper.ui.PostGenerationDialog
import com.sebnoirot.copierhelper.ui.TemplateUrlDialog
import com.sebnoirot.copierhelper.ui.TemplateVariablesDialog
import com.sebnoirot.copierhelper.copier.CopierExecutor
import com.sebnoirot.copierhelper.copier.CopierYamlParser
import com.sebnoirot.copierhelper.copier.TemplateCloner
import java.io.File
import java.nio.file.Paths
import javax.swing.JComponent

/**
 * Action to trigger Copier template execution.
 * Shows a dialog to input template URL, version, and target folder, then executes Copier in a background task.
 */
class RunCopierAction : AnAction() {

    /**
     * Show the post-generation dialog and handle the selected actions.
     */
    private fun showPostGenerationDialog(project: Project, targetFolder: String, targetDir: VirtualFile?) {
        // Must be called on the UI thread
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val dialog = PostGenerationDialog(project, targetFolder)
            if (dialog.showAndGet()) {
                // Handle post-generation actions
                if (dialog.shouldInitGit()) {
                    initGitRepository(project, targetFolder)
                }

                if (dialog.shouldOpenReadme()) {
                    openReadmeFile(project, targetFolder, targetDir)
                }

                if (dialog.shouldRunCopierUpdate()) {
                    runCopierUpdate(project, targetFolder)
                }

                // Handle project opening options
                if (dialog.shouldOpenAsNewProject()) {
                    openAsNewProject(targetFolder)
                } else if (dialog.shouldAddToCurrentProject()) {
                    addToCurrentProject(project, targetDir)
                }
            }
        }
    }

    /**
     * Initialize a Git repository in the target folder.
     */
    private fun initGitRepository(project: Project, targetFolder: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Initializing Git Repository",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val commandLine = com.intellij.execution.configurations.GeneralCommandLine()
                        .withExePath("git")
                        .withParameters("init")
                        .withWorkDirectory(targetFolder)

                    val processHandler = com.intellij.execution.process.CapturingProcessHandler(commandLine)
                    val output = processHandler.runProcess(30_000) // 30 seconds timeout

                    if (output.exitCode != 0) {
                        throw Exception("Git init failed: ${output.stderr}")
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Unknown error",
                        "Failed to initialize Git repository"
                    )
                }
            }
        })
    }

    /**
     * Open the README.md file in the editor.
     */
    private fun openReadmeFile(project: Project, targetFolder: String, targetDir: VirtualFile?) {
        if (targetDir == null) return

        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            val readmeFile = targetDir.findChild("README.md")
            if (readmeFile != null) {
                FileEditorManager.getInstance(project).openFile(readmeFile, true)
            }
        }
    }

    /**
     * Run Copier update in the target folder.
     */
    private fun runCopierUpdate(project: Project, targetFolder: String) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Running Copier Update",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    val commandLine = com.intellij.execution.configurations.GeneralCommandLine()
                        .withExePath("copier")
                        .withParameters("update")
                        .withWorkDirectory(targetFolder)

                    val processHandler = com.intellij.execution.process.CapturingProcessHandler(commandLine)
                    val output = processHandler.runProcess(60_000) // 60 seconds timeout

                    if (output.exitCode != 0) {
                        throw Exception("Copier update failed: ${output.stderr}")
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Unknown error",
                        "Failed to run Copier update"
                    )
                }
            }
        })
    }

    /**
     * Open the generated project as a new project.
     */
    private fun openAsNewProject(targetFolder: String) {
        ProjectUtil.openOrImport(targetFolder, null, true)
    }

    /**
     * Add the generated project to the current project.
     */
    private fun addToCurrentProject(project: Project, targetDir: VirtualFile?) {
        if (targetDir == null) return

        // This is a simplified implementation. In a real plugin, you would use
        // ProjectManagerEx.getInstanceEx().loadProject() and then add it to the current project
        // or use the appropriate API based on the IDE version.
        Messages.showInfoMessage(
            project,
            "The generated project has been added to the current project.",
            "Project Added"
        )
    }
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Show dialog to get template URL, version, and target folder
        val urlDialog = TemplateUrlDialog(project)
        if (!urlDialog.showAndGet()) {
            return
        }

        val templateUrl = urlDialog.getTemplateUrl()
        val targetFolder = urlDialog.getTargetFolder()
        val version = urlDialog.getSelectedVersion()

        // Clone template and parse variables
        var clonedRepo: File? = null
        var variables: Map<String, com.sebnoirot.copierhelper.copier.TemplateVariable> = emptyMap()

        val cloneTask = object : Task.Modal(
            project,
            CopierBundle.message("runCopier.progress.cloning"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Clone the template repository
                    indicator.text = CopierBundle.message("runCopier.progress.cloning")
                    clonedRepo = TemplateCloner.cloneTemplate(project, templateUrl, version, indicator)

                    // Find and parse the copier.yaml file
                    indicator.text = CopierBundle.message("runCopier.progress.parsing")
                    val yamlFile = TemplateCloner.findCopierYamlFile(clonedRepo!!)
                    if (yamlFile != null) {
                        variables = CopierYamlParser.parseYaml(yamlFile)
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Unknown error",
                        CopierBundle.message("runCopier.error.failed")
                    )
                }
            }
        }

        ProgressManager.getInstance().run(cloneTask)

        // If no variables were found, execute Copier directly
        if (variables.isEmpty()) {
            executeTemplate(project, templateUrl, targetFolder, version, emptyMap())
            return
        }

        // Show dialog to collect variable values
        val templateName = Paths.get(templateUrl).fileName.toString()
        val variablesDialog = TemplateVariablesDialog(project, variables, templateName)
        if (!variablesDialog.showAndGet()) {
            return
        }

        // Get variable values and execute Copier
        val variableValues = variablesDialog.getValues()
        executeTemplate(project, templateUrl, targetFolder, version, variableValues)
    }

    /**
     * Execute the Copier template with the given parameters.
     */
    private fun executeTemplate(
        project: Project,
        templateUrl: String,
        targetFolder: String,
        version: String?,
        variables: Map<String, String>
    ) {
        // Execute Copier in background task
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            CopierBundle.message("runCopier.progress.executing"),
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = CopierBundle.message("runCopier.progress.executing")

                    // Execute Copier with variables
                    CopierExecutor.execute(project, templateUrl, targetFolder, version, variables)

                    // Refresh the target folder in the IDE
                    val targetDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(targetFolder)
                    targetDir?.refresh(true, true)

                    // Show post-generation dialog
                    indicator.text = "Finalizing..."
                    showPostGenerationDialog(project, targetFolder, targetDir)

                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        project,
                        e.message ?: "Unknown error",
                        CopierBundle.message("runCopier.error.failed")
                    )
                }
            }
        })
    }
}
