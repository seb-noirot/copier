package com.sebnoirot.copierhelper.update

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VfsUtil
import com.sebnoirot.copierhelper.copier.CopierExecutor
import java.nio.file.Paths

/**
 * Service that notifies users about available Copier template updates.
 */
@Service(Service.Level.PROJECT)
class CopierUpdateNotifier(private val project: Project) {
    private val LOG = Logger.getInstance(CopierUpdateNotifier::class.java)
    private val updateChecker = project.getService(CopierUpdateChecker::class.java)

    companion object {
        const val NOTIFICATION_GROUP_ID = "Copier Helper"
    }

    /**
     * Check for updates and show a notification if an update is available.
     */
    fun checkForUpdatesAndNotify() {
        val updateInfo = updateChecker.checkForUpdates() ?: return
        val (sourcePath, currentVersion, newestVersion) = updateInfo

        LOG.info("Showing update notification for template: $sourcePath, current version: $currentVersion, newest version: $newestVersion")

        // Create notification
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(
                "Copier Template Update Available",
                "A new version of the Copier template is available: $newestVersion (current: $currentVersion)",
                NotificationType.INFORMATION
            )

        // Add action to update the template
        notification.addAction(object : NotificationAction("Update Now") {
            override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                updateTemplate(sourcePath)
                notification.expire()
            }
        })

        notification.isImportant = true

        // Show notification
        Notifications.Bus.notify(notification, project)
    }

    /**
     * Update the template.
     *
     * @param templateUrl The template URL
     */
    private fun updateTemplate(templateUrl: String) {
        LOG.info("Updating template: $templateUrl")

        val basePath = project.basePath ?: return

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

                    // Show success notification on the EDT
                    ApplicationManager.getApplication().invokeLater {
                        val notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(
                                "Copier Template Updated",
                                "The template has been successfully updated.",
                                NotificationType.INFORMATION
                            )

                        Notifications.Bus.notify(notification, project)
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to update template: ${e.message}", e)

                    // Show error notification on the EDT
                    ApplicationManager.getApplication().invokeLater {
                        val notification = NotificationGroupManager.getInstance()
                            .getNotificationGroup(NOTIFICATION_GROUP_ID)
                            .createNotification(
                                "Copier Template Update Failed",
                                "Failed to update the template: ${e.message}",
                                NotificationType.ERROR
                            )

                        Notifications.Bus.notify(notification, project)
                    }
                }
            }
        })
    }

    /**
     * Register a file listener to detect when .copier-answers.yml is opened.
     */
    fun registerFileListener() {
        VirtualFileManager.getInstance().addAsyncFileListener(object : AsyncFileListener {
            override fun prepareChange(events: List<VFileEvent>): AsyncFileListener.ChangeApplier? {
                // Check if any of the events is related to the .copier-answers.yml file
                val hasAnswersFileEvent = events.any { event ->
                    val file = event.file
                    file != null && file.name == CopierUpdateChecker.ANSWERS_FILE
                }

                if (!hasAnswersFileEvent) {
                    return null
                }

                // Return a ChangeApplier that will be called after the changes are applied
                return object : AsyncFileListener.ChangeApplier {
                    override fun afterVfsChange() {
                        LOG.info("Copier answers file changed, checking for updates")
                        ApplicationManager.getApplication().invokeLater {
                            checkForUpdatesAndNotify()
                        }
                    }
                }
            }
        }, project)
    }
}

