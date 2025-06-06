package com.sebnoirot.copierhelper.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.sebnoirot.copierhelper.settings.CopierSettingsConfigurable

/**
 * Action to open the Copier Helper settings panel.
 */
class OpenCopierSettingsAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // Open the Copier Helper settings panel
        ShowSettingsUtil.getInstance().showSettingsDialog(
            project,
            CopierSettingsConfigurable::class.java
        )
    }
}