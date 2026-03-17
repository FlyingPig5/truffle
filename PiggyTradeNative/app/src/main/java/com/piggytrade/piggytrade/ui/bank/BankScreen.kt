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
 *  │   - Mint / Redeem toggle            │
 *  │   - Amount input panel              │
 *  │   - Order Details card              │
 *  │   - Protocol Status card            │
 *  │   - Error display                   │
 *  ├─────────────────────────────────────┤
 *  │  [Action button — fixed at bottom]  │
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
    val isRedeemMode = uiState.bankMode == "redeem"

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {

        // ── Scrollable content ─────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {



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

            // Mode Toggle (Mint / Redeem) — only for protocols that support it
            if (activeProtocol?.supportsRedeem == true) {
                BankModeToggle(
                    currentMode = uiState.bankMode,
                    onModeChange = { viewModel.setBankMode(it) }
                )
            }

            // Amount input panel
            val tokenName = activeProtocol?.mintTokenName ?: "TOKEN"
            val tokenId   = activeProtocol?.mintTokenId ?: ""
            val tokenBalance = if (tokenId.isNotEmpty()) {
                val rawAmount = uiState.walletTokens[tokenId] ?: 0L
                val dec = activeProtocol?.mintTokenDecimals ?: 3
                if (rawAmount > 0L) "%.${dec}f".format(rawAmount.toDouble() / Math.pow(10.0, dec.toDouble())) else ""
            } else ""

            // In redeem mode: panel shows "YOU REDEEM" + token name; in mint mode: "YOU MINT"
            BankReceivePanel(
                amount = uiState.bankAmount,
                tokenName = tokenName,
                tokenBalance = tokenBalance,
                onAmountChange = { viewModel.setBankAmount(it) },
                label = if (isRedeemMode) "YOU REDEEM" else "YOU MINT",
                tokenId = tokenId,
                decimals = activeProtocol?.mintTokenDecimals ?: 2
            )

            // Order Details (collapsed by default — shows total cost/receive + breakdown)
            BankOrderDetailsPanel(
                quote = uiState.bankQuote,
                redeemQuote = uiState.bankRedeemQuote,
                bankMode = uiState.bankMode,
                minerFee = uiState.minerFee,
                onMinerFeeChange = { viewModel.setMinerFee(it) }
            )

            // Protocol Status Card (collapsed by default, with colour indicator)
            ProtocolStatusCard(
                eligibility = uiState.bankEligibility,
                bankMode = uiState.bankMode
            )

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

        // ── Action button — pinned at bottom ──────────────────────────────────
        val amount = uiState.bankAmount.toDoubleOrNull() ?: 0.0

        // Check if the current mode is eligible
        val eligibility = uiState.bankEligibility
        val isEligibleForMode = if (isRedeemMode) {
            eligibility?.canRedeem == true
        } else {
            eligibility?.canMint == true
        }

        val insufficientBalance = if (isRedeemMode) {
            // In redeem mode: check token balance
            val rawBalance = uiState.walletTokens[activeProtocol?.mintTokenId ?: ""] ?: 0L
            val dec = activeProtocol?.mintTokenDecimals ?: 0
            val humanBalance = rawBalance.toDouble() / Math.pow(10.0, dec.toDouble())
            amount > 0.0 && amount > humanBalance
        } else {
            // In mint mode: check ERG balance
            val requiredErg = (uiState.bankQuote?.ergCost ?: 0L) + (uiState.bankQuote?.miningFee ?: 0L)
            requiredErg > 0L && uiState.walletErgBalance < requiredErg.toDouble() / 1_000_000_000.0
        }


        MintButton(
            protocolName = activeProtocol?.mintTokenName ?: "TOKEN",
            isLoading = uiState.isBankLoading,
            isBuildingTx = uiState.isBuildingTx,
            canMint = isEligibleForMode && amount > 0.0,
            insufficientBalance = insufficientBalance,
            bankMode = uiState.bankMode,
            onMint = { onSubmit() }
        )

        Spacer(Modifier.height(8.dp))
    }
}

