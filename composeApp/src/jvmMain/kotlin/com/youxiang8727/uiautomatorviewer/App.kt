package com.youxiang8727.uiautomatorviewer

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Image as AwtImage
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.prefs.Preferences
import javax.swing.ImageIcon
import javax.swing.JComponent
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

    // Search and Tree State
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<UiNode>()) }
    var currentSearchIndex by remember { mutableStateOf(-1) }
    val expandedNodes = remember { mutableStateMapOf<UiNode, Boolean>() }

    val parentMap = remember(rootNode) {
        val map = mutableMapOf<UiNode, UiNode>()
        fun walk(node: UiNode) {
            node.children.forEach {
                map[it] = node
                walk(it)
            }
        }
        rootNode?.let { walk(it) }
        map
    }

    fun expandNodeAndParents(node: UiNode) {
        var current = parentMap[node]
        while (current != null) {
            expandedNodes[current] = true
            current = parentMap[current]
        }
    }

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

    LaunchedEffect(rootNode) {
        selectedNode = null
        searchQuery = ""
        searchResults = emptyList()
        currentSearchIndex = -1
        expandedNodes.clear()
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
                                accessory = FilePreview(this)
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
                                val selectedFile = fileChooser.selectedFile
                                updateLastDirectory(selectedFile)
                                scope.launch {
                                    isLoading = true
                                    withContext(Dispatchers.IO) {
                                        val baseName = selectedFile.absolutePath.substringBeforeLast(".")
                                        val ext = selectedFile.extension.lowercase()
                                        
                                        val (pngFile, dumpFile) = when (ext) {
                                            "uix", "xml" -> {
                                                File("$baseName.png") to selectedFile
                                            }
                                            else -> null to null
                                        }

                                        screenshot = pngFile?.takeIf { it.exists() }?.let {
                                            try { FileInputStream(it).use { stream -> loadImageBitmap(stream) } } catch (e: Exception) { null }
                                        }
                                        rootNode = dumpFile?.let { AdbUtils.parseXmlFile(it) }
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
                                expandNodeAndParents(it)
                            }
                        } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val msg = if (selectedDevice == null) "Connect device or load saved dump" else "No screenshot. Click capture to fetch."
                            Text(msg, color = Color.White)
                        }
                    }

                    VerticalDivider()

                    // Right: Hierarchy and Details
                    Column(modifier = Modifier.weight(0.4f).fillMaxHeight()) {
                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                searchResults = findAllNodes(rootNode, it)
                                currentSearchIndex = if (searchResults.isNotEmpty()) 0 else -1
                                if (currentSearchIndex != -1) {
                                    val node = searchResults[currentSearchIndex]
                                    expandNodeAndParents(node)
                                    selectedNode = node
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            placeholder = { Text("Search nodes...") },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            "${if (searchResults.isEmpty()) 0 else currentSearchIndex + 1}/${searchResults.size}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        IconButton(onClick = {
                                            if (searchResults.isNotEmpty()) {
                                                currentSearchIndex = (currentSearchIndex - 1 + searchResults.size) % searchResults.size
                                                val node = searchResults[currentSearchIndex]
                                                expandNodeAndParents(node)
                                                selectedNode = node
                                            }
                                        }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                        IconButton(onClick = {
                                            if (searchResults.isNotEmpty()) {
                                                currentSearchIndex = (currentSearchIndex + 1) % searchResults.size
                                                val node = searchResults[currentSearchIndex]
                                                expandNodeAndParents(node)
                                                selectedNode = node
                                            }
                                        }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                        IconButton(onClick = {
                                            searchQuery = ""
                                            searchResults = emptyList()
                                            currentSearchIndex = -1
                                        }) { Icon(Icons.Default.Close, null) }
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                        )

                        Box(modifier = Modifier.weight(0.6f)) {
                            rootNode?.let { node ->
                                HierarchyTree(node, selectedNode, expandedNodes) {
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

private data class FlattenedNode(val node: UiNode, val depth: Int)

@Composable
fun HierarchyTree(
    root: UiNode,
    selectedNode: UiNode?,
    expandedNodes: MutableMap<UiNode, Boolean>,
    onNodeSelected: (UiNode) -> Unit
) {
    val state = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()

    val visibleNodes = remember(root, expandedNodes.toMap()) {
        val list = mutableListOf<FlattenedNode>()
        fun walk(node: UiNode, depth: Int) {
            list.add(FlattenedNode(node, depth))
            if (expandedNodes[node] ?: true) {
                node.children.forEach { walk(it, depth + 1) }
            }
        }
        walk(root, 0)
        list
    }

    LaunchedEffect(selectedNode) {
        selectedNode?.let { node ->
            val index = visibleNodes.indexOfFirst { it.node == node }
            if (index != -1) {
                state.animateScrollToItem(index)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScrollState).padding(8.dp)) {
            items(visibleNodes) { flattened ->
                TreeRow(
                    node = flattened.node,
                    depth = flattened.depth,
                    isExpanded = expandedNodes[flattened.node] ?: true,
                    isSelected = flattened.node == selectedNode,
                    onNodeSelected = onNodeSelected,
                    onToggleExpand = { expandedNodes[flattened.node] = !(expandedNodes[flattened.node] ?: true) }
                )
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(state)
        )
    }
}

@Composable
fun TreeRow(
    node: UiNode,
    depth: Int,
    isExpanded: Boolean,
    isSelected: Boolean,
    onNodeSelected: (UiNode) -> Unit,
    onToggleExpand: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNodeSelected(node) }
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .padding(start = (depth * 16).dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (node.children.isNotEmpty()) {
            Text(
                text = if (isExpanded) "▼ " else "▶ ",
                modifier = Modifier.clickable { onToggleExpand() },
                fontSize = 12.sp
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(text = node.getDisplayName(), fontSize = 12.sp, maxLines = 1, softWrap = false)
    }
}

private fun findAllNodes(root: UiNode?, query: String): List<UiNode> {
    if (root == null || query.isBlank()) return emptyList()
    val results = mutableListOf<UiNode>()
    fun walk(node: UiNode) {
        val match = node.text.contains(query, ignoreCase = true) ||
                node.resourceId.contains(query, ignoreCase = true) ||
                node.contentDesc.contains(query, ignoreCase = true) ||
                node.className.contains(query, ignoreCase = true)
        if (match) {
            results.add(node)
        }
        node.children.forEach { walk(it) }
    }
    walk(root)
    return results
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

@Composable
fun ScreenshotView(
    bitmap: ImageBitmap,
    rootNode: UiNode?,
    selectedNode: UiNode?,
    onNodeSelected: (UiNode) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val imageWidth = bitmap.width.toFloat()
        val imageHeight = bitmap.height.toFloat()

        val scale = minOf(
            constraints.maxWidth.toFloat() / imageWidth,
            constraints.maxHeight.toFloat() / imageHeight
        )

        val dw = imageWidth * scale
        val dh = imageHeight * scale

        val density = LocalDensity.current

        Box(
            modifier = Modifier
                .size(with(density) { dw.toDp() }, with(density) { dh.toDp() })
                .pointerInput(rootNode, scale) {
                    detectTapGestures { offset ->
                        val x = offset.x / scale
                        val y = offset.y / scale
                        findNodeAt(rootNode, x, y)?.let { onNodeSelected(it) }
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
                selectedNode?.let { node ->
                    val rect = node.bounds
                    val left = rect.left * scale
                    val top = rect.top * scale
                    val width = rect.width * scale
                    val height = rect.height * scale

                    drawRect(
                        color = Color.Red,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 2.dp.toPx())
                    )

                    drawRect(
                        color = Color.Red.copy(alpha = 0.1f),
                        topLeft = Offset(left, top),
                        size = Size(width, height)
                    )
                }
            }
        }
    }
}

private fun findNodeAt(root: UiNode?, x: Float, y: Float): UiNode? {
    if (root == null) return null
    var bestMatch: UiNode? = null
    fun walk(node: UiNode) {
        if (node.bounds.contains(Offset(x, y))) {
            bestMatch = node
            node.children.forEach { walk(it) }
        }
    }
    walk(root)
    return bestMatch
}

private class FilePreview(fc: JFileChooser) : JComponent(), PropertyChangeListener {
    private var thumbnail: ImageIcon? = null
    private var file: File? = null

    init {
        preferredSize = Dimension(250, 250)
        fc.addPropertyChangeListener(this)
    }

    private fun loadImage() {
        if (file == null || file!!.isDirectory) {
            thumbnail = null
            return
        }

        val path = file!!.absolutePath
        val pngFile = when (file!!.extension.lowercase()) {
            "png" -> file
            "uix", "xml" -> File(path.substringBeforeLast(".") + ".png")
            else -> null
        }

        if (pngFile != null && pngFile.exists()) {
            val tmpIcon = ImageIcon(pngFile.path)
            if (tmpIcon.iconWidth > 240 || tmpIcon.iconHeight > 240) {
                val scale = 240f / maxOf(tmpIcon.iconWidth, tmpIcon.iconHeight)
                val newW = (tmpIcon.iconWidth * scale).toInt()
                val newH = (tmpIcon.iconHeight * scale).toInt()
                thumbnail = ImageIcon(tmpIcon.image.getScaledInstance(newW, newH, AwtImage.SCALE_SMOOTH))
            } else {
                thumbnail = tmpIcon
            }
        } else {
            thumbnail = null
        }
    }

    override fun propertyChange(e: PropertyChangeEvent) {
        if (JFileChooser.SELECTED_FILE_CHANGED_PROPERTY == e.propertyName) {
            file = e.newValue as? File
            if (isShowing) {
                loadImage()
                repaint()
            }
        }
    }

    override fun paintComponent(g: Graphics) {
        if (thumbnail == null) {
            loadImage()
        }
        thumbnail?.let {
            val x = width / 2 - it.iconWidth / 2
            val y = height / 2 - it.iconHeight / 2
            it.paintIcon(this, g, maxOf(x, 0), maxOf(y, 0))
        }
    }
}
