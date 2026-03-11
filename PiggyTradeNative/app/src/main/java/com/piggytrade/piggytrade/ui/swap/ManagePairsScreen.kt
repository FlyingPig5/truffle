package com.piggytrade.piggytrade.ui.swap
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

@Composable
fun ManagePairsScreen(
    viewModel: SwapViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var draggingItem by remember { mutableStateOf<PoolMapping?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var whitelistColumnBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
    var showRenameDialog by remember { mutableStateOf<PoolMapping?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var discoveredSearchQuery by remember { mutableStateOf("") }
    var whitelistSearchQuery by remember { mutableStateOf("") }
    
    // New Toggle State: 0 = All, 1 = ERG to Token, 2 = Token to Token
    var filterType by remember { mutableStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        val syncing = uiState.syncProgress != null && !uiState.syncProgress!!.isFinished
        if (!syncing) {
            viewModel.loadPoolMappings(fetchLiquidity = true)
        }
    }

    if (uiState.syncProgress != null) {
        SyncProgressPopup(uiState.syncProgress!!, onDismiss = { viewModel.dismissSyncPopup() })
    }

    Box(modifier = Modifier.fillMaxSize().background(ColorBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TogaIconButton(
                    icon = "\uEF7D", // BACK
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                    radius = 10.dp,
                    bgColor = ColorBlue
                )
                Text(
                    text = "Manage Trading Pairs",
                    color = ColorText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp).weight(1f)
                )
            }

            // Sub-header with centered filters and right-aligned reset
            var showResetDialog by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                // Centered Filters
                Row(
                    modifier = Modifier
                        .background(ColorInputBg, RoundedCornerShape(10.dp))
                        .padding(2.dp)
                ) {
                    listOf("All", "ERG", "Token").forEachIndexed { index, label ->
                        Box(
                            modifier = Modifier
                                .background(
                                    if (filterType == index) ColorBlue else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { filterType = index }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (filterType == index) Color.White else ColorTextDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Right-aligned Reset Button
                TogaIconButton(
                    icon = "\uE872", // DELETE/TRASH
                    onClick = { showResetDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterEnd),
                    radius = 10.dp,
                    bgColor = ColorDanger
                )

                if (showResetDialog) {
                    AlertDialog(
                        onDismissRequest = { showResetDialog = false },
                        title = { Text("Reset Token Data?") },
                        text = { Text("This will erase all custom whitelisted pairs and downloaded token metadata. The app will revert to the default official token list.") },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.resetTokenData()
                                showResetDialog = false
                            }) {
                                Text("Reset All", color = ColorDanger)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showResetDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }
            }

            // Section Titles Row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Whitelisted",
                        color = ColorAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(1.dp)) // Space for virtual divider
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "All Pairs",
                        color = ColorOrange,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hold & Drag to Whitelist",
                        color = ColorTextDim,
                        fontSize = 9.sp
                    )
                }
            }

            // Search Boxes Row (Aligned Horizontally)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextField(
                    value = whitelistSearchQuery,
                    onValueChange = { whitelistSearchQuery = it },
                    placeholder = { Text("Search whitelisted...", color = ColorInputHint, fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ColorInputBg,
                        unfocusedContainerColor = ColorInputBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )

                TextField(
                    value = discoveredSearchQuery,
                    onValueChange = { discoveredSearchQuery = it },
                    placeholder = { Text("Search discovered...", color = ColorInputHint, fontSize = 12.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ColorInputBg,
                        unfocusedContainerColor = ColorInputBg,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            // Function to apply filters
            fun filterMappings(list: List<PoolMapping>, query: String): List<PoolMapping> {
                return list.filter { mapping ->
                    val matchesQuery = mapping.name.contains(query, ignoreCase = true) || mapping.pid.contains(query, ignoreCase = true)
                    val matchesType = when (filterType) {
                        1 -> !mapping.data.containsKey("id_in") // ERG to Token
                        2 -> mapping.data.containsKey("id_in")  // Token to Token
                        else -> true                     // All
                    }
                    matchesQuery && matchesType
                }
            }

            // Split View with Lists
            Row(modifier = Modifier.fillMaxSize()) {
                // Whitelisted Column
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(5.dp)) {
                    val filteredWhitelist = filterMappings(uiState.whitelistedPools, whitelistSearchQuery)

                    MappingColumn(
                        title = null,
                        items = filteredWhitelist,
                        isWhitelisted = true,
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                whitelistColumnBounds = coordinates.positionInRoot().let { pos ->
                                    androidx.compose.ui.geometry.Rect(pos, coordinates.size.let { androidx.compose.ui.geometry.Size(it.width.toFloat(), it.height.toFloat()) })
                                }
                            },
                        vm = viewModel,
                        onRemove = { mapping -> viewModel.togglePoolWhitelist(mapping, false) }
                    )
                }

                // Divider
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(Color(0xFF535C6E)))

                // Discovered Column
                Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(5.dp)) {
                    val filteredDiscovered = filterMappings(uiState.discoveredPools, discoveredSearchQuery)

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredDiscovered, key = { it.key }) { item ->
                            DraggableMappingItem(
                                mapping = item,
                                vm = viewModel,
                                onDragStart = { initialPos -> 
                                    draggingItem = item
                                    dragOffset = initialPos
                                },
                                onDrag = { offset -> dragOffset += offset },
                                onDragEnd = {
                                    val itemHalfWidthPx = (160.dp.value * density.density) / 2
                                    val itemHalfHeightPx = (60.dp.value * density.density) / 2
                                    
                                    val dropPoint = Offset(
                                        x = dragOffset.x + itemHalfWidthPx,
                                        y = dragOffset.y + itemHalfHeightPx
                                    )
                                    
                                    draggingItem?.let { target ->
                                         // Lenient check: if center is in the left half of the screen
                                         val inBounds = whitelistColumnBounds?.let { bounds ->
                                             dropPoint.x < bounds.right + 20.dp.value * density.density
                                         } ?: false

                                         if (inBounds) {
                                             val existingNames = uiState.whitelistedPools.map { it.name }.toSet()
                                             // If name already exists or it's a "Duplicate" discovery (has suffix)
                                             if (existingNames.contains(target.name) || target.name.contains(" (")) {
                                                 showRenameDialog = target
                                                 // Suggest name without the PID suffix or with a -2 suffix
                                                 val base = target.name.substringBefore(" (")
                                                 renameValue = if (existingNames.contains(base)) "$base-2" else base
                                             } else {
                                                 viewModel.togglePoolWhitelist(target, true)
                                             }
                                         } else {
                                             android.widget.Toast.makeText(context, "Drop on left side to whitelist", android.widget.Toast.LENGTH_SHORT).show()
                                         }
                                     }
                                     draggingItem = null
                                 }
                            )
                        }
                    }
                }
            }
        }

        // Overlay for dragged item
        draggingItem?.let { item ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .zIndex(100f)
                    .width(160.dp)
            ) {
                MappingItemContent(item, viewModel, isDragging = true)
            }
        }
    }

    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Custom Pair") },
            text = {
                Column {
                    Text("An official pair with this name already exists. Please rename your addition.")
                    TextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showRenameDialog?.let { viewModel.togglePoolWhitelist(it, true, renameValue) }
                    showRenameDialog = null
                }) {
                    Text("Whitelist")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DraggableMappingItem(
    mapping: PoolMapping,
    vm: SwapViewModel,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var itemPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .onGloballyPositioned { coordinates ->
                itemPos = coordinates.positionInRoot()
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart(itemPos) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
    ) {
        MappingItemContent(mapping, vm)
    }
}

@Composable
fun MappingColumn(
    title: String?,
    items: List<PoolMapping>,
    isWhitelisted: Boolean,
    modifier: Modifier = Modifier,
    vm: SwapViewModel,
    onRemove: (PoolMapping) -> Unit
) {
    Column(modifier = modifier.fillMaxHeight().padding(5.dp)) {
        if (title != null) {
            Text(
                text = title,
                color = if (isWhitelisted) ColorAccent else ColorOrange,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 10.dp).align(Alignment.CenterHorizontally)
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }) { item ->
                MappingItemWithActions(item, vm, onRemove)
            }
        }
    }
}

