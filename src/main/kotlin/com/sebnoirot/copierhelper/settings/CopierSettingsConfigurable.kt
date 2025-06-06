package com.sebnoirot.copierhelper.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/**
 * Configurable for Copier Helper settings.
 */
class CopierSettingsConfigurable : Configurable {
    private var settingsComponent: CopierSettingsComponent? = null

    override fun getDisplayName(): String = "Copier Helper"

    override fun getPreferredFocusedComponent(): JComponent? {
        return settingsComponent?.getPreferredFocusComponent()
    }

    override fun createComponent(): JComponent? {
        settingsComponent = CopierSettingsComponent()
        return settingsComponent?.getPanel()
    }

    override fun isModified(): Boolean {
        val settings = CopierSettings.getInstance()
        val component = settingsComponent ?: return false

        return component.getCopierPath() != settings.copierPath ||
               component.getDefaultOutputFolder() != settings.defaultOutputFolder ||
               component.getAutoOpenGeneratedProject() != settings.autoOpenGeneratedProject ||
               component.getConflictStrategy() != settings.conflictStrategy ||
               component.getSkipAnsweredQuestions() != settings.skipAnsweredQuestions ||
               component.getUpdateToLatestVersion() != settings.updateToLatestVersion ||
               component.getDefaultTemplates() != settings.defaultTemplates
    }

    override fun apply() {
        val settings = CopierSettings.getInstance()
        val component = settingsComponent ?: return

        settings.copierPath = component.getCopierPath()
        settings.defaultOutputFolder = component.getDefaultOutputFolder()
        settings.autoOpenGeneratedProject = component.getAutoOpenGeneratedProject()
        settings.conflictStrategy = component.getConflictStrategy()
        settings.skipAnsweredQuestions = component.getSkipAnsweredQuestions()
        settings.updateToLatestVersion = component.getUpdateToLatestVersion()

        // Update templates list
        settings.defaultTemplates.clear()
        settings.defaultTemplates.addAll(component.getDefaultTemplates())
    }

    override fun reset() {
        val settings = CopierSettings.getInstance()
        val component = settingsComponent ?: return

        component.setCopierPath(settings.copierPath)
        component.setDefaultOutputFolder(settings.defaultOutputFolder)
        component.setAutoOpenGeneratedProject(settings.autoOpenGeneratedProject)
        component.setConflictStrategy(settings.conflictStrategy)
        component.setSkipAnsweredQuestions(settings.skipAnsweredQuestions)
        component.setUpdateToLatestVersion(settings.updateToLatestVersion)
        component.setDefaultTemplates(settings.defaultTemplates)
    }

    override fun disposeUIResources() {
        settingsComponent = null
    }
}
