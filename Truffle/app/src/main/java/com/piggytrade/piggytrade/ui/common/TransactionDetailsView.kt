package com.piggytrade.piggytrade.ui.common

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified data model for a transaction box (input or output).
 * Works with both ErgoPay reduced TX data and regular prepared TX data.
 */
data class TxDetailBox(
    val address: String,
    val valueNano: Long,
    val tokens: List<TxDetailToken>
)

data class TxDetailToken(
    val tokenId: String,
    val amount: Long
)

/**
 * Unified transaction details view.
 * Shows inputs and outputs as individual cards with labels:
 * YOUR WALLET, CONTRACT, APP FEE, MINER FEE, or EXTERNAL.
 *
 * Used across: ReviewTxScreen, SendReviewScreen, ErgoPayReviewScreen.
 */
@Composable
fun TransactionDetailsView(
    inputs: List<TxDetailBox>,
    outputs: List<TxDetailBox>,
    walletAddresses: Set<String>,
    viewModel: SwapViewModel,
    contractAddresses: Map<String, String> = emptyMap(), // address → protocol name
    appFeeAddress: String = "",
    unresolvedInputIds: List<String> = emptyList() // fallback box IDs if inputs couldn't be resolved
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ─── INPUTS ───
        if (inputs.isNotEmpty()) {
            Text("INPUTS", color = ColorTextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp))
            inputs.forEachIndexed { idx, input ->
                TxDetailBoxCard(
                    label = "Input ${idx + 1}",
                    directionLabel = "From:",
                    box = input,
                    walletAddresses = walletAddresses,
                    viewModel = viewModel,
                    contractAddresses = contractAddresses,
                    appFeeAddress = appFeeAddress
                )
            }
        } else if (unresolvedInputIds.isNotEmpty()) {
            Text("INPUTS (box IDs only — could not resolve)", color = ColorTextDim,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
            unresolvedInputIds.forEach { boxId ->
                SelectionContainer {
                    Text(boxId, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
        }

        if (inputs.isNotEmpty() || unresolvedInputIds.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // ─── OUTPUTS ───
        Text("OUTPUTS", color = ColorTextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp))
        outputs.forEachIndexed { idx, output ->
            TxDetailBoxCard(
                label = "Output ${idx + 1}",
                directionLabel = "To:",
                box = output,
                walletAddresses = walletAddresses,
                viewModel = viewModel,
                contractAddresses = contractAddresses,
                appFeeAddress = appFeeAddress
            )
        }
    }
}

/**
 * Individual card for an input or output box.
 */
@Composable
private fun TxDetailBoxCard(
    label: String,
    directionLabel: String,
    box: TxDetailBox,
    walletAddresses: Set<String>,
    viewModel: SwapViewModel,
    contractAddresses: Map<String, String>,
    appFeeAddress: String
) {
    val isWallet = box.address in walletAddresses
    val isMinerFee = box.valueNano in 1_000_000L..2_000_000L && box.tokens.isEmpty() && !isWallet
    val protocolName = contractAddresses[box.address]
    val isAppFee = box.address == appFeeAddress && appFeeAddress.isNotEmpty()

    val typeLabel: String
    val typeColor: Color
    when {
        isMinerFee -> { typeLabel = "MINER FEE"; typeColor = ColorOrange }
        isWallet -> { typeLabel = "YOUR WALLET"; typeColor = ColorAccent }
        protocolName != null -> { typeLabel = "CONTRACT — $protocolName"; typeColor = ColorAccent }
        isAppFee -> { typeLabel = "APP FEE"; typeColor = Color(0xFFFF9800) }
        else -> { typeLabel = "EXTERNAL"; typeColor = ColorBlue }
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = ColorTextDim, fontSize = 10.sp)
                Text(typeLabel, color = typeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Text(directionLabel, color = ColorTextDim, fontSize = 10.sp)
            SelectionContainer {
                Text(
                    box.address,
                    color = if (isWallet) ColorAccent else Color.White,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Σ ${String.format("%.9f", box.valueNano / 1_000_000_000.0).trimEnd('0').trimEnd('.')} ERG",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            box.tokens.forEach { tok ->
                val name = viewModel.getTokenName(tok.tokenId).ifEmpty { "${tok.tokenId.take(8)}..." }
                val dec = viewModel.getTokenDecimals(tok.tokenId)
                val displayAmt = if (dec > 0) {
                    val dbl = tok.amount.toDouble() / Math.pow(10.0, dec.toDouble())
                    String.format("%.${dec}f", dbl).trimEnd('0').trimEnd('.')
                } else {
                    tok.amount.toString()
                }
                Text("+ $displayAmt $name", color = ColorAccent, fontSize = 11.sp)
            }
        }
    }
}

/**
 * Parse standard prepared TX data (Map-based) into TxDetailBox lists.
 * Works with both swap and send transaction data.
 */
fun parsePreparedTxData(txData: Map<String, Any>?): Pair<List<TxDetailBox>, List<TxDetailBox>> {
    if (txData == null) return Pair(emptyList(), emptyList())

    @Suppress("UNCHECKED_CAST")
    val inputBoxes = txData["input_boxes"] as? List<Map<String, Any>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val requests = txData["requests"] as? List<Map<String, Any>> ?: emptyList()

    val inputs = inputBoxes.map { box ->
        val addr = box["address"] as? String ?: "unknown"
        val value = (box["value"] as? Number)?.toLong() ?: 0L
        @Suppress("UNCHECKED_CAST")
        val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
        val tokens = assets.map { asset ->
            TxDetailToken(
                tokenId = asset["tokenId"] as? String ?: "",
                amount = (asset["amount"] as? Number)?.toLong() ?: 0L
            )
        }
        TxDetailBox(address = addr, valueNano = value, tokens = tokens)
    }

    val outputs = requests.map { req ->
        val addr = req["address"] as? String ?: "unknown"
        val value = (req["value"] as? Number)?.toLong() ?: 0L
        @Suppress("UNCHECKED_CAST")
        val assets = req["assets"] as? List<Map<String, Any>> ?: emptyList()
        val tokens = assets.map { asset ->
            TxDetailToken(
                tokenId = asset["tokenId"] as? String ?: "",
                amount = (asset["amount"] as? Number)?.toLong() ?: 0L
            )
        }
        TxDetailBox(address = addr, valueNano = value, tokens = tokens)
    }

    return Pair(inputs, outputs)
}
