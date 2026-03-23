package com.piggytrade.piggytrade.ui.wallet

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.*

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.blockchain.ErgoPayReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ErgoPayTxDetails(
    val inputBoxIds: List<String>,        // raw box IDs from reduced TX
    val inputs: List<ErgoPayOutput>,       // resolved input boxes (fetched from node)
    val outputs: List<ErgoPayOutput>
)

private data class ErgoPayOutput(
    val address: String,
    val valueNano: Long,
    val tokens: List<ErgoPayToken>
)

private data class ErgoPayToken(
    val tokenId: String,
    val amount: Long
)

private sealed class SignResult {
    data class Success(val txId: String) : SignResult()
    data class Failed(val error: String) : SignResult()
}

/**
 * Net-change summary computed from inputs AND outputs.
 * Positive netErg = you are receiving ERG; negative = you are sending ERG.
 * Same for tokens.
 */
private data class TxSummary(
    val netErg: Double,                      // + = receiving, - = sending
    val netTokens: Map<String, Long>,        // tokenName → net amount (+ receiving, - sending)
    val minerFee: Double,
    val externalAddresses: List<String>,      // non-wallet, non-miner output addresses
    val inputsResolved: Boolean              // whether we managed to fetch input data
)

/** Top-level screen state */
private enum class ScreenMode {
    LOADING,
    WALLET_PICKER,     // Connect wallet flow — pick wallet before fetching
    CONNECTED,         // Connect wallet succeeded
    TX_REVIEW,         // Transaction review (signing flow)
    SIGN_SUCCESS,
    SIGN_FAILED,
    ERROR
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErgoPayReviewScreen(
    viewModel: SwapViewModel,
    ergoPayUrl: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    val activity = context as? android.app.Activity

    // Finish the activity to return to the calling app (browser)
    val finishAndReturn = {
        viewModel.clearSendState()
        activity?.finish() ?: onBack()
    }

    // Screen state
    var screenMode by remember { mutableStateOf(ScreenMode.LOADING) }
    var errorMessage by remember { mutableStateOf("") }
    var ergoPayResult by remember { mutableStateOf<ErgoPayReceiver.ErgoPayResult?>(null) }
    var txDetails by remember { mutableStateOf<ErgoPayTxDetails?>(null) }
    var parseError by remember { mutableStateOf("") }
    var serverMessage by remember { mutableStateOf("") }

    // Signing state
    var password by remember { mutableStateOf("") }
    var isSigning by remember { mutableStateOf(false) }
    var signResult by remember { mutableStateOf<SignResult?>(null) }
    var showFullDetails by remember { mutableStateOf(false) }
    var showSourceDetails by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }

    // Wallet selector
    val signableWallets = remember { viewModel.getSignableWalletNames() }
    // For connect flow, show ALL wallets (including ErgoPay-only)
    val allWalletNames = remember {
        val wallets = viewModel.getSignableWalletNames().toMutableList()
        // Also include ErgoPay wallets for connect flow (address only)
        wallets
    }
    var selectedWalletName by remember { mutableStateOf(uiState.selectedWallet.replace(" (Ergopay)", "").trim()) }
    var walletDropdownExpanded by remember { mutableStateOf(false) }

    val walletData = viewModel.getWalletData(selectedWalletName)
    val useBiometrics = walletData?.get("use_biometrics") as? Boolean ?: false
    val walletAddress = walletData?.get("address") as? String ?: ""
    val hasWallet = selectedWalletName.isNotEmpty() && walletData != null

    // Detect if this is a connect-wallet request (needs address first)
    val isAddressRequest = remember(ergoPayUrl) {
        ErgoPayReceiver().isAddressRequest(ergoPayUrl)
    }

