package com.sebnoirot.copierhelper.update

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Startup activity to check for Copier template updates when the project opens.
 */
class CopierUpdateStartupActivity : ProjectActivity {
    private val LOG = Logger.getInstance(CopierUpdateStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        LOG.info("Running Copier update startup activity")

        // Get the update notifier service
        val updateNotifier = project.getService(CopierUpdateNotifier::class.java)

        // Register file listener
        updateNotifier.registerFileListener()

        // Check for updates
        updateNotifier.checkForUpdatesAndNotify()
    }
}