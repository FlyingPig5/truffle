package com.piggytrade.piggytrade.ui.bank
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.stablecoin.StablecoinRegistry
import com.piggytrade.piggytrade.ui.swap.SwapState
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

/**
 * Main composable for the BANK tab.
 *
 * Layout:
 *  ┌─────────────────────────────────────┐
 *  │  [Scrollable content]               │
 *  │   - Protocol selector               │
 *  │   - YOU PAY panel                   │
 *  │   - arrow                           │
 *  │   - YOU RECEIVE panel               │
 *  │   - Protocol Status card            │
 *  │   - Order Details card              │
 *  │   - Error display                   │
 *  ├─────────────────────────────────────┤
 *  │  [MINT button — fixed at bottom]    │
 *  └─────────────────────────────────────┘
 */
@Composable
fun BankScreen(
    uiState: SwapState,
    viewModel: SwapViewModel,
    onSubmit: () -> Unit
) {
    val protocols = StablecoinRegistry.getAll()
    val activeProtocol = protocols.firstOrNull { it.id == uiState.activeProtocolId }
        ?: protocols.firstOrNull()

    val scrollState = rememberScrollState()

    // Column Scope content (since it's inside TradeCard)
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {

        // ── Scrollable content ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {

            // ── Experimental Warning Banner ──────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .androidBorder(radius = 10.dp, borderWidth = 1.dp, borderColor = Color(0xFFFF3B30), bgColor = Color(0xFF3A1010))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "\uE002",
                    color = Color(0xFFFF3B30),
                    fontSize = 18.sp,
                    fontFamily = MaterialDesignIcons
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Experimental — Please validate all output values before confirming.",
                    color = Color(0xFFFF6B60),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Protocol Selector
            if (protocols.size > 1) {
                ProtocolSelectorRow(
                    protocols = protocols,
                    activeProtocolId = uiState.activeProtocolId.ifEmpty { activeProtocol?.id ?: "" },
                    onSelect = { viewModel.setBankProtocol(it) }
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = activeProtocol?.displayName ?: "Bank",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Mint ${activeProtocol?.mintTokenName ?: ""}",
                        color = ColorTextDim,
                        fontSize = 11.sp
                    )
                }
            }

            // YOU MINT (user enters desired amount)
            val tokenName = activeProtocol?.mintTokenName ?: "TOKEN"
            val tokenId   = activeProtocol?.mintTokenId ?: ""
            val tokenBalance = if (tokenId.isNotEmpty()) {
                val rawAmount = uiState.walletTokens[tokenId] ?: 0L
                val dec = activeProtocol?.mintTokenDecimals ?: 3
                if (rawAmount > 0L) "%.${dec}f".format(rawAmount.toDouble() / Math.pow(10.0, dec.toDouble())) else ""
            } else ""

            BankReceivePanel(
                amount = uiState.bankAmount,
                tokenName = tokenName,
                tokenBalance = tokenBalance,
                onAmountChange = { viewModel.setBankAmount(it) }
            )

            // Order Details (collapsed by default — shows total cost + breakdown)
            BankOrderDetailsPanel(quote = uiState.bankQuote)

            // Protocol Status Card (collapsed by default, with colour indicator)
            ProtocolStatusCard(eligibility = uiState.bankEligibility)

            // Error display
            if (!uiState.bankError.isNullOrEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = Color(0xFF3A1A1A))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(uiState.bankError, color = Color(0xFFFF5252), fontSize = 11.sp)
                }
            }
        }

        // ── MINT button — pinned at bottom, above the nav bar ─────────────────
        val amount = uiState.bankAmount.toDoubleOrNull() ?: 0.0
        val requiredErg = (uiState.bankQuote?.ergCost ?: 0L) + (uiState.bankQuote?.miningFee ?: 0L)
        val insufficientErg = requiredErg > 0L && uiState.walletErgBalance < requiredErg.toDouble() / 1_000_000_000.0

        MintButton(
            protocolName = activeProtocol?.mintTokenName ?: "TOKEN",
            isLoading = uiState.isBankLoading,
            isBuildingTx = uiState.isBuildingTx,
            canMint = (uiState.bankEligibility?.canMint == true) && amount > 0.0,
            insufficientBalance = insufficientErg,
            onMint = { onSubmit() }
        )

        Spacer(Modifier.height(8.dp))
    }
}
