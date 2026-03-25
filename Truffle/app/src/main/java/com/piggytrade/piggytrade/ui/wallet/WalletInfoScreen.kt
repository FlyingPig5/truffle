package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.market.MarketViewModel
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import java.text.SimpleDateFormat
import java.util.*

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
    marketViewModel: MarketViewModel,
    onBack: () -> Unit,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToSend: () -> Unit = {}
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

        // Send button (only if wallet functionality enabled in settings)
        if (uiState.walletFunctionalityEnabled && uiState.selectedWallet.isNotEmpty()) {
            androidx.compose.material3.Button(
                onClick = onNavigateToSend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .height(48.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ColorAccent
                )
            ) {
                Text(
                    text = "↑",
                    color = ColorBg,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Send",
                    color = ColorBg,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        WalletInfoContent(
            walletName = walletName,
            viewModel = viewModel,
            marketViewModel = marketViewModel,
            onDeleteComplete = onBack,
            showTitle = false,
            onNavigateToAddWallet = onNavigateToAddWallet,
            onNavigateToSend = onNavigateToSend
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletInfoContent(
    walletName: String,
    viewModel: SwapViewModel,
    marketViewModel: MarketViewModel,
    onDeleteComplete: (() -> Unit)? = null,
    showTitle: Boolean = true,
    onNavigateToAddWallet: (() -> Unit)? = null,
    onNavigateToSend: (() -> Unit)? = null,
    onNavigateToQrScanner: (() -> Unit)? = null,
    onAddressClick: ((String) -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val marketState by marketViewModel.uiState.collectAsState()
    var showDeleteConfirm1 by remember { mutableStateOf(false) }
    var showDeleteConfirm2 by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        viewModel.fetchWalletBalances()
        viewModel.fetchTransactionHistory()
        marketViewModel.fetchTokenUsdValues(uiState.walletTokens, uiState.whitelistedPools, uiState.discoveredPools, uiState.includeUnconfirmed)
    }
    
    val tokenUsdValues = marketState.tokenUsdValues
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Balance", color = ColorTextDim, fontSize = 12.sp)
                        Text(
                            text = "${SwapViewModel.formatErg(uiState.walletErgBalance)} ERG",
                            color = ColorAccent,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (onNavigateToQrScanner != null && walletName.isNotEmpty() && walletName != "Select Wallet") {
                        IconButton(
                            onClick = onNavigateToQrScanner,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = ColorAccent,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Total portfolio USD value
                val ergPriceUsd = marketState.ergPriceUsd
                val ergUsdValue = if (ergPriceUsd != null) uiState.walletErgBalance * ergPriceUsd else null
                val tokenUsdTotal = marketState.tokenUsdValues.values.sum()
                val totalPortfolio = (ergUsdValue ?: 0.0) + tokenUsdTotal
                if (ergPriceUsd != null) {
                    Text(
                        text = "Portfolio: \$${String.format("%.2f", totalPortfolio)}",
                        color = ColorTextDim,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                
                if (uiState.isLoadingWallet) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        color = ColorAccent,
                        trackColor = ColorInputBg
                    )
                }

                // Send button inside balance card
                if (uiState.walletFunctionalityEnabled && onNavigateToSend != null && walletName.isNotEmpty() && walletName != "Select Wallet") {
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onNavigateToSend,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                    ) {
                        Text("↑", color = ColorBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Send", color = ColorBg, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
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
                        TokenBalanceItem(tokenId, amount, viewModel, marketViewModel)
                    }
                }
            }
        } else if (selectedTab == 1) {
            val networkTrades = uiState.networkTrades

            // DeFi filter mode
            var txFilter by remember { mutableStateOf("all") } // "all", "defi"
            val displayTrades = remember(networkTrades, txFilter) {
                if (txFilter == "defi") networkTrades.filter { it.label != null }
                else networkTrades
            }

            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Filter chips row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    listOf("all" to "All", "defi" to "DeFi").forEach { (key, label) ->
                        val isActive = txFilter == key
                        val bgColor by animateColorAsState(
                            targetValue = if (isActive) ColorAccent else ColorInputBg,
                            animationSpec = tween(200)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = bgColor,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { txFilter = key }
                            )
                        ) {
                            Text(
                                text = label,
                                color = if (isActive) ColorBg else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (displayTrades.isEmpty() && !uiState.isLoadingHistory) {
                    Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (txFilter == "defi") "No DeFi activity yet" else "No transaction history",
                            color = ColorTextDim
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    ) {
                        if (txFilter == "defi") {
                            items(displayTrades.take(50)) { trade ->
                                DeFiActivityRow(tx = trade, viewModel = viewModel, onAddressClick = onAddressClick)
                            }
                        } else {
                            items(
                                items = displayTrades,
                                key = { it.id }
                            ) { trade ->
                                NetworkTradeHistoryItemView(trade, viewModel, onAddressClick = onAddressClick)
                            }
                        }
                        if (uiState.isLoadingHistory) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(24.dp))
                                }
                            }
                        } else if (txFilter == "all" && networkTrades.isNotEmpty()) {
                            item {
                                LaunchedEffect(Unit) {
                                    viewModel.fetchTransactionHistory(loadMore = true)
                                }
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
fun NetworkTradeHistoryItemView(trade: NetworkTransaction, viewModel: SwapViewModel, onAddressClick: ((String) -> Unit)? = null) {
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
                        CollapsibleBoxRow(inp, viewModel, myWalletAddrs, onAddressClick = onAddressClick)
                    }
                    
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("To:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    items(trade.outputs) { out ->
                        CollapsibleBoxRow(out, viewModel, myWalletAddrs, onAddressClick = onAddressClick)
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
fun TokenBalanceItem(tokenId: String, amount: Long, viewModel: SwapViewModel, marketViewModel: MarketViewModel? = null) {
    val usdValue = marketViewModel?.getTokenUsdValue(tokenId)
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
fun CollapsibleBoxRow(box: TxBox, viewModel: SwapViewModel, myAddresses: Set<String> = emptySet(), onAddressClick: ((String) -> Unit)? = null) {
    var isExpanded by remember { mutableStateOf(false) }
    val threshold = 5
    val isMine = box.address in myAddresses

    // Explore popup state
    var showExplorePopup by remember { mutableStateOf(false) }

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
            Text(
                addr,
                color = if (onAddressClick != null && !isMine) ColorAccent else if (isMine) Color.Green.copy(alpha = 0.7f) else ColorTextDim,
                fontSize = 10.sp,
                modifier = if (onAddressClick != null) Modifier.clickable { showExplorePopup = true } else Modifier
            )
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

        // Explore this address popup
        if (showExplorePopup && onAddressClick != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExplorePopup = false },
                containerColor = ColorCard,
                title = { Text("Explore Wallet", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("View balance & transactions for:", color = ColorTextDim, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        val truncAddr = if (box.address.length > 20) "${box.address.take(10)}...${box.address.takeLast(8)}" else box.address
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
                        showExplorePopup = false
                        onAddressClick(box.address)
                    }) {
                        Text("Explore", color = ColorAccent, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showExplorePopup = false }) {
                        Text("Cancel", color = ColorTextDim)
                    }
                }
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

// ─── DeFi Activity Helpers ─────────────────────────────────────────────

/** Material icon for a protocol transaction label */
private fun getTxIcon(label: String): ImageVector {
    val l = label.lowercase()
    return when {
        l.contains("freemint") -> Icons.Rounded.AccountBalance
        l.contains("arbmint") -> Icons.Rounded.AccountBalance
        l.contains("dex") || l.contains("lp swap") -> Icons.Rounded.SwapHoriz
        l.contains("duckpools") -> Icons.Rounded.Waves
        l.contains("buyback") -> Icons.Rounded.CurrencyExchange
        l.contains("bank") -> Icons.Rounded.AccountBalance
        else -> Icons.Rounded.Receipt
    }
}

/** Tag color for a protocol transaction */
@Composable
private fun getTxTagColor(label: String): Color {
    val l = label.lowercase()
    return when {
        l.contains("freemint") -> ColorAccent
        l.contains("arbmint") -> Color(0xFFFF9800)
        l.contains("bank") -> ColorBlue
        l.contains("buyback") -> Color(0xFFFF9800)
        l.contains("use lp") -> Color(0xFF29B6F6)
        l.contains("dexygold lp") -> Color(0xFFFFB300)
        l.contains("lp") -> Color(0xFF29B6F6)
        l.contains("dex swap") || l.contains("dex t2t") -> Color(0xFF26C6DA)
        l.contains("dex") -> Color(0xFF26C6DA)
        else -> ColorTextDim
    }
}

/** Describe what was sent and received in a transaction */
private fun describeSwap(
    netErgChange: Long,
    netTokenChanges: Map<String, Long>,
    viewModel: SwapViewModel
): Pair<String, String>? {
    val spent = mutableListOf<String>()
    val gained = mutableListOf<String>()
    if (netErgChange < 0) spent.add("${SwapViewModel.formatErg(Math.abs(netErgChange).toDouble() / 1e9)} ERG")
    else if (netErgChange > 0) gained.add("${SwapViewModel.formatErg(netErgChange.toDouble() / 1e9)} ERG")
    for ((tid, amt) in netTokenChanges) {
        val name = viewModel.getTokenName(tid)
        val formatted = viewModel.formatBalance(tid, Math.abs(amt))
        if (amt < 0) spent.add("$formatted $name")
        else gained.add("$formatted $name")
    }
    if (spent.isEmpty() && gained.isEmpty()) return null
    return (spent.joinToString(" + ").ifEmpty { "—" }) to (gained.joinToString(" + ").ifEmpty { "—" })
}

private fun defiTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val mins = diffMs / 60000
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    if (days < 7) return "${days}d ago"
    val sdf = SimpleDateFormat("MMM d", Locale.US)
    return sdf.format(Date(timestamp))
}

private fun defiDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}

@Composable
fun DeFiActivityRow(tx: NetworkTransaction, viewModel: SwapViewModel, onAddressClick: ((String) -> Unit)? = null) {
    val label = tx.label ?: "Transaction"
    val tagColor = getTxTagColor(label)
    val icon = getTxIcon(label)
    val swap = describeSwap(tx.netErgChange, tx.netTokenChanges, viewModel)
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
            .clickable { showDetails = true },
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: icon + protocol label
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(58.dp).padding(horizontal = 4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = label,
                    color = tagColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 10.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            // Right: details
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: confirmed status (left) + time/date (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (tx.isConfirmed) "✓ Confirmed" else "⏳ Pending",
                        color = if (tx.isConfirmed) Color.White.copy(alpha = 0.85f) else Color.Yellow,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${defiTimeAgo(tx.timestamp)} · ${defiDate(tx.timestamp)}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Sent ➜ Received
                if (swap != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            swap.first, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Text(
                            " ➜ ", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            swap.second, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                } else {
                    Text("Transaction", color = ColorTextDim, fontSize = 12.sp)
                }

                // TxID
                Text(
                    text = "TxID: ${tx.id.take(8)}...${tx.id.takeLast(8)}",
                    color = ColorTextDim,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }

    // Transaction details dialog (same as NetworkTradeHistoryItemView)
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
                        Text(tx.id, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
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
                                    uriHandler.openUri("https://explorer.ergoplatform.com/en/transactions/${tx.id}")
                                }
                            )
                            Text(
                                "ErgExplorer",
                                color = ColorAccent,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://ergexplorer.com/transactions#${tx.id}")
                                }
                            )
                            Text(
                                "Sigmaspace.io",
                                color = ColorAccent,
                                fontSize = 11.sp,
                                modifier = Modifier.clickable {
                                    uriHandler.openUri("https://sigmaspace.io/en/transaction/${tx.id}")
                                }
                            )
                        }

                        Spacer(Modifier.height(15.dp))
                    }

                    item {
                        Text("Status: ${if (tx.isConfirmed) "Confirmed (${tx.numConfirmations} confs)" else "Unconfirmed" }", color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(10.dp))
                    }

                    item {
                        Text("From:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    val myWalletAddrs = viewModel.uiState.value.walletAddresses.toSet()
                    items(tx.inputs) { inp ->
                        CollapsibleBoxRow(inp, viewModel, myWalletAddrs, onAddressClick = onAddressClick)
                    }

                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("To:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    items(tx.outputs) { out ->
                        CollapsibleBoxRow(out, viewModel, myWalletAddrs, onAddressClick = onAddressClick)
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
