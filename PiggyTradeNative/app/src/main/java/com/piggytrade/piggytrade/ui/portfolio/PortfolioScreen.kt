package com.piggytrade.piggytrade.ui.portfolio

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PortfolioScreen(viewModel: SwapViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    // Fetch data that isn't already loaded — each call has its own guard so no double-fetching
    LaunchedEffect(Unit) {
        viewModel.fetchTokenUsdValues()
        if (uiState.walletErgBalance == 0.0 && uiState.walletTokens.isEmpty()) viewModel.fetchWalletBalances()
        if (uiState.networkTrades.isEmpty() && !uiState.isLoadingHistory) viewModel.fetchTransactionHistory()
    }

    // Trigger token USD calculation once tokens + pools are available, or when wallet changes
    // (fetchTokenUsdValues fetches oracle price internally, no need to wait for ergPriceUsd)
    val hasTokens = uiState.walletTokens.isNotEmpty()
    val hasPools = uiState.whitelistedPools.isNotEmpty() || uiState.discoveredPools.isNotEmpty()
    LaunchedEffect(hasTokens, hasPools, uiState.selectedWallet) {
        if (hasTokens && hasPools) {
            viewModel.fetchTokenUsdValues()
        }
    }

    var subTab by remember { mutableStateOf("overview") } // "overview", "tokens", "activity"

    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-tab bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            listOf("overview" to "Overview", "tokens" to "Tokens", "activity" to "Activity").forEach { (key, label) ->
                val isActive = subTab == key
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
                        onClick = { subTab = key }
                    )
                ) {
                    Text(
                        text = label,
                        color = if (isActive) ColorBg else Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }

        when (subTab) {
            "overview" -> OverviewTab(uiState, viewModel)
            "tokens" -> TokensTab(uiState, viewModel)
            "activity" -> ActivityTab(uiState, viewModel)
        }
    }
}

