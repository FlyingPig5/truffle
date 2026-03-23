package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.SwapState
import com.piggytrade.piggytrade.ui.swap.SwapViewModel

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.R

@Composable
fun AppHeader(
    isLoading: Boolean,
    onNavigateToSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: App icon + wordmark
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "Truffle",
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = painterResource(id = R.drawable.truffle),
                contentDescription = "Truffle",
                modifier = Modifier.height(24.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.FillHeight
            )
        }
        
        // Right: Loading + Settings
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = ColorAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            
            TogaIconButton(
                icon = "\uE8B8", // ICON_COG
                onClick = onNavigateToSettings,
                modifier = Modifier.size(36.dp),
                bgColor = Color.Transparent,
                radius = 10.dp
            )
        }
    }
}

@Composable
fun WalletSelectorCard(
    uiState: SwapState,
    viewModel: SwapViewModel,
    onNavigateToAddWallet: () -> Unit
) {
    // Wallet selector card — standalone
    WalletCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp)
                .height(50.dp)
                .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorInputBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var expanded by remember { mutableStateOf(false) }
            var menuWidth by remember { mutableStateOf(0.dp) }
            val density = LocalDensity.current

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .onSizeChanged {
                        menuWidth = with(density) { it.width.toDp() }
                    }
                    .clickable { expanded = true }
                    .padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .width(menuWidth)
                        .background(ColorCard)
                ) {
                    uiState.wallets.forEach { wallet ->
                        DropdownMenuItem(
                            text = { Text(wallet, color = Color.White) },
                            onClick = {
                                viewModel.setSelectionContext("wallet")
                                viewModel.finalizeSelection(wallet)
                                expanded = false
                            }
                        )
                    }
                    if (uiState.wallets.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Add Wallet", color = ColorAccent) },
                            onClick = {
                                expanded = false
                                onNavigateToAddWallet()
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f).padding(start = 0.dp)
                ) {
                    val walletName = uiState.selectedWallet.ifEmpty { "Select Wallet" }
                    val displayAddr = if (uiState.changeAddress.isNotEmpty()) {
                        uiState.changeAddress
                    } else {
                        uiState.selectedAddress
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = walletName.replace(" (Ergopay)", ""),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        if (displayAddr.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${displayAddr.take(5)}...${displayAddr.takeLast(5)})",
                                color = ColorTextDim,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                    if (uiState.selectedAddress.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val numAddrs = uiState.selectedAddresses.size
                            if (numAddrs > 1) {
                                Text(
                                    text = "$numAddrs addresses active",
                                    color = ColorTextDim.copy(alpha = 0.6f),
                                    fontSize = 9.sp
                                )
                            }
                            if (uiState.isLoadingWallet) {
                                if (numAddrs > 1) Spacer(modifier = Modifier.width(8.dp))
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = ColorAccent,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }

                Icon(
                    painter = painterResource(id = android.R.drawable.arrow_down_float),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp).padding(end = 8.dp)
                )
            }
        }
    }
}

@Composable
fun WalletSelectorRow(
    uiState: SwapState,
    viewModel: SwapViewModel,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToSettings: () -> Unit,
    showWalletSelector: Boolean = true
) {
    // TOP: App header — outside the wallet card, on main background
    AppHeader(
        isLoading = uiState.isLoadingQuote || uiState.isLoadingHistory,
        onNavigateToSettings = onNavigateToSettings
    )

    if (showWalletSelector) {
        WalletSelectorCard(uiState, viewModel, onNavigateToAddWallet)
    }
}
