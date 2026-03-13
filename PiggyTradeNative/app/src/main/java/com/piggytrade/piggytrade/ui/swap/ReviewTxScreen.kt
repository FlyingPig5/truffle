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
                    "${String.format("%.5f", payAmtValue + totalFee)} ERG"
                } else {
                    "${params.payAmount} ${params.payToken} + ${String.format("%.5f", totalFee)} ERG"
                }

                Text(
                    text = displayText,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 5.dp)
                )

                Text(
                    text = "Miner Fee: ${String.format("%.5f", params.minerFee)} + App Fee: ${String.format("%.5f", params.serviceFee)} ERG",
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ColorInputBg, RoundedCornerShape(10.dp))
                            .padding(15.dp)
                    ) {
                        Text("CONTRACT (AMM POOL)", color = ColorAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Sending: ${params.buyAmount} ${params.buyToken}", color = Color.White, fontSize = 13.sp)
                        Text("Receiving: ${params.payAmount} ${params.payToken}", color = Color.White, fontSize = 13.sp)
                        
                        Spacer(Modifier.height(10.dp))
                        
                        Text("USER WALLET", color = ColorBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("Sending: ${params.payAmount} ${params.payToken}", color = Color.White, fontSize = 13.sp)
                        Text("Sending: ${String.format("%.5f", params.minerFee + params.serviceFee)} ERG (Fee)", color = Color.White, fontSize = 13.sp)
                        Text("Receiving: ${params.buyAmount} ${params.buyToken}", color = Color.White, fontSize = 13.sp)
                    }
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
