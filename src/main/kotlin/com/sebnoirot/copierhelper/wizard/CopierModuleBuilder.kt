package com.sebnoirot.copierhelper.wizard

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleType
import com.intellij.openapi.module.EmptyModuleType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.vfs.LocalFileSystem
import com.sebnoirot.copierhelper.CopierBundle
import com.sebnoirot.copierhelper.copier.CopierExecutor
import com.sebnoirot.copierhelper.copier.CopierYamlParser
import com.sebnoirot.copierhelper.copier.TemplateCloner
import com.sebnoirot.copierhelper.settings.CopierSettings
import java.io.File
import javax.swing.Icon

/**
 * Module builder for creating projects from Copier templates.
 */
class CopierModuleBuilder : ModuleBuilder() {
    private var templateUrl: String = ""
    private var templateVersion: String? = null
    private var templateVariables: Map<String, String> = emptyMap()

    override fun getModuleType(): ModuleType<*> = EmptyModuleType.getInstance()

    override fun getPresentableName(): String = "Copier Template"

    override fun getDescription(): String = "Create a new project from a Copier template"

    override fun getNodeIcon(): Icon? = null // Use default icon

    override fun getCustomOptionsStep(context: WizardContext, parentDisposable: Disposable): ModuleWizardStep {
        return CopierTemplateWizardStep(context, this)
    }

    override fun setupRootModel(modifiableRootModel: ModifiableRootModel) {
        val project = modifiableRootModel.project
        val contentEntryPath = contentEntryPath ?: return

        // Execute Copier in a background task
        ProgressManager.getInstance().run(object : Task.Modal(
            project,
            "Generating Project from Copier Template",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "Executing Copier..."

                    // Create the target directory if it doesn't exist
                    val targetDir = File(contentEntryPath)
                    if (!targetDir.exists()) {
                        targetDir.mkdirs()
                    }

                    // Execute Copier with variables
                    CopierExecutor.execute(project, templateUrl, contentEntryPath, templateVersion, templateVariables)

                    // Refresh the target folder in the IDE
                    val targetVirtualDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(contentEntryPath)
                    targetVirtualDir?.refresh(true, true)

                } catch (e: Exception) {
                    com.intellij.openapi.ui.Messages.showErrorDialog(
                        project,
                        e.message ?: "Unknown error",
                        "Failed to generate project from Copier template"
                    )
                }
            }
        })
    }

    /**
     * Set the template URL.
     */
    fun setTemplateUrl(url: String) {
        templateUrl = url
    }

    /**
     * Set the template version.
     */
    fun setTemplateVersion(version: String?) {
        templateVersion = version
    }

    /**
     * Set the template variables.
     */
    fun setTemplateVariables(variables: Map<String, String>) {
        templateVariables = variables
    }
}