    // Compute net-change summary from inputs AND outputs
    val summary = remember(txDetails, walletAddress) {
        val details = txDetails ?: return@remember null

        // Sum up what the wallet is SPENDING (inputs from wallet)
        var inputErgFromWallet = 0L
        val inputTokensFromWallet = mutableMapOf<String, Long>()
        val inputsResolved = details.inputs.isNotEmpty()

        for (input in details.inputs) {
            if (input.address == walletAddress) {
                inputErgFromWallet += input.valueNano
                for (tok in input.tokens) {
                    inputTokensFromWallet[tok.tokenId] = (inputTokensFromWallet[tok.tokenId] ?: 0L) + tok.amount
                }
            }
        }

        // Sum up what the wallet is RECEIVING (outputs to wallet)
        var outputErgToWallet = 0L
        val outputTokensToWallet = mutableMapOf<String, Long>()
        var minerFee = 0.0
        val externalAddrs = mutableListOf<String>()

        for (output in details.outputs) {
            val isMinerFee = output.valueNano in 1_000_000L..2_000_000L && output.tokens.isEmpty()
            when {
                isMinerFee -> minerFee += output.valueNano / 1_000_000_000.0
                output.address == walletAddress -> {
                    outputErgToWallet += output.valueNano
                    for (tok in output.tokens) {
                        outputTokensToWallet[tok.tokenId] = (outputTokensToWallet[tok.tokenId] ?: 0L) + tok.amount
                    }
                }
                else -> {
                    if (output.address !in externalAddrs) externalAddrs.add(output.address)
                }
            }
        }

        // Net change = outputs_to_wallet - inputs_from_wallet
        // Positive = receiving, Negative = sending
        val netErg = (outputErgToWallet - inputErgFromWallet) / 1_000_000_000.0

        val allTokenIds = (inputTokensFromWallet.keys + outputTokensToWallet.keys).toSet()
        val netTokens = mutableMapOf<String, Long>()
        for (tokenId in allTokenIds) {
            val net = (outputTokensToWallet[tokenId] ?: 0L) - (inputTokensFromWallet[tokenId] ?: 0L)
            if (net != 0L) {
                val name = viewModel.getTokenName(tokenId).ifEmpty { "${tokenId.take(8)}..." }
                netTokens[name] = net
            }
        }

        TxSummary(
            netErg = netErg,
            netTokens = netTokens,
            minerFee = minerFee,
            externalAddresses = externalAddrs,
            inputsResolved = inputsResolved
        )
    }

