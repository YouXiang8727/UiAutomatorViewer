package com.youxiang8727.uiautomatorviewer

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "UiAutomatorViewer",
    ) {
        App()
    }
}