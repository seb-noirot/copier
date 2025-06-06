package com.sebnoirot.copierhelper.settings

/**
 * Represents a named Copier template.
 *
 * @property name The display name of the template.
 * @property url The Git URL of the template repository.
 */
data class TemplateEntry(
    var name: String = "",
    var url: String = ""
) {
    override fun toString(): String {
        return name
    }
}