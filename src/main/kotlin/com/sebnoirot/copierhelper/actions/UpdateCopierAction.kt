package com.sebnoirot.copierhelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.sebnoirot.copierhelper.copier.CopierExecutor
import com.sebnoirot.copierhelper.copier.GitVersionFetcher
import com.sebnoirot.copierhelper.update.CopierUpdateChecker
import java.io.File

/**
 * Action to update a Copier project.
 * Checks if the current project contains a .copier-answers.yml file and runs the Copier update command.
 */
class UpdateCopierAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val basePath = project.basePath ?: return

        // Check if the project contains a .copier-answers.yml file
        val answersFile = File(basePath, CopierUpdateChecker.ANSWERS_FILE)
        if (!answersFile.exists()) {
            Messages.showErrorDialog(
                project,
                "This project does not contain a .copier-answers.yml file. It may not be a Copier project.",
                "Copier Update Failed"
            )
            return
        }

        // Check if the repository has uncommitted changes
        if (GitVersionFetcher.hasUncommittedChanges(basePath)) {
            Messages.showErrorDialog(
                project,
                "Destination repository is dirty; cannot continue. Please commit or stash your local changes and retry.",
                "Copier Update Failed"
            )
            return
        }

        // Run Copier update in background task
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
                    // Use invokeLater to avoid "Do not perform a synchronous refresh under read lock" error
                    val projectDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(basePath)
                    if (projectDir != null) {
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            com.intellij.openapi.vfs.VfsUtil.markDirtyAndRefresh(false, true, true, projectDir)
                        }
                    }

                    // Show success message
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showInfoMessage(
                            project,
                            "The template has been successfully updated.",
                            "Copier Template Updated"
                        )
                    }
                } catch (e: Exception) {
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            e.message ?: "Unknown error",
                            "Copier Update Failed"
                        )
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if a project is open
        val project = e.project
        e.presentation.isEnabled = project != null
    }
}
