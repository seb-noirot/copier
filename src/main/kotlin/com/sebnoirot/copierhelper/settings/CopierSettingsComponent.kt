package com.sebnoirot.copierhelper.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.DefaultListCellRenderer
import javax.swing.JList
import javax.swing.JComboBox
import javax.swing.DefaultComboBoxModel
import java.awt.Component

/**
 * Component for the Copier Helper settings UI.
 */
class CopierSettingsComponent {
    private val panel: JPanel
    private val copierPathField = TextFieldWithBrowseButton()
    private val defaultOutputFolderField = TextFieldWithBrowseButton()
    private val autoOpenCheckBox = JBCheckBox("Automatically open generated projects")
    private val skipAnsweredCheckBox = JBCheckBox("Skip already answered questions")
    private val updateToLatestCheckBox = JBCheckBox("Always update to latest version (HEAD)")
    private val conflictStrategyComboBox = JComboBox<String>()
    private val templatesListModel = CollectionListModel<TemplateEntry>()
    private val templatesList = JBList(templatesListModel)

    init {
        // Configure file choosers
        copierPathField.addBrowseFolderListener(
            "Select Copier Executable",
            "Select the path to the Copier CLI executable",
            null,
            FileChooserDescriptorFactory.createSingleFileDescriptor()
        )

        defaultOutputFolderField.addBrowseFolderListener(
            "Select Default Output Folder",
            "Select the default folder where generated projects will be placed",
            null,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        // Configure conflict strategy combo box
        conflictStrategyComboBox.model = DefaultComboBoxModel(arrayOf("inline", "rej"))
        conflictStrategyComboBox.toolTipText = "How to handle conflicts during template updates"

        // Configure templates list with add/remove buttons
        val templatesPanel = ToolbarDecorator.createDecorator(templatesList)
            .setAddAction { addTemplate() }
            .setRemoveAction { removeSelectedTemplates() }
            .setMoveUpAction { moveTemplateUp() }
            .setMoveDownAction { moveTemplateDown() }
            .createPanel()

        // Set custom renderer to show template name and URL
        templatesList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                if (value is TemplateEntry) {
                    text = "${value.name} (${value.url})"
                }
                return component
            }
        }

