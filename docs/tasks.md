# 📦 Copier Helper Plugin for JetBrains (via Juni)

This plugin integrates the [Copier](https://copier.readthedocs.io/) project templating tool directly into JetBrains IDEs. It simplifies template usage by providing UI for template selection, version management, input forms, and execution.

---

## ✅ Objectives

- Run Copier templates via IDE actions
- Support input collection based on `copier.yaml`
- Handle version selection (tags/branches)
- Post-processing actions and plugin settings panel

---

## 🧱 Tasks

### 1. Project Setup ✅
```juni
Create a JetBrains plugin named `Copier Helper` starting from the `intellij-platform-plugin-template`. It should use Kotlin. The plugin is intended to provide a UI for running Copier templates from the IDE. The plugin will call the Copier CLI and parse the `copier.yaml` file to generate an input form for the user.
```

### 2. Action to Trigger Copier ✅
```juni
Add a basic action named `Run Copier` to the Tools menu. When clicked, it should show a popup asking the user for a Copier template Git URL. Then it should execute `copier copy <url> <target-folder>` using a background task and show the result in a console window.
```

### 3. Version Selection Support ✅
```juni
Improve the “Run Copier” action: after the user inputs the template Git URL, fetch available tags and branches using `git ls-remote --tags` and `--heads`, and show them in a dropdown. Let the user pick one before running `copier copy`.
```

### 4. UI for copier.yaml Inputs ✅
```juni
After selecting the template version, clone the template into a temp folder, read the `copier.yaml` file, and generate a UI form to collect the required variables. Each variable should be shown with a label, type hint, default value, and possibly a help tooltip if available.
```

### 5. Execute Copier with Data ✅
```juni
From the user input form, generate a temporary answers file and call `copier copy` using the `--data` option to inject the user inputs. Execute the command as a background task and display the console output in a tool window.
```

### 6. Plugin Settings Panel ✅
```juni
Create a plugin settings panel under “Settings → Tools → Copier Helper” to define:
- Default Copier CLI path
- Default templates (list of Git URLs)
- Default output folder
- Enable/disable auto-open of generated project
```

### 7. Post-Generation Actions ✅
```juni
After the Copier template finishes generating, ask the user if they want to open the generated folder as a new project or add it to the current one. Provide checkboxes for post-generation steps like `git init`, `open README.md`, or run `copier update`.
```

### 8. Integrate Copier into New Project Wizard ✅
```juni
Add a project generator to integrate Copier into the "New Project" wizard. The generator should appear as "Copier Template" and allow the user to:
- Enter the Copier template Git URL
- Choose a tag/branch from the repository
- Fill in fields parsed from copier.yaml
- Execute copier copy in the selected project folder
- Open the generated project automatically
```

### 9. Reusable Templates from Settings ✅
```juni
Update the Copier Helper plugin so that users can define reusable Copier templates in the settings panel. Each template should have:
- A name
- A Git URL

When executing the "Run Copier" action, show a dropdown menu of configured templates. Selecting one should prefill the Git URL. Also include an option to use a custom URL.
```

### 10. Auto-detect Copier Projects and Propose Update ✅
```juni
Detect if the current project contains a `.copier-answers.yml` file. If found:
- Parse the `_src_path` and `version` keys
- Use `git ls-remote` to fetch tags from the template repo
- If a newer tag exists, show a notification in the IDE: “A new Copier template version is available. Update now?”
- If the user clicks, run `copier update` in the corresponding folder using a background task
- Trigger this check when the project opens or when `.copier-answers.yml` is opened in the editor
```

### 11. Organize Plugin Actions Under Copier Menu ✅
```juni
Refactor the plugin menu to organize actions under a single “Copier” menu in the Tools menu. The menu should include:
- “Copy” → opens the Copier input dialog
- “Update” → manually triggers copier update in the current project if `.copier-answers.yml` is found
- “Settings” → opens the Copier Helper section in the plugin settings
```

### 12. Editor Notification for Copier Update Status ✅
```juni
Add an Editor Notification Panel that appears when a `.copier-answers.yml` file is opened. It should:
- Parse `_src_path` and `version` from the file
- Use `git ls-remote` to check for newer tags
- If a new version exists, show a banner: “Copier template update available” with a button “Update now”
- If already up to date, show a passive info message: “Template is up to date”
- If the check fails, show a warning: “Could not check template version”
```

### 13. Improve Update Check Logic with Tag and Branch Fallback ✅
```juni
Improve the version check logic for Copier templates:
- If the template repository has Git tags, use them to determine the latest version and compare it with the `version` field from `.copier-answers.yml`.
- If there are no tags, fall back to comparing the `_commit` field with the latest commit on the main branch (e.g., `main`, `master`, or default branch).
- Based on this, decide if an update is available.
```

### 14. Conflict Resolution Strategy for Copier Update ✅
```juni
Add a user setting to control the Copier conflict resolution strategy during update. In the Copier Helper settings panel, add a dropdown labeled “Conflict handling strategy” with options:
- “inline” (default): use conflict markers in the files
- “rej”: create `.rej` files for unresolved conflicts

When running `copier update`, use the `--conflict` option accordingly.
```

### 15. Error on Dirty Repository Before Update ✅
```juni
Before running copier update, check if the project folder's Git working directory has uncommitted changes. If dirty, show an error popup: "Destination repository is dirty; cannot continue. Please commit or stash your local changes and retry." and abort the update.
```

### 16. Add Support for Copier Update Options (--skip-answered and --vcs-ref) ✅
```juni
Update the copier update command to support the following options:

- `--skip-answered`: Always include this flag to avoid prompting for values already defined in the `.copier-answers.yml`
- `--vcs-ref=HEAD`: Allow the user to optionally specify this flag via settings to always update to the latest commit on the default branch

Add an option in the Copier Helper plugin settings panel:
- A checkbox for “Skip already answered questions” (default: true)
- A checkbox for “Always update to latest version (HEAD)” (default: false)

When running `copier update`, include these options based on the user preferences.
```

### 17. Fix VFS Refresh Error After Copier Update ✅
```juni
Fix the exception “Do not perform a synchronous refresh under read lock”. Make sure any VFS refresh operations after `copier update` do not happen under a read lock. Use:

ApplicationManager.getApplication().invokeLater {
    VfsUtil.markDirtyAndRefresh(false, true, true, updatedDir)
}

to defer the refresh safely outside of the read context.
```

### 18. Move Copier Update Execution Off the EDT ✅
```juni
Fix threading violations by ensuring `CopierExecutor.update()` does not run on the Event Dispatch Thread (EDT).

Wrap the update logic using `ApplicationManager.getApplication().executeOnPooledThread { ... }` to offload work to a background thread.

If UI interaction is needed before or after the update, wrap those portions inside `ApplicationManager.getApplication().invokeLater { ... }`.

Alternatively, use `Task.Backgroundable` to run the update with IDE-native progress integration.
```

### 19. Avoid Git Command Execution in ReadAction ✅
```juni
Fix the error: “Synchronous execution under ReadAction: git ls-remote …”.

Ensure that `CapturingProcessHandler` or any git command execution does not happen within a ReadAction context.

Move all such synchronous process executions (e.g., in `GitVersionFetcher.fetchLatestCommit`) to a background thread using:

ApplicationManager.getApplication().executeOnPooledThread { ... }

Or use a `Task.Backgroundable` if you want to integrate the update check into the IntelliJ progress UI. If any result needs to be shown in the UI, wrap it with `invokeLater { ... }`.
```

### 20. Replace Deprecated API: TextFieldWithBrowseButton.addBrowseFolderListener ✅
```juni
The plugin uses `TextFieldWithBrowseButton.addBrowseFolderListener(...)`, which is scheduled for removal.

Replace it with the newer `BrowseFolderListenerListener` usage pattern. Use `FileChooserDescriptor` and `TextBrowseFolderListener` as required.

Update the following classes:
- `TemplateUrlDialog.<init>(Project)`
- `CopierSettingsComponent.<init>()`

Ensure compatibility with IntelliJ Platform 2025.2 and suppress warnings for deprecated APIs where appropriate if transitional.
```

### 21. Replace Deprecated APIs for IntelliJ Platform 2025.2 ✅
```juni
The plugin uses several deprecated APIs that may be removed in future IntelliJ releases. Replace the following:

1. Replace `FileChooserDescriptorFactory.createSingleFileDescriptor()` with a suitable non-deprecated alternative (e.g., using new constructors).
2. Replace `VirtualFileManager.addVirtualFileListener(...)` with the new recommended `VirtualFileListener` registration approach via `VirtualFileManager.addAsyncFileListener(...)`.
3. Replace `UI.PanelFactory.panel(JComponent)` with a supported layout method such as using `JBPanel`, `FormBuilder`, or `PanelFactory.createPanel()`.
4. Replace `StringsKt__StringsJVMKt.capitalize(String)` with `string.replaceFirstChar(Char::titlecase)` (Kotlin idiomatic).
5. Replace usage of deprecated class `ComponentPanelBuilder` in `TemplateVariablesDialog.createCenterPanel()` with a modern alternative (e.g., `FormBuilder`).

Ensure compatibility with IntelliJ Platform 2025.2 and update UI layout structure where necessary.
```
