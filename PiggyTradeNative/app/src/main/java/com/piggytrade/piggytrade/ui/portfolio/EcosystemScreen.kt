package com.piggytrade.piggytrade.ui.portfolio

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.swap.SwapViewModel
import com.piggytrade.piggytrade.ui.swap.EcosystemTx
import com.piggytrade.piggytrade.ui.home.MarketSyncButton
import com.piggytrade.piggytrade.ui.home.MarketSyncDialog

import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CurrencyExchange
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun EcosystemScreen(viewModel: SwapViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchEcosystemData()
    }

    var filter by remember { mutableStateOf("dex") }

    val selectedChartToken = uiState.selectedChartToken

    // Auto-switch to "trades" tab when a new token is selected from the chart
    LaunchedEffect(selectedChartToken) {
        filter = if (selectedChartToken != null) "trades" else "dex"
    }

    val filteredActivity = remember(uiState.ecosystemActivity, filter) {
        uiState.ecosystemActivity.filter { tx ->
            val l = tx.protocol.lowercase()
            when (filter) {
                "dex" -> l.contains("dex") || l.contains("lp swap")
                "stablecoin" -> l.contains("bank") || l.contains("freemint") || l.contains("arbmint")
                else -> true
            }
        }
    }

    // Pull-to-refresh state — separate from isLoadingEcosystem to avoid
    // pagination (fetchMore) triggering the pull indicator
    val pullRefreshState = rememberPullToRefreshState()
    var isManualRefreshing by remember { mutableStateOf(false) }

    // Trigger refresh when pull gesture completes
    if (pullRefreshState.isRefreshing && !isManualRefreshing) {
        isManualRefreshing = true
        LaunchedEffect(true) {
            viewModel.fetchEcosystemData(forceRefresh = true)
        }
    }

    // Stop the indicator only when a manual refresh finishes
    LaunchedEffect(uiState.isLoadingEcosystem) {
        if (!uiState.isLoadingEcosystem && isManualRefreshing) {
            isManualRefreshing = false
            pullRefreshState.endRefresh()
        }
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(pullRefreshState.nestedScrollConnection)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title + refresh icon
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ecosystem Activity", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (uiState.isLoadingEcosystem) {
                    CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        "↻",
                        color = ColorAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.fetchEcosystemData(forceRefresh = true) }
                        )
                    )
                }
            }

            // ─── Swipeable Price / TVL Pager ───────────────────────────
            val pagerState = rememberPagerState(initialPage = 0) { 2 }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth()
            ) { page ->
                when (page) {
                    0 -> ErgPriceChartCard(uiState, viewModel)
                    1 -> {
                        if (uiState.ecosystemTvl.isNotEmpty()) {
                            TvlSection(uiState.ecosystemTvl, uiState.ergPriceUsd)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Loading TVL...", color = ColorTextDim, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Dot indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { i ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                            .background(
                                if (pagerState.currentPage == i) ColorAccent else ColorTextDim.copy(alpha = 0.4f),
                                CircleShape
                            )
                    )
                }
            }

            // ── Market Sync Button (replaces inline sync bar) ──
            var showSyncDialog by remember { mutableStateOf(false) }
            val store = viewModel.oraclePriceStore

            MarketSyncButton(
                viewModel = viewModel,
                onClick = { showSyncDialog = true }
            )

            if (showSyncDialog) {
                MarketSyncDialog(
                    viewModel = viewModel,
                    isFirstSync = uiState.lastMarketSyncMs == 0L && uiState.marketSyncIncomplete,
                    onDismiss = { showSyncDialog = false }
                )
            }

            // Filter chips: DEX Swaps | Stablecoins | Market [| Latest Trades (token only)]
            val baseChips = listOf("dex" to "DEX Swaps", "stablecoin" to "Stablecoins", "market" to "Market")
            val chips = if (selectedChartToken != null)
                baseChips + ("trades" to "Latest Trades")
            else {
                // If filter was "trades" but no token selected, reset to "dex"
                if (filter == "trades") filter = "dex"
                baseChips
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                chips.forEach { (key, label) ->
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Activity feed OR Latest Trades depending on filter
            if (filter == "trades" && selectedChartToken != null) {
                // ── Latest pool trades for selected token ──────────────────
                val poolTrades = uiState.poolTrades
                val isLoadingTrades = uiState.isLoadingPoolTrades
                if (isLoadingTrades && poolTrades.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Loading $selectedChartToken/ERG trades…", color = ColorTextDim, fontSize = 12.sp)
                        }
                    }
                } else if (poolTrades.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No recent trades found", color = ColorTextDim, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isLoadingTrades) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Enriching addresses…", color = ColorTextDim, fontSize = 10.sp)
                                }
                            }
                        }
                        items(poolTrades) { trade ->
                            val isBuy = trade.isBuy
                            val color = if (isBuy) Color(0xFF4CAF50) else Color(0xFFEF5350)
                            val label = if (isBuy) "BUY" else "SELL"
                            val ergFmt = String.format("%.3f", trade.ergAmount)
                            val tokenFmt = when {
                                trade.tokenAmount >= 1000 -> String.format("%.1f", trade.tokenAmount)
                                trade.tokenAmount >= 1 -> String.format("%.3f", trade.tokenAmount)
                                else -> String.format("%.6f", trade.tokenAmount)
                            }
                            val uriHandler = LocalUriHandler.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ColorInputBg)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(color.copy(alpha = 0.18f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    if (isBuy) {
                                        Text("$ergFmt ERG → $tokenFmt $selectedChartToken", color = Color.White, fontSize = 12.sp)
                                    } else {
                                        Text("$tokenFmt $selectedChartToken → $ergFmt ERG", color = Color.White, fontSize = 12.sp)
                                    }
                                    if (trade.traderAddress.isNotEmpty()) {
                                        Text(
                                            trade.traderAddress.take(10) + "…",
                                            color = ColorTextDim, fontSize = 10.sp
                                        )
                                    }
                                }
                                val ageMs = System.currentTimeMillis() - trade.timestamp
                                val ageStr = when {
                                    trade.timestamp <= 0L -> ""
                                    ageMs < 60_000 -> "just now"
                                    ageMs < 3_600_000 -> "${ageMs / 60_000}m ago"
                                    ageMs < 86_400_000 -> "${ageMs / 3_600_000}h ago"
                                    else -> "${ageMs / 86_400_000}d ago"
                                }
                                if (ageStr.isNotEmpty()) {
                                    Text(ageStr, color = ColorTextDim, fontSize = 10.sp)
                                }
                                if (trade.txId.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Rounded.OpenInNew,
                                        contentDescription = "Explorer",
                                        tint = ColorBlue,
                                        modifier = Modifier.size(14.dp).clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { uriHandler.openUri("https://explorer.ergoplatform.com/en/transactions/${trade.txId}") }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (filter == "market") {
                // ── All Tokens Market View ──────────────────────────────────
                val marketData = uiState.tokenMarketData.ifEmpty { viewModel.oraclePriceStore.allTokenMarketData }
                var sortBy by remember { mutableStateOf("vol7d") } // vol24h, vol7d, change24h, change7d, price
                var sortAsc by remember { mutableStateOf(false) }

                val sorted = remember(marketData, sortBy, sortAsc) {
                    val comparator: Comparator<com.piggytrade.piggytrade.data.OraclePriceStore.TokenMarketData> = when (sortBy) {
                        "vol24h" -> compareBy { it.volume24hErg }
                        "vol7d" -> compareBy { it.volume7dErg }
                        "change24h" -> compareBy { it.priceChange24h }
                        "change7d" -> compareBy { it.priceChange7d }
                        "price" -> compareBy { it.currentPriceErg }
                        else -> compareBy { it.volume7dErg }
                    }
                    val list = marketData.sortedWith(comparator)
                    if (sortAsc) list else list.reversed()
                }

                fun toggleSort(key: String) {
                    if (sortBy == key) sortAsc = !sortAsc
                    else { sortBy = key; sortAsc = false }
                }

                if (marketData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val store = viewModel.oraclePriceStore
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            if (store.isSyncingAllTokens) {
                                Text("📊", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Syncing Market Data", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(8.dp))
                                // Token name being synced
                                val tokenLabel = store.allTokenSyncLabel
                                val idx = store.allTokenSyncIndex
                                val total = store.allTokenSyncTotal
                                if (tokenLabel.isNotEmpty()) {
                                    Text(
                                        "Syncing $tokenLabel ($idx of $total)",
                                        color = ColorAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Overall progress bar
                                if (total > 0) {
                                    LinearProgressIndicator(
                                        progress = { (idx.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                        color = ColorAccent,
                                        trackColor = ColorInputBg
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "${(idx.toFloat() / total.toFloat() * 100).toInt()}% complete",
                                        color = ColorTextDim, fontSize = 11.sp
                                    )
                                }
                                // Per-token sub-progress (boxes synced)
                                val subLabel = store.syncProgressLabel
                                if (subLabel.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(subLabel, color = ColorTextDim.copy(alpha = 0.7f), fontSize = 10.sp)
                                    val subProg = store.syncProgressPercent
                                    if (subProg >= 0f) {
                                        LinearProgressIndicator(
                                            progress = { subProg.coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 2.dp),
                                            color = ColorAccent.copy(alpha = 0.5f),
                                            trackColor = ColorInputBg
                                        )
                                    }
                                }
                            } else {
                                Text("📊", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (store.syncProgressLabel.isNotEmpty()) {
                                    // Oracle sync is running, market sync comes after
                                    Text("No market data yet", color = ColorTextDim, fontSize = 14.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Market data will sync after oracle price sync completes", color = ColorTextDim.copy(alpha = 0.6f), fontSize = 11.sp)
                                } else {
                                    Text("No market data yet", color = ColorTextDim, fontSize = 14.sp)
                                    Text("Data will sync automatically on startup", color = ColorTextDim.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Frozen sortable header row
                        stickyHeader {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ColorCard)
                                    .padding(bottom = 4.dp, top = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Token", color = ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(1f))
                                Text(
                                    "Price" + if (sortBy == "price") (if (sortAsc) " ↑" else " ↓") else "",
                                    color = if (sortBy == "price") ColorAccent else ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(0.9f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { toggleSort("price") }
                                )
                                Text(
                                    "24h" + if (sortBy == "change24h") (if (sortAsc) " ↑" else " ↓") else "",
                                    color = if (sortBy == "change24h") ColorAccent else ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(0.6f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { toggleSort("change24h") }
                                )
                                Text(
                                    "7d" + if (sortBy == "change7d") (if (sortAsc) " ↑" else " ↓") else "",
                                    color = if (sortBy == "change7d") ColorAccent else ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(0.5f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { toggleSort("change7d") }
                                )
                                Text(
                                    "V24h" + if (sortBy == "vol24h") (if (sortAsc) " ↑" else " ↓") else "",
                                    color = if (sortBy == "vol24h") ColorAccent else ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(0.7f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { toggleSort("vol24h") }
                                )
                                Text(
                                    "V7d" + if (sortBy == "vol7d") (if (sortAsc) " ↑" else " ↓") else "",
                                    color = if (sortBy == "vol7d") ColorAccent else ColorTextDim, fontSize = 9.sp,
                                    modifier = Modifier.weight(0.7f).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { toggleSort("vol7d") }
                                )
                            }
                        }
                        // Sync progress bar when data exists but still syncing
                        val store = viewModel.oraclePriceStore
                        if (store.isSyncingAllTokens) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(ColorInputBg.copy(alpha = 0.6f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(12.dp),
                                        color = ColorAccent, strokeWidth = 1.5.dp
                                    )
                                    val idx = store.allTokenSyncIndex
                                    val total = store.allTokenSyncTotal
                                    val label = store.allTokenSyncLabel
                                    Text(
                                        "Syncing $label ($idx/$total)" +
                                            (if (store.syncProgressLabel.isNotEmpty()) " — ${store.syncProgressLabel}" else ""),
                                        color = ColorAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold
                                    )
                                    if (total > 0) {
                                        LinearProgressIndicator(
                                            progress = { (idx.toFloat() / total.toFloat()).coerceIn(0f, 1f) },
                                            modifier = Modifier.weight(1f).height(3.dp),
                                            color = ColorAccent,
                                            trackColor = ColorInputBg
                                        )
                                    }
                                }
                            }
                        }
                        items(sorted) { token ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ColorInputBg)
                                    .clickable { viewModel.selectChartToken(token.name) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    token.name,
                                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f), maxLines = 1
                                )
                                val priceFmt = when {
                                    token.currentPriceErg >= 1.0 -> String.format("%.3f", token.currentPriceErg)
                                    token.currentPriceErg >= 0.001 -> String.format("%.5f", token.currentPriceErg)
                                    else -> String.format("%.7f", token.currentPriceErg)
                                }
                                Text(priceFmt, color = Color.White, fontSize = 10.sp,
                                    modifier = Modifier.weight(0.9f), maxLines = 1)
                                val c24Color = when {
                                    token.priceChange24h > 0 -> Color(0xFF4CAF50)
                                    token.priceChange24h < 0 -> Color(0xFFEF5350)
                                    else -> ColorTextDim
                                }
                                Text(
                                    "${if (token.priceChange24h >= 0) "+" else ""}${String.format("%.1f", token.priceChange24h)}%",
                                    color = c24Color, fontSize = 10.sp,
                                    modifier = Modifier.weight(0.6f), maxLines = 1
                                )
                                val c7Color = when {
                                    token.priceChange7d > 0 -> Color(0xFF4CAF50)
                                    token.priceChange7d < 0 -> Color(0xFFEF5350)
                                    else -> ColorTextDim
                                }
                                Text(
                                    "${if (token.priceChange7d >= 0) "+" else ""}${String.format("%.1f", token.priceChange7d)}%",
                                    color = c7Color, fontSize = 10.sp,
                                    modifier = Modifier.weight(0.5f), maxLines = 1
                                )
                                Text(
                                    String.format("%.0f", token.volume24hErg),
                                    color = ColorTextDim, fontSize = 10.sp,
                                    modifier = Modifier.weight(0.7f), maxLines = 1
                                )
                                Text(
                                    String.format("%.0f", token.volume7dErg),
                                    color = ColorTextDim, fontSize = 10.sp,
                                    modifier = Modifier.weight(0.7f), maxLines = 1
                                )
                            }
                        }
                    }
                }
            } else if (uiState.isLoadingEcosystem && uiState.ecosystemActivity.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Scanning protocols...", color = ColorTextDim, fontSize = 12.sp)
                    }
                }
            } else if (filteredActivity.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌐", fontSize = 32.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No ecosystem activity found", color = ColorTextDim, fontSize = 14.sp)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filteredActivity) { tx ->
                        EcosystemTxRow(tx = tx)
                    }
                    item {
                        // Only try to load more if we have enough items and are not already loading
                        if (!uiState.isLoadingEcosystem && filteredActivity.size >= 10) {
                            LaunchedEffect(filteredActivity.size) {
                                viewModel.fetchMoreEcosystemActivity()
                            }
                        } else if (uiState.isLoadingEcosystem && !isManualRefreshing) {
                            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

        // Pull-to-refresh indicator
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            containerColor = ColorCard,
            contentColor = ColorAccent
        )
    }
}

// ─── Price Chart Card ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ErgPriceChartCard(
    uiState: com.piggytrade.piggytrade.ui.swap.SwapState,
    viewModel: SwapViewModel
) {
    val context = LocalContext.current
    val range = uiState.chartRange
    val ranges = listOf("24H", "7D", "30D", "3M", "6M", "1Y", "3Y", "MAX")
    val selectedToken = uiState.selectedChartToken

    val useColor = Color(0xFF4CAF50)
    val sigOracleColor = Color(0xFF42A5F5)
    val sigDexColor = Color(0xFFFFCA28)
    val tokenColor = Color(0xFF4CAF50)

    val cardBounds = remember { mutableStateOf(android.graphics.Rect()) }

    // Dropdown state
    var dropdownExpanded by remember { mutableStateOf(false) }
    val tokensWithPools = remember { viewModel.getTokensWithPools() }
    var searchQuery by remember { mutableStateOf("") }

    // Chart favorites persistence
    val prefs = context.getSharedPreferences("chart_favorites", android.content.Context.MODE_PRIVATE)
    var chartFavorites by remember {
        mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
    }
    fun toggleFavorite(token: String) {
        val updated = if (chartFavorites.contains(token)) chartFavorites - token else chartFavorites + token
        chartFavorites = updated
        prefs.edit().putStringSet("favorites", updated).apply()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                cardBounds.value = android.graphics.Rect(
                    pos.x.toInt(), pos.y.toInt(),
                    (pos.x + coords.size.width).toInt(),
                    (pos.y + coords.size.height).toInt()
                )
            },
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: Token selector + price + range + share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Token selector dropdown — pill-shaped trigger
                Box {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = ColorInputBg,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                searchQuery = ""
                                dropdownExpanded = true
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (selectedToken != null) "$selectedToken/ERG" else "ERG/USD",
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("▾", color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.width(260.dp).heightIn(max = 420.dp)
                    ) {
                        // Sticky search field — not scrollable
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search tokens...", fontSize = 13.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .height(52.dp),
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White, fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ColorAccent,
                                unfocusedBorderColor = ColorInputBg,
                                cursorColor = ColorAccent
                            )
                        )

                        // ERG/USD always first
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("⭐ ", fontSize = 12.sp)
                                    Text("ERG/USD (Oracle)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            },
                            onClick = {
                                viewModel.selectChartToken(null)
                                dropdownExpanded = false
                            }
                        )
                        HorizontalDivider(color = ColorInputBg.copy(alpha = 0.5f))

                        // Build sorted list: favorites first, then rest
                        val query = searchQuery.lowercase()
                        val filtered = tokensWithPools.filter {
                            query.isEmpty() || it.lowercase().contains(query)
                        }
                        val favoriteTokens = filtered.filter { chartFavorites.contains(it) }.sorted()
                        val otherTokens = filtered.filter { !chartFavorites.contains(it) }.sorted()
                        val orderedList = favoriteTokens + otherTokens

                        orderedList.forEach { tokenName ->
                            val isFav = chartFavorites.contains(tokenName)
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "$tokenName/ERG",
                                            color = if (isFav) ColorAccent else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isFav) FontWeight.Bold else FontWeight.Normal
                                        )
                                        Text(
                                            if (isFav) "★" else "☆",
                                            color = if (isFav) ColorAccent else ColorTextDim,
                                            fontSize = 16.sp,
                                            modifier = Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { toggleFavorite(tokenName) }
                                            )
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.selectChartToken(tokenName)
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Current price
                if (selectedToken != null) {
                    val latestPrice = viewModel.oraclePriceStore.getTokenLatestPrice(selectedToken)
                    if (latestPrice != null) {
                        val ergPriceUsd = uiState.ergPriceUsd
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "${String.format("%.6f", latestPrice)} ERG",
                                color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            if (ergPriceUsd != null) {
                                val usePrice = latestPrice * ergPriceUsd
                                Text(
                                    "/ ${String.format("%.3f", usePrice)} \$USE",
                                    color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp
                                )
                            }
                        }
                    } else if (viewModel.oraclePriceStore.isTokenSyncing) {
                        CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    }
                } else {
                    val currentPrice = uiState.ergPriceUsd
                    if (currentPrice != null) {
                        Text(
                            "$${String.format("%.4f", currentPrice)}",
                            color = ColorAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Stats row: price change + volume (token pairs only) ────────
            if (selectedToken != null) {
                val history = uiState.tokenPriceHistory
                val priceChangePct = if (history.size >= 2) {
                    val first = history.first().second
                    val last = history.last().second
                    if (first > 0.0) ((last - first) / first) * 100.0 else null
                } else null

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Price change for selected period
                    if (priceChangePct != null) {
                        val isUp = priceChangePct >= 0
                        val changeColor = if (isUp) Color(0xFF4CAF50) else Color(0xFFEF5350)
                        val arrow = if (isUp) "▲" else "▼"
                        Text(
                            "$arrow${String.format("%.1f", Math.abs(priceChangePct))}% ($range)",
                            color = changeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold
                        )
                    }
                    // Volume
                    val v24 = uiState.poolVolume24h
                    val v7d = uiState.poolVolume7d
                    if (v24 > 0 || v7d > 0) {
                        Text(
                            "Vol 24h: ${String.format("%.1f", v24)} ERG · 7d: ${String.format("%.1f", v7d)} ERG",
                            color = ColorTextDim, fontSize = 10.sp
                        )
                    }
                }
            } else {
                // ERG/USD price change for selected period
                val history = uiState.ergPriceHistory
                if (history.size >= 2) {
                    val first = history.first().second
                    val last = history.last().second
                    val pct = if (first > 0.0) ((last - first) / first) * 100.0 else null
                    if (pct != null) {
                        val isUp = pct >= 0
                        val changeColor = if (isUp) Color(0xFF4CAF50) else Color(0xFFEF5350)
                        val arrow = if (isUp) "▲" else "▼"
                        Text(
                            "$arrow${String.format("%.1f", Math.abs(pct))}% ($range)",
                            color = changeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Range selector + share
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    ranges.forEach { r ->
                        val isActive = r == range
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isActive) ColorAccent else ColorInputBg,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { viewModel.setChartRange(r) }
                            )
                        ) {
                            Text(
                                r, color = if (isActive) ColorBg else Color.White,
                                fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(ColorBlue.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                        .clickable { shareChart(context, cardBounds.value) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share chart",
                        tint = ColorBlue,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Syncing message for tokens
            if (selectedToken != null && viewModel.oraclePriceStore.isTokenSyncing && uiState.tokenPriceHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Syncing $selectedToken price data...\nFirst sync fetches full history",
                            color = ColorTextDim, fontSize = 11.sp, textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (selectedToken != null) {
                // Token price chart (single line in ERG)
                TokenPriceChart(uiState.tokenPriceHistory, tokenColor, range, "ERG")
            } else {
                // ERG/USD multi-line oracle chart
                val useHistory = uiState.ergPriceHistory
                val sigOracleHistory = uiState.sigUsdOracleHistory
                val sigDexHistory = uiState.sigUsdDexHistory

                var showUse by remember { mutableStateOf(true) }
                var showSigOracle by remember { mutableStateOf(false) }
                var showSigDex by remember { mutableStateOf(false) }

                // Only include visible series for Y-axis range
                val visibleSeries = buildList {
                    if (showUse) add(useHistory)
                    if (showSigOracle) add(sigOracleHistory)
                    if (showSigDex) add(sigDexHistory)
                }
                val allPoints = visibleSeries.flatMap { it.map { p -> p.second } }

                if (allPoints.isNotEmpty()) {
                    val minP = allPoints.min()
                    val maxP = allPoints.max()
                    val rangeP = if (maxP - minP > 0) maxP - minP else 1.0

                    // Price range label
                    Text(
                        "$range range: $${String.format("%.3f", minP)} – $${String.format("%.3f", maxP)}",
                        color = ColorTextDim, fontSize = 9.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )

                    Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val w = size.width; val h = size.height
                            val chartTop = 4f; val chartHeight = h - 30f

                            // Grid lines
                            val gridPaint = android.graphics.Paint().apply {
                                color = 0x18FFFFFF
                                strokeWidth = 1f
                            }
                            for (i in 0..2) {
                                val gy = chartTop + chartHeight * (1f - i / 2f)
                                drawContext.canvas.nativeCanvas.drawLine(0f, gy, w, gy, gridPaint)
                            }

                            // Global time range driven explicitly by UI range, so 2-month-old tokens
                            // correctly show empty space on the left of a 1Y chart instead of stretching
                            val nowMs = System.currentTimeMillis()
                            val rangeMs = when (range) {
                                "24H" -> 24L * 60 * 60 * 1000
                                "7D" -> 7L * 24 * 60 * 60 * 1000
                                "30D" -> 30L * 24 * 60 * 60 * 1000
                                "3M" -> 90L * 24 * 60 * 60 * 1000
                                "6M" -> 180L * 24 * 60 * 60 * 1000
                                "1Y" -> 365L * 24 * 60 * 60 * 1000
                                "3Y" -> 3L * 365 * 24 * 60 * 60 * 1000
                                "MAX" -> {
                                    val allTs = visibleSeries
                                        .flatMap { it.map { p -> p.first } }
                                    if (allTs.isNotEmpty()) nowMs - allTs.min()
                                    else 365L * 24 * 60 * 60 * 1000
                                }
                                else -> 7L * 24 * 60 * 60 * 1000
                            }
                            val minTs = nowMs - rangeMs
                            val maxTs = nowMs
                            val tsRange = rangeMs.toFloat()

                            fun drawSeries(series: List<Pair<Long, Double>>, color: Color, strokeWidth: Float) {
                                if (series.size < 2) return
                                val path = Path()
                                series.forEachIndexed { i, point ->
                                    val x = ((point.first - minTs).toFloat() / tsRange) * w
                                    val y = chartTop + chartHeight - ((point.second - minP) / rangeP).toFloat() * chartHeight
                                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path = path, color = color, style = Stroke(width = strokeWidth))
                            }

                            if (showSigDex) drawSeries(sigDexHistory, sigDexColor, 1.5f)
                            if (showSigOracle) drawSeries(sigOracleHistory, sigOracleColor, 1.5f)
                            if (showUse) drawSeries(useHistory, useColor, 2.5f)

                            // X-axis labels
                            val labelCount = 4
                            val sdf = when (range) {
                                "24H" -> SimpleDateFormat("HH:mm", Locale.US)
                                "3Y", "MAX" -> SimpleDateFormat("MMM ''yy", Locale.US)
                                "1Y" -> SimpleDateFormat("MMM ''yy", Locale.US)
                                "6M", "3M" -> SimpleDateFormat("MMM d", Locale.US)
                                else -> SimpleDateFormat("M/d", Locale.US)
                            }
                            val paint = android.graphics.Paint().apply {
                                this.color = 0xFFB0B8C4.toInt()
                                textSize = 32f
                            }
                            for (j in 0..labelCount) {
                                val t = minTs + ((maxTs - minTs) * j / labelCount)
                                val x = (j.toFloat() / labelCount) * w
                                paint.textAlign = when (j) {
                                    0 -> android.graphics.Paint.Align.LEFT
                                    labelCount -> android.graphics.Paint.Align.RIGHT
                                    else -> android.graphics.Paint.Align.CENTER
                                }
                                drawContext.canvas.nativeCanvas.drawText(sdf.format(Date(t)), x, h - 2f, paint)
                            }
                        }

                        // Y-axis labels on RIGHT
                        Canvas(modifier = Modifier.width(62.dp).fillMaxHeight()) {
                            val h = size.height
                            val chartTop = 4f
                            val chartHeight = h - 46f
                            
                            val paintY = android.graphics.Paint().apply {
                                this.color = 0xCCFFFFFF.toInt()
                                textSize = 32f
                                textAlign = android.graphics.Paint.Align.LEFT
                                isFakeBoldText = true
                            }
                            drawContext.canvas.nativeCanvas.drawText("$${String.format("%.3f", maxP)}", 8f, chartTop + 24f, paintY)
                            drawContext.canvas.nativeCanvas.drawText("$${String.format("%.3f", (minP + maxP) / 2)}", 8f, chartTop + chartHeight / 2 + 8f, paintY)
                            drawContext.canvas.nativeCanvas.drawText("$${String.format("%.3f", minP)}", 8f, chartTop + chartHeight, paintY)

                            // Highlight current price for the primary ERG source (useHistory)
                            if (useHistory.isNotEmpty()) {
                                val curP = useHistory.last().second
                                val curY = chartTop + chartHeight - ((curP - minP) / rangeP).toFloat() * chartHeight
                                val drawY = curY.coerceIn(chartTop + 24f, chartTop + chartHeight)
                                
                                // Dark blue bg so price text is readable over chart lines
                                val bgPaint = android.graphics.Paint().apply { color = 0xCC0A1020.toInt() }
                                drawContext.canvas.nativeCanvas.drawRect(4f, drawY - 30f, size.width, drawY + 6f, bgPaint)
                                val paintCurrent = android.graphics.Paint().apply {
                                    this.color = useColor.toArgb()
                                    textSize = 30f
                                    textAlign = android.graphics.Paint.Align.LEFT
                                    isFakeBoldText = true
                                }
                                val path = Path().apply {
                                    moveTo(8f, drawY - 8f)
                                    lineTo(0f, drawY - 14f)
                                    lineTo(0f, drawY - 2f)
                                    close()
                                }
                                drawPath(path, color = useColor)
                                drawContext.canvas.nativeCanvas.drawText("$${String.format("%.3f", curP)}", 12f, drawY, paintCurrent)
                            }
                        }
                    }

                    // Legend — tap to toggle visibility
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LegendDot("USE", useColor, showUse) { showUse = !showUse }
                        LegendDot("SigUSD Oracle", sigOracleColor, showSigOracle) { showSigOracle = !showSigOracle }
                        LegendDot("SigUSD DEX", sigDexColor, showSigDex) { showSigDex = !showSigDex }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ColorAccent, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Loading price data...", color = ColorTextDim, fontSize = 12.sp)
                            Text("First sync may take a minute — subsequent loads are instant", color = ColorTextDim, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Formats a price compactly for Y-axis labels.
 * For very small numbers (e.g. 0.000034), shows as "0.₄34" where subscript = count of leading zeros.
 * For normal numbers, shows with appropriate decimal places.
 */
private fun formatCompactPrice(value: Double): String {
    if (value <= 0.0) return "0"
    if (value >= 1.0) return String.format("%.3f", value)
    if (value >= 0.01) return String.format("%.4f", value)
    // Count leading zeros after decimal point
    val str = String.format("%.10f", value)
    val afterDecimal = str.substringAfter(".")
    val leadingZeros = afterDecimal.takeWhile { it == '0' }.length
    return if (leadingZeros >= 2) {
        val significant = afterDecimal.drop(leadingZeros).take(2).trimEnd('0').ifEmpty { "0" }
        val subscript = leadingZeros.toString().map { c ->
            when (c) { '0' -> '₀'; '1' -> '₁'; '2' -> '₂'; '3' -> '₃'; '4' -> '₄'
                '5' -> '₅'; '6' -> '₆'; '7' -> '₇'; '8' -> '₈'; else -> '₉' }
        }.joinToString("")
        "0.${subscript}$significant"
    } else {
        String.format("%.6f", value)
    }
}

@Composable
private fun TokenPriceChart(
    history: List<Pair<Long, Double>>,
    lineColor: Color,
    range: String,
    unit: String
) {
    if (history.size < 2) {
        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
            Text("Not enough data for chart", color = ColorTextDim, fontSize = 12.sp)
        }
        return
    }

    val allPrices = history.map { it.second }
    val minP = allPrices.min()
    val maxP = allPrices.max()
    val rangeP = if (maxP - minP > 0) maxP - minP else 1.0
    // When all prices are the same (flat line), adjust min/max so line is centered
    val displayMinP = if (maxP == minP) minP - 0.5 else minP
    val displayMaxP = if (maxP == minP) maxP + 0.5 else maxP
    val displayRangeP = displayMaxP - displayMinP

    val priceFormat = if (maxP < 0.001) "%.8f" else if (maxP < 1.0) "%.6f" else "%.4f"

    Text(
        "$range range: ${String.format(priceFormat, minP)} – ${String.format(priceFormat, maxP)} $unit",
        color = ColorTextDim, fontSize = 9.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )

    Row(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
            val w = size.width; val h = size.height
            val chartTop = 4f; val chartHeight = h - 30f

            val gridPaint = android.graphics.Paint().apply { color = 0x18FFFFFF; strokeWidth = 1f }
            for (i in 0..2) {
                val gy = chartTop + chartHeight * (1f - i / 2f)
                drawContext.canvas.nativeCanvas.drawLine(0f, gy, w, gy, gridPaint)
            }

            val nowMs = System.currentTimeMillis()
            val rangeMs = when (range) {
                "24H" -> 24L * 60 * 60 * 1000
                "7D" -> 7L * 24 * 60 * 60 * 1000
                "30D" -> 30L * 24 * 60 * 60 * 1000
                "3M" -> 90L * 24 * 60 * 60 * 1000
                "6M" -> 180L * 24 * 60 * 60 * 1000
                "1Y" -> 365L * 24 * 60 * 60 * 1000
                "3Y" -> 3L * 365 * 24 * 60 * 60 * 1000
                "MAX" -> {
                    val allTs = history.map { it.first }
                    if (allTs.isNotEmpty()) nowMs - allTs.min()
                    else 365L * 24 * 60 * 60 * 1000
                }
                else -> 7L * 24 * 60 * 60 * 1000
            }
            val minTs = nowMs - rangeMs
            val tsRange = rangeMs.toFloat()

            val path = Path()
            history.forEachIndexed { i, point ->
                val x = ((point.first - minTs).toFloat() / tsRange) * w
                val y = chartTop + chartHeight - ((point.second - displayMinP) / displayRangeP).toFloat() * chartHeight
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = lineColor, style = Stroke(width = 2.5f))

            // X-axis labels explicitly mapped to time chunks
            val sdf = when (range) {
                "24H" -> SimpleDateFormat("HH:mm", Locale.US)
                "3Y", "MAX" -> SimpleDateFormat("MMM ''yy", Locale.US)
                "1Y" -> SimpleDateFormat("MMM ''yy", Locale.US)
                "6M", "3M" -> SimpleDateFormat("MMM d", Locale.US)
                else -> SimpleDateFormat("M/d", Locale.US)
            }
            val paint = android.graphics.Paint().apply {
                this.color = 0xFFB0B8C4.toInt()
                textSize = 32f
            }
            val labelCount = 4
            for (j in 0..labelCount) {
                val t = minTs + (rangeMs * j / labelCount)
                val x = (j.toFloat() / labelCount) * w
                paint.textAlign = when (j) {
                    0 -> android.graphics.Paint.Align.LEFT
                    labelCount -> android.graphics.Paint.Align.RIGHT
                    else -> android.graphics.Paint.Align.CENTER
                }
                drawContext.canvas.nativeCanvas.drawText(sdf.format(Date(t)), x, h - 2f, paint)
            }
        }

        Canvas(
            modifier = Modifier.width(62.dp).fillMaxHeight()
        ) {
            val h = size.height
            val chartTop = 4f
            val chartHeight = h - 46f
            
            val paintY = android.graphics.Paint().apply {
                this.color = 0xCCFFFFFF.toInt()
                textSize = 32f
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText(formatCompactPrice(displayMaxP), 8f, chartTop + 24f, paintY)
            drawContext.canvas.nativeCanvas.drawText(formatCompactPrice((displayMinP + displayMaxP) / 2), 8f, chartTop + chartHeight / 2 + 8f, paintY)
            drawContext.canvas.nativeCanvas.drawText(formatCompactPrice(displayMinP), 8f, chartTop + chartHeight, paintY)

            val curP = history.last().second
            val curY = chartTop + chartHeight - ((curP - displayMinP) / displayRangeP).toFloat() * chartHeight
            val drawY = curY.coerceIn(chartTop + 24f, chartTop + chartHeight)
            
            // Dark blue bg so price text is readable over chart lines
            val bgPaint = android.graphics.Paint().apply { color = 0xCC0A1020.toInt() }
            drawContext.canvas.nativeCanvas.drawRect(4f, drawY - 30f, size.width, drawY + 6f, bgPaint)
            val paintCurrent = android.graphics.Paint().apply {
                this.color = lineColor.toArgb()
                textSize = 30f
                textAlign = android.graphics.Paint.Align.LEFT
                isFakeBoldText = true
            }
            val path = Path().apply {
                moveTo(8f, drawY - 8f)
                lineTo(0f, drawY - 14f)
                lineTo(0f, drawY - 2f)
                close()
            }
            drawPath(path, color = lineColor)
            drawContext.canvas.nativeCanvas.drawText(formatCompactPrice(curP), 12f, drawY, paintCurrent)
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color, enabled: Boolean = true, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clickable { onClick() } else Modifier
    ) {
        Box(
            modifier = Modifier.size(8.dp).background(
                if (enabled) color else color.copy(alpha = 0.25f),
                CircleShape
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            label,
            color = if (enabled) ColorTextDim else ColorTextDim.copy(alpha = 0.35f),
            fontSize = 9.sp,
            textDecoration = if (enabled) null else TextDecoration.LineThrough
        )
    }
}

private fun shareChart(context: android.content.Context, cardBounds: android.graphics.Rect) {
    val activity = context as? android.app.Activity ?: return
    val rootView = activity.window.decorView.rootView

    val bounds = android.graphics.Rect(
        cardBounds.left.coerceAtLeast(0),
        cardBounds.top.coerceAtLeast(0),
        cardBounds.right.coerceAtMost(rootView.width),
        cardBounds.bottom.coerceAtMost(rootView.height)
    )
    if (bounds.width() <= 0 || bounds.height() <= 0) return

    val bitmap = Bitmap.createBitmap(bounds.width(), bounds.height(), Bitmap.Config.ARGB_8888)

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.view.PixelCopy.request(
            activity.window,
            bounds,
            bitmap,
            { result ->
                if (result == android.view.PixelCopy.SUCCESS) {
                    addWatermarkAndShare(context, bitmap)
                } else {
                    bitmap.recycle()
                }
            },
            android.os.Handler(android.os.Looper.getMainLooper())
        )
    } else {
        try {
            rootView.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
            val canvas = android.graphics.Canvas(bitmap)
            rootView.draw(canvas)
            rootView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
            addWatermarkAndShare(context, bitmap)
        } catch (_: Exception) {
            bitmap.recycle()
        }
    }
}

private fun addWatermarkAndShare(context: android.content.Context, bitmap: Bitmap) {
    try {
        val canvas = android.graphics.Canvas(bitmap)

        try {
            val logoBitmap = android.graphics.BitmapFactory.decodeResource(
                context.resources, com.piggytrade.piggytrade.R.drawable.logo_topbar_and_standard_launcher
            )
            if (logoBitmap != null) {
                val logoSize = minOf(bitmap.width, bitmap.height) / 4
                // Preserve original aspect ratio
                val aspectRatio = logoBitmap.width.toFloat() / logoBitmap.height.toFloat()
                val logoW = logoSize
                val logoH = (logoSize / aspectRatio).toInt()
                val logoPaint = android.graphics.Paint().apply { alpha = 55 }
                val scaledLogo = Bitmap.createScaledBitmap(logoBitmap, logoW, logoH, true)
                canvas.drawBitmap(
                    scaledLogo,
                    (bitmap.width - logoW) / 2f,
                    (bitmap.height - logoH) / 2f,
                    logoPaint
                )
                scaledLogo.recycle()
                logoBitmap.recycle()
            }
        } catch (_: Exception) {}

        val textPaint = android.graphics.Paint().apply {
            color = 0x88FFFFFF.toInt()  // more visible watermark
            textSize = 30f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        // Draw text at top of image
        canvas.drawText(
            "PiggyTrade \uD83D\uDC37 on-chain data",
            bitmap.width / 2f, textPaint.textSize + 24f, textPaint
        )

        val cacheDir = java.io.File(context.cacheDir, "shared_charts")
        cacheDir.mkdirs()
        val file = java.io.File(cacheDir, "price_chart.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 95, it) }
        bitmap.recycle()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "Price chart from PiggyTrade \uD83D\uDC37 — on-chain DEX data!")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Price Chart"))
    } catch (e: Exception) {
        bitmap.recycle()
    }
}

// ─── TVL Section ───────────────────────────────────────────────

/** Format large number as compact K/M string */
private fun compactErg(erg: Double): String {
    return when {
        erg >= 1_000_000 -> "${String.format("%.1f", erg / 1_000_000)}M"
        erg >= 1_000 -> "${String.format("%.1f", erg / 1_000)}K"
        else -> String.format("%.0f", erg)
    }
}

private fun compactUsd(usd: Double): String {
    return when {
        usd >= 1_000_000 -> "$${String.format("%.1f", usd / 1_000_000)}M"
        usd >= 1_000 -> "$${String.format("%.0f", usd / 1_000)}K"
        else -> "$${String.format("%.0f", usd)}"
    }
}

@Composable
private fun TvlSection(tvl: Map<String, Double>, ergPriceUsd: Double?) {
    val totalErg = tvl.values.sum()
    val totalUsd = if (ergPriceUsd != null) totalErg * ergPriceUsd else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ColorCard),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Text(
                "Total Value Locked",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    "${compactErg(totalErg)} ERG",
                    color = ColorAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                if (totalUsd != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "≈ ${compactUsd(totalUsd)}",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Protocol rows — two columns
            val entries = tvl.entries.toList()
            for (i in entries.indices step 2) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TvlProtocolCell(entries[i].key, entries[i].value, ergPriceUsd, Modifier.weight(1f))
                    if (i + 1 < entries.size) {
                        TvlProtocolCell(entries[i + 1].key, entries[i + 1].value, ergPriceUsd, Modifier.weight(1f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvlProtocolCell(name: String, erg: Double, ergPriceUsd: Double?, modifier: Modifier) {
    val usd = if (ergPriceUsd != null) erg * ergPriceUsd else null
    val icon = when {
        name.contains("Spectrum") || name.contains("Pool") || name.contains("USE") -> Icons.Rounded.SwapHoriz
        else -> Icons.Rounded.AccountBalance
    }

    Row(
        modifier = modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp).padding(end = 0.dp))
        Column {
            Text(name, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Row {
                Text(
                    "${compactErg(erg)} ERG",
                    color = ColorAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold
                )
                if (usd != null) {
                    Text(
                        " / ${compactUsd(usd)}",
                        color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// ─── Activity Row ──────────────────────────────────────────────

@Composable
private fun EcosystemTxRow(tx: EcosystemTx) {
    val uriHandler = LocalUriHandler.current
    val icon = getEcoIcon(tx.protocol)
    val tagColor = getEcoTagColor(tx.protocol)
    val truncAddr = if (tx.traderAddress.length > 16)
        "${tx.traderAddress.take(8)}…${tx.traderAddress.takeLast(6)}" else tx.traderAddress

    // Detail + explorer dialog
    var showDetail by remember { mutableStateOf(false) }

    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            containerColor = ColorCard,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(26.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(tx.protocol, color = tagColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        if (tx.isConfirmed) "✓ Confirmed" else "⏳ Pending",
                        color = if (tx.isConfirmed) ColorAccent else Color.Yellow,
                        fontSize = 12.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Trader address — full, monospace
                    Text("Trader", color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        tx.traderAddress,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 16.sp
                    )

                    // Sent → Received
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sent", color = ColorTextDim, fontSize = 11.sp)
                            Text(tx.sent, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(" ➜ ", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Received", color = ColorTextDim, fontSize = 11.sp)
                            Text(tx.received, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Price impact
                    tx.priceImpact?.let { impact ->
                        val impactText = String.format("%+.2f%%", impact)
                        val impactColor = if (impact >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Price Impact: ", color = Color.White, fontSize = 13.sp)
                            Text(impactText, color = impactColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Date/time
                    Text("Date", color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        "${ecoTimeAgo(tx.timestamp)} · ${ecoDate(tx.timestamp)}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )

                    // TX ID
                    Text("Transaction ID", color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        tx.txId,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Explorer links
                    Text("Open in Explorer", color = ColorTextDim, fontSize = 11.sp)
                    listOf(
                        "Ergo Explorer" to "https://explorer.ergoplatform.com/en/transactions/${tx.txId}",
                        "ErgExplorer" to "https://ergexplorer.com/transactions#${tx.txId}",
                        "Sigmaspace.io" to "https://sigmaspace.io/en/transaction/${tx.txId}"
                    ).forEach { (name, url) ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ColorInputBg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    uriHandler.openUri(url)
                                    showDetail = false
                                }
                        ) {
                            Text(
                                text = name,
                                color = ColorAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetail = false }) {
                    Text("Close", color = ColorTextDim)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable { showDetail = true },
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left column: icon + protocol label — vertically centered
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.width(58.dp).padding(horizontal = 4.dp)
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = tagColor, modifier = Modifier.size(26.dp))
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tx.protocol,
                    color = tagColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    lineHeight = 8.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Right: details
            Column(modifier = Modifier.weight(1f)) {
                // Row 1: address (left) + time/date (right)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        truncAddr,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        "${ecoTimeAgo(tx.timestamp)} · ${ecoDate(tx.timestamp)}",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 9.sp
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Row 2: Sent ➜ Received — white font, no colors
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        tx.sent, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        " ➜ ", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        tx.received, color = Color.White, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                // Price impact (only for DEX swaps)
                tx.priceImpact?.let { impact ->
                    val impactText = String.format("%+.2f%%", impact)
                    val impactColor = if (impact >= 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 3.dp)
                    ) {
                        Text("Price impact ", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = impactColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                impactText, color = impactColor, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Helpers ───────────────────────────────────────────

private fun getEcoIcon(protocol: String): ImageVector {
    val l = protocol.lowercase()
    return when {
        l.contains("freemint") -> Icons.Rounded.AccountBalance
        l.contains("arbmint") -> Icons.Rounded.AccountBalance
        l.contains("lp swap") -> Icons.Rounded.SwapHoriz
        l.contains("dex") -> Icons.Rounded.SwapHoriz
        l.contains("use pool") -> Icons.Rounded.SwapHoriz
        l.contains("buyback") -> Icons.Rounded.CurrencyExchange
        l.contains("duckpools") -> Icons.Rounded.Waves
        l.contains("bank") -> Icons.Rounded.AccountBalance
        l.contains("pool") -> Icons.Rounded.SwapHoriz
        else -> Icons.Rounded.Receipt
    }
}

private fun getEcoTagColor(protocol: String): Color {
    val l = protocol.lowercase()
    return when {
        l.contains("freemint") -> ColorAccent           // green
        l.contains("arbmint") -> Color(0xFFFF9800)      // orange
        l.contains("bank") -> ColorBlue
        l.contains("buyback") -> Color(0xFFFF9800)
        l.contains("use lp") -> Color(0xFF29B6F6)       // light blue — USE LP
        l.contains("dexygold lp") || l.contains("dexygold lp") -> Color(0xFFFFB300) // amber — DexyGold LP
        l.contains("lp") -> Color(0xFF29B6F6)           // light blue fallback
        l.contains("dex swap") || l.contains("dex t2t") -> Color(0xFF26C6DA) // cyan — Spectrum
        l.contains("dex") -> Color(0xFF26C6DA)
        else -> ColorTextDim
    }
}

private fun ecoTimeAgo(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diffMs = now - timestamp
    val mins = diffMs / 60000
    if (mins < 1) return "Just now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24
    return "${days}d ago"
}

private fun ecoDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("d MMM yyyy", Locale.US)
    return sdf.format(Date(timestamp))
}
