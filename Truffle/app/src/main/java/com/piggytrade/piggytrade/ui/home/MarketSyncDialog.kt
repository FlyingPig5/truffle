package com.piggytrade.piggytrade.ui.home

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.market.MarketViewModel

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Full dialog for user-controlled market data synchronization.
 * Shows progress, allows continue-in-background or stop.
 */
@Composable
fun MarketSyncDialog(
    viewModel: MarketViewModel,
    isFirstSync: Boolean,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val store = viewModel.oraclePriceStore

    // Read observable progress directly from OraclePriceStore
    val isSyncing = store.isSyncingAllTokens
    val syncIndex = store.allTokenSyncIndex
    val syncTotal = store.allTokenSyncTotal
    val syncLabel = store.allTokenSyncLabel
    val syncState = uiState.marketSyncState

    // Track whether the sync completed while THIS dialog was open
    // (vs. "completed" persisted from a previous session)
    var syncJustCompleted by remember { mutableStateOf(false) }
    var wasEverSyncing by remember { mutableStateOf(syncState == "syncing") }

    LaunchedEffect(syncState) {
        if (syncState == "syncing") wasEverSyncing = true
        if (syncState == "completed" && wasEverSyncing) syncJustCompleted = true
    }

    // Effective display state: only show "completed" if it just finished here
    val displayState = when {
        syncState == "syncing" -> "syncing"
        syncJustCompleted -> "completed"
        else -> "idle"
    }

    // Auto-start sync only for first sync or incomplete — NOT when already completed
    LaunchedEffect(Unit) {
        if (syncState != "syncing" && (isFirstSync || uiState.marketSyncIncomplete)) {
            viewModel.startMarketSync()
        }
    }

    // Pulsing animation for the sync indicator
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Dialog(
        onDismissRequest = { /* Don't allow dismiss by back button during first sync */ if (!isFirstSync || syncState != "syncing") onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = !isFirstSync)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            colors = CardDefaults.cardColors(containerColor = ColorCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = if (isFirstSync) "First-Time Sync" else "Market Data Sync",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                // Subtitle
                val lastSyncMs = uiState.lastMarketSyncMs
                val minutesSinceSync = if (lastSyncMs > 0) ((System.currentTimeMillis() - lastSyncMs) / 60_000L) else -1L

                Text(
                    text = when {
                        isFirstSync -> "The app needs to sync market data from the blockchain. This will take a few minutes."
                        displayState == "completed" -> "Sync complete! All token prices and volumes are up to date."
                        displayState == "syncing" -> "Syncing market data. You can continue in the background or stop and resume later."
                        uiState.marketSyncIncomplete -> "Previous sync was interrupted. Tap Sync Now to continue where you left off."
                        minutesSinceSync in 0..29 -> "Prices were synced ${minutesSinceSync}m ago. Re-sync to get the latest data?"
                        minutesSinceSync in 30..59 -> "Prices are ${minutesSinceSync}m old. Would you like to refresh?"
                        minutesSinceSync >= 60 -> "Prices are ${minutesSinceSync / 60}h old. It's time for a refresh."
                        else -> "Sync market data to get the latest token prices and volumes."
                    },
                    color = ColorTextDim,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(Modifier.height(20.dp))

                // Progress circle + text
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (displayState == "syncing" && isSyncing) {
                        CircularProgressIndicator(
                            progress = { if (syncTotal > 0) (syncIndex.toFloat() / syncTotal).coerceIn(0f, 1f) else 0f },
                            modifier = Modifier.fillMaxSize(),
                            color = ColorAccent,
                            trackColor = ColorInputBg,
                            strokeWidth = 6.dp
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$syncIndex/$syncTotal",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "tokens",
                                color = ColorTextDim,
                                fontSize = 11.sp
                            )
                        }
                    } else if (displayState == "completed") {
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(ColorAccent.copy(alpha = 0.15f))
                                .border(2.dp, ColorAccent, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✓", color = ColorAccent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    } else if (!isFirstSync && !uiState.marketSyncIncomplete) {
                        // Idle — show a sync icon
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(ColorBlue.copy(alpha = 0.12f))
                                .border(2.dp, ColorBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("↻", color = ColorBlue, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // First sync or incomplete — pulsing spinner
                        CircularProgressIndicator(
                            modifier = Modifier.size(40.dp).alpha(pulseAlpha),
                            color = ColorAccent,
                            strokeWidth = 3.dp
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Current token label
                if (displayState == "syncing" && syncLabel.isNotEmpty()) {
                    Text(
                        text = "Syncing: $syncLabel",
                        color = ColorAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                }

                // Progress bar
                if (displayState == "syncing" && syncTotal > 0) {
                    LinearProgressIndicator(
                        progress = { (syncIndex.toFloat() / syncTotal).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = ColorAccent,
                        trackColor = ColorInputBg
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    Spacer(Modifier.height(12.dp))
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (displayState) {
                        "syncing" -> {
                            // Continue in Background
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorInputBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Background", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            // Stop Sync
                            Button(
                                onClick = {
                                    viewModel.stopMarketSync()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorOrange.copy(alpha = 0.8f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Stop Sync", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        "completed" -> {
                            val failedCount = uiState.syncFailedCount
                            if (failedCount > 0) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Warning banner
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(ColorOrange.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("⚠", color = ColorOrange, fontSize = 16.sp)
                                        Text(
                                            "$failedCount token${if (failedCount > 1) "s" else ""} could not be synced due to timeouts. Retry to try again.",
                                            color = ColorOrange,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.retryFailedSync() },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorOrange),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Retry Failed ($failedCount)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(
                                            onClick = onDismiss,
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("Done", color = ColorBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onDismiss,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Done", color = ColorBg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {
                            // Idle — show Sync Now + Close
                            Button(
                                onClick = { viewModel.startMarketSync() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Sync Now", color = ColorBg, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorInputBg),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Close", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dynamic sync button for the ecosystem screen.
 * Changes appearance based on sync state and time since last sync.
 */
@Composable
fun MarketSyncButton(
    viewModel: MarketViewModel,
    onClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val store = viewModel.oraclePriceStore
    val isSyncing = store.isSyncingAllTokens || uiState.marketSyncState == "syncing"

    // Determine button state
    val lastSyncMs = uiState.lastMarketSyncMs
    val incomplete = uiState.marketSyncIncomplete
    val now = System.currentTimeMillis()
    val minutesSinceSync = if (lastSyncMs > 0) ((now - lastSyncMs) / 60_000L) else -1L

    // Pulsing animation for incomplete/stale states
    val pulseAlpha by rememberInfiniteTransition(label = "btnPulse").animateFloat(
        initialValue = 0.7f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "btnPulseAlpha"
    )

    val bgColor: Color
    val textColor: Color
    val label: String

    when {
        isSyncing -> {
            val idx = store.allTokenSyncIndex
            val total = store.allTokenSyncTotal
            bgColor = ColorAccent.copy(alpha = 0.15f)
            textColor = ColorAccent
            label = "Syncing… $idx/$total"
        }
        incomplete -> {
            bgColor = ColorOrange.copy(alpha = 0.2f * pulseAlpha)
            textColor = ColorOrange
            label = "Sync incomplete — tap to continue"
        }
        lastSyncMs == 0L -> {
            bgColor = ColorOrange.copy(alpha = 0.2f * pulseAlpha)
            textColor = ColorOrange
            label = "No market data — tap to sync"
        }
        minutesSinceSync < 30 -> {
            bgColor = ColorBlue.copy(alpha = 0.12f)
            textColor = ColorBlue
            label = "Prices synced ✓"
        }
        minutesSinceSync < 60 -> {
            bgColor = Color(0xFFFFD700).copy(alpha = 0.12f)
            textColor = Color(0xFFFFD700)
            label = "${minutesSinceSync}m since last sync"
        }
        else -> {
            val hours = minutesSinceSync / 60
            bgColor = ColorOrange.copy(alpha = 0.15f)
            textColor = ColorOrange
            label = if (hours == 1L) "1h since last sync" else "${hours}h since last sync"
        }
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = ColorAccent,
                    strokeWidth = 1.5.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = textColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
