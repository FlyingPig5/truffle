package com.piggytrade.piggytrade.ui.swap
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.google.gson.GsonBuilder

@Composable
fun ReviewTxScreen(
    viewModel: SwapViewModel,
    onBack: () -> Unit,
    onConfirm: (password: String, onResult: (String?) -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val params = uiState.reviewParams

    var password by remember { mutableStateOf("") }
    var showSummary by remember { mutableStateOf(false) }
    var showJson by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var isSigning by remember { mutableStateOf(false) }
    
    val clipboardManager = LocalClipboardManager.current
    val context = androidx.compose.ui.platform.LocalContext.current
    
    val formattedJson = remember(uiState.unsignedTxJson) {
        try {
            val gson = GsonBuilder().setPrettyPrinting().create()
            val el = com.google.gson.JsonParser.parseString(uiState.unsignedTxJson)
            gson.toJson(el) ?: uiState.unsignedTxJson
        } catch (e: Exception) {
            uiState.unsignedTxJson
        }
    }

    if (params == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ColorAccent)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // Header
        TogaRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(
                icon = "\uEF7D", // BACK
                onClick = onBack,
                modifier = Modifier.size(36.dp),
                radius = 10.dp,
                bgColor = ColorBlue
            )
            Text(
                text = if (params.isSimulation) "Review TX (SIMULATION)" else "Review Transaction",
                color = ColorText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f)
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .androidBorder(radius = 30.dp, borderWidth = 0.dp, bgColor = ColorCard)
                .padding(20.dp)
        ) {
            item {
                Text("WHAT YOU RECEIVE:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${params.buyAmount} ${params.buyToken}",
                    color = ColorAccent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 15.dp)
                )

                Text("WHAT YOU PAY:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                val payAmtValue = params.payAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
                val totalFee = params.minerFee + params.serviceFee
                
                val displayText = if (params.payToken == "ERG") {
                    "${String.format("%.9f", payAmtValue + totalFee).trimEnd('0').trimEnd('.')} ERG"
                } else {
                    "${params.payAmount} ${params.payToken} + ${String.format("%.9f", totalFee).trimEnd('0').trimEnd('.')} ERG"
                }

                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )

                Text(
                    text = "Miner Fee: ${String.format("%.9f", params.minerFee).trimEnd('0').trimEnd('.')} + App Fee: ${String.format("%.9f", params.serviceFee).trimEnd('0').trimEnd('.')} ERG",
                    color = ColorTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transaction Details", color = ColorText, fontSize = 14.sp)
                    Switch(
                        checked = showSummary,
                        onCheckedChange = { showSummary = it }
                    )
                }

                if (uiState.debugMode) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Full JSON (Advanced)", color = ColorText, fontSize = 14.sp)
                        Switch(
                            checked = showJson,
                            onCheckedChange = { showJson = it }
                        )
                    }
                }
            }

            if (showSummary) {
                item {
                    val (inputs, outputs) = remember(uiState.preparedTxData) {
                        parsePreparedTxData(uiState.preparedTxData)
                    }
                    val walletAddrs = remember(uiState.walletAddresses, uiState.selectedAddress, uiState.changeAddress) {
                        val addrs = mutableSetOf<String>()
                        addrs.addAll(uiState.walletAddresses)
                        if (uiState.selectedAddress.isNotEmpty()) addrs.add(uiState.selectedAddress)
                        if (uiState.changeAddress.isNotEmpty()) addrs.add(uiState.changeAddress)
                        addrs
                    }
                    val contractAddresses = remember { com.piggytrade.piggytrade.protocol.NetworkConfig.KNOWN_PROTOCOLS }
                    val appFeeAddress = remember { viewModel.getNodeAuthLink() }

                    TransactionDetailsView(
                        inputs = inputs,
                        outputs = outputs,
                        walletAddresses = walletAddrs,
                        viewModel = viewModel,
                        contractAddresses = contractAddresses,
                        appFeeAddress = appFeeAddress
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            if (showJson) {
                item {
                    // Copy buttons for debug
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(formattedJson))
                                Toast.makeText(context, "Unsigned TX JSON copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ColorSelectionBg),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("📋 Copy Unsigned TX", color = Color.White, fontSize = 11.sp)
                        }

                        // Copy the raw request data (pre-signing input map)
                        if (uiState.preparedTxData != null) {
                            Button(
                                onClick = {
                                    try {
                                        val gson = GsonBuilder().setPrettyPrinting().create()
                                        val txDataJson = gson.toJson(uiState.preparedTxData)
                                        clipboardManager.setText(AnnotatedString(txDataJson))
                                        Toast.makeText(context, "TX Request Data copied", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Copy failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorSelectionBg),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("📋 Copy Request Data", color = Color.White, fontSize = 11.sp)
                            }
                        }
                    }

                    SelectionContainer {
                        Text(
                            text = formattedJson,
                            color = Color(0xFFAAAAAA),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ColorInputBg)
                                .padding(10.dp)
                        )
                    }
                }
            }

            if (errorText.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(errorText))
                                Toast.makeText(context, "Error message copied", Toast.LENGTH_SHORT).show()
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF440000)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = errorText,
                            color = ColorSent,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(15.dp)
                        )

                    }
                }
            }

            item {
                val walletData = viewModel.getWalletData(uiState.selectedWallet)
                val useBiometrics = walletData?.get("use_biometrics") as? Boolean ?: false

                if (!params.isErgopay && !useBiometrics) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Wallet Password", color = ColorTextDim) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorInputBg,
                            unfocusedContainerColor = ColorInputBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                } else {
                    Spacer(modifier = Modifier.height(20.dp))
                }

                if (isSigning) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(10.dp), color = ColorAccent)
                }

                Button(
                    onClick = {
                        val resultHandler: (String?) -> Unit = { err ->
                            isSigning = false
                            if (err == null) {
                                // Success - MainActivity handles transition back to main
                            } else {
                                errorText = err
                            }
                        }

                        if (!params.isErgopay && useBiometrics) {
                            val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                            if (fragmentActivity != null) {
                                val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                val prompt = androidx.biometric.BiometricPrompt(
                                    fragmentActivity,
                                    executor,
                                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                            isSigning = true
                                            errorText = ""
                                            viewModel.setBiometricVerified(true)
                                            onConfirm("", resultHandler)
                                        }
                                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                            errorText = "Biometric error: $errString"
                                        }
                                    }
                                )
                                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                    .setTitle("Sign Transaction")
                                    .setSubtitle("Confirm with biometrics")
                                    .setNegativeButtonText("Cancel")
                                    .build()
                                prompt.authenticate(promptInfo)
                            } else {
                                errorText = "Cannot show biometrics"
                            }
                        } else {
                            isSigning = true
                            errorText = ""
                            onConfirm(password, resultHandler)
                        }
                    },
                    enabled = !isSigning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(55.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (params.isErgopay) ColorBlue else Color(0xFF00D18B)
                    )
                ) {
                    Text(
                        text = if (params.isErgopay) {
                            if (params.isSimulation) "Simulate (ErgoPay)" else "Ergopay"
                        } else {
                            if (params.isSimulation) "Sign & Simulate" else "Confirm Swap"
                        },
                        color = ColorBg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    uiState.txSuccessData?.let { data ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = { viewModel.dismissTxSuccessDialog(); onBack() },
            containerColor = ColorCard,
            title = {
                Text(
                    if (data.isSimulation) "Simulation Successful" else "Transaction Submitted",
                    color = if (data.isSimulation) Color.Yellow else ColorAccent,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        if (data.isSimulation) "The transaction was validated by the node but NOT broadcast to the network. No funds were sent."
                        else "Your transaction has been sent to the network.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(15.dp))
                    Text("Transaction ID:", color = ColorTextDim, fontSize = 12.sp)
                    SelectionContainer {
                        Text(data.txId, color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }
                    if (!data.isSimulation) {
                        Spacer(Modifier.height(15.dp))
                        Text(
                            "View on Sigmaspace",
                            color = ColorBlue,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { uriHandler.openUri(data.sigmaspaceUrl) }
                        )
                    }
                    if (uiState.debugMode && data.signedTxJson.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "📋 Copy Signed TX JSON",
                            color = ColorTextDim,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable {
                                clipboardManager.setText(AnnotatedString(data.signedTxJson))
                                Toast.makeText(context, "Signed TX JSON copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.dismissTxSuccessDialog(); onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                ) {
                    Text("Done", color = ColorBg)
                }
            }
        )
    }
}

/**
 * Dynamically parses the prepared transaction data to show per-address net changes.
 * Labels addresses as: YOUR WALLET, CONTRACT, APP FEE, or EXTERNAL.
 */
@Composable
fun TransactionAddressBreakdown(
    txData: Map<String, Any>?,
    viewModel: SwapViewModel,
    serviceFee: Double
) {
    if (txData == null) {
        Text("No transaction data available", color = ColorTextDim, fontSize = 12.sp)
        return
    }
    
    val uiState by viewModel.uiState.collectAsState()
    
    // Decode the app fee address for labeling
    val appFeeAddress = remember { viewModel.getNodeAuthLink() }
    
    // Collect all user wallet addresses
    val userAddresses = remember(uiState.walletAddresses, uiState.selectedAddress) {
        val addrs = mutableSetOf<String>()
        addrs.addAll(uiState.walletAddresses)
        if (uiState.selectedAddress.isNotEmpty()) addrs.add(uiState.selectedAddress)
        if (uiState.changeAddress.isNotEmpty()) addrs.add(uiState.changeAddress)
        addrs
    }
    
    // Known contract addresses
    val knownProtocols = com.piggytrade.piggytrade.protocol.NetworkConfig.KNOWN_PROTOCOLS
    
    // Parse inputs and outputs
    @Suppress("UNCHECKED_CAST")
    val inputBoxes = txData["input_boxes"] as? List<Map<String, Any>> ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    val requests = txData["requests"] as? List<Map<String, Any>> ?: emptyList()

    
    data class AddressFlow(
        val address: String,
        var inputErg: Long = 0L,
        var outputErg: Long = 0L,
        val inputTokens: MutableMap<String, Long> = mutableMapOf(),
        val outputTokens: MutableMap<String, Long> = mutableMapOf()
    )
    
    val addressFlows = remember(txData) {
        val flows = mutableMapOf<String, AddressFlow>()
        
        // Process inputs (what each address is spending)
        for (box in inputBoxes) {
            val addr = box["address"] as? String ?: continue
            val value = (box["value"] as? Number)?.toLong() ?: 0L
            val flow = flows.getOrPut(addr) { AddressFlow(addr) }
            flow.inputErg += value
            @Suppress("UNCHECKED_CAST")
            val assets = box["assets"] as? List<Map<String, Any>> ?: emptyList()
            for (asset in assets) {
                val tid = asset["tokenId"] as? String ?: continue
                val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                flow.inputTokens[tid] = flow.inputTokens.getOrDefault(tid, 0L) + amt
            }
        }
        
        // Process outputs (what each address is receiving)
        for (req in requests) {
            val addr = req["address"] as? String ?: continue
            val value = (req["value"] as? Number)?.toLong() ?: 0L
            val flow = flows.getOrPut(addr) { AddressFlow(addr) }
            flow.outputErg += value
            @Suppress("UNCHECKED_CAST")
            val assets = req["assets"] as? List<Map<String, Any>> ?: emptyList()
            for (asset in assets) {
                val tid = asset["tokenId"] as? String ?: continue
                val amt = (asset["amount"] as? Number)?.toLong() ?: 0L
                flow.outputTokens[tid] = flow.outputTokens.getOrDefault(tid, 0L) + amt
            }
        }
        
        flows.values.toList()
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorInputBg, RoundedCornerShape(10.dp))
            .padding(15.dp)
    ) {
        // Merge all user wallet flows into a single combined entry
        val userFlows = addressFlows.filter { userAddresses.contains(it.address) }
        val nonUserFlows = addressFlows.filter { !userAddresses.contains(it.address) }
        
        data class CombinedFlow(
            val label: String,
            val labelColor: Color,
            val truncAddresses: List<String>,
            val netErg: Long,
            val netTokens: Map<String, Long>
        )
        
        val displayFlows = mutableListOf<CombinedFlow>()
        
        // Combine all user wallet flows into one
        if (userFlows.isNotEmpty()) {
            val totalNetErg = userFlows.sumOf { it.outputErg - it.inputErg }
            val combinedTokens = mutableMapOf<String, Long>()
            for (flow in userFlows) {
                val allTids = (flow.inputTokens.keys + flow.outputTokens.keys).distinct()
                for (tid in allTids) {
                    val net = (flow.outputTokens[tid] ?: 0L) - (flow.inputTokens[tid] ?: 0L)
                    combinedTokens[tid] = (combinedTokens[tid] ?: 0L) + net
                }
            }
            val walletLabel = if (userFlows.size > 1) "YOUR WALLET (Combined)" else "YOUR WALLET"
            val truncAddrs = userFlows.map { f ->
                if (f.address.length > 20) "${f.address.take(8)}...${f.address.takeLast(8)}" else f.address
            }
            displayFlows.add(CombinedFlow(walletLabel, ColorBlue, truncAddrs, totalNetErg, combinedTokens))
        }
        
        // Add non-user flows individually
        for (flow in nonUserFlows) {
            val protocolName = knownProtocols[flow.address]
            val isAppFee = flow.address == appFeeAddress
            val label = when {
                protocolName != null -> "CONTRACT — $protocolName"
                isAppFee -> "APP FEE"
                else -> "EXTERNAL"
            }
            val labelColor = when {
                protocolName != null -> ColorAccent
                isAppFee -> Color(0xFFFF9800)
                else -> ColorTextDim
            }
            val netErg = flow.outputErg - flow.inputErg
            val netTokens = mutableMapOf<String, Long>()
            val allTids = (flow.inputTokens.keys + flow.outputTokens.keys).distinct()
            for (tid in allTids) {
                val net = (flow.outputTokens[tid] ?: 0L) - (flow.inputTokens[tid] ?: 0L)
                if (net != 0L) netTokens[tid] = net
            }
            val truncAddr = if (flow.address.length > 20) "${flow.address.take(8)}...${flow.address.takeLast(8)}" else flow.address
            displayFlows.add(CombinedFlow(label, labelColor, listOf(truncAddr), netErg, netTokens))
        }
        
        // Sort: user wallet first, then contracts, then app fee, then external
        val sortedDisplayFlows = displayFlows.sortedBy { flow ->
            when {
                flow.labelColor == ColorBlue -> 0
                flow.label.startsWith("CONTRACT") -> 1
                flow.label == "APP FEE" -> 2
                else -> 3
            }
        }
        
        for ((index, flow) in sortedDisplayFlows.withIndex()) {
            if (index > 0) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 1.dp)
                Spacer(Modifier.height(8.dp))
            }
            
            // Label
            Text(flow.label, color = flow.labelColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            
            // Truncated address(es)
            for (addr in flow.truncAddresses) {
                Text(addr, color = ColorTextDim.copy(alpha = 0.6f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Net ERG change
            if (flow.netErg != 0L) {
                val ergVal = flow.netErg.toDouble() / 1_000_000_000.0
                val prefix = if (flow.netErg > 0) "+" else ""
                val ergColor = if (flow.netErg > 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                Text(
                    text = "${prefix}${SwapViewModel.formatErg(ergVal)} ERG",
                    color = ergColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Net token changes
            for ((tid, netToken) in flow.netTokens) {
                if (netToken != 0L) {
                    val tokenName = viewModel.getTokenName(tid)
                    val dec = viewModel.getTokenDecimals(tid)
                    val displayAmt = if (dec > 0) {
                        netToken.toDouble() / Math.pow(10.0, dec.toDouble())
                    } else netToken.toDouble()
                    
                    val prefix = if (netToken > 0) "+" else ""
                    val tokenColor = if (netToken > 0) Color(0xFF4CAF50) else Color(0xFFFF5252)
                    
                    val formatted = if (dec > 0) {
                        String.format("%.${dec}f", displayAmt).trimEnd('0').trimEnd('.')
                    } else {
                        netToken.toString()
                    }
                    
                    Text(
                        text = "$prefix$formatted $tokenName",
                        color = tokenColor,
                        fontSize = 12.sp
                    )
                }
            }
        }
        
    }
}
