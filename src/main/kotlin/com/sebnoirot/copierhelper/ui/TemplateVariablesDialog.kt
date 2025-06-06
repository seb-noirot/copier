package com.sebnoirot.copierhelper.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.sebnoirot.copierhelper.copier.TemplateVariable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Dialog for collecting template variables from the user.
 */
class TemplateVariablesDialog(
    private val project: Project,
    private val variables: Map<String, TemplateVariable>,
    private val templateName: String
) : DialogWrapper(project) {

    private val inputComponents = mutableMapOf<String, JComponent>()
    private val values = mutableMapOf<String, String>()

    init {
        title = "Configure Template: $templateName"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val formBuilder = FormBuilder.createFormBuilder()

        // Sort variables by name for consistent display
        val sortedVariables = variables.entries.sortedBy { it.key }

        for ((name, variable) in sortedVariables) {
            val component = createInputComponent(variable)
            inputComponents[name] = component

            val label = JBLabel(formatVariableName(name))

            // Add help text if available
            if (variable.help.isNotBlank()) {
                label.toolTipText = variable.help
            }

            formBuilder.addLabeledComponent(label, component, 1, false)
        }

        val panel = formBuilder.panel
        panel.border = JBUI.Borders.empty(10)

        // Wrap in a scrollable panel
        return UI.PanelFactory.panel(panel)
            .withComment("Configure the template variables. Hover over labels for help text.")
            .createPanel()
    }

    /**
     * Create an input component for a variable based on its type.
     */
    private fun createInputComponent(variable: TemplateVariable): JComponent {
        return when {
            variable.choices.isNotEmpty() -> {
                // Create a dropdown for variables with choices
                val comboBox = ComboBox(CollectionComboBoxModel(variable.choices))
                if (variable.defaultValue.isNotBlank() && variable.choices.contains(variable.defaultValue)) {
                    comboBox.selectedItem = variable.defaultValue
                }
                comboBox
            }
            variable.type == "bool" -> {
                // Create a checkbox for boolean variables
                val checkBox = JBCheckBox()
                checkBox.isSelected = variable.defaultValue.equals("true", ignoreCase = true)
                checkBox
            }
            variable.type == "list" -> {
                // Create a text area for list variables
                val textArea = JTextArea(3, 30)
                textArea.text = variable.defaultValue
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                JPanel(BorderLayout()).apply {
                    add(textArea, BorderLayout.CENTER)
                }
            }
            else -> {
                // Create a text field for other variables
                val textField = JBTextField(30)
                textField.text = variable.defaultValue
                textField
            }
        }
    }

    /**
     * Format a variable name for display.
     */
    private fun formatVariableName(name: String): String {
        return name.replace("_", " ")
            .split(" ")
            .joinToString(" ") { it.capitalize() }
    }

    /**
     * Get the values entered by the user.
     */
    fun getValues(): Map<String, String> {
        for ((name, component) in inputComponents) {
            val value = when (component) {
                is ComboBox<*> -> component.selectedItem?.toString() ?: ""
                is JBCheckBox -> component.isSelected.toString()
                is JPanel -> {
                    // Extract text from text area inside panel
                    val textArea = (component.getComponent(0) as? JTextArea)
                    textArea?.text ?: ""
                }
                is JBTextField -> component.text
                else -> ""
            }
            values[name] = value
        }
        return values
    }

    override fun doValidate(): ValidationInfo? {
        // Validate required fields (no validation for now, all fields are optional)
        return null
    }
}
