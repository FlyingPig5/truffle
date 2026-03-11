package com.piggytrade.piggytrade.ui.settings
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.wallet.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

@Composable
fun SettingsScreen(
    viewModel: SwapViewModel, 
    onBack: () -> Unit, 
    onNavigateToAddNode: () -> Unit,
    onNavigateToManagePairs: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showExportWarning by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showSyncConfirm by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf("") }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val contentResolver = context.contentResolver
            try {
                contentResolver.openInputStream(it)?.use { inputStream ->
                    val json = inputStream.bufferedReader().readText()
                    pendingImportJson = json
                    showImportConfirm = true
                }
            } catch (e: Exception) {
                android.util.Log.e("Settings", "Failed to read file: ${e.message}")
            }
        }
    }

    if (uiState.syncProgress != null) {
        SyncProgressPopup(uiState.syncProgress!!, onDismiss = { viewModel.dismissSyncPopup() })
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
        // Header
        TogaRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .padding(bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(
                icon = "\uEF7D", // ICON_BACK
                onClick = onBack,
                modifier = Modifier.size(36.dp),
                radius = 10.dp,
                bgColor = ColorBlue, // BTN_BACK_SET_COLOR is COLOR_BLUE
                iconColor = Color.White
            )
            Text(
                text = "Settings",
                color = ColorText,
                fontSize = 18.sp, // FONT_SIZE_TITLE ?? theme.py says FONT_SIZE_TITLE=18
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            )
        }

        // Main Content Area
        TradeCard(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            TogaColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 20.dp, top = 5.dp)
            ) {

            TogaColumn(
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_settings_screen_header),
                        contentDescription = "Large Logo",
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(55.dp))
                    )
                    
                    // The "Feather" Overlay: Fades from transparent in the center 
                    // to the card background color at the edges.
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.5f to Color.Transparent,
                                        0.65f to Color.Transparent,
                                        0.95f to ColorCard
                                    )
                                )
                            )
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Text(
                        text = "Ergo trading shouldn't be a desk job.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Swap on the go!",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.width(6.dp))

                        Image(
                            painter = painterResource(id = R.drawable.ic_github_logo_settings_footer),
                            contentDescription = "GitHub",
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { 
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW, 
                                        android.net.Uri.parse("https://github.com/FlyingPig5/piggy-trade")
                                    )
                                    context.startActivity(intent)
                                }
                        )
                    }
                }
            }

            // Node Section
            Text(
                text = "NODE",
                color = ColorText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 15.dp, bottom = 5.dp, start = 10.dp)
            )

            TogaRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TogaRow(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorInputBg)
                        .clickable { viewModel.setActiveSelector("node") },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_compass),
                        contentDescription = null,
                        tint = ColorAccent,
                        modifier = Modifier.padding(start = 12.dp).size(24.dp)
                    )
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                    ) {
                        val nodeName = uiState.nodes.getOrNull(uiState.selectedNodeIndex) ?: "Select Node"
                        Text(
                            text = nodeName.substringBefore(":"),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            text = nodeName.substringAfter(": "),
                            color = ColorTextDim,
                            fontSize = 10.sp
                        )
                    }
                    Icon(
                        painter = painterResource(id = android.R.drawable.arrow_down_float),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))
                TogaIconButton(
                    icon = "\uE145", // ICON_PLUS
                    onClick = onNavigateToAddNode,
                    modifier = Modifier.size(40.dp),
                    radius = 8.dp,
                    borderColor = ColorBorder,
                    bgColor = ColorInputBg
                )
                Spacer(modifier = Modifier.width(6.dp))
                TogaIconButton(
                    icon = "\uE872", // ICON_TRASH
                    onClick = { viewModel.deleteNode() },
                    modifier = Modifier.size(40.dp),
                    radius = 8.dp,
                    bgColor = Color(0xFF9E1F1F)
                )
            }


            TogaRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 10.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Include unconfirmed",
                    color = ColorText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.includeUnconfirmed,
                    onCheckedChange = { viewModel.setIncludeUnconfirmed(it) },
                    modifier = Modifier.scale(0.8f)
                )
            }

            // Favorites Setting
            Text(
                text = "FAVORITES",
                color = ColorText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp, bottom = 5.dp, start = 10.dp)
            )
            TogaRow(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Count:", color = ColorText, fontSize = 12.sp)
                Slider(
                    value = uiState.numFavorites.toFloat(),
                    onValueChange = { viewModel.setNumFavorites(it.toInt()) },
                    valueRange = 4f..20f,
                    steps = 15,
                    modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                )
                Text(text = "${uiState.numFavorites}", color = ColorText, fontSize = 12.sp, modifier = Modifier.width(30.dp))
            }
            TogaRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = 10.dp, end = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced",
                    color = ColorText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.debugMode,
                    onCheckedChange = { viewModel.setDebugMode(it) },
                    modifier = Modifier.scale(0.8f)
                )
            }


            if (uiState.debugMode) {

                // ── Check Tx Mode ─────────────────────────────────────────────
                Text(
                    text = "TRANSACTION VALIDATION",
                    color = ColorText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 5.dp, start = 10.dp)
                )

                TogaRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Check Tx",
                        color = ColorText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = uiState.isSimulation,
                        onCheckedChange = { viewModel.setSimulationMode(it) },
                        modifier = Modifier.scale(0.8f)
                    )
                }

                Text(
                    text = "When enabled, signed transactions are submitted to the node for validation only — " +
                           "the node will verify all scripts and balances, but the transaction will NOT be broadcast " +
                           "to the network or executed on-chain.",
                    color = ColorTextDim,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp).padding(bottom = 10.dp)
                )

                // Token Management Section
                Text(
                    text = "TOKEN LIST MANAGEMENT",
                    color = ColorText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 10.dp)
                )

                TogaRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg)
                            .clickable { 
                                showSyncConfirm = true
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Manage Trading Pairs", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }


                // Wallet Management Section
                Text(
                    text = "WALLET MANAGEMENT",
                    color = ColorText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 10.dp, start = 10.dp)
                )

                TogaRow(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg)
                            .clickable { showExportWarning = true }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Backup Wallets", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = Color(0xFF9E1F1F))
                            .clickable { importLauncher.launch("*/*") }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Restore Wallets", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

            }
        }
    }
}



    // Animated Selector Overlay for Node Selection
    Box(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = uiState.activeSelector == "node",
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { viewModel.setActiveSelector(null) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.75f)
                        .align(Alignment.BottomCenter)
                        .background(ColorBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { /* Consume click */ }
                ) {
                    SelectorScreen(
                        title = "Select Node",
                        items = uiState.nodes,
                        onSelect = { node ->
                            viewModel.finalizeSelection(node)
                            viewModel.setActiveSelector(null)
                        },
                        onBack = { viewModel.setActiveSelector(null) },
                        getName = { it.substringBefore(":") },
                        getId = { it.substringAfter(": ") },
                        getBalance = { null },
                        showFullId = true,
                        showSearch = false
                    )
                }
            }
        }
    }

    if (showSyncConfirm) {
        AlertDialog(
            onDismissRequest = { showSyncConfirm = false },
            title = { Text("Sync Trading Pairs", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { 
                Text(
                    "Do you want to sync with chain before managing pairs?",
                    color = Color.White,
                    fontSize = 16.sp
                ) 
            },
            containerColor = ColorCard,
            confirmButton = {
                TextButton(onClick = {
                    showSyncConfirm = false
                    viewModel.syncTokenList()
                    onNavigateToManagePairs()
                }) {
                    Text("YES", color = ColorAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showSyncConfirm = false
                    onNavigateToManagePairs()
                }) {
                    Text("NO", color = Color.White)
                }
            }
        )
    }

    if (showExportWarning) {
        AlertDialog(
            onDismissRequest = { showExportWarning = false },
            title = { Text("SECURITY WARNING", color = ColorSent, fontWeight = FontWeight.ExtraBold) },
            text = { 
                Text(
                    "This will export all your wallets including encrypted mnemonics.\n\n" +
                    "IMPORTANT: Wallets secured with Biometrics are encrypted with a device-specific key. " +
                    "They can only be restored on THIS device. For other devices, use the original mnemonic.\n\n" +
                    "Even though they are encrypted, you MUST store this backup file securely.",
                    color = Color.White,
                    fontSize = 16.sp
                ) 
            },
            containerColor = ColorCard,
            confirmButton = {
                TextButton(onClick = {
                    showExportWarning = false
                    viewModel.exportWallets(context)
                }) {
                    Text("I UNDERSTAND", color = ColorAccent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarning = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Overwrite Wallets?", color = Color.White) },
            text = { 
                Text(
                    "Importing wallets will OVERWRITE all current wallets on this device.\n\n" +
                    "Are you sure you want to proceed?",
                    color = Color.White
                ) 
            },
            containerColor = ColorCard,
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    viewModel.importWallets(pendingImportJson)
                }) {
                    Text("OVERWRITE", color = ColorSent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }
}
}
