package com.piggytrade.piggytrade.ui.home
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*
import com.piggytrade.piggytrade.ui.bank.*
import com.piggytrade.piggytrade.ui.portfolio.*
import com.piggytrade.piggytrade.ui.market.MarketViewModel
import android.widget.Toast
import androidx.activity.compose.BackHandler

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    viewModel: SwapViewModel,
    marketViewModel: MarketViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTokenSelector: (String) -> Unit,
    onNavigateToWalletSelector: () -> Unit,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToWalletInfo: (String) -> Unit,
    onSubmit: () -> Unit,
    onNavigateToSend: () -> Unit = {},
    onNavigateToAddressExplorer: (String) -> Unit = {},
    onNavigateToQrScannerExplorer: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Re-trigger sync when app returns to foreground (e.g. phone screen off/on)
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                marketViewModel.syncOraclePrices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    
    // Validation Logic for Swap Button
    val fromAmountValue = uiState.fromAmount.replace(",", ".").toDoubleOrNull() ?: 0.0
    val fromBalanceValue = uiState.fromBalance.replace(",", ".").toDoubleOrNull() ?: 0.0
    val isSwapValid = uiState.fromAsset.isNotEmpty() && 
                      uiState.toAsset.isNotEmpty() && 
                      uiState.fromAsset != uiState.toAsset && 
                      fromAmountValue > 0 && 
                      fromAmountValue <= fromBalanceValue

    val scrollState = rememberScrollState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBetaDisclaimer by remember { mutableStateOf(false) }

    // Clear explorer state when navigating away from the explorer tab
    LaunchedEffect(uiState.activeTab) {
        if (uiState.activeTab != "explorer") {
            viewModel.clearExplorerState()
        }
    }

    // Handle physical back button on non-default tabs
    BackHandler(enabled = uiState.activeTab != "swap") {
        if (uiState.activeTab == "explorer" && uiState.explorerAddress.isNotEmpty()) {
            // If exploring an address, go back to address book
            viewModel.clearExplorerState()
        } else {
            // Return to the default tab
            viewModel.setActiveTab("swap")
        }
    }


    Scaffold(
        bottomBar = {
            BottomMenuBar(
                activeTab = uiState.activeTab,
                onTabClick = { viewModel.setActiveTab(it) }
            )
        },
        containerColor = ColorBg
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            TogaColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ColorBg)
                    .graphicsLayer {
                        if (uiState.activeSelector != null) {
                            renderEffect = null // Fallback
                        }
                    }
                    .blur(if (uiState.activeSelector != null) 10.dp else 0.dp)
            ) {
                // Constant App Header (Logo + Settings Cog)
                AppHeader(
                    isLoading = uiState.isLoadingQuote || uiState.isLoadingHistory,
                    onNavigateToSettings = onNavigateToSettings
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Tab Content with Animated Transitions
                AnimatedContent(
                    targetState = uiState.activeTab,
                    transitionSpec = {
                        val tabOrder = listOf("dex", "bank", "ecosystem", "explorer", "wallet")
                        val fromIdx = tabOrder.indexOf(initialState)
                        val toIdx = tabOrder.indexOf(targetState)
                        if (toIdx > fromIdx) {
                            // Moving right: new tab slides in from right
                            slideInHorizontally(animationSpec = tween(400)) { it } togetherWith 
                            slideOutHorizontally(animationSpec = tween(400)) { -it }
                        } else {
                            // Moving left: new tab slides in from left
                            slideInHorizontally(animationSpec = tween(400)) { -it } togetherWith 
                            slideOutHorizontally(animationSpec = tween(400)) { it }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "TabTransition"
                ) { tab ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Wallet selector card — visible only on functional tabs
                        if (tab != "ecosystem" && tab != "explorer") {
                            WalletSelectorCard(uiState, viewModel, onNavigateToAddWallet)
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Screen specific content
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            when (tab) {
                                "dex" -> TradeCard(modifier = Modifier.fillMaxSize()) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Column {
                                            // FROM section
                                            FromTokenPanel(uiState, viewModel)

                                            Spacer(modifier = Modifier.height(8.dp)) // Small gap for the overlay button

                                            // TO section
                                            ToTokenPanel(uiState, viewModel)
                                        }

                                        // SWAP BUTTON OVERLAY
                                        TogaIconButton(
                                            icon = "\uE8D5", // Material swap_vert
                                            onClick = { viewModel.swapDirection() },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .offset(y = if (uiState.showFavorites) 24.dp else 0.dp),
                                            iconSize = 24.dp,
                                            iconColor = Color.White,
                                            radius = 20.dp,
                                            borderWidth = 0.dp,
                                            bgColor = ColorCard
                                        )
                                    }

                                    // Status Box - Expanded Quote Details
                                    OrderDetailsPanel(uiState, viewModel)

                                    Spacer(modifier = Modifier.weight(1f)) // Push button to bottom of card

                                    // Swap Button
                                    SwapButton(uiState, isSwapValid, fromAmountValue, fromBalanceValue, { showBetaDisclaimer = true })
                                }
                                "bank" -> TradeCard(modifier = Modifier.fillMaxSize()) {
                                     BankScreen(
                                         uiState = uiState,
                                         viewModel = viewModel,
                                         onSubmit = { showBetaDisclaimer = true }
                                     )
                                }
                                "ecosystem" -> Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                                    EcosystemScreen(viewModel = viewModel, marketViewModel = marketViewModel, onNavigateToAddressExplorer = onNavigateToAddressExplorer)
                                }
                                "explorer" -> Box(modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp)) {
                                    AddressExplorerScreen(
                                        viewModel = viewModel,
                                        onBack = { viewModel.setActiveTab("wallet") },
                                        onNavigateToQrScanner = onNavigateToQrScannerExplorer,
                                        onNavigateToAddressExplorer = onNavigateToAddressExplorer
                                    )
                                }
                                else -> Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                                    WalletInfoContent(
                                        walletName = uiState.selectedWallet,
                                        viewModel = viewModel,
                                        marketViewModel = marketViewModel,
                                        onDeleteComplete = null,
                                        showTitle = true,
                                        onNavigateToAddWallet = onNavigateToAddWallet,
                                        onNavigateToSend = onNavigateToSend,
                                        onNavigateToQrScanner = onNavigateToQrScannerExplorer,
                                        onAddressClick = onNavigateToAddressExplorer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Animated Popup Selector - OUTSIDE BLURRED COLUMN
            TokenSelectorPopup(uiState, viewModel)

            if (showBetaDisclaimer) {
                BetaDisclaimerDialog(
                    showBetaDisclaimer = showBetaDisclaimer,
                    onConfirm = {
                        if (uiState.activeTab == "dex") {
                            viewModel.prepareSwap(
                                onSuccess = { onSubmit() },
                                onError = { err ->
                                    Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                }
                            )
                        } else if (uiState.activeTab == "bank") {
                            if (uiState.bankMode == "redeem") {
                                viewModel.buildRedeemTransaction(onSubmit)
                            } else {
                                viewModel.buildMintTransaction(onSubmit)
                            }
                        }
                    },
                    onDismiss = { showBetaDisclaimer = false }
                )
            }
        }
    }
}
