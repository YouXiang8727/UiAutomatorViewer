package com.youxiang8727.uiautomatorviewer

import androidx.compose.ui.geometry.Rect

data class UiNode(
    val index: String = "",
    val text: String = "",
    val resourceId: String = "",
    val className: String = "",
    val packageName: String = "",
    val contentDesc: String = "",
    val checkable: Boolean = false,
    val checked: Boolean = false,
    val clickable: Boolean = false,
    val enabled: Boolean = true,
    val focusable: Boolean = false,
    val focused: Boolean = false,
    val scrollable: Boolean = false,
    val longClickable: Boolean = false,
    val password: Boolean = false,
    val selected: Boolean = false,
    val bounds: Rect = Rect.Zero,
    val children: MutableList<UiNode> = mutableListOf()
) {
    fun getDisplayName(): String {
        val name = if (resourceId.isNotEmpty()) resourceId.substringAfterLast("/") else className.substringAfterLast(".")
        return "[$index] $name ${if (text.isNotEmpty()) "(\"$text\")" else ""}"
    }
}