@Composable
fun MappingItemWithActions(
    mapping: PoolMapping,
    vm: SwapViewModel,
    onRemove: (PoolMapping) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .androidBorder(radius = 10.dp, borderWidth = 1.dp, borderColor = Color(0xFF535C6E), bgColor = ColorCard)
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenImage(tokenId = vm.getTokenId(mapping.name), modifier = Modifier.size(20.dp))
                Text(
                    text = mapping.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 5.dp).weight(1f)
                )
                if (mapping.status == 1) { // User added
                    Text(
                        text = "\uE872", // Trash
                        fontFamily = MaterialDesignIcons,
                        color = ColorDanger,
                        fontSize = 16.sp,
                        modifier = Modifier.clickable { onRemove(mapping) }.padding(start = 5.dp)
                    )
                } else if (mapping.status == 0) {
                    Text(
                        text = "\uE897", // Lock
                        fontFamily = MaterialDesignIcons,
                        color = ColorTextDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }
            Text(
                text = "ID: ${mapping.pid.take(8)}...",
                color = ColorTextDim,
                fontSize = 8.sp
            )
            Text(
                text = "Liquidity: ${mapping.liquidity}",
                color = if (mapping.status == 0) ColorAccent else ColorOrange,
                fontSize = 11.sp, // Slightly larger for clarity
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )

            // User Balance
            val parts = mapping.name.split("-")
            val balanceStr = if (parts.size == 2) {
                val b1 = vm.getUserBalance(parts[0]) ?: "0"
                val b2 = vm.getUserBalance(parts[1]) ?: "0"
                "Your Balance: $b1 ${parts[0]} / $b2 ${parts[1]}"
            } else {
                val bToken = vm.getUserBalance(mapping.name) ?: "0"
                "Your Balance: $bToken ${mapping.name}"
            }
            Text(
                text = balanceStr,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun MappingItemContent(
    mapping: PoolMapping,
    vm: SwapViewModel,
    isDragging: Boolean = false
) {
    val scale by animateFloatAsState(if (isDragging) 1.1f else 1f)
    val alpha by animateFloatAsState(if (isDragging) 0.8f else 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .androidBorder(radius = 10.dp, borderWidth = 1.dp, borderColor = Color(0xFF535C6E), bgColor = if (isDragging) ColorBlue.copy(alpha=0.5f) else ColorCard)
            .padding(8.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenImage(tokenId = vm.getTokenId(mapping.name), modifier = Modifier.size(20.dp))
                Text(
                    text = mapping.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 5.dp).weight(1f)
                )
            }
            Text(
                text = "ID: ${mapping.pid.take(8)}...",
                color = ColorTextDim,
                fontSize = 8.sp
            )
            Text(
                text = mapping.liquidity,
                color = ColorOrange,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp)
            )

            // User Balance
            val parts = mapping.name.split("-")
            val balanceStr = if (parts.size == 2) {
                val b1 = vm.getUserBalance(parts[0]) ?: "0"
                val b2 = vm.getUserBalance(parts[1]) ?: "0"
                "Your Balance: $b1 ${parts[0]} / $b2 ${parts[1]}"
            } else {
                val bToken = vm.getUserBalance(mapping.name) ?: "0"
                "Your Balance: $bToken ${mapping.name}"
            }
            Text(
                text = balanceStr,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 8.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
