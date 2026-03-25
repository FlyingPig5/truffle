package com.piggytrade.piggytrade.ui.wallet

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.*

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Send transaction review screen.
 * Shows: net outgoing assets, recipient addresses, expandable TX details, then auth + confirm.
 * Follows the same pattern as ReviewTxScreen (no auto-biometric — user must tap Confirm first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendReviewScreen(
    viewModel: SwapViewModel,
    onBack: () -> Unit,
    onConfirm: (password: String, onResult: (String?) -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val review = uiState.sendReviewParams ?: return

    var password by remember { mutableStateOf("") }
    var isSigning by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var showDetails by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val walletData = viewModel.getWalletData(uiState.selectedWallet)
    val useBiometrics = walletData?.get("use_biometrics") as? Boolean ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // ── Header ──
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
                text = "Review Send",
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
            // ── NET OUTGOING ──
            item {
                Text("YOU ARE SENDING:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                // ERG total (to recipients only, fee shown separately)
                Text(
                    text = "${SwapViewModel.formatErg(review.totalErgOut)} ERG",
                    color = ColorSent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                // Tokens being sent
                for ((tokenId, amount) in review.totalTokensOut) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TokenImage(tokenId = tokenId, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${viewModel.formatBalance(tokenId, amount)} ${viewModel.getTokenName(tokenId)}",
                            color = ColorSent,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Miner fee
                Text(
                    text = "Miner Fee: ${SwapViewModel.formatErg(review.minerFee)} ERG",
                    color = ColorTextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )
            }

            // ── RECIPIENT ADDRESSES ──
            item {
                Text("TO:", color = ColorTextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
            }

            for ((i, r) in review.recipients.withIndex()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = ColorInputBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            // Address — show full, truncated
                            val displayAddr = if (r.address.length > 20)
                                "${r.address.take(10)}...${r.address.takeLast(10)}"
                            else r.address

                            Text(
                                text = displayAddr,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // ERG amount
                            Text(
                                text = "${r.ergAmount} ERG",
                                color = ColorSent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Tokens for this recipient
                            for (t in r.tokens) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    TokenImage(tokenId = t.tokenId, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${t.amount} ${viewModel.getTokenName(t.tokenId)}",
                                        color = ColorSent,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── EXPANDABLE TRANSACTION DETAILS (same pattern as dex ReviewTxScreen) ──
            item {
                Spacer(modifier = Modifier.height(15.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Transaction Details", color = ColorText, fontSize = 14.sp)
                    Switch(
                        checked = showDetails,
                        onCheckedChange = { showDetails = it }
                    )
                }
            }

            if (showDetails) {
                item {
                    val (inputs, outputs) = remember(uiState.sendPreparedTxData) {
                        parsePreparedTxData(uiState.sendPreparedTxData)
                    }
                    val walletAddrs = remember(uiState.walletAddresses, uiState.selectedAddress, uiState.changeAddress) {
                        val addrs = mutableSetOf<String>()
                        addrs.addAll(uiState.walletAddresses)
                        if (uiState.selectedAddress.isNotEmpty()) addrs.add(uiState.selectedAddress)
                        if (uiState.changeAddress.isNotEmpty()) addrs.add(uiState.changeAddress)
                        addrs
                    }

                    TransactionDetailsView(
                        inputs = inputs,
                        outputs = outputs,
                        walletAddresses = walletAddrs,
                        viewModel = viewModel
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }

            // ── ERROR ──
            if (errorText.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .clickable {
                                clipboardManager.setText(AnnotatedString(errorText))
                                Toast.makeText(context, "Error copied", Toast.LENGTH_SHORT).show()
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

            // ── PASSWORD + CONFIRM BUTTON ──
            item {
                if (!review.isErgopay && !useBiometrics) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Wallet Password", color = ColorTextDim) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 15.dp),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(10.dp),
                        color = ColorAccent
                    )
                }

                Button(
                    onClick = {
                        val resultHandler: (String?) -> Unit = { err ->
                            isSigning = false
                            if (err != null) {
                                errorText = err
                            }
                        }

                        if (!review.isErgopay && useBiometrics) {
                            // Biometric: triggered by button click, NOT auto
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
                                    .setTitle("Sign Send Transaction")
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
                        containerColor = if (review.isErgopay) ColorBlue else Color(0xFF00D18B)
                    )
                ) {
                    Text(
                        text = if (review.isErgopay) "Open in ErgoPay" else "Confirm & Send",
                        color = ColorBg,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // ── Success Dialog ──
    uiState.txSuccessData?.let { data ->
        val uriHandler = LocalUriHandler.current
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissTxSuccessDialog()
                viewModel.clearSendState()
                onBack()
            },
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
                    if (data.isSimulation) {
                        Text(
                            "The transaction was validated by the node but NOT broadcast to the network. No funds were sent.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            "Your transaction has been sent to the network.",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
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
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissTxSuccessDialog()
                        viewModel.clearSendState()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                ) {
                    Text("Done", color = ColorBg)
                }
            }
        )
    }
}
