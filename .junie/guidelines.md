# 🧑‍💻 Coding Guidelines for Copier Helper Plugin

## 📝 General
- Use Kotlin (not Java) for all plugin logic
- Follow JetBrains Platform SDK idioms and best practices
- Keep UI components decoupled from business logic

## 📦 Package Naming
- Use `com.sebnoirot.copierhelper` as base package

## 🎨 UI/UX
- Use standard IntelliJ UI components (Swing)
- Always run long operations in background tasks with progress indication
- Validate all user input before executing commands

## 🧪 Testing
- Prefer unit tests for pure logic
- Mock subprocess and file system operations where applicable

## 📂 Structure
- Separate modules:
  - `actions`: IDE entry points
  - `ui`: Swing components
  - `copier`: logic to call and parse Copier templates
  - `settings`: plugin config panel