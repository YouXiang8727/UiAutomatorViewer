package com.youxiang8727.uiautomatorviewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.prefs.Preferences
import javax.swing.JFileChooser
import javax.swing.filechooser.FileFilter
import javax.swing.filechooser.FileNameExtensionFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var rootNode by remember { mutableStateOf<UiNode?>(null) }
    var screenshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var selectedNode by remember { mutableStateOf<UiNode?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    var devices by remember { mutableStateOf(listOf<String>()) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var showDeviceMenu by remember { mutableStateOf(false) }
    
    val prefs = remember { Preferences.userRoot().node("com.youxiang8727.uiautomatorviewer") }
    var lastDirectory by remember { mutableStateOf(prefs.get("last_directory", ".")) }

    fun updateLastDirectory(file: File) {
        val dir = if (file.isDirectory) file.absolutePath else file.parentFile.absolutePath
        lastDirectory = dir
        prefs.put("last_directory", dir)
        try { prefs.flush() } catch (e: Exception) {}
    }

    // Initial fetch
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            devices = AdbUtils.listDevices()
            if (devices.isNotEmpty()) selectedDevice = devices[0]
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("UiAutomatorViewer") },
                    actions = {
                        // Device Selector
                        Box {
                            TextButton(
                                onClick = { 
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            devices = AdbUtils.listDevices()
                                        }
                                        showDeviceMenu = true 
                                    }
                                },
                                enabled = !isLoading
                            ) {
                                Icon(Icons.Default.Smartphone, null)
                                Spacer(Modifier.width(4.dp))
                                Text(selectedDevice ?: "No Device")
                            }
                            DropdownMenu(
                                expanded = showDeviceMenu && !isLoading, 
                                onDismissRequest = { showDeviceMenu = false }
                            ) {
                                if (devices.isEmpty()) {
                                    DropdownMenuItem(text = { Text("No devices found") }, onClick = {})
                                }
                                devices.forEach { device ->
                                    DropdownMenuItem(
                                        text = { Text(device) },
                                        onClick = {
                                            selectedDevice = device
                                            showDeviceMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // Capture from Device
                        IconButton(onClick = {
                            scope.launch {
                                isLoading = true
                                withContext(Dispatchers.IO) {
                                    val screenshotFile = AdbUtils.takeScreenshot(selectedDevice)
                                    if (screenshotFile != null) {
                                        screenshot = try {
                                            FileInputStream(screenshotFile).use { loadImageBitmap(it) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    rootNode = AdbUtils.dumpUiHierarchy(selectedDevice)
                                }
                                isLoading = false
                            }
                        }, enabled = !isLoading && selectedDevice != null) {
                            Icon(Icons.Default.CameraAlt, contentDescription = "Capture")
                        }

                        // Save Button
                        IconButton(onClick = {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                            val defaultName = "dump_${selectedDevice ?: "device"}_$timestamp.uix"
                            
                            val fileChooser = JFileChooser().apply {
                                dialogTitle = "Save UI Dump"
                                fileFilter = FileNameExtensionFilter("UIX/XML files", "uix", "xml")
                                val currentLastDir = File(lastDirectory)
                                if (currentLastDir.exists()) {
                                    currentDirectory = currentLastDir
                                }
                                selectedFile = File(defaultName)
                            }
                            
                            val result = fileChooser.showSaveDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                var file = fileChooser.selectedFile
                                if (!file.name.lowercase().endsWith(".xml") && !file.name.lowercase().endsWith(".uix")) {
                                    file = File(file.absolutePath + ".uix")
                                }
                                updateLastDirectory(file)
                                scope.launch(Dispatchers.IO) {
                                    AdbUtils.saveCurrentDumpTo(file)
                                }
                            }
                        }, enabled = !isLoading && rootNode != null && screenshot != null) {
                            Icon(Icons.Default.Save, contentDescription = "Save As")
                        }

                        // Load Button
                        IconButton(onClick = {
                            val fileChooser = JFileChooser().apply {
                                dialogTitle = "Open UI Dump (Must have matching PNG)"
                                fileFilter = object : FileFilter() {
                                    override fun accept(f: File): Boolean {
                                        if (f.isDirectory) return true
                                        val ext = f.extension.lowercase()
                                        if (ext == "xml" || ext == "uix") {
                                            val baseName = f.absolutePath.substringBeforeLast(".")
                                            return File("$baseName.png").exists()
                                        }
                                        return false
                                    }
                                    override fun getDescription(): String = "UIX/XML Dumps with matching PNG (*.uix, *.xml)"
                                }
                                val currentLastDir = File(lastDirectory)
                                if (currentLastDir.exists()) {
                                    currentDirectory = currentLastDir
                                }
                            }
                            
                            val result = fileChooser.showOpenDialog(null)
                            if (result == JFileChooser.APPROVE_OPTION) {
                                val file = fileChooser.selectedFile
                                updateLastDirectory(file)
                                scope.launch {
                                    isLoading = true
                                    withContext(Dispatchers.IO) {
                                        val pngFile = File(file.absolutePath.substringBeforeLast(".") + ".png")
                                        if (pngFile.exists()) {
                                            screenshot = FileInputStream(pngFile).use { loadImageBitmap(it) }
                                        }
                                        rootNode = AdbUtils.parseXmlFile(file)
                                    }
                                    isLoading = false
                                }
                            }
                        }, enabled = !isLoading) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Open")
                        }
                    }
                )
            }
        ) { padding ->
            if (isLoading) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                    // Left: Screenshot
                    Box(modifier = Modifier.weight(0.6f).fillMaxHeight().background(Color.DarkGray)) {
                        screenshot?.let { bitmap ->
                            ScreenshotView(bitmap, rootNode, selectedNode) {
                                selectedNode = it
                            }
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val msg = if (selectedDevice == null) "Connect device or load saved dump" else "No screenshot. Click capture to fetch."
                            Text(msg, color = Color.White)
                        }
                    }

                    VerticalDivider()

                    // Right: Hierarchy and Details
                    Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                        Box(modifier = Modifier.weight(0.6f)) {
                            rootNode?.let { node ->
                                HierarchyTree(node, selectedNode) {
                                    selectedNode = it
                                }
                            } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No hierarchy data.")
                            }
                        }
                        HorizontalDivider()
                        Box(modifier = Modifier.weight(0.4f)) {
                            selectedNode?.let { NodeDetails(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenshotView(
    bitmap: ImageBitmap,
    rootNode: UiNode?,
    selectedNode: UiNode?,
    onNodeSelected: (UiNode) -> Unit
) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    
    val containerAspectRatio = if (canvasSize.height > 0) canvasSize.width.toFloat() / canvasSize.height else 1f
    val bitmapAspectRatio = bitmap.width.toFloat() / bitmap.height
    
    val (displayedWidth, displayedHeight) = if (containerAspectRatio > bitmapAspectRatio) {
        val h = canvasSize.height.toFloat()
        val w = h * bitmapAspectRatio
        w to h
    } else {
        val w = canvasSize.width.toFloat()
        val h = w / bitmapAspectRatio
        w to h
    }

    val offsetX = (canvasSize.width - displayedWidth) / 2
    val offsetY = (canvasSize.height - displayedHeight) / 2
    
    val scaleX = displayedWidth / bitmap.width
    val scaleY = displayedHeight / bitmap.height

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { canvasSize = it.size }
            .pointerHoverIcon(PointerIcon.Crosshair)
            .pointerInput(rootNode, bitmap, canvasSize) {
                detectTapGestures { offset ->
                    rootNode?.let { root ->
                        val imageX = (offset.x - offsetX) / scaleX
                        val imageY = (offset.y - offsetY) / scaleY
                        findSmallestNodeAt(root, imageX, imageY)?.let {
                            onNodeSelected(it)
                        }
                    }
                }
            }
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            selectedNode?.bounds?.let { bounds ->
                drawRect(
                    color = Color.Red,
                    topLeft = Offset(bounds.left * scaleX + offsetX, bounds.top * scaleY + offsetY),
                    size = androidx.compose.ui.geometry.Size(
                        (bounds.right - bounds.left) * scaleX,
                        (bounds.bottom - bounds.top) * scaleY
                    ),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

private fun findSmallestNodeAt(node: UiNode, x: Float, y: Float): UiNode? {
    if (!node.bounds.contains(Offset(x, y))) return null

    var smallestNode = node
    var smallestArea = node.bounds.width * node.bounds.height

    for (child in node.children) {
        val found = findSmallestNodeAt(child, x, y)
        if (found != null) {
            val foundArea = found.bounds.width * found.bounds.height
            // 如果找到的節點面積更小，或者面積相等（代表更深層或更後面的節點），則選取它
            if (foundArea <= smallestArea) {
                smallestNode = found
                smallestArea = foundArea
            }
        }
    }
    return smallestNode
}

@Composable
fun HierarchyTree(root: UiNode, selectedNode: UiNode?, onNodeSelected: (UiNode) -> Unit) {
    val expandedNodes = remember { mutableStateMapOf<UiNode, Boolean>() }
    val state = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState).padding(8.dp)) {
            item {
                TreeNode(root, 0, expandedNodes, selectedNode, onNodeSelected)
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(state)
        )
    }
}

@Composable
fun TreeNode(
    node: UiNode,
    depth: Int,
    expandedNodes: MutableMap<UiNode, Boolean>,
    selectedNode: UiNode?,
    onNodeSelected: (UiNode) -> Unit
) {
    val isExpanded = expandedNodes[node] ?: true
    
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNodeSelected(node) }
                .background(if (node == selectedNode) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (node.children.isNotEmpty()) {
                Text(
                    text = if (isExpanded) "▼ " else "▶ ",
                    modifier = Modifier.clickable { expandedNodes[node] = !isExpanded },
                    fontSize = 12.sp
                )
            } else {
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(text = node.getDisplayName(), fontSize = 12.sp, maxLines = 1, softWrap = false)
        }
        
        if (isExpanded) {
            node.children.forEach { child ->
                TreeNode(child, depth + 1, expandedNodes, selectedNode, onNodeSelected)
            }
        }
    }
}

@Composable
fun NodeDetails(node: UiNode) {
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer {
            Column(modifier = Modifier.fillMaxSize().padding(8.dp).verticalScroll(verticalScrollState).horizontalScroll(horizontalScrollState)) {
                Text("Node Details", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                DetailItem("Index", node.index)
                DetailItem("Text", node.text)
                DetailItem("Resource-ID", node.resourceId)
                DetailItem("Class", node.className)
                DetailItem("Package", node.packageName)
                DetailItem("Content-Desc", node.contentDesc)
                DetailItem("Bounds", node.bounds.toString())
                DetailItem("Checkable", node.checkable.toString())
                DetailItem("Checked", node.checked.toString())
                DetailItem("Clickable", node.clickable.toString())
                DetailItem("Enabled", node.enabled.toString())
                DetailItem("Focusable", node.focusable.toString())
                DetailItem("Focused", node.focused.toString())
                DetailItem("Scrollable", node.scrollable.toString())
                DetailItem("Long-Clickable", node.longClickable.toString())
                DetailItem("Password", node.password.toString())
                DetailItem("Selected", node.selected.toString())
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(verticalScrollState)
        )
    }
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(text = "$label: ", modifier = Modifier.width(100.dp), style = MaterialTheme.typography.labelMedium)
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}
