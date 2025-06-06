package com.sebnoirot.copierhelper.ui

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.sebnoirot.copierhelper.CopierBundle
import com.sebnoirot.copierhelper.copier.GitVersionFetcher
import com.sebnoirot.copierhelper.settings.CopierSettings
import com.sebnoirot.copierhelper.settings.TemplateEntry
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.DefaultComboBoxModel

/**
 * Dialog for entering Copier template URL, version, and target folder.
 */
class TemplateUrlDialog(private val project: Project) : DialogWrapper(project) {
    private val templateComboBox = ComboBox<TemplateEntry>()
    private val templateUrlField = JBTextField(50)
    private val targetFolderField = TextFieldWithBrowseButton()
    private val versionComboBox = ComboBox<String>()
    private val fetchVersionsButton = JButton("Fetch Versions")

    private var versions: Map<String, List<String>> = emptyMap()
    private var selectedVersion: String? = null

    init {
        title = CopierBundle.message("runCopier.dialog.title")

        // Configure template combo box
        val templates = CopierSettings.getInstance().defaultTemplates
        val templateItems = ArrayList<TemplateEntry>()
        templateItems.add(TemplateEntry("Custom URL", ""))
        templateItems.addAll(templates)
        templateComboBox.model = DefaultComboBoxModel(templateItems.toTypedArray())

        // Add listener to template combo box
        templateComboBox.addActionListener {
            val selectedTemplate = templateComboBox.selectedItem as? TemplateEntry
            if (selectedTemplate != null && selectedTemplate.name != "Custom URL") {
                templateUrlField.text = selectedTemplate.url
                fetchVersions()
            } else {
                templateUrlField.text = ""
                fetchVersionsButton.isEnabled = false
            }
        }

        // Configure target folder chooser
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor()
        targetFolderField.addBrowseFolderListener(
            TextBrowseFolderListener(descriptor, project)
        )

        // Configure fetch versions button
        fetchVersionsButton.addActionListener {
            fetchVersions()
        }

        // Add listener to URL field to enable/disable fetch button
        templateUrlField.document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                fetchVersionsButton.isEnabled = templateUrlField.text.isNotBlank()
            }
        })

        // Initially disable fetch button
        fetchVersionsButton.isEnabled = false

        init()
    }

    override fun createCenterPanel(): JComponent {
        // Create version selection panel with fetch button
        val versionPanel = JPanel()
        versionPanel.add(versionComboBox)
        versionPanel.add(fetchVersionsButton)

        val panel = FormBuilder.createFormBuilder()
            .addLabeledComponent(
                JBLabel("Template:"),
                templateComboBox,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(CopierBundle.message("runCopier.dialog.url.label")),
                templateUrlField,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(CopierBundle.message("runCopier.dialog.version.label")),
                versionPanel,
                1,
                false
            )
            .addLabeledComponent(
                JBLabel(CopierBundle.message("runCopier.dialog.target.label")),
                targetFolderField,
                1,
                false
            )
            .panel

        panel.border = JBUI.Borders.empty(10)
        return panel
    }

    /**
     * Fetch versions from the Git repository.
     */
    private fun fetchVersions() {
        val templateUrl = templateUrlField.text.trim()
        if (templateUrl.isBlank()) {
            return
        }

        ProgressManager.getInstance().run(object : Task.Modal(
            project,
            "Fetching Versions",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Fetching versions from repository..."

                versions = GitVersionFetcher.fetchVersions(templateUrl, indicator)
            }

            override fun onSuccess() {
                updateVersionComboBox()
            }
        })
    }

    /**
     * Update the version combo box with fetched versions.
     */
    private fun updateVersionComboBox() {
        val items = mutableListOf<String>()

        // Add "main" branch as default if available
        versions["Branches"]?.let { branches ->
            if (branches.contains("main")) {
                items.add("main")
            } else if (branches.contains("master")) {
                items.add("master")
            }
        }

        // Add all tags
        versions["Tags"]?.let { tags ->
            if (tags.isNotEmpty()) {
                if (items.isNotEmpty()) {
                    items.add("---Tags---")
                }
                items.addAll(tags)
            }
        }

        // Add all branches except main/master which are already added
        versions["Branches"]?.let { branches ->
            val filteredBranches = branches.filter { it != "main" && it != "master" }
            if (filteredBranches.isNotEmpty()) {
                if (items.isNotEmpty()) {
                    items.add("---Branches---")
                }
                items.addAll(filteredBranches)
            }
        }

        versionComboBox.model = CollectionComboBoxModel(items)
        if (items.isNotEmpty()) {
            versionComboBox.selectedItem = items[0]
            selectedVersion = items[0]
        }
    }

    /**
     * Get the entered template URL.
     */
    fun getTemplateUrl(): String = templateUrlField.text.trim()

    /**
     * Get the selected target folder.
     */
    fun getTargetFolder(): String = targetFolderField.text.trim()

    /**
     * Get the selected version.
     */
    fun getSelectedVersion(): String? = versionComboBox.selectedItem as? String

    override fun doValidate(): ValidationInfo? {
        if (templateUrlField.text.isBlank()) {
            return ValidationInfo(
                "Template URL cannot be empty",
                templateUrlField
            )
        }

        if (targetFolderField.text.isBlank()) {
            return ValidationInfo(
                "Target folder cannot be empty",
                targetFolderField
            )
        }

        return null
    }
}
