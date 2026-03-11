package com.piggytrade.piggytrade.ui.bank
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.piggytrade.piggytrade.stablecoin.EligibilityResult
import com.piggytrade.piggytrade.stablecoin.MintQuote
import com.piggytrade.piggytrade.stablecoin.StatusField
import com.piggytrade.piggytrade.stablecoin.StablecoinProtocol
import com.piggytrade.piggytrade.ui.swap.SwapState
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

// ─── Protocol Selector Chips ─────────────────────────────────────────────────

/**
 * Horizontal chip row showing all registered protocols.
 * Clicking selects the active protocol; active chip is highlighted.
 */
@Composable
fun ProtocolSelectorRow(
    protocols: List<StablecoinProtocol>,
    activeProtocolId: String,
    onSelect: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        protocols.forEach { protocol ->
            val isActive = protocol.id == activeProtocolId
            val chipBg by animateColorAsState(
                targetValue = if (isActive) ColorAccent else ColorInputBg,
                animationSpec = tween(200)
            )
            Box(
                modifier = Modifier
                    .androidBorder(radius = 20.dp, borderWidth = 0.dp, bgColor = chipBg)
                    .clickable { onSelect(protocol.id) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = protocol.displayName,
                    color = if (isActive) ColorBg else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

// ─── Cost Summary ────────────────────────────────────────────────────────────

/** Format nanoERG as ERG string, stripping trailing zeros but keeping at least 3 decimals. */
private fun formatErg(nanoErg: Long): String {
    val erg = nanoErg.toDouble() / 1_000_000_000.0
    return formatErgValue(erg)
}

private fun formatErgValue(erg: Double): String {
    // Format to 9 decimals, then strip trailing zeros but keep at least 3 decimal places
    val full = "%.9f".format(erg)
    val dotIdx = full.indexOf('.')
    if (dotIdx < 0) return full
    val minEnd = dotIdx + 4 // at least 3 decimals (e.g. "0.002")
    var end = full.length
    while (end > minEnd && full[end - 1] == '0') end--
    return full.substring(0, end)
}

/**
 * Compact cost summary shown below the mint input.
 * Shows total cost, fee breakdown from the quote, and wallet balance.
 */
@Composable
fun BankCostSummary(
    ergCost: Long,
    miningFee: Long,
    walletErgBalance: Double,
    feeBreakdown: List<Pair<String, Long>> = emptyList()
) {
    val totalCostErg = (ergCost + miningFee).toDouble() / 1_000_000_000.0
    val hasCost = ergCost > 0L
    val balanceDisplay = formatErgValue(walletErgBalance)
    val insufficientBalance = hasCost && totalCostErg > walletErgBalance

    // Load ERG logo from assets
    val context = LocalContext.current
    val ergBitmap = remember {
        try {
            val stream = context.assets.open("token_logos/ergo.png")
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        // Total cost — prominent line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Total Cost", color = ColorTextDim, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            if (ergBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = ergBitmap,
                    contentDescription = "ERG",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = if (hasCost) "${formatErgValue(totalCostErg)} ERG" else "—",
                color = if (insufficientBalance) Color(0xFFFF5252) else Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Breakdown from quote (bank fee, buyback fee, app fee, etc.)
        if (hasCost) {
            for ((label, nanoErg) in feeBreakdown) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("  $label", color = ColorTextDim, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${formatErg(nanoErg)} ERG", color = ColorTextDim, fontSize = 11.sp)
                }
            }
            // Mining fee
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("  Mining fee", color = ColorTextDim, fontSize = 11.sp)
                Spacer(Modifier.weight(1f))
                Text("${formatErg(miningFee)} ERG", color = ColorTextDim, fontSize = 11.sp)
            }
        }

        // Wallet balance
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp)
        ) {
            Text(
                text = "Wallet: $balanceDisplay ERG",
                color = if (insufficientBalance) Color(0xFFFF5252) else ColorTextDim,
                fontSize = 10.sp
            )
            if (insufficientBalance) {
                Spacer(Modifier.width(8.dp))
                Text("Insufficient balance", color = Color(0xFFFF5252), fontSize = 10.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Mint Amount Panel ────────────────────────────────────────────────────────

/**
 * "YOU MINT" input panel. The user types the desired mint amount here.
 * Shows the token logo from assets instead of text.
 */
@Composable
fun BankReceivePanel(
    amount: String,
    tokenName: String,
    tokenBalance: String,
    onAmountChange: (String) -> Unit
) {
    // Load the USE token logo from assets
    val context = LocalContext.current
    val tokenBitmap = remember {
        try {
            val stream = context.assets.open("token_logos/a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669.png")
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    TogaColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .padding(bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(90.dp)
                .androidBorder(
                    radius = 16.dp,
                    borderWidth = 0.5.dp,
                    borderColor = Color.White.copy(alpha = 0.1f),
                    bgColor = ColorInputBg
                )
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                Text("YOU MINT", color = ColorTextDim, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = amount,
                            onValueChange = onAmountChange,
                            textStyle = TextStyle(
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (amount.isEmpty()) Text("Enter amount", color = ColorInputHint, fontSize = 22.sp)
                                    inner()
                                }
                            }
                        )
                        if (tokenBalance.isNotEmpty()) {
                            Text("Balance: $tokenBalance $tokenName", color = ColorTextDim, fontSize = 10.sp)
                        }
                    }
                    // Token badge with logo
                    Row(
                        modifier = Modifier
                            .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (tokenBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = tokenBitmap,
                                contentDescription = tokenName,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(tokenName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Protocol Status Card ─────────────────────────────────────────────────────

/**
 * Renders the [EligibilityResult.statusFields] list as labelled rows.
 * Completely protocol-agnostic — just renders whatever the protocol returns.
 */
@Composable
fun ProtocolStatusCard(eligibility: EligibilityResult?) {
    var isExpanded by remember { mutableStateOf(false) }  // default collapsed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .androidBorder(
                radius = 12.dp,
                borderWidth = 0.5.dp,
                borderColor = Color.White.copy(alpha = 0.05f),
                bgColor = ColorSelectionBg.copy(alpha = 0.8f)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(15.dp)
    ) {
        // Header row with status indicator
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Green/red indicator based on canMint
                val indicatorColor = when {
                    eligibility == null -> ColorTextDim
                    eligibility.canMint -> Color(0xFF00D18B)
                    else -> Color(0xFFFF5252)
                }
                val indicatorIcon = when {
                    eligibility == null -> "\uE88E" // hourglass / info
                    eligibility.canMint -> "\uE876" // check_circle
                    else -> "\uE000" // cancel / error
                }
                Text(
                    text = indicatorIcon,
                    fontFamily = MaterialDesignIcons,
                    fontSize = 16.sp,
                    color = indicatorColor
                )
                Text("PROTOCOL STATUS", color = ColorTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text(
                text = if (isExpanded) "\uE5CE" else "\uE5CF",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = MaterialDesignIcons
            )
        }

        if (isExpanded && eligibility != null) {
            Spacer(Modifier.height(10.dp))

            eligibility.statusFields.forEach { field ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(field.label, color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        text = field.value,
                        color = when (field.status) {
                            StatusField.Status.OK -> ColorAccent
                            StatusField.Status.WARNING -> Color(0xFFFFC107)
                            StatusField.Status.ERROR -> Color(0xFFFF5252)
                            StatusField.Status.NEUTRAL -> Color.White
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Not-eligible banner
            if (!eligibility.canMint && eligibility.reason != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .androidBorder(radius = 8.dp, borderWidth = 0.dp, bgColor = Color(0xFF3A1A1A))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(eligibility.reason, color = Color(0xFFFF5252), fontSize = 11.sp)
                }
            }
        } else if (isExpanded && eligibility == null) {
            Spacer(Modifier.height(8.dp))
            Text("Loading protocol status…", color = ColorTextDim, fontSize = 11.sp)
        }
    }
}

// ─── Order Details Panel ──────────────────────────────────────────────────────

/**
 * Renders the [MintQuote.feeBreakdown] list plus miner fee.
 * Protocol-agnostic — fee labels come from the protocol itself.
 */
@Composable
fun BankOrderDetailsPanel(quote: MintQuote?) {
    var isExpanded by remember { mutableStateOf(false) }  // default collapsed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .androidBorder(
                radius = 12.dp,
                borderWidth = 0.5.dp,
                borderColor = Color.White.copy(alpha = 0.05f),
                bgColor = ColorSelectionBg.copy(alpha = 0.8f)
            )
            .clickable { isExpanded = !isExpanded }
            .padding(15.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val totalErg = quote?.let { (it.ergCost + it.miningFee).toDouble() / 1_000_000_000.0 }
            Text(
                text = if (totalErg != null) "Total cost: ${formatErgValue(totalErg)} ERG" else "ORDER DETAILS",
                color = if (quote != null) Color.White else ColorTextDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isExpanded) "\uE5CE" else "\uE5CF",
                color = Color.White,
                fontSize = 20.sp,
                fontFamily = MaterialDesignIcons
            )
        }

        if (isExpanded && quote != null) {
            Spacer(Modifier.height(10.dp))
            Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // Dynamic fee rows from the protocol
            quote.feeBreakdown.forEach { (label, nanoErg) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(label, color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        "%.6f ERG".format(nanoErg.toDouble() / 1_000_000_000.0),
                        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }
            }

            // Mining fee (always shown separately)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Mining Fee", color = ColorTextDim, fontSize = 11.sp)
                Text(
                    "%.6f ERG".format(quote.miningFee.toDouble() / 1_000_000_000.0),
                    color = ColorAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─── Mint Button ─────────────────────────────────────────────────────────────

/**
 * Primary action button for the BANK tab.
 *
 * Disabled if:
 *  - Protocol is loading
 *  - Eligibility check says canMint = false
 *  - Amount is zero
 *  - Wallet has insufficient ERG
 */
@Composable
fun MintButton(
    protocolName: String,
    isLoading: Boolean,
    isBuildingTx: Boolean,
    canMint: Boolean,
    insufficientBalance: Boolean,
    onMint: () -> Unit
) {
    val enabled = canMint && !isLoading && !isBuildingTx && !insufficientBalance
    val label = when {
        isBuildingTx -> "Building transaction…"
        isLoading -> "Loading…"
        insufficientBalance -> "INSUFFICIENT BALANCE"
        !canMint -> "NOT ELIGIBLE"
        else -> "MINT $protocolName"
    }

    Button(
        onClick = onMint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(55.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) Color(0xFF00D18B) else ColorSelectionBg,
            disabledContainerColor = ColorSelectionBg.copy(alpha = 0.5f)
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading || isBuildingTx) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp).padding(end = 8.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    strokeWidth = 2.dp
                )
            }
            Text(
                text = label,
                color = if (enabled) ColorBg else ColorTextDim,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
