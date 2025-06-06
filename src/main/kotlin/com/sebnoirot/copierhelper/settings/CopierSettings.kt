package com.sebnoirot.copierhelper.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent settings for the Copier Helper plugin.
 */
@State(
    name = "com.sebnoirot.copierhelper.settings.CopierSettings",
    storages = [Storage("CopierHelperSettings.xml")]
)
class CopierSettings : PersistentStateComponent<CopierSettings> {

    /**
     * Path to the Copier CLI executable.
     */
    var copierPath: String = "copier"

    /**
     * List of default templates with names and URLs.
     */
    var defaultTemplates: MutableList<TemplateEntry> = mutableListOf()

    /**
     * Default output folder for generated projects.
     */
    var defaultOutputFolder: String = ""

    /**
     * Whether to automatically open generated projects.
     */
    var autoOpenGeneratedProject: Boolean = true

    /**
     * Conflict handling strategy for Copier update.
     * Options: "inline" (default) or "rej"
     */
    var conflictStrategy: String = "inline"

    /**
     * Whether to skip already answered questions during update.
     */
    var skipAnsweredQuestions: Boolean = true

    /**
     * Whether to always update to the latest version (HEAD).
     */
    var updateToLatestVersion: Boolean = false

    override fun getState(): CopierSettings = this

    override fun loadState(state: CopierSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * Get the instance of the settings.
         */
        fun getInstance(): CopierSettings {
            return ApplicationManager.getApplication().getService(CopierSettings::class.java)
        }
    }
}
