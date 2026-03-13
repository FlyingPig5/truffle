package com.piggytrade.piggytrade.ui.bank
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.piggytrade.piggytrade.stablecoin.RedeemQuote
import com.piggytrade.piggytrade.stablecoin.StatusField
import com.piggytrade.piggytrade.stablecoin.StablecoinProtocol
import com.piggytrade.piggytrade.ui.swap.SwapState
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

/**
 * Family-based protocol selector.
 *
 * Top row: USE | SigUSD | SigRSV  (with icons + info button)
 * Sub-row: Freemint / Arbmint shown only when USE is selected.
 */
@Composable
fun ProtocolSelectorRow(
    protocols: List<StablecoinProtocol>,
    activeProtocolId: String,
    onSelect: (String) -> Unit
) {
    val context = LocalContext.current
    var showInfoFamily by remember { mutableStateOf<String?>(null) }

    // Group protocols into families
    data class ProtocolFamily(
        val key: String,
        val label: String,
        val iconTokenId: String,
        val protocols: List<StablecoinProtocol>,
        val info: String
    )

    val families = remember(protocols) {
        val useProtos = protocols.filter { it.id.startsWith("use_") }
        val dexyProtos = protocols.filter { it.id.startsWith("dexygold_") }
        val sigUsdProtos = protocols.filter { it.id == "sigusd_mint" }
        val sigRsvProtos = protocols.filter { it.id == "sigrsv_mint" }

        buildList {
            if (useProtos.isNotEmpty()) add(ProtocolFamily(
                "use", "USE",
                "a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669",
                useProtos,
                "USE is a stablecoin on Ergo that tracks the US Dollar.\n\n" +
                "• Freemint: Mint new USE tokens by locking ERG.\n" +
                "• Arbmint: Mint USE at a discount when the price deviates, helping restore the peg.\n\n" +
                "To sell USE, swap it in the DEX tab — there is no direct redeem from the bank. " +
                "Mint cycles limit how much USE can be minted in each period.\n\n" +
                "Benefits: USE has no reserve ratio gates — you can always mint. The protocol is " +
                "self-stabilising through arbitrage incentives, and provides a simple way to hold " +
                "dollar-pegged value on Ergo."
            ))
            if (dexyProtos.isNotEmpty()) add(ProtocolFamily(
                "dexygold", "DexyGold",
                "6122f7289e7bb2df2de273e09d4b2756cda6aeb0f40438dc9d257688f45183ad",
                dexyProtos,
                "DexyGold is a stablecoin on Ergo that tracks the price of Gold (XAU).\n\n" +
                "• Freemint: Mint new DexyGold tokens by locking ERG.\n" +
                "• Arbmint: Mint DexyGold at a discount when the price deviates, helping restore the peg.\n\n" +
                "Same protocol design as USE but pegged to gold instead of USD. " +
                "Mint cycles limit how much DexyGold can be minted in each period.\n\n" +
                "Benefits: Provides decentralised gold exposure on Ergo, with no reserve ratio gates " +
                "and self-stabilising arbitrage incentives."
            ))
            if (sigUsdProtos.isNotEmpty()) add(ProtocolFamily(
                "sigusd", "SigUSD",
                "03faf2cb329f2e90d6d23b58d91bbb6c046aa143261cc21f52fbe2824bfcbf04",
                sigUsdProtos,
                "SigUSD is a stablecoin backed by ERG reserves (AgeUSD protocol).\n\n" +
                "• Mint SigUSD by depositing ERG into the reserve.\n" +
                "• Redeem SigUSD to get ERG back — always allowed, no restrictions.\n" +
                "• Minting requires reserve ratio ≥ 400%.\n\n" +
                "Benefits: SigUSD is the most battle-tested stablecoin on Ergo. It is fully " +
                "decentralised with no custodian — your USD value is backed entirely by on-chain ERG. " +
                "Redeeming is always available, so you can exit at any time."
            ))
            if (sigRsvProtos.isNotEmpty()) add(ProtocolFamily(
                "sigrsv", "SigRSV",
                "003bd19d0187117f130b62e1bcab0939929ff5c7709f843c5c4dd158949285d0",
                sigRsvProtos,
                "SigRSV is the reserve coin that backs SigUSD.\n\n" +
                "• Mint SigRSV to add reserves (allowed when ratio ≤ 800%).\n" +
                "• Redeem SigRSV to withdraw equity (allowed when ratio ≥ 400%).\n\n" +
                "Benefits: SigRSV gives you leveraged ERG exposure — when ERG goes up, SigRSV " +
                "gains more than holding ERG alone. It's fully on-chain with no liquidation risk. " +
                "You earn the spread as people mint and redeem SigUSD. The trade-off is that " +
                "SigRSV loses more when ERG drops."
            ))
        }
    }

    // Determine which family the active protocol belongs to
    val activeFamily = families.firstOrNull { fam ->
        fam.protocols.any { it.id == activeProtocolId }
    }?.key ?: families.firstOrNull()?.key ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── Top row: family chips (icon on top, label below) ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            families.forEach { family ->
                val isActive = family.key == activeFamily
                val chipBg by animateColorAsState(
                    targetValue = if (isActive) ColorAccent else ColorInputBg,
                    animationSpec = tween(200)
                )

                val iconBitmap = remember(family.iconTokenId) {
                    try {
                        val stream = context.assets.open("token_logos/${family.iconTokenId}.png")
                        BitmapFactory.decodeStream(stream)?.asImageBitmap()
                    } catch (e: Exception) { null }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .androidBorder(radius = 14.dp, borderWidth = if (isActive) 0.dp else 0.5.dp,
                            borderColor = Color.White.copy(alpha = 0.1f), bgColor = chipBg)
                        .clickable {
                            family.protocols.firstOrNull()?.let { onSelect(it.id) }
                        }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (iconBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = iconBitmap,
                            contentDescription = family.label,
                            modifier = Modifier
                                .size(28.dp)
                                .androidBorder(radius = 14.dp, borderWidth = 0.dp, bgColor = Color.Transparent),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    }
                    Text(
                        text = family.label,
                        color = if (isActive) ColorBg else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        // ── Sub-row: shown for any family with multiple protocols (Freemint / Arbmint) ──
        val activeFamilyObj = families.firstOrNull { it.key == activeFamily }
        if (activeFamilyObj != null && activeFamilyObj.protocols.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                activeFamilyObj.protocols.forEach { protocol ->
                    val isActive = protocol.id == activeProtocolId
                    val subChipBg by animateColorAsState(
                        targetValue = if (isActive) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        animationSpec = tween(150)
                    )
                    Box(
                        modifier = Modifier
                            .androidBorder(radius = 14.dp, borderWidth = if (isActive) 0.dp else 0.5.dp,
                                borderColor = Color.White.copy(alpha = 0.1f), bgColor = subChipBg)
                            .clickable { onSelect(protocol.id) }
                            .padding(horizontal = 18.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = protocol.displayName,
                            color = if (isActive) Color.White else ColorTextDim,
                            fontSize = 13.sp,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                // (i) Info button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .androidBorder(radius = 13.dp, borderWidth = 0.5.dp,
                            borderColor = Color.White.copy(alpha = 0.12f), bgColor = Color.Transparent)
                        .clickable { showInfoFamily = activeFamily },
                    contentAlignment = Alignment.Center
                ) {
                    Text("ℹ", color = ColorTextDim, fontSize = 13.sp)
                }
            }
        }
    }

    // ── Info Dialog ───────────────────────────────────────────────────────────
    showInfoFamily?.let { familyKey ->
        val family = families.firstOrNull { it.key == familyKey }
        if (family != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showInfoFamily = null },
                containerColor = ColorCard,
                title = {
                    Text(
                        "How ${family.label} works",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                },
                text = {
                    Text(
                        family.info,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { showInfoFamily = null }) {
                        Text("Got it", color = ColorAccent)
                    }
                }
            )
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
    onAmountChange: (String) -> Unit,
    label: String = "YOU MINT",
    tokenId: String = "",
    decimals: Int = 2
) {
    // Load the protocol token logo from assets (falls back to USE logo)
    val context = LocalContext.current
    val tokenBitmap = remember(tokenId) {
        try {
            val logoFile = if (tokenId.isNotEmpty()) "token_logos/$tokenId.png"
                           else "token_logos/a55b8735ed1a99e46c2c89f8994aacdf4b1109bdcf682f1e5b34479c6e392669.png"
            val stream = context.assets.open(logoFile)
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        } catch (e: Exception) { null }
    }

    // Filter input: for 0-decimal tokens (e.g. SigRSV), reject dots/commas
    val filteredOnChange: (String) -> Unit = { newValue ->
        if (decimals == 0) {
            // Integer only — strip any decimal separators
            val cleaned = newValue.filter { it.isDigit() }
            onAmountChange(cleaned)
        } else {
            onAmountChange(newValue)
        }
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
                Text(label, color = ColorTextDim, fontSize = 10.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        BasicTextField(
                            value = amount,
                            onValueChange = filteredOnChange,
                            textStyle = TextStyle(
                                fontSize = 22.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            cursorBrush = SolidColor(Color.White),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = if (decimals == 0)
                                    androidx.compose.ui.text.input.KeyboardType.NumberPassword
                                else
                                    androidx.compose.ui.text.input.KeyboardType.Decimal
                            ),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (amount.isEmpty()) Text(
                                        "Enter amount",
                                        color = ColorInputHint, fontSize = 22.sp
                                    )
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
fun ProtocolStatusCard(
    eligibility: EligibilityResult?,
    bankMode: String = "mint"
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Determine if the current mode is blocked
    val isCurrentModeBlocked = eligibility != null && when (bankMode) {
        "redeem" -> !eligibility.canRedeem
        else -> !eligibility.canMint
    }

    // Auto-expand when blocked so user sees why
    LaunchedEffect(isCurrentModeBlocked) {
        if (isCurrentModeBlocked) isExpanded = true
    }

    val borderColor = when {
        isCurrentModeBlocked -> Color(0xFFFF6B00)
        eligibility?.canMint == true -> Color.White.copy(alpha = 0.05f)
        else -> Color.White.copy(alpha = 0.05f)
    }
    val bgColor = when {
        isCurrentModeBlocked -> Color(0xFF3A2A10)
        else -> ColorSelectionBg.copy(alpha = 0.8f)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 2.dp)
            .androidBorder(
                radius = 12.dp,
                borderWidth = if (isCurrentModeBlocked) 1.dp else 0.5.dp,
                borderColor = borderColor,
                bgColor = bgColor
            )
            .clickable { isExpanded = !isExpanded }
            .padding(15.dp)
    ) {
        // Header row with status indicator
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val indicatorColor = when {
                    eligibility == null -> ColorTextDim
                    isCurrentModeBlocked -> Color(0xFFFF6B00)
                    else -> Color(0xFF00D18B)
                }
                val indicatorIcon = when {
                    eligibility == null -> "\uE88E"
                    isCurrentModeBlocked -> "\uE002"  // warning
                    else -> "\uE876"  // check_circle
                }
                Text(
                    text = indicatorIcon,
                    fontFamily = MaterialDesignIcons,
                    fontSize = 16.sp,
                    color = indicatorColor
                )
                if (isCurrentModeBlocked) {
                    Text(
                        text = "${bankMode.uppercase()} BLOCKED — TAP FOR DETAILS",
                        color = Color(0xFFFFAA44),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text("PROTOCOL STATUS", color = ColorTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
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

            // Show blocked reason prominently at the top if current mode is blocked
            if (isCurrentModeBlocked && eligibility.reason != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .androidBorder(radius = 8.dp, borderWidth = 0.dp, bgColor = Color(0xFF3A1A1A))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(eligibility.reason, color = Color(0xFFFFAA44), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(8.dp))
            }

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
fun BankOrderDetailsPanel(
    quote: MintQuote? = null,
    redeemQuote: RedeemQuote? = null,
    bankMode: String = "mint",
    minerFee: Double = 0.0011,
    onMinerFeeChange: (Double) -> Unit = {}
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Unify fee display from whichever quote is active
    val feeBreakdown = if (bankMode == "redeem") redeemQuote?.feeBreakdown else quote?.feeBreakdown
    val miningFee = if (bankMode == "redeem") redeemQuote?.miningFee else quote?.miningFee
    val hasQuote = if (bankMode == "redeem") redeemQuote != null else quote != null

    val headerText = when {
        bankMode == "redeem" && redeemQuote != null -> {
            val ergReceived = (redeemQuote.ergReceived).toDouble() / 1_000_000_000.0
            "You receive: ${formatErgValue(ergReceived)} ERG"
        }
        bankMode == "mint" && quote != null -> {
            val totalErg = (quote.ergCost + quote.miningFee).toDouble() / 1_000_000_000.0
            "Total cost: ${formatErgValue(totalErg)} ERG"
        }
        else -> "ORDER DETAILS"
    }

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
            Text(
                text = headerText,
                color = if (hasQuote) Color.White else ColorTextDim,
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

        if (isExpanded && hasQuote) {
            Spacer(Modifier.height(10.dp))
            Divider(color = Color.White.copy(alpha = 0.05f), thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            feeBreakdown?.forEach { (label, nanoErg) ->
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

            if (miningFee != null) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Mining Fee", color = ColorTextDim, fontSize = 11.sp)
                    Text(
                        "${SwapViewModel.formatErg(minerFee)} ERG",
                        color = ColorAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = minerFee.toFloat(),
                    onValueChange = { onMinerFeeChange(it.toDouble()) },
                    valueRange = 0.0011f..0.2f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = ColorAccent,
                        activeTrackColor = ColorAccent,
                        inactiveTrackColor = ColorTextDim.copy(alpha = 0.3f)
                    )
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "Slow", color = ColorTextDim, fontSize = 9.sp)
                    Text(text = "Fast", color = ColorTextDim, fontSize = 9.sp)
                }
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
    bankMode: String = "mint",
    onMint: () -> Unit
) {
    val enabled = canMint && !isLoading && !isBuildingTx && !insufficientBalance
    val actionVerb = if (bankMode == "redeem") "REDEEM" else "MINT"
    val label = when {
        isBuildingTx -> "Building transaction…"
        isLoading -> "Loading…"
        insufficientBalance -> "INSUFFICIENT BALANCE"
        !canMint -> "NOT ELIGIBLE"
        else -> "$actionVerb $protocolName"
    }

    val buttonColor = Color(0xFF00D18B)  // Always green for both mint and redeem

    Button(
        onClick = onMint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .height(55.dp),
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) buttonColor else ColorSelectionBg,
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

// ─── Mode Toggle (Mint / Redeem) ────────────────────────────────────────────

/**
 * Segmented toggle for switching between Mint and Redeem modes.
 * Only shown when the active protocol supports redeem.
 */
@Composable
fun BankModeToggle(
    currentMode: String,
    onModeChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .androidBorder(
                radius = 12.dp,
                borderWidth = 0.5.dp,
                borderColor = Color.White.copy(alpha = 0.1f),
                bgColor = ColorSelectionBg.copy(alpha = 0.5f)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        listOf("mint" to "MINT", "redeem" to "REDEEM").forEach { (mode, label) ->
            val isActive = currentMode == mode
            val bgColor by animateColorAsState(
                targetValue = if (isActive) {
                    if (mode == "mint") Color(0xFF00D18B).copy(alpha = 0.25f)
                    else Color(0xFFFF6B6B).copy(alpha = 0.25f)
                } else Color.Transparent,
                animationSpec = tween(200)
            )
            val textColor = if (isActive) Color.White else ColorTextDim

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(bgColor, RoundedCornerShape(10.dp))
                    .clickable { onModeChange(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}
