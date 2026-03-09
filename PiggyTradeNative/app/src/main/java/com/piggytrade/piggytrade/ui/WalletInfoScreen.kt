package com.piggytrade.piggytrade.ui

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

@Composable
fun WalletInfoScreen(
    walletName: String,
    viewModel: SwapViewModel,
    onBack: () -> Unit,
    onNavigateToAddWallet: () -> Unit
) {
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
                    IconButton(onClick = { showDeleteConfirm1 = true }, modifier = Modifier.size(24.dp)) {
                        Text(
                            text = "\uE872",
                            color = Color.Red.copy(alpha = 0.7f),
                            fontSize = 18.sp,
                            fontFamily = MaterialDesignIcons
                        )
                    }
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
                            color = ColorAccent,
                            fontSize = 18.sp,
                            fontFamily = MaterialDesignIcons
                        )
                    }
                }
                
                Spacer(Modifier.height(15.dp))
                
                Text("Balance", color = ColorTextDim, fontSize = 12.sp)
                Text(
                    text = String.format("%.4f ERG", uiState.walletErgBalance),
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
                TabRowDefaults.Indicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = ColorAccent
                )
            },
            modifier = Modifier.padding(bottom = 15.dp)
        ) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                Text("Tokens", modifier = Modifier.padding(vertical = 10.dp), fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
            }
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                Text("History", modifier = Modifier.padding(vertical = 10.dp), fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
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
        } else {
            val walletTrades = remember(uiState.trades, uiState.selectedAddress) {
                uiState.trades.filter { it.address == uiState.selectedAddress }.reversed()
            }
            
            if (walletTrades.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No transaction history", color = ColorTextDim)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(walletTrades) { trade ->
                        TradeHistoryItemView(trade)
                    }
                }
            }
        }
    }
}

@Composable
fun TradeHistoryItemView(trade: TradeHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(trade.timestamp, color = ColorTextDim, fontSize = 10.sp)
                if (trade.isSimulation) {
                    Text("SIMULATION", color = Color.Yellow, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bought: ", color = ColorTextDim, fontSize = 12.sp)
                Text(trade.buy, color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Paid:   ", color = ColorTextDim, fontSize = 12.sp)
                Text(trade.pay, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("TxID: ${trade.txId.take(12)}...${trade.txId.takeLast(12)}", color = ColorTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
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
