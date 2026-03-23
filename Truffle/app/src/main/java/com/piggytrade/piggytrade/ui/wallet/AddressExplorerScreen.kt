package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.*

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Address Explorer screen — a mini blockchain explorer for any Ergo address.
 * Can be launched from:
 *  - The "Explorer" bottom tab (empty address, user types/scans)
 *  - Tapping an address in transaction details (pre-filled)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressExplorerScreen(
    viewModel: SwapViewModel,
    initialAddress: String = "",
    onBack: () -> Unit,
    onNavigateToQrScanner: () -> Unit = {},
    onNavigateToAddressExplorer: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Local address input state
    var addressInput by remember(initialAddress) { mutableStateOf(initialAddress) }
    var selectedTab by remember { mutableStateOf(0) }

    // Save dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var saveLabel by remember { mutableStateOf("") }

    // Explore popup state for recursive exploration
    var explorePopupAddress by remember { mutableStateOf<String?>(null) }

    // Delete confirmation state
    var deleteConfirmAddress by remember { mutableStateOf<String?>(null) }

    // Trigger fetch when initial address is provided
    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotEmpty()) {
            viewModel.openAddressExplorer(initialAddress)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ─── Header ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.explorerAddress.isNotEmpty()) {
                // Back arrow clears the current exploration (returns to saved list / empty state)
                IconButton(onClick = {
                    viewModel.clearExplorerState()
                    addressInput = ""
                }) {
                    Text(
                        text = "\uE5C4",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontFamily = MaterialDesignIcons
                    )
                }
            }
            Text(
                text = "🔍 Address Explorer",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (uiState.explorerAddress.isEmpty()) 0.dp else 6.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            if (uiState.isLoadingExplorer || uiState.isLoadingExplorerHistory) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ColorAccent,
                    strokeWidth = 2.dp
                )
            }
        }

        // ─── Address Input ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = addressInput,
                onValueChange = { addressInput = it },
                placeholder = { Text("Enter Ergo address...", color = ColorTextDim, fontSize = 13.sp) },
                modifier = Modifier.weight(1f).height(52.dp),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color.White,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ColorAccent,
                    unfocusedBorderColor = ColorInputBg,
                    cursorColor = ColorAccent
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    if (addressInput.isNotEmpty()) {
                        viewModel.openAddressExplorer(addressInput)
                        keyboardController?.hide()
                    }
                }),
                shape = RoundedCornerShape(10.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // QR scan button
            IconButton(
                onClick = onNavigateToQrScanner,
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR",
                    tint = ColorAccent,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            // Go button
            TogaIconButton(
                icon = "\uE8B6", // search icon
                onClick = {
                    if (addressInput.isNotEmpty()) {
                        viewModel.openAddressExplorer(addressInput)
                        keyboardController?.hide()
                    }
                },
                modifier = Modifier.size(44.dp),
                radius = 10.dp,
                bgColor = ColorAccent,
                iconColor = ColorBg
            )
        }

        // ─── Saved Addresses (when no active exploration) ───
        if (uiState.explorerAddress.isEmpty() && uiState.savedExplorerAddresses.isNotEmpty()) {
            Text(
                "Saved Addresses",
                color = ColorAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(uiState.savedExplorerAddresses.entries.toList()) { (address, label) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                addressInput = address
                                viewModel.openAddressExplorer(address)
                            },
                        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    "${address.take(10)}...${address.takeLast(8)}",
                                    color = ColorTextDim,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            TogaIconButton(
                                icon = "\uE872", // delete icon
                                onClick = { deleteConfirmAddress = address },
                                modifier = Modifier.size(28.dp),
                                radius = 6.dp,
                                bgColor = Color(0xFF9E1F1F),
                                iconColor = Color.White,
                                iconSize = 16.dp
                            )
                        }
                    }
                }
            }
        } else if (uiState.explorerAddress.isEmpty()) {
            // Empty state
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Enter an Ergo address to explore",
                        color = ColorTextDim,
                        fontSize = 14.sp
                    )
                    Text(
                        "View balance, tokens & transactions",
                        color = ColorTextDim.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // ─── Active Exploration Content ───
        if (uiState.explorerAddress.isNotEmpty()) {
            // Address & Balance Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // External address badge + save button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = ColorAccent.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = if (uiState.savedExplorerAddresses.containsKey(uiState.explorerAddress))
                                    "★ ${uiState.savedExplorerAddresses[uiState.explorerAddress]}"
                                else "External Address",
                                color = ColorAccent,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        if (!uiState.savedExplorerAddresses.containsKey(uiState.explorerAddress)) {
                            Text(
                                text = "★ Save",
                                color = ColorAccent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable {
                                    saveLabel = ""
                                    showSaveDialog = true
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // Address (copyable)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${uiState.explorerAddress.take(12)}...${uiState.explorerAddress.takeLast(10)}",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { clipboardManager.setText(AnnotatedString(uiState.explorerAddress)) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(
                                text = "\uE14D",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontFamily = MaterialDesignIcons
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))

                    // Balance
                    Text("Balance", color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        text = "${SwapViewModel.formatErg(uiState.explorerErgBalance)} ERG",
                        color = ColorAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (uiState.isLoadingExplorer) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = ColorAccent,
                            trackColor = ColorInputBg
                        )
                    }
                }
            }

            // ─── Tabs ───
            val tabLabels = listOf("Tokens", "Transactions")
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = ColorAccent,
                divider = { HorizontalDivider(color = ColorInputBg, thickness = 1.dp) },
                modifier = Modifier.padding(bottom = 10.dp)
            ) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                        Text(
                            label,
                            modifier = Modifier.padding(vertical = 8.dp),
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            // ─── Tokens Tab ───
            if (selectedTab == 0) {
                val sortedTokens = remember(uiState.explorerTokens) {
                    uiState.explorerTokens.toList().sortedByDescending { (_, amount) -> amount }
                }
                if (sortedTokens.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.isLoadingExplorer) "Fetching balances..." else "No tokens found",
                            color = ColorTextDim
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(sortedTokens) { (tokenId, amount) ->
                            TokenBalanceItem(tokenId, amount, viewModel)
                        }
                    }
                }
            }

            // ─── Transactions Tab ───
            if (selectedTab == 1) {
                val trades = uiState.explorerTrades
                if (trades.isEmpty() && !uiState.isLoadingExplorerHistory) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No transaction history", color = ColorTextDim)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        items(trades) { trade ->
                            NetworkTradeHistoryItemView(
                                trade = trade,
                                viewModel = viewModel,
                                onAddressClick = { addr ->
                                    explorePopupAddress = addr
                                }
                            )
                        }
                        if (uiState.isLoadingExplorerHistory) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(24.dp))
                                }
                            }
                        } else if (trades.isNotEmpty()) {
                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.fetchExplorerTransactionHistory(loadMore = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Delete Confirmation Dialog ───
    if (deleteConfirmAddress != null) {
        val addrToDelete = deleteConfirmAddress!!
        val addrLabel = uiState.savedExplorerAddresses[addrToDelete] ?: "Unknown"
        val truncAddr = if (addrToDelete.length > 20) "${addrToDelete.take(10)}...${addrToDelete.takeLast(8)}" else addrToDelete
        AlertDialog(
            onDismissRequest = { deleteConfirmAddress = null },
            containerColor = ColorCard,
            title = { Text("Delete Saved Address", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Do you want to delete this saved address?", color = ColorTextDim, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(addrLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(truncAddr, color = ColorTextDim, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeExplorerAddress(addrToDelete)
                    // Clear explorer state if this address was being explored
                    if (uiState.explorerAddress == addrToDelete) {
                        viewModel.clearExplorerState()
                        addressInput = ""
                    }
                    deleteConfirmAddress = null
                }) {
                    Text("Yes", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmAddress = null }) {
                    Text("No", color = ColorTextDim)
                }
            }
        )
    }

    // ─── Save Address Dialog ───
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = ColorCard,
            title = { Text("Save Address", color = Color.White) },
            text = {
                Column {
                    Text(
                        "Give this address a name to track it:",
                        color = ColorTextDim,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = saveLabel,
                        onValueChange = { saveLabel = it },
                        placeholder = { Text("e.g. Treasury, Mining Pool...", color = ColorTextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ColorAccent,
                            unfocusedBorderColor = ColorInputBg,
                            cursorColor = ColorAccent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (saveLabel.isNotBlank()) {
                            viewModel.saveExplorerAddress(uiState.explorerAddress, saveLabel.trim())
                            showSaveDialog = false
                        }
                    }
                ) {
                    Text("Save", color = ColorAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = ColorTextDim)
                }
            }
        )
    }

    // ─── Explore Address Popup ───
    if (explorePopupAddress != null) {
        val addr = explorePopupAddress!!
        val truncAddr = if (addr.length > 20) "${addr.take(10)}...${addr.takeLast(8)}" else addr
        AlertDialog(
            onDismissRequest = { explorePopupAddress = null },
            containerColor = ColorCard,
            title = { Text("Explore Wallet", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("View balance & transactions for:", color = ColorTextDim, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        truncAddr,
                        color = ColorAccent,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = explorePopupAddress!!
                    explorePopupAddress = null
                    addressInput = target
                    viewModel.openAddressExplorer(target)
                }) {
                    Text("Explore", color = ColorAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { explorePopupAddress = null }) {
                    Text("Cancel", color = ColorTextDim)
                }
            }
        )
    }
}
