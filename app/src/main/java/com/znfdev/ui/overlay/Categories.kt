package com.znfdev.ui.overlay

/** One tab of the panel: a Material Symbols ligature and its label. */
class Category(val icon: String, val label: String)

val PanelCategories = listOf(
    Category("code", "Execute"),
    Category("folder", "Files"),
    Category("script", "Scripts"),
    Category("extension", "Plugins"),
    Category("groups", "Team"),
    Category("settings", "Settings")
)
