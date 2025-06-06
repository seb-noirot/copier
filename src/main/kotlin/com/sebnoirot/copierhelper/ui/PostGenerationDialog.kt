package com.sebnoirot.copierhelper.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBRadioButton
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.sebnoirot.copierhelper.CopierBundle
import java.io.File
import javax.swing.ButtonGroup
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Dialog for post-generation actions after a Copier template has been generated.
 */
class PostGenerationDialog(
    private val project: Project,
    private val targetFolder: String
) : DialogWrapper(project) {
    
    private val openAsNewProjectRadio = JBRadioButton(CopierBundle.message("postGeneration.openAsNewProject"))
    private val addToCurrentProjectRadio = JBRadioButton(CopierBundle.message("postGeneration.addToCurrentProject"))
    private val doNotOpenRadio = JBRadioButton(CopierBundle.message("postGeneration.doNotOpen"))
    
    private val gitInitCheckBox = JBCheckBox(CopierBundle.message("postGeneration.gitInit"))
    private val openReadmeCheckBox = JBCheckBox(CopierBundle.message("postGeneration.openReadme"))
    private val runCopierUpdateCheckBox = JBCheckBox(CopierBundle.message("postGeneration.runCopierUpdate"))
    
    init {
        title = CopierBundle.message("postGeneration.title")
        
        // Set up radio button group
        val projectGroup = ButtonGroup()
        projectGroup.add(openAsNewProjectRadio)
        projectGroup.add(addToCurrentProjectRadio)
        projectGroup.add(doNotOpenRadio)
        
        // Set default selection based on settings
        val settings = com.sebnoirot.copierhelper.settings.CopierSettings.getInstance()
        if (settings.autoOpenGeneratedProject) {
            openAsNewProjectRadio.isSelected = true
        } else {
            doNotOpenRadio.isSelected = true
        }
        
        // Check if README.md exists in the target folder
        val readmeFile = File(targetFolder, "README.md")
        openReadmeCheckBox.isEnabled = readmeFile.exists()
        
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        val panel = FormBuilder.createFormBuilder()
            .addComponent(JBLabel(CopierBundle.message("postGeneration.projectOptions")))
            .addComponent(openAsNewProjectRadio)
            .addComponent(addToCurrentProjectRadio)
            .addComponent(doNotOpenRadio)
            .addSeparator()
            .addComponent(JBLabel(CopierBundle.message("postGeneration.additionalActions")))
            .addComponent(gitInitCheckBox)
            .addComponent(openReadmeCheckBox)
            .addComponent(runCopierUpdateCheckBox)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        panel.border = JBUI.Borders.empty(10)
        return panel
    }
    
    /**
     * Get whether to open the generated project as a new project.
     */
    fun shouldOpenAsNewProject(): Boolean = openAsNewProjectRadio.isSelected
    
    /**
     * Get whether to add the generated project to the current project.
     */
    fun shouldAddToCurrentProject(): Boolean = addToCurrentProjectRadio.isSelected
    
    /**
     * Get whether to initialize a Git repository.
     */
    fun shouldInitGit(): Boolean = gitInitCheckBox.isSelected
    
    /**
     * Get whether to open the README.md file.
     */
    fun shouldOpenReadme(): Boolean = openReadmeCheckBox.isSelected
    
    /**
     * Get whether to run Copier update.
     */
    fun shouldRunCopierUpdate(): Boolean = runCopierUpdateCheckBox.isSelected
}