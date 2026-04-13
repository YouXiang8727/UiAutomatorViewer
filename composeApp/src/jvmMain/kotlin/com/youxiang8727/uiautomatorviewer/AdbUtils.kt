package com.youxiang8727.uiautomatorviewer

import androidx.compose.ui.geometry.Rect
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

object AdbUtils {

    private val tempDir = File(System.getProperty("java.io.tmpdir"), "uiautomator_viewer").apply { mkdirs() }
    private val localXmlFile = File(tempDir, "view.xml")
    private val localPngFile = File(tempDir, "view.png")

    private val adbExecutable: String by lazy {
        val os = System.getProperty("os.name").lowercase()
        val isWin = os.contains("win")
        val fileName = if (isWin) "adb.exe" else "adb"
        val subDir = if (isWin) "win" else "mac"

        try {
            val resourcePath = "/bin/$subDir/$fileName"
            val stream = AdbUtils::class.java.getResourceAsStream(resourcePath)
            if (stream != null) {
                val binDir = File(tempDir, "bin").apply { mkdirs() }
                val targetFile = File(binDir, fileName)
                stream.use { input ->
                    Files.copy(input, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
                if (!isWin) targetFile.setExecutable(true)
                return@lazy targetFile.absolutePath
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val userHome = System.getProperty("user.home")
        val commonPaths = if (os.contains("mac")) {
            listOf("/usr/local/bin/adb", "/opt/homebrew/bin/adb", "$userHome/Library/Android/sdk/platform-tools/adb")
        } else if (os.contains("win")) {
            listOf("C:\\platform-tools\\adb.exe", "$userHome\\AppData\\Local\\Android\\Sdk\\platform-tools\\adb.exe")
        } else emptyList()

        commonPaths.firstOrNull { File(it).exists() } ?: "adb"
    }

    private fun adbCommand(serial: String?, vararg args: String): Array<String> {
        return if (!serial.isNullOrBlank()) {
            arrayOf(adbExecutable, "-s", serial) + args
        } else {
            arrayOf(adbExecutable) + args
        }
    }

    fun executeCommand(vararg command: String): String? {
        return try {
            val process = ProcessBuilder(*command)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor(15, TimeUnit.SECONDS)
            output
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun listDevices(): List<String> {
        val output = executeCommand(adbExecutable, "devices") ?: return emptyList()
        return output.lines()
            .filter { it.endsWith("\tdevice") }
            .map { it.substringBefore("\t") }
    }

    fun dumpUiHierarchy(serial: String?): UiNode? {
        executeCommand(*adbCommand(serial, "shell", "uiautomator", "dump", "/sdcard/view.xml"))
        executeCommand(*adbCommand(serial, "pull", "/sdcard/view.xml", localXmlFile.absolutePath))
        return parseXmlFile(localXmlFile)
    }

    fun takeScreenshot(serial: String?): File? {
        executeCommand(*adbCommand(serial, "shell", "screencap", "-p", "/sdcard/view.png"))
        executeCommand(*adbCommand(serial, "pull", "/sdcard/view.png", localPngFile.absolutePath))
        return if (localPngFile.exists()) localPngFile else null
    }

    fun parseXmlFile(file: File): UiNode? {
        if (!file.exists()) return null
        return try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc = dBuilder.parse(file)
            doc.documentElement.normalize()
            val rootNode = doc.getElementsByTagName("node").item(0) as? Element
            rootNode?.let { parseNode(it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseNode(element: Element): UiNode {
        val bounds = parseBounds(element.getAttribute("bounds"))
        val node = UiNode(
            index = element.getAttribute("index"),
            text = element.getAttribute("text"),
            resourceId = element.getAttribute("resource-id"),
            className = element.getAttribute("class"),
            packageName = element.getAttribute("package"),
            contentDesc = element.getAttribute("content-desc"),
            checkable = element.getAttribute("checkable").toBoolean(),
            checked = element.getAttribute("checked").toBoolean(),
            clickable = element.getAttribute("clickable").toBoolean(),
            enabled = element.getAttribute("enabled").toBoolean(),
            focusable = element.getAttribute("focusable").toBoolean(),
            focused = element.getAttribute("focused").toBoolean(),
            scrollable = element.getAttribute("scrollable").toBoolean(),
            longClickable = element.getAttribute("long-clickable").toBoolean(),
            password = element.getAttribute("password").toBoolean(),
            selected = element.getAttribute("selected").toBoolean(),
            bounds = bounds
        )
        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && child.nodeName == "node") {
                node.children.add(parseNode(child as Element))
            }
        }
        return node
    }

    private fun parseBounds(boundsStr: String): Rect {
        val regex = "\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]".toRegex()
        val matchResult = regex.find(boundsStr)
        return if (matchResult != null) {
            val (left, top, right, bottom) = matchResult.destructured
            Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
        } else Rect.Zero
    }

    fun saveCurrentDumpTo(targetXmlFile: File): Boolean {
        if (!localXmlFile.exists() || !localPngFile.exists()) return false
        val targetPngFile = File(targetXmlFile.absolutePath.substringBeforeLast(".") + ".png")
        return try {
            Files.copy(localXmlFile.toPath(), targetXmlFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.copy(localPngFile.toPath(), targetPngFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            true
        } catch (e: Exception) {
            false
        }
    }
}
