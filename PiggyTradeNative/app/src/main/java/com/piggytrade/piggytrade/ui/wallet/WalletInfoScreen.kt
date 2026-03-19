package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment.Companion.TopCenter
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletInfoScreen(
    walletName: String,
    viewModel: SwapViewModel,
    onBack: () -> Unit,
    onNavigateToAddWallet: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
            .padding(20.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Text(
                    text = "\uE5C4", 
                    color = Color.White, 
                    fontSize = 24.sp,
                    fontFamily = MaterialDesignIcons
                )
            }
            Text(
                text = "Wallet Info: $walletName",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            if (uiState.isLoadingWallet || uiState.isLoadingHistory) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ColorAccent,
                    strokeWidth = 2.dp
                )
            }
        }

        WalletInfoContent(
            walletName = walletName,
            viewModel = viewModel,
            onDeleteComplete = onBack,
            showTitle = false,
            onNavigateToAddWallet = onNavigateToAddWallet
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletInfoContent(
    walletName: String,
    viewModel: SwapViewModel,
    onDeleteComplete: (() -> Unit)? = null,
    showTitle: Boolean = true,
    onNavigateToAddWallet: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteConfirm1 by remember { mutableStateOf(false) }
    var showDeleteConfirm2 by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.fetchWalletBalances()
        viewModel.fetchTransactionHistory()
        viewModel.fetchTokenUsdValues()
    }
    
    val tokenUsdValues = uiState.tokenUsdValues
    val sortedTokens = remember(uiState.walletTokens, tokenUsdValues) {
        uiState.walletTokens.toList().sortedWith { a, b ->
            val usdA = tokenUsdValues[a.first] ?: 0.0
            val usdB = tokenUsdValues[b.first] ?: 0.0
            // Swappable tokens first (those with USD value > 0)
            val swapA = if (usdA > 0.01) 0 else 1
            val swapB = if (usdB > 0.01) 0 else 1
            if (swapA != swapB) swapA.compareTo(swapB)
            // Within swappable: sort by USD value descending
            else if (swapA == 0) usdB.compareTo(usdA)
            // Within non-swappable: sort by name
            else {
                val nameA = viewModel.getTokenName(a.first)
                val nameB = viewModel.getTokenName(b.first)
                nameA.compareTo(nameB)
            }
        }
    }

    val pullToRefreshState = rememberPullToRefreshState()
    if (pullToRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            viewModel.fetchWalletBalances()
            viewModel.fetchTransactionHistory(loadMore = false)
            // Wait a bit to show the animation
            delay(1000)
            pullToRefreshState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullToRefreshState.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (showTitle) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onNavigateToAddWallet != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorSelectionBg,
                            modifier = Modifier.clickable { onNavigateToAddWallet() }
                        ) {
                            Text(
                                text = "Add Wallet",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
                
                if (walletName.isNotEmpty() && walletName != "Select Wallet") {
                    TogaIconButton(
                        icon = "\uE872", // ICON_TRASH
                        onClick = { showDeleteConfirm1 = true },
                        modifier = Modifier.size(36.dp),
                        radius = 8.dp,
                        bgColor = Color(0xFF9E1F1F),
                        iconColor = Color.White
                    )
                }

            }
        }

        if (showDeleteConfirm1) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm1 = false },
                title = { Text("Confirm Delete", color = Color.White) },
                text = { Text("Delete wallet $walletName?", color = Color.White) },
                containerColor = ColorCard,
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm1 = false
                        showDeleteConfirm2 = true
                    }) {
                        Text("Yes", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm1 = false }) {
                        Text("No", color = Color.White)
                    }
                }
            )
        }

        if (showDeleteConfirm2) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm2 = false },
                title = { Text("Confirm Delete", color = Color.White) },
                text = { Text("Are you really REALLY sure? This cannot be undone!", color = Color.White) },
                containerColor = ColorCard,
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm2 = false
                        viewModel.deleteWallet(walletName)
                        onDeleteComplete?.invoke()
                    }) {
                        Text("DELETE", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm2 = false }) {
                        Text("Cancel", color = Color.White)
                    }
                }
            )
        }

        // Address & Balance Card
        val isMnemonicWallet = !walletName.contains("Ergopay", ignoreCase = true) && walletName.isNotEmpty() && walletName != "Select Wallet"
        val numSelected = uiState.selectedAddresses.size
        
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = ColorCard),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                val clipboardManager = LocalClipboardManager.current
                
                // Show the selected (change) address prominently
                val displayAddr = if (uiState.changeAddress.isNotEmpty()) {
                    uiState.changeAddress
                } else {
                    uiState.selectedAddress
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val truncatedAddr = if (displayAddr.length > 20) {
                        "${displayAddr.take(8)}...${displayAddr.takeLast(8)}"
                    } else displayAddr
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = truncatedAddr,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (numSelected > 1) {
                            Text(
                                text = "$numSelected addresses active",
                                color = ColorTextDim,
                                fontSize = 11.sp
                            )
                        }
                    }
                    IconButton(
                        onClick = { 
                            clipboardManager.setText(AnnotatedString(displayAddr))
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            text = "\uE14D", // content_copy
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = MaterialDesignIcons
                        )
                    }
                }
                
                Spacer(Modifier.height(15.dp))
                
                Text("Balance", color = ColorTextDim, fontSize = 12.sp)
                Text(
                    text = "${SwapViewModel.formatErg(uiState.walletErgBalance)} ERG",
                    color = ColorAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                
                if (uiState.isLoadingWallet) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = ColorAccent,
                        trackColor = ColorInputBg
                    )
                }
            }
        }

        // Tabs — show 3 tabs for mnemonic wallets, 2 for ErgoPay
        val tabLabels = if (isMnemonicWallet) {
            listOf("Tokens", "Transactions", "Addresses")
        } else {
            listOf("Tokens", "Transactions")
        }
        var selectedTab by remember { mutableStateOf(0) }
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = ColorAccent,
            divider = {},
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = ColorAccent
                    )
                }
            },
            modifier = Modifier.padding(bottom = 15.dp)
        ) {
            tabLabels.forEachIndexed { index, label ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index }) {
                    Text(label, modifier = Modifier.padding(vertical = 10.dp), fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        if (selectedTab == 0) {
            if (uiState.walletTokens.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (uiState.isLoadingWallet) "Fetching balances..." else "No tokens found",
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
        } else if (selectedTab == 1) {
            val networkTrades = uiState.networkTrades
            
            if (networkTrades.isEmpty() && !uiState.isLoadingHistory) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No transaction history", color = ColorTextDim)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(networkTrades) { trade ->
                        NetworkTradeHistoryItemView(trade, viewModel)
                    }
                    if (uiState.isLoadingHistory) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(24.dp))
                            }
                        }
                    } else if (networkTrades.isNotEmpty()) {
                        item {
                            LaunchedEffect(Unit) {
                                viewModel.fetchTransactionHistory(loadMore = true)
                            }
                        }
                    }
                }
            }
        } else if (selectedTab == 2) {
            // ─── ADDRESSES TAB ─────────────────────────────────────────────
            AddressManagementTab(
                uiState = uiState,
                viewModel = viewModel
            )
        }
        }
    }
}

