# 📐 Plugin Plan: Copier Helper for JetBrains

## 🎯 Objective

Develop a JetBrains plugin that integrates the [Copier](https://copier.readthedocs.io) templating tool into the IDE.
The plugin should provide UI-driven access to Copier features such as template selection, version control, variable input, and project generation.

---

## 🧭 Scope

- Support Git-based Copier templates
- Fetch available template versions (tags/branches)
- Parse `copier.yaml` to generate dynamic input forms
- Run Copier commands and show console output
- Allow user to open or initialize projects post-generation
- Persist user configuration in plugin settings

---

## 🔩 Architecture Overview

- Kotlin plugin using IntelliJ Platform SDK
- Subprocess wrapper for calling `copier` CLI
- YAML parsing via embedded Kotlin YAML library
- UI form generation mapped from `copier.yaml`
- Background task execution for long-running operations

---

## 🗺️ Future Enhancements (Out of Scope Initially)

- Full LSP-style error checking for template inputs
- Automatic template discovery from GitHub orgs
- UI-driven re-generation/update support