        // Build the form
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel("Copier CLI Path:"), copierPathField, 1, false)
            .addLabeledComponent(JBLabel("Default Output Folder:"), defaultOutputFolderField, 1, false)
            .addComponent(autoOpenCheckBox, 1)
            .addSeparator(5)
            .addComponent(JBLabel("Update Options:"), 1)
            .addComponent(skipAnsweredCheckBox, 1)
            .addComponent(updateToLatestCheckBox, 1)
            .addLabeledComponent(JBLabel("Conflict handling strategy:"), conflictStrategyComboBox, 1, false)
            .addSeparator(10)
            .addLabeledComponentFillVertically("Default Templates:", templatesPanel)
            .getPanel()

        panel.border = JBUI.Borders.empty(10)
    }

    /**
     * Get the panel for the settings UI.
     */
    fun getPanel(): JPanel = panel

    /**
     * Get the preferred focus component.
     */
    fun getPreferredFocusComponent(): JComponent = copierPathField

    /**
     * Get the Copier CLI path.
     */
    fun getCopierPath(): String = copierPathField.text

    /**
     * Set the Copier CLI path.
     */
    fun setCopierPath(path: String) {
        copierPathField.text = path
    }

    /**
     * Get the default output folder.
     */
    fun getDefaultOutputFolder(): String = defaultOutputFolderField.text

    /**
     * Set the default output folder.
     */
    fun setDefaultOutputFolder(folder: String) {
        defaultOutputFolderField.text = folder
    }

    /**
     * Get whether to automatically open generated projects.
     */
    fun getAutoOpenGeneratedProject(): Boolean = autoOpenCheckBox.isSelected

    /**
     * Set whether to automatically open generated projects.
     */
    fun setAutoOpenGeneratedProject(autoOpen: Boolean) {
        autoOpenCheckBox.isSelected = autoOpen
    }

    /**
     * Get the conflict handling strategy.
     */
    fun getConflictStrategy(): String = conflictStrategyComboBox.selectedItem as String

    /**
     * Set the conflict handling strategy.
     */
    fun setConflictStrategy(strategy: String) {
        conflictStrategyComboBox.selectedItem = strategy
    }

    /**
     * Get whether to skip already answered questions during update.
     */
    fun getSkipAnsweredQuestions(): Boolean = skipAnsweredCheckBox.isSelected

    /**
     * Set whether to skip already answered questions during update.
     */
    fun setSkipAnsweredQuestions(skip: Boolean) {
        skipAnsweredCheckBox.isSelected = skip
    }

    /**
     * Get whether to always update to the latest version (HEAD).
     */
    fun getUpdateToLatestVersion(): Boolean = updateToLatestCheckBox.isSelected

    /**
     * Set whether to always update to the latest version (HEAD).
     */
    fun setUpdateToLatestVersion(update: Boolean) {
        updateToLatestCheckBox.isSelected = update
    }

    /**
     * Get the default templates.
     */
    fun getDefaultTemplates(): List<TemplateEntry> = templatesListModel.items

    /**
     * Set the default templates.
     */
    fun setDefaultTemplates(templates: List<TemplateEntry>) {
        templatesListModel.removeAll()
        for (template in templates) {
            templatesListModel.add(template)
        }
    }

    /**
     * Add a new template.
     */
    private fun addTemplate() {
        val dialog = TemplateInputDialog()
        if (dialog.showAndGet()) {
            val templateName = dialog.getTemplateName()
            val templateUrl = dialog.getTemplateUrl()
            if (templateName.isNotBlank() && templateUrl.isNotBlank()) {
                templatesListModel.add(TemplateEntry(templateName, templateUrl))
            }
        }
    }

    /**
     * Remove the selected templates.
     */
    private fun removeSelectedTemplates() {
        val selectedIndices = templatesList.selectedIndices
        for (i in selectedIndices.size - 1 downTo 0) {
            templatesListModel.remove(selectedIndices[i])
        }
    }

    /**
     * Move the selected template up.
     */
    private fun moveTemplateUp() {
        val selectedIndex = templatesList.selectedIndex
        if (selectedIndex > 0) {
            val template = templatesListModel.getElementAt(selectedIndex)
            templatesListModel.remove(selectedIndex)
            templatesListModel.add(selectedIndex - 1, template)
            templatesList.selectedIndex = selectedIndex - 1
        }
    }

    /**
     * Move the selected template down.
     */
    private fun moveTemplateDown() {
        val selectedIndex = templatesList.selectedIndex
        if (selectedIndex < templatesListModel.size - 1) {
            val template = templatesListModel.getElementAt(selectedIndex)
            templatesListModel.remove(selectedIndex)
            templatesListModel.add(selectedIndex + 1, template)
            templatesList.selectedIndex = selectedIndex + 1
        }
    }

    /**
     * Dialog for entering a template name and URL.
     */
    private inner class TemplateInputDialog : com.intellij.openapi.ui.DialogWrapper(true) {
        private val templateNameField = JBTextField(40)
        private val templateUrlField = JBTextField(40)

        init {
            title = "Add Template"
            init()
        }

        override fun createCenterPanel(): JComponent {
            return FormBuilder.createFormBuilder()
                .addLabeledComponent("Template Name:", templateNameField)
                .addLabeledComponent("Template URL:", templateUrlField)
                .addComponentFillVertically(JPanel(), 0)
                .panel
        }

        fun getTemplateName(): String = templateNameField.text.trim()
        fun getTemplateUrl(): String = templateUrlField.text.trim()
    }
}
