package com.piggytrade.piggytrade

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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val viewModel: SwapViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsState()
            var currentScreen by remember { mutableStateOf("main") }
            var selectedWalletForInfo by remember { mutableStateOf("") }

            BackHandler(enabled = currentScreen != "main") {
                currentScreen = when (currentScreen) {
                    "add_node" -> "settings"
                    "manage_pairs" -> "settings"
                    "token_selector", "wallet_selector", "add_wallet", "wallet_info", "review_tx", "settings" -> "main"
                    else -> "main"
                }
            }

            PiggyTradeNativeTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ColorBg
                ) {
                    when (currentScreen) {
                        "main" -> MainScreen(
                            viewModel = viewModel,
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
                            onSubmit = { currentScreen = "review_tx" }
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
                            onBack = { currentScreen = "main" },
                            onNavigateToAddWallet = { currentScreen = "add_wallet" }
                        )
                        "settings" -> SettingsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "main" },
                            onNavigateToAddNode = { currentScreen = "add_node" },
                            onNavigateToManagePairs = { currentScreen = "manage_pairs" }
                        )
                        "manage_pairs" -> ManagePairsScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "settings" }
                        )
                        "add_node" -> AddNodeScreen(
                            onBack = { currentScreen = "settings" }
                        )
                        "add_wallet" -> AddWalletScreen(
                            viewModel = viewModel,
                            onBack = { currentScreen = "main" },
                            isDebugMode = uiState.debugMode
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
                }
            }
        }
    }
}
