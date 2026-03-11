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
    }
    
    val sortedTokens = remember(uiState.walletTokens) {
        uiState.walletTokens.toList().sortedWith { a, b ->
            val tokenIdA = a.first
            val tokenIdB = b.first
            val nameA = viewModel.getTokenName(tokenIdA)
            val nameB = viewModel.getTokenName(tokenIdB)
            
            fun getAssetPriority(name: String, id: String): Int {
                if (name.uppercase() == "SIGUSD") return 0
                if (name.uppercase() == "USE") return 1
                if (name.uppercase() == "DEXYGOLD") return 2
                val isNamed = !name.startsWith(id.take(5))
                if (isNamed) return 10
                return 100
            }
            
            val prioA = getAssetPriority(nameA, tokenIdA)
            val prioB = getAssetPriority(nameB, tokenIdB)
            
            if (prioA != prioB) return@sortedWith prioA.compareTo(prioB)
            
            val hasLogoA = viewModel.hasLogo(tokenIdA)
            val hasLogoB = viewModel.hasLogo(tokenIdB)
            
            if (hasLogoA != hasLogoB) return@sortedWith if (hasLogoA) -1 else 1
            
            nameA.compareTo(nameB)
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
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
            colors = CardDefaults.cardColors(containerColor = ColorCard),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                val clipboardManager = LocalClipboardManager.current
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayAddress = if (uiState.selectedAddress.length > 20) {
                        "Address: ${uiState.selectedAddress.take(8)}...${uiState.selectedAddress.takeLast(8)}"
                    } else {
                        "Address: ${uiState.selectedAddress}"
                    }
                    Text(
                        text = displayAddress,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { 
                            clipboardManager.setText(AnnotatedString(uiState.selectedAddress))
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

        // Tabs
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
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Tokens", modifier = Modifier.padding(vertical = 10.dp), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("Transaction History", modifier = Modifier.padding(vertical = 10.dp), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
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
                var isTokensExpanded by remember { mutableStateOf(false) }
                val tokensToDisplay = if (isTokensExpanded) sortedTokens else sortedTokens.take(5)

                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(tokensToDisplay) { (tokenId, amount) ->
                        TokenBalanceItem(tokenId, amount, viewModel)
                    }

                    if (sortedTokens.size > 5) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTokensExpanded = !isTokensExpanded }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isTokensExpanded) "Show Less" else "+${sortedTokens.size - 5}",
                                    color = ColorAccent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        } else {
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
                    items(trade.inputs) { inp ->
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            val addr = if (inp.address.length > 20) inp.address.take(10) + "..." + inp.address.takeLast(10) else inp.address
                            Text(addr, color = ColorTextDim, fontSize = 10.sp)
                            Text("${SwapViewModel.formatErg(inp.value.toDouble() / 1_000_000_000.0)} ERG", color = Color.White, fontSize = 12.sp)
                            inp.assets.forEach { map ->
                                val tId = map["tokenId"] as? String ?: ""
                                val amt = (map["amount"] as? Number)?.toLong() ?: 0L
                                val tName = viewModel.getTokenName(tId)
                                Text("${viewModel.formatBalance(tId, amt)} $tName", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                    
                    item {
                        Spacer(Modifier.height(10.dp))
                        Text("To:", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    items(trade.outputs) { out ->
                        Column(modifier = Modifier.padding(vertical = 5.dp)) {
                            val addr = if (out.address.length > 20) out.address.take(10) + "..." + out.address.takeLast(10) else out.address
                            Text(addr, color = ColorTextDim, fontSize = 10.sp)
                            Text("${SwapViewModel.formatErg(out.value.toDouble() / 1_000_000_000.0)} ERG", color = Color.White, fontSize = 12.sp)
                            out.assets.forEach { map ->
                                val tId = map["tokenId"] as? String ?: ""
                                val amt = (map["amount"] as? Number)?.toLong() ?: 0L
                                val tName = viewModel.getTokenName(tId)
                                Text("${viewModel.formatBalance(tId, amt)} $tName", color = Color.White, fontSize = 12.sp)
                            }
                        }
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
            Text(
                text = viewModel.formatBalance(tokenId, amount),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
