package com.piggytrade.piggytrade
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.piggytrade.piggytrade.ui.*
import com.piggytrade.piggytrade.ui.market.MarketViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SwapViewModel = viewModel()
            val marketViewModel: MarketViewModel = viewModel()

            // Startup: trigger oracle price sync via MarketViewModel
            LaunchedEffect(Unit) { marketViewModel.syncOraclePrices() }
            val uiState by viewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf("main") }
            var selectedWalletForInfo by remember { mutableStateOf("") }
            var qrScanRecipientIndex by remember { mutableIntStateOf(0) }
            var sendTokenRecipientIndex by remember { mutableIntStateOf(0) }
            var pendingAddWalletAddress by remember { mutableStateOf("") }

            // Handle ergopay: deep link on launch
            LaunchedEffect(Unit) {
                intent?.data?.let { uri ->
                    if (uri.scheme == "ergopay") {
                        viewModel.handleErgoPayUrl(uri.toString())
                        currentScreen = "ergopay_review"
                    }
                }
            }

            BackHandler(enabled = currentScreen != "main") {
                currentScreen = when (currentScreen) {
                    "add_node" -> "settings"
                    "manage_pairs" -> "settings"
                    "send_review" -> "send"
                    "qr_scanner" -> "send"
                    "qr_scanner_add_wallet" -> "add_wallet"
                    "qr_scanner_explorer" -> "main"
                    "send_token_selector" -> "send"
                    "ergopay_review" -> "main"
                    "token_selector", "wallet_selector", "add_wallet", "wallet_info", "review_tx", "settings", "send" -> "main"
                    else -> "main"
                }
            }

            TruffleTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ColorBg
                ) {
                    when (currentScreen) {
                        "main" -> MainScreen(
                            viewModel = viewModel,
                            marketViewModel = marketViewModel,
                            onNavigateToSettings = { currentScreen = "settings" },
                            onNavigateToTokenSelector = { context ->
                                viewModel.setSelectionContext(context)
                                currentScreen = "token_selector"
                            },
                            onNavigateToWalletSelector = {
                                viewModel.setSelectionContext("wallet")
                                currentScreen = "wallet_selector"
                            },
                            onNavigateToAddWallet = { currentScreen = "add_wallet" },
                            onNavigateToWalletInfo = { wName ->
                                selectedWalletForInfo = wName
                                currentScreen = "wallet_info"
                            },
                            onSubmit = { currentScreen = "review_tx" },
                            onNavigateToSend = {
                                viewModel.clearSendState()
                                currentScreen = "send"
                            },
                            onNavigateToAddressExplorer = { address ->
                                viewModel.openAddressExplorer(address)
                                viewModel.setActiveTab("explorer")
                            },
                            onNavigateToQrScannerExplorer = {
                                currentScreen = "qr_scanner_explorer"
                            }
                        )
                        "review_tx" -> ReviewTxScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "main" },
                            onConfirm = { password, onResult -> 
                                if (uiState.reviewParams?.isErgopay == true) {
                                    val url = uiState.reviewParams?.ergopayUrl
                                    if (!url.isNullOrEmpty()) {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW, 
                                                android.net.Uri.parse(url)
                                            )
                                            this@MainActivity.startActivity(intent)
                                            onResult(null)
                                            currentScreen = "main"
                                        } catch (e: Exception) {
                                            onResult("No app found to handle ErgoPay intent")
                                        }
                                    } else {
                                        onResult("Missing ErgoPay URL")
                                    }
                                } else {
                                    viewModel.signAndBroadcast(
                                        password = password, 
                                        context = this@MainActivity,
                                        onSuccess = {
                                            onResult(null)
                                        },
                                        onError = { err ->
                                            onResult(err)
                                        }
                                    )
                                }
                            }
                        )
                        "wallet_info" -> WalletInfoScreen(
                            walletName = selectedWalletForInfo,
                            viewModel = viewModel,
                            marketViewModel = marketViewModel,
                            onBack = { currentScreen = "main" },
                            onNavigateToAddWallet = { currentScreen = "add_wallet" },
                            onNavigateToSend = {
                                viewModel.clearSendState()
                                currentScreen = "send"
                            }
                        )
                        // ─── SEND FLOW ───
                        "send" -> SendScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "main" },
                            onNavigateToQrScanner = { recipientIdx ->
                                qrScanRecipientIndex = recipientIdx
                                currentScreen = "qr_scanner"
                            },
                            onNavigateToTokenSelector = { recipientIdx ->
                                sendTokenRecipientIndex = recipientIdx
                                currentScreen = "send_token_selector"
                            },
                            onNavigateToReview = { currentScreen = "send_review" }
                        )
                        "send_review" -> SendReviewScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "send" },
                            onConfirm = { password, onResult ->
                                if (uiState.sendReviewParams?.isErgopay == true) {
                                    val url = uiState.sendReviewParams?.ergopayUrl
                                    if (!url.isNullOrEmpty()) {
                                        try {
                                            val intent = android.content.Intent(
                                                android.content.Intent.ACTION_VIEW,
                                                android.net.Uri.parse(url)
                                            )
                                            this@MainActivity.startActivity(intent)
                                            onResult(null)
                                            viewModel.clearSendState()
                                            currentScreen = "main"
                                        } catch (e: Exception) {
                                            onResult("No app found to handle ErgoPay intent")
                                        }
                                    } else {
                                        onResult("Missing ErgoPay URL")
                                    }
                                } else {
                                    viewModel.signAndBroadcastSend(
                                        password = password,
                                        context = this@MainActivity,
                                        onSuccess = { onResult(null) },
                                        onError = { err -> onResult(err) }
                                    )
                                }
                            }
                        )
                        "qr_scanner" -> QrScannerScreen(
                            onBack = { currentScreen = "send" },
                            onAddressScanned = { address ->
                                viewModel.setSendRecipientAddress(qrScanRecipientIndex, address)
                                currentScreen = "send"
                            },
                            onErgoPayScanned = { url ->
                                viewModel.handleErgoPayUrl(url)
                                currentScreen = "ergopay_review"
                            }
                        )
                        "ergopay_review" -> ErgoPayReviewScreen(
                            viewModel = viewModel,
                            ergoPayUrl = uiState.ergoPayIncomingUrl,
                            onBack = {
                                viewModel.clearSendState()
                                currentScreen = "main"
                            }
                        )
                        "send_token_selector" -> {
                            // Show tokens the user holds for adding to a send recipient
                            val heldTokenIds = uiState.walletTokens.keys.toList()
                            val heldTokenNames = heldTokenIds.map { id ->
                                viewModel.getTokenName(id)
                            }
                            SelectorScreen(
                                title = "Add Token to Send",
                                items = heldTokenNames,
                                onSelect = { name ->
                                    val tokenId = heldTokenIds.getOrNull(heldTokenNames.indexOf(name))
                                    if (tokenId != null) {
                                        viewModel.addSendToken(sendTokenRecipientIndex, tokenId)
                                    }
                                    currentScreen = "send"
                                },
                                onBack = { currentScreen = "send" },
                                getName = { it },
                                getId = { heldTokenIds.getOrNull(heldTokenNames.indexOf(it)) ?: it },
                                getBalance = { name ->
                                    val tokenId = heldTokenIds.getOrNull(heldTokenNames.indexOf(name)) ?: ""
                                    viewModel.getUserBalance(name)
                                },
                                idLabel = "ID: "
                            )
                        }
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            marketViewModel = marketViewModel,
                            onBack = { currentScreen = "main" },
                            onNavigateToAddNode = { currentScreen = "add_node" },
                            onNavigateToManagePairs = { currentScreen = "manage_pairs" }
                        )
                        "manage_pairs" -> ManagePairsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "settings" }
                        )
                        "add_node" -> AddNodeScreen(
                            onBack = { currentScreen = "settings" },
                            allowHttpNodes = uiState.allowHttpNodes
                        )
                        "add_wallet" -> AddWalletScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "main" },
                            isDebugMode = uiState.debugMode,
                            initialAddress = pendingAddWalletAddress,
                            onScanQr = {
                                currentScreen = "qr_scanner_add_wallet"
                            }
                        )
                        "qr_scanner_add_wallet" -> QrScannerScreen(
                            onBack = { currentScreen = "add_wallet" },
                            onAddressScanned = { scannedAddress ->
                                pendingAddWalletAddress = scannedAddress
                                currentScreen = "add_wallet"
                            },
                            onErgoPayScanned = { url ->
                                viewModel.handleErgoPayUrl(url)
                                currentScreen = "ergopay_review"
                            }
                        )
                        "qr_scanner_explorer" -> QrScannerScreen(
                            onBack = {
                                viewModel.setActiveTab("explorer")
                                currentScreen = "main"
                            },
                            onAddressScanned = { scannedAddress ->
                                viewModel.openAddressExplorer(scannedAddress)
                                viewModel.setActiveTab("explorer")
                                currentScreen = "main"
                            },
                            onErgoPayScanned = { url ->
                                viewModel.handleErgoPayUrl(url)
                                currentScreen = "ergopay_review"
                            }
                        )
                        "token_selector" -> {
                            val contextToken = if (uiState.selectionContext == "to") uiState.fromAsset else if (uiState.selectionContext == "from") uiState.toAsset else ""
                            val filteredTokens = uiState.tokens.filter { token ->
                                val status = viewModel.getVerificationStatus(token)
                                val isWhitelisted = status < 2
                                val isValid = viewModel.isValidPair(token, contextToken)
                                isWhitelisted && isValid
                            }
                            val sortedTokens = filteredTokens.sortedWith { a, b ->
                                val idA = viewModel.getTokenId(a)
                                val idB = viewModel.getTokenId(b)
                                
                                val balanceA = if (idA == "ERG") uiState.walletErgBalance else (uiState.walletTokens[idA] ?: 0L).toDouble()
                                val balanceB = if (idB == "ERG") uiState.walletErgBalance else (uiState.walletTokens[idB] ?: 0L).toDouble()
                                
                                val hasA = balanceA > 0.0
                                val hasB = balanceB > 0.0
                                
                                if (hasA != hasB) {
                                    if (hasA) -1 else 1
                                } else {
                                    a.compareTo(b, ignoreCase = true)
                                }
                            }
                            SelectorScreen(
                                title = "Select Token",
                                items = sortedTokens,
                                onSelect = { item ->
                                    viewModel.finalizeSelection(item)
                                    currentScreen = "main"
                                },
                                onBack = { currentScreen = "main" },
                                getName = { it },
                                getId = viewModel::getTokenId,
                                getVerificationStatus = viewModel::getVerificationStatus,
                                getBalance = viewModel::getUserBalance,
                                idLabel = "ID: "
                            )
                        }
                        "wallet_selector" -> SelectorScreen(
                            title = "Select Wallet",
                            items = uiState.wallets,
                            onSelect = { item ->
                                viewModel.finalizeSelection(item)
                                currentScreen = "main"
                            },
                            onBack = { currentScreen = "main" },
                            getName = { it },
                            getId = { it },
                            idLabel = "Addr: "
                        )
                    }

                    if (uiState.syncProgress != null) {
                        SyncProgressPopup(uiState.syncProgress!!, onDismiss = { viewModel.dismissSyncPopup() })
                    }
                }
            }
        }
    }
}