@Composable
private fun OverviewTab(uiState: com.piggytrade.piggytrade.ui.swap.SwapState, viewModel: SwapViewModel) {
    val ergPriceUsd = uiState.ergPriceUsd
    val ergBalance = uiState.walletErgBalance
    val ergUsdValue = if (ergPriceUsd != null) ergBalance * ergPriceUsd else null

    // Total token USD value from pre-computed batch values
    val tokenUsdValues = uiState.tokenUsdValues
    val tokenUsdTotal = tokenUsdValues.values.sum()
    val totalPortfolio = (ergUsdValue ?: 0.0) + tokenUsdTotal

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Total Portfolio Value
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ColorCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Total Portfolio", color = ColorTextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (ergPriceUsd != null) "$${String.format("%.2f", totalPortfolio)}" else "Loading...",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // ERG balance
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TokenImage(tokenId = "ERG", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${String.format("%.4f", ergBalance)} ERG",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        if (ergUsdValue != null) {
                            Text(
                                text = "  ($${String.format("%.2f", ergUsdValue)})",
                                color = ColorTextDim,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (tokenUsdTotal > 0) {
                        Text(
                            text = "Tokens: $${String.format("%.2f", tokenUsdTotal)}",
                            color = ColorTextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }



        // Top tokens (first 5)
        item {
            Text("Top Tokens", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        val sortedTokens = uiState.walletTokens.toList().sortedByDescending { (tid, _) ->
            tokenUsdValues[tid] ?: 0.0
        }.take(5)

        items(sortedTokens) { (tokenId, amount) ->
            PortfolioTokenRow(tokenId, amount, viewModel)
        }
    }
}

@Composable
private fun TokensTab(uiState: com.piggytrade.piggytrade.ui.swap.SwapState, viewModel: SwapViewModel) {
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

    if (sortedTokens.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tokens found", color = ColorTextDim, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ERG row
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TokenImage(tokenId = "ERG", modifier = Modifier.size(32.dp))
                        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("ERG", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("Ergo", color = ColorTextDim, fontSize = 10.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = String.format("%.4f", uiState.walletErgBalance),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            uiState.ergPriceUsd?.let { price ->
                                Text(
                                    text = "$${String.format("%.2f", uiState.walletErgBalance * price)}",
                                    color = ColorTextDim,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            items(sortedTokens) { (tokenId, amount) ->
                PortfolioTokenRow(tokenId, amount, viewModel)
            }
        }
    }
}

@Composable
private fun ActivityTab(uiState: com.piggytrade.piggytrade.ui.swap.SwapState, viewModel: SwapViewModel) {
    var filter by remember { mutableStateOf("all") } // "all", "swap", "mint", "redeem"

    // Filter: only show transactions with known protocol labels (not null/unknown)
    // Actual labels from KNOWN_PROTOCOLS: "DEX", "DEX LP Swap", "SigmaUSD Bank",
    // "USE Bank", "USE Freemint", "USE Arbmint", "DexyGold Bank", "DexyGold Freemint",
    // "DexyGold Arbmint", "USE Buyback", "DexyGold Buyback", "Duckpools"
    val protocolTxs = remember(uiState.networkTrades) {
        uiState.networkTrades.filter { tx -> tx.label != null }
    }

    fun isSwap(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("dex") || l.contains("duckpools") || l.contains("lp swap")
    }
    fun isMint(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("freemint") || l.contains("arbmint") ||
               (l.contains("bank") && !l.contains("buyback"))
    }
    fun isRedeem(label: String): Boolean {
        val l = label.lowercase()
        return l.contains("buyback")
    }

    val filteredTxs = remember(protocolTxs, filter) {
        if (filter == "all") protocolTxs
        else protocolTxs.filter { tx ->
            val l = tx.label ?: ""
            when (filter) {
                "swap" -> isSwap(l)
                "mint" -> isMint(l)
                "redeem" -> isRedeem(l)
                else -> true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
        ) {
            listOf("all" to "All types", "swap" to "Swaps", "mint" to "Mints", "redeem" to "Redeems").forEach { (key, label) ->
                val isActive = filter == key
                val bgColor by animateColorAsState(
                    targetValue = if (isActive) ColorAccent else ColorInputBg,
                    animationSpec = tween(150)
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = bgColor,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { filter = key }
                    )
                ) {
                    Text(
                        text = label,
                        color = if (isActive) ColorBg else Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                    )
                }
            }
        }

        if (protocolTxs.isEmpty() && uiState.isLoadingHistory) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(32.dp))
            }
        } else if (filteredTxs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📋", fontSize = 32.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No activity yet", color = ColorTextDim, fontSize = 14.sp)
                    Text("Protocol transactions will appear here.", color = ColorTextDim.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTxs.take(50)) { tx ->
                    PortfolioActivityRow(tx = tx, viewModel = viewModel)
                }
            }
        }
    }
}

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
        l.contains("use lp") -> Color(0xFF29B6F6)       // light blue — USE LP
        l.contains("dexygold lp") -> Color(0xFFFFB300)  // amber — DexyGold LP
        l.contains("lp") -> Color(0xFF29B6F6)           // light blue fallback
        l.contains("dex swap") || l.contains("dex t2t") -> Color(0xFF26C6DA) // cyan — Spectrum
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

/** Time ago string */
private fun timeAgo(timestamp: Long): String {
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

private fun portfolioDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}

@Composable
private fun PortfolioActivityRow(tx: com.piggytrade.piggytrade.ui.swap.NetworkTransaction, viewModel: SwapViewModel) {
    val label = tx.label ?: "Transaction"
    val tagColor = getTxTagColor(label)
    val icon = getTxIcon(label)
    val swap = describeSwap(tx.netErgChange, tx.netTokenChanges, viewModel)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: icon + protocol label — vertically centered (matches ecosystem)
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
                        "${timeAgo(tx.timestamp)} · ${portfolioDate(tx.timestamp)}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Sent ➜ Received — white font
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
            }
        }
    }
}

@Composable
private fun PortfolioTokenRow(tokenId: String, amount: Long, viewModel: SwapViewModel) {
    val name = viewModel.getTokenName(tokenId)
    val formattedBalance = viewModel.formatBalance(tokenId, amount)
    val usdValue = viewModel.getTokenUsdValue(tokenId)

    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    text = name,
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
                    text = formattedBalance,
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


