package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.settings.*
import com.piggytrade.piggytrade.crypto.MnemonicValidator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG

import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddWalletScreen(
    viewModel: SwapViewModel,
    onBack: () -> Unit,
    isDebugMode: Boolean = false,
    initialAddress: String = "",
    onScanQr: (() -> Unit)? = null
) {
    val context = LocalContext.current

    var walletName by remember { mutableStateOf("") }
    var isErgoPay by remember { mutableStateOf(true) }
    var address by remember { mutableStateOf(initialAddress) }
    var mnemonic by remember { mutableStateOf("") }

    // BIP39 wordlist — loaded once from assets
    val mnemonicWordSet = remember { MnemonicValidator.loadWordList(context) }
    val mnemonicWordList = remember { MnemonicValidator.loadWordListOrdered(context) }

    // Live validation result
    val mnemonicValidation by remember(mnemonic) {
        derivedStateOf { MnemonicValidator.validate(mnemonic, mnemonicWordSet, mnemonicWordList) }
    }

    // Update address when returning from QR scanner
    LaunchedEffect(initialAddress) {
        if (initialAddress.isNotEmpty()) {
            address = initialAddress
        }
    }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var useBiometrics by remember { mutableStateOf(false) }
    var isLegacy by remember { mutableStateOf(false) }

    val biometricManager = remember { BiometricManager.from(context) }
    val isBiometricAvailable = remember {
        biometricManager.canAuthenticate(BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    val scrollState = rememberScrollState()

    fun handleSave() {
        if (useBiometrics && !isErgoPay) {
            val activity = context as? FragmentActivity ?: return
            val executor = ContextCompat.getMainExecutor(activity)
            val biometricPrompt = BiometricPrompt(activity, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        viewModel.saveWallet(
                            name = walletName,
                            mnemonic = mnemonic,
                            address = null,
                            password = null,
                            useBiometrics = true,
                            useLegacy = isLegacy
                        )
                        onBack()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Confirm Fingerprint")
                .setSubtitle("Verify biometrics to secure your wallet")
                .setNegativeButtonText("Cancel")
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            viewModel.saveWallet(
                name = walletName,
                mnemonic = if (isErgoPay) null else mnemonic,
                address = if (isErgoPay) address else null,
                password = if (isErgoPay || useBiometrics) null else password,
                useBiometrics = false,
                useLegacy = isLegacy
            )
            onBack()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // Header
        TogaRow(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(icon = "\uEF7D", onClick = onBack, modifier = Modifier.size(36.dp), radius = 10.dp, bgColor = ColorBlue)
            Text(text = "Add Wallet", color = ColorText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }

        TogaColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .androidBorder(radius = 30.dp, borderWidth = 0.dp, bgColor = ColorCard)
                .verticalScroll(scrollState)
                .padding(20.dp)
        ) {
            // Wallet Name
            OutlinedTextField(
                value = walletName,
                onValueChange = { walletName = it },
                placeholder = { Text("Wallet Name", color = Color(0xFFAAAAAA)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 15.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ColorInputBg,
                    unfocusedContainerColor = ColorInputBg,
                    focusedBorderColor = Color(0xFF535C6E),
                    unfocusedBorderColor = Color(0xFF535C6E),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Ergopay / Mnemonic Toggle
            TogaRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isErgoPay,
                    onCheckedChange = { isErgoPay = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ColorAccent,
                        checkedTrackColor = ColorAccent.copy(alpha = 0.5f)
                    )
                )
                Text(
                    text = if (isErgoPay) "Ergopay" else "Mnemonic",
                    color = ColorText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }

            // Help instructions
            if (isErgoPay) {
                Text("Recommended:", color = Color(0xFF28A745), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("With Ergopay you sign the transaction using \nthe official Ergo Mobile Wallet.", color = Color(0xFF28A745), fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
            } else {
                Text("Mnemonics are encrypted on device and\nallow for faster trades by signing transactions\n in the app. Only primary address is supported.", color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(bottom = 10.dp))
            }

            val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
            
            if (isErgoPay) {
                // Address field
                TogaRow(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = { Text("Ergo address (9h...)", color = Color(0xFFAAAAAA)) },
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorInputBg,
                            unfocusedContainerColor = ColorInputBg,
                            focusedBorderColor = Color(0xFF535C6E),
                            unfocusedBorderColor = Color(0xFF535C6E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TogaIconButton(
                        icon = "\uE2C4", // PASTE
                        onClick = { 
                            clipboardManager.getText()?.let { address = it.text }
                        },
                        modifier = Modifier.size(50.dp),
                        radius = 10.dp,
                        bgColor = ColorInputBg,
                        borderWidth = 1.dp,
                        borderColor = Color(0xFF535C6E)
                    )
                    if (onScanQr != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { onScanQr() },
                            modifier = Modifier.size(50.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = "Scan QR",
                                tint = ColorAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            } else {
                // ── Mnemonic input with BIP39 validation ──
                val mnemonicBorderColor = when {
                    mnemonic.isBlank() -> Color(0xFF535C6E)
                    mnemonicValidation.isFullyValid -> Color(0xFF28A745)
                    else -> Color(0xFFFF4444)
                }

                TogaRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    OutlinedTextField(
                        value = mnemonic,
                        onValueChange = { mnemonic = it },
                        placeholder = { Text("Secret mnemonic (12, 15, 18, 21 or 24 words)", color = Color(0xFFAAAAAA)) },
                        modifier = Modifier.weight(1f).height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorInputBg,
                            unfocusedContainerColor = ColorInputBg,
                            focusedBorderColor = mnemonicBorderColor,
                            unfocusedBorderColor = mnemonicBorderColor,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    TogaIconButton(
                        icon = "\uE2C4", // PASTE
                        onClick = { clipboardManager.getText()?.let { mnemonic = it.text } },
                        modifier = Modifier.size(50.dp).height(110.dp),
                        radius = 10.dp,
                        bgColor = ColorInputBg,
                        borderWidth = 1.dp,
                        borderColor = Color(0xFF535C6E)
                    )
                }

                // ── Per-word chips ──
                if (mnemonic.isNotBlank()) {
                    val words = mnemonic.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        words.forEachIndexed { idx, word ->
                            val isInvalid = idx in mnemonicValidation.invalidWordIndices
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isInvalid) Color(0xFF3A0000) else Color(0xFF1E2530),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isInvalid) Color(0xFFFF4444) else Color(0xFF535C6E),
                                            shape = RoundedCornerShape(6.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = word,
                                        color = if (isInvalid) Color(0xFFFF6666) else Color(0xFFCCCCCC),
                                        fontSize = 11.sp,
                                        fontWeight = if (isInvalid) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                                if (isInvalid) {
                                    Text(
                                        text = "\"$word\" is not a\nvalid mnemonic word",
                                        color = Color(0xFFFF4444),
                                        fontSize = 9.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // ── Status line ──
                    val (statusText, statusColor) = when {
                        mnemonicValidation.invalidWordIndices.isNotEmpty() ->
                            Pair("Fix the highlighted words above", Color(0xFFFF4444))
                        !mnemonicValidation.isValidWordCount ->
                            Pair("Mnemonic must be 12, 15, 18, 21, or 24 words (currently ${mnemonic.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }.size})", Color(0xFFFF6B35))
                        !mnemonicValidation.checksumValid ->
                            Pair("Invalid mnemonic — checksum failed", Color(0xFFFF6B35))
                        else ->
                            Pair("✓ Valid mnemonic", Color(0xFF28A745))
                    }
                    Text(
                        text = statusText,
                        color = statusColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp)
                    )
                }

                // Legacy toggle (only in debug mode)
                if (isDebugMode) {
                    TogaRow(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Legacy (Pre-1.6.27)",
                            color = ColorText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = isLegacy,
                            onCheckedChange = { isLegacy = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = ColorAccent,
                                checkedTrackColor = ColorAccent.copy(alpha = 0.5f)
                            )
                        )
                    }
                }

                // Password fields
                if (!useBiometrics) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("Encryption Password", color = Color(0xFFAAAAAA)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorInputBg,
                            unfocusedContainerColor = ColorInputBg,
                            focusedBorderColor = Color(0xFF535C6E),
                            unfocusedBorderColor = Color(0xFF535C6E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        placeholder = { Text("Confirm Password", color = Color(0xFFAAAAAA)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = ColorInputBg,
                            unfocusedContainerColor = ColorInputBg,
                            focusedBorderColor = Color(0xFF535C6E),
                            unfocusedBorderColor = Color(0xFF535C6E),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    // Password strength indicator
                    val pwColor = when {
                        password.isEmpty() -> ColorTextDim
                        password.length < 8 -> Color(0xFFFF4444)
                        password.length < 12 -> Color(0xFFFF6B35)
                        else -> Color(0xFF28A745)
                    }
                    val pwText = when {
                        password.isEmpty() -> "Minimum 8 characters required"
                        password.length < 8 -> "Too short — ${8 - password.length} more characters needed"
                        password != confirmPassword && confirmPassword.isNotEmpty() -> "Passwords do not match"
                        password.length < 12 -> "Fair — consider a longer password"
                        else -> "Strong password ✓"
                    }
                    Text(
                        text = pwText,
                        color = pwColor,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 15.dp)
                    )
                }

                // Biometrics toggle
                TogaRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBiometricAvailable) "Use Biometrics for Security" else "Biometrics Unavailable",
                        color = if (isBiometricAvailable) ColorText else ColorTextDim,
                        fontSize = 10.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = useBiometrics,
                        onCheckedChange = { useBiometrics = it },
                        enabled = isBiometricAvailable,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ColorAccent,
                            checkedTrackColor = ColorAccent.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            val isSaveEnabled = if (isErgoPay) {
                walletName.isNotEmpty() && address.isNotEmpty()
            } else {
                walletName.isNotEmpty() &&
                mnemonicValidation.isFullyValid &&
                (useBiometrics || (password.length >= 8 && password == confirmPassword))
            }

            Button(
                onClick = { handleSave() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = isSaveEnabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorAccent,
                    contentColor = ColorBg,
                    disabledContainerColor = Color(0xFF1C1C1C),
                    disabledContentColor = Color(0xFF555555)
                )
            ) {
                Text("Encrypt & Save Wallet", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
