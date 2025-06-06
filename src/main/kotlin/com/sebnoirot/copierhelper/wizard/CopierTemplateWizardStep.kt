package com.sebnoirot.copierhelper.wizard

import com.intellij.ide.util.projectWizard.ModuleWizardStep
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBList
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.sebnoirot.copierhelper.CopierBundle
import com.sebnoirot.copierhelper.copier.CopierYamlParser
import com.sebnoirot.copierhelper.copier.GitVersionFetcher
import com.sebnoirot.copierhelper.copier.TemplateCloner
import com.sebnoirot.copierhelper.copier.TemplateVariable
import com.sebnoirot.copierhelper.settings.CopierSettings
import java.awt.BorderLayout
import java.io.File
import javax.swing.DefaultComboBoxModel
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Wizard step for configuring a Copier template project.
 */
class CopierTemplateWizardStep(
    private val context: WizardContext,
    private val builder: CopierModuleBuilder
) : ModuleWizardStep() {

    private val panel = JPanel(BorderLayout())
    private val templateUrlField = JBTextField()
    private val versionComboBox = JComboBox<String>()
    private val variablesPanel = JPanel(BorderLayout())
    private val variableFields = mutableMapOf<String, JComponent>()

    init {
        // Load default templates from settings
        val settings = CopierSettings.getInstance()
        val defaultTemplates = settings.defaultTemplates

        // Create template URL field with default templates
        if (defaultTemplates.isNotEmpty()) {
            val templatesComboBox = JComboBox(DefaultComboBoxModel(defaultTemplates.toTypedArray()))
            templatesComboBox.isEditable = true
            templatesComboBox.addActionListener {
                val selectedTemplate = templatesComboBox.selectedItem?.toString() ?: ""
                if (selectedTemplate.isNotBlank()) {
                    templateUrlField.text = selectedTemplate
                    fetchVersions(selectedTemplate)
                }
            }

            val templatesPanel = JPanel(BorderLayout())
            templatesPanel.add(templatesComboBox, BorderLayout.CENTER)

            panel.add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent("Template URL:", templatesPanel)
                    .addLabeledComponent("Version:", versionComboBox)
                    .addComponentFillVertically(variablesPanel, 0)
                    .panel,
                BorderLayout.CENTER
            )
        } else {
            // Simple form with just a text field
            panel.add(
                FormBuilder.createFormBuilder()
                    .addLabeledComponent("Template URL:", templateUrlField)
                    .addLabeledComponent("Version:", versionComboBox)
                    .addComponentFillVertically(variablesPanel, 0)
                    .panel,
                BorderLayout.CENTER
            )

            // Add listener to fetch versions when URL is entered
            templateUrlField.addActionListener {
                val url = templateUrlField.text
                if (url.isNotBlank()) {
                    fetchVersions(url)
                }
            }
        }

        panel.border = JBUI.Borders.empty(10)
    }

    override fun getComponent(): JComponent = panel

    override fun updateDataModel() {
        // Set the template URL and version in the builder
        builder.setTemplateUrl(templateUrlField.text)
        builder.setTemplateVersion(versionComboBox.selectedItem?.toString())

        // Collect variable values
        val variables = mutableMapOf<String, String>()
        for ((name, component) in variableFields) {
            val value = when (component) {
                is JBTextField -> component.text
                is JComboBox<*> -> component.selectedItem?.toString() ?: ""
                else -> ""
            }
            variables[name] = value
        }

        builder.setTemplateVariables(variables)
    }

    /**
     * Fetch available versions for the template.
     */
    private fun fetchVersions(templateUrl: String) {
        if (templateUrl.isBlank()) return

        ProgressManager.getInstance().run(object : Task.Modal(
            context.project,
            "Fetching Template Versions",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Fetch versions
                    val versionsMap = GitVersionFetcher.fetchVersions(templateUrl)

                    // Update UI on EDT
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        versionComboBox.removeAllItems()
                        versionComboBox.addItem("--- Latest ---")

                        // Add tags
                        val tags = versionsMap["Tags"] ?: emptyList()
                        for (tag in tags) {
                            versionComboBox.addItem(tag)
                        }

                        // Add branches
                        val branches = versionsMap["Branches"] ?: emptyList()
                        for (branch in branches) {
                            versionComboBox.addItem(branch)
                        }

                        // Clone and parse template to get variables
                        cloneAndParseTemplate(templateUrl, null)
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        context.project,
                        e.message ?: "Unknown error",
                        "Failed to fetch template versions"
                    )
                }
            }
        })
    }

    /**
     * Clone the template and parse the copier.yaml file to get variables.
     */
    private fun cloneAndParseTemplate(templateUrl: String, version: String?) {
        ProgressManager.getInstance().run(object : Task.Modal(
            context.project,
            "Parsing Template Configuration",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    // Clone the template
                    val project = context.project
                    if (project == null) {
                        Messages.showErrorDialog(
                            "Project is null",
                            "Failed to parse template configuration"
                        )
                        return
                    }

                    val clonedRepo = TemplateCloner.cloneTemplate(project, templateUrl, version, indicator)

                    // Find and parse the copier.yaml file
                    val yamlFile = TemplateCloner.findCopierYamlFile(clonedRepo)
                    if (yamlFile != null) {
                        val variables = CopierYamlParser.parseYaml(yamlFile)

                        // Update UI on EDT
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            createVariablesForm(variables)
                        }
                    }
                } catch (e: Exception) {
                    Messages.showErrorDialog(
                        context.project,
                        e.message ?: "Unknown error",
                        "Failed to parse template configuration"
                    )
                }
            }
        })
    }

    /**
     * Create a form for the template variables.
     */
    private fun createVariablesForm(variables: Map<String, TemplateVariable>) {
        // Clear previous variables
        variablesPanel.removeAll()
        variableFields.clear()

        if (variables.isEmpty()) {
            variablesPanel.add(JBLabel("No variables found in template"), BorderLayout.CENTER)
            return
        }

        // Create a form builder
        val formBuilder = FormBuilder.createFormBuilder()
        formBuilder.addSeparator(10)
        formBuilder.addComponent(JBLabel("Template Variables"))

        // Add fields for each variable
        for ((name, variable) in variables) {
            val field = when {
                variable.choices.isNotEmpty() -> {
                    // Create a combo box for variables with choices
                    val comboBox = JComboBox(DefaultComboBoxModel(variable.choices.toTypedArray()))
                    if (variable.defaultValue.isNotBlank()) {
                        comboBox.selectedItem = variable.defaultValue
                    }
                    variableFields[name] = comboBox
                    comboBox
                }
                else -> {
                    // Create a text field for other variables
                    val textField = JBTextField()
                    if (variable.defaultValue.isNotBlank()) {
                        textField.text = variable.defaultValue
                    }
                    variableFields[name] = textField
                    textField
                }
            }

            // Add the field to the form with a label and tooltip
            val label = "${variable.name}:"
            formBuilder.addLabeledComponent(label, field)
        }

        // Add the form to the panel
        variablesPanel.add(formBuilder.panel, BorderLayout.CENTER)
        variablesPanel.revalidate()
        variablesPanel.repaint()
    }
}