@Composable
fun NetworkTradeHistoryItemView(trade: NetworkTransaction, viewModel: SwapViewModel) {
    var showDetails by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable { showDetails = true },
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(15.dp)
        ) {
            // Left Column: Date, Financials, TxID
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .align(Alignment.TopStart)
            ) {
                // DATE
                val sdf = java.text.SimpleDateFormat("d MMMM yyyy HH:mm", java.util.Locale.US)
                val dateString = sdf.format(java.util.Date(trade.timestamp))
                Text(dateString, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)

                // Line gap before financial data
                Spacer(Modifier.height(15.dp))

                // ERG CHANGE
                if (trade.netErgChange != 0L) {
                    val symbol = if (trade.netErgChange > 0) "+" else "-"
                    val ergVal = Math.abs(trade.netErgChange.toDouble() / 1_000_000_000.0)
                    val color = if (trade.netErgChange > 0) Color.Green else ColorSent
                    Text("$symbol${SwapViewModel.formatErg(ergVal)} ERG", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // TOKEN CHANGES
                trade.netTokenChanges.forEach { (tokenId, netAmt) ->
                    val symbol = if (netAmt > 0) "+" else "-"
                    val name = viewModel.getTokenName(tokenId)
                    val formattedAmt = viewModel.formatBalance(tokenId, Math.abs(netAmt))
                    val color = if (netAmt > 0) Color.Green else ColorSent
                    Text("$symbol$formattedAmt $name", color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                if (trade.netErgChange == 0L && trade.netTokenChanges.isEmpty()) {
                    Text("Self transfer / No balance change", color = ColorTextDim, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                // TxID
                Text(
                    text = "TxID: ${trade.id.take(12)}...${trade.id.takeLast(12)}", 
                    color = ColorTextDim, 
                    fontSize = 10.sp, 
                    fontFamily = FontFamily.Monospace
                )
            }

            // Right Column - Top: Status, Label
            Column(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalAlignment = Alignment.End
            ) {
                // STATUS
                if (trade.isConfirmed) {
                    Text("CONFIRMED", color = Color.Green.copy(alpha=0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Text("UNCONFIRMED", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // PROTOCOL LABEL (under status)
                trade.label?.let { label ->
                    Text(
                        text = label,
                        color = ColorAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Right Column - Bottom: Fee
            if (trade.fee > 0L) {
                Text(
                    text = "fee: ${SwapViewModel.formatErg(trade.fee.toDouble() / 1_000_000_000.0)} ERG",
                    color = ColorTextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomEnd)
                )
            }
        }


    }
    
    if (showDetails) {
        val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
        
        AlertDialog(
            onDismissRequest = { showDetails = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = ColorCard,
            modifier = Modifier.fillMaxWidth(0.98f),
            title = {
                Text("Transaction Details", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    item {
                        Text("TxID:", color = ColorTextDim, fontSize = 12.sp)
                        Text(trade.id, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(15.dp))
                        
                        // External Links
                        Text("View on Explorer:", color = ColorAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Ergo Explorer", 
                                color = ColorAccent, 
                                fontSize = 11.sp, 
                                modifier = Modifier.clickable { 
                                    uriHandler.openUri("https://explorer.ergoplatform.com/en/transactions/${trade.id}")
                                }
                            )
                            Text(
                                "ErgExplorer", 
                                color = ColorAccent, 
                                fontSize = 11.sp, 
                                modifier = Modifier.clickable { 
                                    uriHandler.openUri("https://ergexplorer.com/transactions#${trade.id}")
                                }
                            )
                            Text(
                                "Sigmaspace.io", 
                                color = ColorAccent, 
                                fontSize = 11.sp, 
                                modifier = Modifier.clickable { 
                                    uriHandler.openUri("https://sigmaspace.io/en/transaction/${trade.id}")
                                }
                            )
                        }
                        
                        Spacer(Modifier.height(15.dp))
                    }
                    
                    item {
                        Text("Status: ${if (trade.isConfirmed) "Confirmed (${trade.numConfirmations} confs)" else "Unconfirmed" }", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    
                    item {
                        Text("From:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    val myWalletAddrs = viewModel.uiState.value.walletAddresses.toSet()
                    items(trade.inputs) { inp ->
                        CollapsibleBoxRow(inp, viewModel, myWalletAddrs)
                    }
                    
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("To:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    items(trade.outputs) { out ->
                        CollapsibleBoxRow(out, viewModel, myWalletAddrs)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetails = false }) {
                    Text("Close", color = ColorAccent)
                }
            }
        )
    }
}

@Composable
fun TokenBalanceItem(tokenId: String, amount: Long, viewModel: SwapViewModel) {
    val usdValue = viewModel.getTokenUsdValue(tokenId)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TokenImage(tokenId = tokenId, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    text = viewModel.getTokenName(tokenId),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                if (tokenId.length > 20) {
                    Text(text = "ID: ${tokenId.take(8)}...", color = ColorTextDim, fontSize = 10.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = viewModel.formatBalance(tokenId, amount),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (usdValue != null && usdValue > 0.01) {
                    Text(
                        text = "$${String.format("%.2f", usdValue)}",
                        color = ColorTextDim,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CollapsibleBoxRow(box: TxBox, viewModel: SwapViewModel, myAddresses: Set<String> = emptySet()) {
    var isExpanded by remember { mutableStateOf(false) }
    val threshold = 5
    val isMine = box.address in myAddresses

    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .then(
                if (isMine) Modifier
                    .background(Color.Green.copy(alpha = 0.06f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                else Modifier
            )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val addr = if (box.address.length > 20) box.address.take(10) + "..." + box.address.takeLast(10) else box.address
            Text(addr, color = if (isMine) Color.Green.copy(alpha = 0.7f) else ColorTextDim, fontSize = 10.sp)
            if (isMine) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "You",
                    color = Color.Green,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Green.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }
        Text("${SwapViewModel.formatErg(box.value.toDouble() / 1_000_000_000.0)} ERG", color = Color.White, fontSize = 12.sp)

        val assetsToShow = if (box.assets.size >= threshold && !isExpanded) box.assets.take(threshold - 1) else box.assets
        assetsToShow.forEach { map ->
            val tId = map["tokenId"] as? String ?: ""
            val amt = (map["amount"] as? Number)?.toLong() ?: 0L
            val tName = viewModel.getTokenName(tId)
            Text("${viewModel.formatBalance(tId, amt)} $tName", color = Color.White, fontSize = 12.sp)
        }

        if (box.assets.size >= threshold) {
            Text(
                text = if (isExpanded) "▲ Show less" else "▼ +${box.assets.size - (threshold - 1)} more tokens",
                color = ColorAccent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { isExpanded = !isExpanded }
                    .padding(top = 2.dp)
            )
        }
    }
}

/**
 * Address management tab for mnemonic wallets.
 * Shows all derived addresses with checkboxes for selection and radio buttons for change address.
 */
@Composable
fun AddressManagementTab(
    uiState: SwapState,
    viewModel: SwapViewModel
) {
    val clipboardManager = LocalClipboardManager.current
    
    // Delete confirmation state
    var addressToDelete by remember { mutableStateOf<String?>(null) }
    var deleteBalance by remember { mutableStateOf(0.0) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            text = "Derivation Addresses (EIP-3)",
            color = ColorAccent,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Select which addresses to include in balances and transactions. The selected address is where leftover funds are sent.",
            color = ColorTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 15.dp)
        )
        
        // Column headers
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Use", color = ColorTextDim, fontSize = 10.sp, modifier = Modifier.width(44.dp), fontWeight = FontWeight.Bold)
            Text("Selected", color = ColorTextDim, fontSize = 10.sp, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Bold)
            Text("Address", color = ColorTextDim, fontSize = 10.sp, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Text("Balance", color = ColorTextDim, fontSize = 10.sp, modifier = Modifier.width(90.dp), fontWeight = FontWeight.Bold)
        }
        
        uiState.walletAddresses.forEachIndexed { index, address ->
            val isSelected = uiState.selectedAddresses.contains(address)
            val isChangeAddr = uiState.changeAddress == address
            
            // Calculate per-address ERG balance from cached boxes
            val addrBalance = uiState.addressBoxes[address]?.sumOf { box ->
                (box["value"] as? Number)?.toLong() ?: 0L
            } ?: 0L
            val ergBalance = addrBalance.toDouble() / 1_000_000_000.0
            
            val borderColor = when {
                isChangeAddr && isSelected -> ColorAccent
                isSelected -> Color.White.copy(alpha = 0.2f)
                else -> Color.Transparent
            }
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp)
                    .then(
                        if (isSelected) Modifier.border(1.dp, borderColor, RoundedCornerShape(10.dp)) else Modifier
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) ColorInputBg else ColorInputBg.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Checkbox for selecting this address
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { viewModel.toggleAddress(address) },
                        modifier = Modifier.size(36.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = ColorAccent,
                            uncheckedColor = ColorTextDim,
                            checkmarkColor = Color.White
                        )
                    )
                    
                    // Radio button for change address (only clickable if address is selected)
                    RadioButton(
                        selected = isChangeAddr,
                        onClick = { if (isSelected) viewModel.setChangeAddress(address) },
                        modifier = Modifier.size(36.dp),
                        enabled = isSelected,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = ColorAccent,
                            unselectedColor = ColorTextDim,
                            disabledSelectedColor = ColorTextDim,
                            disabledUnselectedColor = ColorTextDim.copy(alpha = 0.3f)
                        )
                    )
                    
                    // Address info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(address))
                            }
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${address.take(6)}...${address.takeLast(6)}",
                                color = if (isSelected) Color.White else ColorTextDim,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (index == 0) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "PRIMARY",
                                    color = ColorAccent,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "m/44'/429'/0'/0/$index",
                            color = ColorTextDim.copy(alpha = 0.6f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    
                    // ERG Balance
                    Text(
                        text = if (ergBalance > 0) SwapViewModel.formatErg(ergBalance) else "0",
                        color = if (ergBalance > 0 && isSelected) Color.White else ColorTextDim,
                        fontSize = 12.sp,
                        fontWeight = if (ergBalance > 0) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.width(70.dp)
                    )
                    
                    // Delete button (not for primary address)
                    if (index > 0) {
                        IconButton(
                            onClick = {
                                deleteBalance = ergBalance
                                addressToDelete = address
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text(
                                text = "×",
                                color = ColorTextDim.copy(alpha = 0.5f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Spacer(Modifier.width(28.dp))
                    }
                }
            }
        }
        
        // Summary + add button
        Spacer(Modifier.height(15.dp))
        val totalSelected = uiState.selectedAddresses.size
        val totalAddresses = uiState.walletAddresses.size
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$totalSelected of $totalAddresses addresses selected",
                color = ColorTextDim,
                fontSize = 12.sp
            )
            
            Button(
                onClick = { viewModel.deriveMoreAddresses(1) },
                enabled = !uiState.isScanningAddresses,
                modifier = Modifier.height(32.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                if (uiState.isScanningAddresses) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "+ Add Address",
                        color = ColorBg,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
    
    // Delete confirmation dialog
    addressToDelete?.let { addr ->
        val truncAddr = if (addr.length > 20) "${addr.take(8)}...${addr.takeLast(8)}" else addr
        AlertDialog(
            onDismissRequest = { addressToDelete = null },
            containerColor = ColorCard,
            title = {
                Text("Remove Address?", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = truncAddr,
                        color = ColorTextDim,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    if (deleteBalance > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "⚠ This address holds ${SwapViewModel.formatErg(deleteBalance)} ERG. Make sure to move funds before removing.",
                            color = Color(0xFFFF9800),
                            fontSize = 13.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "You can always add it back later.",
                        color = ColorTextDim,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeAddress(addr)
                        addressToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Remove", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { addressToDelete = null }) {
                    Text("Cancel", color = ColorTextDim)
                }
            }
        )
    }
}
