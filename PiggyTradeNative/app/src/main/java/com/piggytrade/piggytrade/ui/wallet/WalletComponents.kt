package com.piggytrade.piggytrade.ui.wallet
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
fun WalletSelectorRow(
    uiState: SwapState,
    viewModel: SwapViewModel,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    WalletCard {
        // TOP: Branding and Settings (Centered, on Navy)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 15.dp, vertical = 8.dp)
        ) {
            // Center: Branding
            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Piggy", color = ColorText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Image(
                    painter = painterResource(id = R.drawable.logo_topbar_and_standard_launcher),
                    contentDescription = "Logo",
                    modifier = Modifier.size(24.dp).padding(horizontal = 4.dp)
                )
                Text(text = "Trade", color = ColorText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Right side Content
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isLoadingQuote || uiState.isLoadingHistory) {
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

        // BOTTOM: Wallet Selection (Blue Container)
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
                        Text(
                            text = walletName.replace(" (Ergopay)", ""),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (uiState.selectedAddress.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = uiState.selectedAddress.take(7) + "...",
                                    color = ColorTextDim,
                                    fontSize = 10.sp
                                )
                                if (uiState.isLoadingWallet) {
                                    Spacer(modifier = Modifier.width(8.dp))
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