    /** Fetch and process the ErgoPay URL */
    suspend fun fetchAndProcess(address: String) {
        if (isAddressRequest && address.isEmpty()) {
            errorMessage = "Please select a wallet first"
            screenMode = ScreenMode.ERROR
            return
        }
        try {
            val receiver = ErgoPayReceiver()
            val result = withContext(Dispatchers.IO) {
                receiver.parseErgoPayUrl(ergoPayUrl, address)
            }
            ergoPayResult = result
            serverMessage = result.message ?: ""

            if (result.isConnectRequest || result.reducedTxBase64 == null) {
                // Connect-only flow: no TX to sign, just connected
                screenMode = ScreenMode.CONNECTED
            } else {
                // TX signing flow: parse the TX details
                try {
                    val detailsJson = withContext(Dispatchers.IO) {
                        org.ergoplatform.wallet.jni.WalletLib.parseReducedTxBytes(result.reducedTxBase64)
                    }
                    val parsed = com.google.gson.Gson().fromJson(detailsJson, Map::class.java)
                    @Suppress("UNCHECKED_CAST")
                    val inputBoxIds = (parsed["inputs"] as? List<String>) ?: emptyList()
                    @Suppress("UNCHECKED_CAST")
                    val outputsList = (parsed["outputs"] as? List<Map<String, Any>>) ?: emptyList()
                    val outputs = outputsList.map { out ->
                        val addr = out["address"] as? String ?: "unknown"
                        val value = (out["value"] as? Number)?.toLong() ?: 0L
                        @Suppress("UNCHECKED_CAST")
                        val tokens = (out["tokens"] as? List<Map<String, Any>>)?.map { tok ->
                            ErgoPayToken(
                                tokenId = tok["tokenId"] as? String ?: "",
                                amount = (tok["amount"] as? Number)?.toLong() ?: 0L
                            )
                        } ?: emptyList()
                        ErgoPayOutput(address = addr, valueNano = value, tokens = tokens)
                    }

                    // Fetch input boxes from the node to get their values/tokens/addresses
                    val resolvedInputs = mutableListOf<ErgoPayOutput>()
                    for (boxId in inputBoxIds) {
                        try {
                            val boxData = withContext(Dispatchers.IO) {
                                viewModel.fetchBoxById(boxId)
                            }
                            if (boxData != null) {
                                val addr = boxData["address"] as? String ?: "unknown"
                                val value = (boxData["value"] as? Number)?.toLong() ?: 0L
                                @Suppress("UNCHECKED_CAST")
                                val assets = (boxData["assets"] as? List<Map<String, Any>>) ?: emptyList()
                                val tokens = assets.map { asset ->
                                    ErgoPayToken(
                                        tokenId = asset["tokenId"] as? String ?: "",
                                        amount = (asset["amount"] as? Number)?.toLong() ?: 0L
                                    )
                                }
                                resolvedInputs.add(ErgoPayOutput(address = addr, valueNano = value, tokens = tokens))
                            }
                        } catch (e: Exception) {
                            Log.w("ErgoPayReview", "Failed to fetch input box $boxId: ${e.message}")
                        }
                    }

                    txDetails = ErgoPayTxDetails(
                        inputBoxIds = inputBoxIds,
                        inputs = resolvedInputs,
                        outputs = outputs
                    )

                    // Auto-detect correct wallet from dApp-specified addresses or TX input/output addresses
                    val allWallets = viewModel.getSignableWalletNames()
                    var matched = false

                    // Priority 1: dApp specified which address to use (EIP-0020 address/addresses field)
                    if (result.addresses.isNotEmpty()) {
                        for (name in allWallets) {
                            val addr = viewModel.getWalletAddress(name)
                            if (addr.isNotEmpty() && addr in result.addresses) {
                                Log.d("ErgoPayReview", "Auto-switching to '$name' (dApp-specified address)")
                                selectedWalletName = name
                                matched = true
                                break
                            }
                        }
                    }

                    // Priority 2: match input addresses (the wallet being spent from)
                    if (!matched) {
                        val inputAddresses = resolvedInputs.map { it.address }.toSet()
                        for (name in allWallets) {
                            val addr = viewModel.getWalletAddress(name)
                            if (addr.isNotEmpty() && addr in inputAddresses && addr != walletAddress) {
                                Log.d("ErgoPayReview", "Auto-switching to '$name' (matches TX input)")
                                selectedWalletName = name
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("ErgoPayReview", "Failed to parse TX details", e)
                    parseError = "Could not parse transaction details: ${e.message}"
                }
                screenMode = ScreenMode.TX_REVIEW
            }
        } catch (e: Exception) {
            Log.e("ErgoPayReview", "Failed to fetch ErgoPay data", e)
            errorMessage = "Failed to load ErgoPay request:\n${e.message}"
            screenMode = ScreenMode.ERROR
        }
    }

    // On launch: if NOT an address request, fetch immediately. Otherwise show wallet picker.
    LaunchedEffect(ergoPayUrl) {
        if (ergoPayUrl.isEmpty()) {
            // URL not yet set (race with ViewModel state) — stay on LOADING
            return@LaunchedEffect
        }
        if (isAddressRequest) {
            // Need wallet selection first
            screenMode = ScreenMode.WALLET_PICKER
        } else {
            // Direct fetch (opaque or URL without address placeholder)
            fetchAndProcess(walletAddress)
        }
    }

    // Handle signResult changes
    LaunchedEffect(signResult) {
        when (signResult) {
            is SignResult.Success -> screenMode = ScreenMode.SIGN_SUCCESS
            is SignResult.Failed -> screenMode = ScreenMode.SIGN_FAILED
            null -> {}
        }
    }

    // Coroutine scope for connect button
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxSize().background(ColorBg)
    ) {
        // ── Header ──
        TogaRow(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(
                icon = "\uEF7D",
                onClick = { viewModel.clearSendState(); onBack() },
                modifier = Modifier.size(36.dp), radius = 10.dp, bgColor = ColorBlue
            )
            Text(
                text = if (isAddressRequest && screenMode != ScreenMode.TX_REVIEW) "ErgoPay Connect" else "ErgoPay Request",
                color = ColorText, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 10.dp).weight(1f)
            )
        }

        // ── Content ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .androidBorder(radius = 30.dp, borderWidth = 0.dp, bgColor = ColorCard)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = when (screenMode) {
                ScreenMode.LOADING, ScreenMode.CONNECTED, ScreenMode.SIGN_SUCCESS,
                ScreenMode.SIGN_FAILED, ScreenMode.ERROR -> Arrangement.Center
                else -> Arrangement.Top
            },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (screenMode) {

                // ══════════════════════════════
                // ── LOADING ──
                // ══════════════════════════════
                ScreenMode.LOADING -> {
                    CircularProgressIndicator(color = ColorAccent)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading ErgoPay request...", color = ColorTextDim, fontSize = 14.sp)
                }

                // ══════════════════════════════
                // ── ERROR ──
                // ══════════════════════════════
                ScreenMode.ERROR -> {
                    Text("⚠", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("ErgoPay Error", color = ColorSent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF440000)),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text(errorMessage, color = ColorSent, fontSize = 12.sp, modifier = Modifier.padding(15.dp)) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { finishAndReturn() },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Return to App", color = Color.White, fontWeight = FontWeight.Bold) }
                }

                // ══════════════════════════════════════════
                // ── WALLET PICKER (connect flow) ──
                // ══════════════════════════════════════════
                ScreenMode.WALLET_PICKER -> {
                    Text("🔗", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Connect Wallet", color = ColorAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "A website is requesting to connect with your wallet address.",
                        color = Color.White, fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Show domain
                    val domain = try {
                        java.net.URL(ergoPayUrl.replace("ergopay://", "https://")).host
                    } catch (_: Exception) { "Unknown" }
                    Text("From: $domain", color = ColorBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                    Spacer(modifier = Modifier.height(20.dp))

                    // Wallet selector
                    Text(
                        "SELECT WALLET TO CONNECT", color = ColorTextDim, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
                    )

                    if (signableWallets.isEmpty()) {
                        Text("No wallets found", color = ColorSent, fontSize = 13.sp)
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = walletDropdownExpanded,
                            onExpandedChange = { walletDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedWalletName, onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ColorInputBg, unfocusedContainerColor = ColorInputBg,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            ExposedDropdownMenu(
                                expanded = walletDropdownExpanded,
                                onDismissRequest = { walletDropdownExpanded = false },
                                modifier = Modifier.background(ColorInputBg)
                            ) {
                                signableWallets.forEach { name ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(name, color = Color.White, fontSize = 14.sp)
                                                val addr = viewModel.getWalletAddress(name)
                                                if (addr.isNotEmpty()) {
                                                    Text("${addr.take(8)}...${addr.takeLast(6)}", color = ColorTextDim,
                                                        fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        },
                                        onClick = { selectedWalletName = name; walletDropdownExpanded = false }
                                    )
                                }
                            }
                        }
                        if (walletAddress.isNotEmpty()) {
                            Text(
                                "${walletAddress.take(10)}...${walletAddress.takeLast(8)}",
                                color = ColorTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2E00)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "⚠ Only connect to websites you trust. This will share your wallet address.",
                            color = ColorOrange, fontSize = 11.sp, modifier = Modifier.padding(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isConnecting) {
                        CircularProgressIndicator(color = ColorAccent)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Connecting...", color = ColorTextDim, fontSize = 12.sp)
                    }

                    // Connect button
                    Button(
                        onClick = {
                            isConnecting = true
                            scope.launch {
                                fetchAndProcess(walletAddress)
                                isConnecting = false
                            }
                        },
                        enabled = hasWallet && !isConnecting,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ColorBlue,
                            disabledContainerColor = ColorBlue.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            if (isConnecting) "Connecting..." else "Connect Wallet",
                            color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(onClick = { viewModel.clearSendState(); onBack() }) {
                        Text("Cancel", color = ColorTextDim)
                    }
                }

                // ══════════════════════════════════════
                // ── CONNECTED (wallet connected ok) ──
                // ══════════════════════════════════════
                ScreenMode.CONNECTED -> {
                    Text("✅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Wallet Connected", color = ColorAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Your wallet address has been shared with the website.",
                        color = Color.White, fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Wallet:", color = ColorTextDim, fontSize = 11.sp)
                            Text(selectedWalletName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Address:", color = ColorTextDim, fontSize = 11.sp)
                            SelectionContainer {
                                Text(walletAddress, color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    if (serverMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(serverMessage, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You can now return to the website. Any future signing requests will appear in this app.",
                        color = ColorTextDim, fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { finishAndReturn() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                    ) {
                        Text("Done", color = ColorBg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // ══════════════════════════════
                // ── SIGN SUCCESS ──
                // ══════════════════════════════
                ScreenMode.SIGN_SUCCESS -> {
                    val txId = (signResult as? SignResult.Success)?.txId ?: ""

                    Text("✅", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Transaction Submitted", color = ColorAccent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Your ErgoPay transaction has been signed and broadcast.", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboardManager.setText(AnnotatedString(txId))
                            Toast.makeText(context, "TX ID copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("Transaction ID:", color = ColorTextDim, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            SelectionContainer { Text(txId, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap to copy", color = ColorTextDim, fontSize = 9.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { uriHandler.openUri("https://sigmaspace.io/tx/$txId") }) {
                        Text("View on Sigmaspace ↗", color = ColorBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.dismissTxSuccessDialog(); finishAndReturn() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                    ) { Text("Close & Return", color = ColorBg, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }

                // ══════════════════════════════
                // ── SIGN FAILED ──
                // ══════════════════════════════
                ScreenMode.SIGN_FAILED -> {
                    val err = (signResult as? SignResult.Failed)?.error ?: ""

                    Text("❌", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Signing Failed", color = ColorSent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            clipboardManager.setText(AnnotatedString(err))
                            Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
                        },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF440000)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(err, color = ColorSent, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Tap to copy", color = ColorTextDim, fontSize = 9.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { signResult = null; password = ""; screenMode = ScreenMode.TX_REVIEW },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ColorBlue)
                    ) { Text("Try With Different Wallet", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { finishAndReturn() },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Return to App", color = ColorTextDim, fontSize = 14.sp) }
                }

                // ══════════════════════════════════════
                // ── TX REVIEW (signing flow) ──
                // ══════════════════════════════════════
                ScreenMode.TX_REVIEW -> {
                    val result = ergoPayResult ?: return@Column

                    // Warning
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF3D2E00)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "⚠ External transaction — only sign if you trust the source.",
                            color = ColorOrange, fontSize = 12.sp, modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // Server message
                    if (serverMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(serverMessage, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(12.dp)) }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // ═══ NET CHANGE SUMMARY ═══
                    if (summary != null) {
                        if (!summary.inputsResolved) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF442200)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("⚠ Could not fetch input boxes — showing output-only view",
                                    color = ColorOrange, fontSize = 11.sp, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Separate sending (negative net) and receiving (positive net)
                        val sendingTokens = summary.netTokens.filter { it.value < 0 }
                        val receivingTokens = summary.netTokens.filter { it.value > 0 }
                        val isSendingErg = summary.netErg < -0.000001
                        val isReceivingErg = summary.netErg > 0.000001

                        // ─── WHAT YOU SEND ───
                        Text("WHAT YOU SEND:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (isSendingErg || sendingTokens.isNotEmpty() || summary.minerFee > 0) {
                            // Show tokens being sent
                            sendingTokens.forEach { (name, amount) ->
                                Text("${kotlin.math.abs(amount)} $name", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            // Show ERG being sent (absolute value) 
                            if (isSendingErg) {
                                val absErg = kotlin.math.abs(summary.netErg)
                                if (sendingTokens.isNotEmpty()) {
                                    // ERG shown smaller when tokens are the main item
                                    Text("+ ${String.format("%.9f", absErg).trimEnd('0').trimEnd('.')} ERG", color = Color.White, fontSize = 14.sp)
                                } else {
                                    Text("${String.format("%.9f", absErg).trimEnd('0').trimEnd('.')} ERG", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                }
                                if (summary.minerFee > 0) {
                                    Text("(incl. miner fee: ${String.format("%.9f", summary.minerFee).trimEnd('0').trimEnd('.')} ERG)", color = ColorTextDim, fontSize = 11.sp)
                                }
                            } else if (summary.minerFee > 0 && sendingTokens.isEmpty()) {
                                // Only the miner fee is being sent — show it as the main item
                                Text("${String.format("%.9f", summary.minerFee).trimEnd('0').trimEnd('.')} ERG (miner fee)", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            } else if (summary.minerFee > 0) {
                                // Sending tokens but no extra ERG — just show miner fee note
                                Text("+ ${String.format("%.9f", summary.minerFee).trimEnd('0').trimEnd('.')} ERG (miner fee)", color = Color.White, fontSize = 14.sp)
                            }
                        } else {
                            Text("Nothing", color = ColorTextDim, fontSize = 16.sp)
                        }

                        // Show destination addresses
                        if (summary.externalAddresses.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("TO:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            summary.externalAddresses.forEach { addr ->
                                SelectionContainer {
                                    Text("${addr.take(12)}...${addr.takeLast(8)}", color = ColorBlue,
                                        fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ─── WHAT YOU RECEIVE ───
                        Text("WHAT YOU RECEIVE:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (isReceivingErg || receivingTokens.isNotEmpty()) {
                            receivingTokens.forEach { (name, amount) ->
                                Text("$amount $name", color = ColorAccent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            if (isReceivingErg) {
                                val label = if (receivingTokens.isEmpty()) "" else " (change)"
                                Text("${String.format("%.9f", summary.netErg).trimEnd('0').trimEnd('.')} ERG$label",
                                    color = ColorAccent,
                                    fontSize = if (receivingTokens.isEmpty()) 22.sp else 13.sp,
                                    fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Text("Nothing", color = ColorTextDim, fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    } else if (parseError.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF442200)),
                            shape = RoundedCornerShape(10.dp)
                        ) { Text(parseError, color = ColorOrange, fontSize = 11.sp, modifier = Modifier.padding(12.dp)) }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Transaction Details toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Transaction Details", color = ColorText, fontSize = 14.sp)
                        Switch(checked = showFullDetails, onCheckedChange = { showFullDetails = it })
                    }
                    if (showFullDetails && txDetails != null) {
                        val inputs = txDetails!!.inputs.map { inp ->
                            TxDetailBox(address = inp.address, valueNano = inp.valueNano,
                                tokens = inp.tokens.map { TxDetailToken(it.tokenId, it.amount) })
                        }
                        val outputs = txDetails!!.outputs.map { out ->
                            TxDetailBox(address = out.address, valueNano = out.valueNano,
                                tokens = out.tokens.map { TxDetailToken(it.tokenId, it.amount) })
                        }
                        TransactionDetailsView(
                            inputs = inputs,
                            outputs = outputs,
                            walletAddresses = setOf(walletAddress),
                            viewModel = viewModel,
                            unresolvedInputIds = if (txDetails!!.inputs.isEmpty()) txDetails!!.inputBoxIds else emptyList()
                        )
                    }

                    // Raw data toggle (advanced)
                    if (ergoPayResult?.reducedTxBase64 != null) {
                        var showRawData by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Raw Data (Advanced)", color = ColorText, fontSize = 14.sp)
                            Switch(checked = showRawData, onCheckedChange = { showRawData = it })
                        }
                        if (showRawData) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(ergoPayResult!!.reducedTxBase64!!))
                                        Toast.makeText(context, "Reduced TX copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = ColorSelectionBg),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("📋 Copy Reduced TX", color = Color.White, fontSize = 11.sp)
                                }
                            }
                            SelectionContainer {
                                Text(
                                    text = ergoPayResult!!.reducedTxBase64!!,
                                    color = Color(0xFFAAAAAA),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(ColorInputBg, RoundedCornerShape(8.dp))
                                        .padding(10.dp)
                                )
                            }
                        }
                    }

                    // Source Details
                    Text(
                        if (showSourceDetails) "▼ Source Details" else "▶ Source Details",
                        color = ColorBlue, fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth().clickable { showSourceDetails = !showSourceDetails }.padding(vertical = 4.dp)
                    )
                    if (showSourceDetails) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Source:", color = ColorTextDim, fontSize = 10.sp)
                                SelectionContainer {
                                    Text(
                                        if (result.isUrlBased) result.originalUrl.replace("ergopay://", "https://") else "Direct payload",
                                        color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Wallet Selector
                    Text("SIGN WITH WALLET", color = ColorTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                    if (signableWallets.isEmpty()) {
                        Text("No signable wallets found", color = ColorSent, fontSize = 13.sp)
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = walletDropdownExpanded,
                            onExpandedChange = { walletDropdownExpanded = it },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = selectedWalletName, onValueChange = {}, readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletDropdownExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = ColorInputBg, unfocusedContainerColor = ColorInputBg,
                                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
                            )
                            ExposedDropdownMenu(
                                expanded = walletDropdownExpanded,
                                onDismissRequest = { walletDropdownExpanded = false },
                                modifier = Modifier.background(ColorInputBg)
                            ) {
                                signableWallets.forEach { name ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(name, color = Color.White, fontSize = 14.sp)
                                                val addr = viewModel.getWalletAddress(name)
                                                if (addr.isNotEmpty()) Text("${addr.take(8)}...${addr.takeLast(6)}",
                                                    color = ColorTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        },
                                        onClick = { selectedWalletName = name; walletDropdownExpanded = false; password = "" }
                                    )
                                }
                            }
                        }
                        if (walletAddress.isNotEmpty()) {
                            Text("${walletAddress.take(10)}...${walletAddress.takeLast(8)}",
                                color = ColorTextDim, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Password (non-biometric)
                    if (hasWallet && !useBiometrics) {
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            placeholder = { Text("Wallet Password", color = ColorTextDim) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = ColorInputBg, unfocusedContainerColor = ColorInputBg,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White
                            )
                        )
                    }

                    if (isSigning) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(modifier = Modifier.padding(10.dp), color = ColorAccent)
                        Text("Signing and submitting...", color = ColorTextDim, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(8.dp))

                    // Sign button
                    Button(
                        onClick = {
                            val doSign = {
                                isSigning = true
                                viewModel.signAndBroadcastErgoPay(
                                    reducedTxBase64 = result.reducedTxBase64 ?: "",
                                    password = password, context = context,
                                    signingWallet = selectedWalletName,
                                    onSuccess = { txId ->
                                        isSigning = false
                                        signResult = SignResult.Success(txId)
                                        // EIP-0020: send reply to dApp
                                        result.replyToUrl?.let { replyUrl ->
                                            scope.launch { ErgoPayReceiver().sendReplyToDApp(replyUrl, txId) }
                                        }
                                    },
                                    onError = { err -> isSigning = false; signResult = SignResult.Failed(err) }
                                )
                            }
                            if (useBiometrics) {
                                val fragmentActivity = context as? androidx.fragment.app.FragmentActivity
                                if (fragmentActivity != null) {
                                    val executor = androidx.core.content.ContextCompat.getMainExecutor(context)
                                    val prompt = androidx.biometric.BiometricPrompt(
                                        fragmentActivity, executor,
                                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                                            override fun onAuthenticationSucceeded(authResult: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                                viewModel.setBiometricVerified(true); doSign()
                                            }
                                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                                signResult = SignResult.Failed("Biometric error: $errString")
                                            }
                                        }
                                    )
                                    prompt.authenticate(
                                        androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                                            .setTitle("Sign ErgoPay Transaction").setSubtitle("Confirm with biometrics")
                                            .setNegativeButtonText("Cancel").build()
                                    )
                                }
                            } else { doSign() }
                        },
                        enabled = hasWallet && !isSigning,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF00D18B), disabledContainerColor = Color(0xFF00D18B).copy(alpha = 0.3f)
                        )
                    ) { Text(if (isSigning) "Signing..." else "Sign & Submit", color = ColorBg, fontSize = 16.sp, fontWeight = FontWeight.Bold) }

                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { viewModel.clearSendState(); onBack() }) { Text("Cancel", color = ColorTextDim) }
                }
            }
        }
    }
}
