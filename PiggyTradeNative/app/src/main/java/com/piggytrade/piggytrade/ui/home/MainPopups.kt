package com.piggytrade.piggytrade.ui.home
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TokenSelectorPopup(uiState: SwapState, viewModel: SwapViewModel) {
    androidx.compose.animation.AnimatedVisibility(
        visible = uiState.activeSelector != null,
        enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }, animationSpec = tween(400)),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }, animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { viewModel.setActiveSelector(null) }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .align(Alignment.BottomCenter)
                    .background(ColorBg, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { /* Consume click to prevent closing */ }
            ) {
                when (uiState.activeSelector) {
                    "from", "to", "fav" -> {
                        val items = if (uiState.activeSelector == "to") {
                            viewModel.getReachableTokens()
                        } else {
                            uiState.tokens
                        }
                        SelectorScreen(
                            title = "Select Token",
                            items = items,
                            onSelect = { token ->
                                viewModel.finalizeSelection(token)
                                viewModel.setActiveSelector(null)
                            },
                            onBack = { viewModel.setActiveSelector(null) },
                            getName = { it },
                            getId = { viewModel.getTokenId(it) },
                            getBalance = { viewModel.getUserBalance(it) },
                            getVerificationStatus = { viewModel.getVerificationStatus(it) },
                            idLabel = "ID: "
                        )
                    }
                    "wallet" -> {
                        SelectorScreen(
                            title = "Select Wallet",
                            items = uiState.wallets,
                            onSelect = { wallet ->
                                viewModel.finalizeSelection(wallet)
                                viewModel.setActiveSelector(null)
                            },
                            onBack = { viewModel.setActiveSelector(null) },
                            getName = { it },
                            getId = { viewModel.getWalletAddress(it) },
                            getBalance = { null },
                            idLabel = "Addr: "
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BetaDisclaimerDialog(
    showBetaDisclaimer: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (showBetaDisclaimer) {
        AlertDialog(
            onDismissRequest = { onDismiss() },
            title = {
                Text(
                    "⚠️ Beta Version Warning",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        "This is a Beta Version which may have errors. You must validate and check the output carefully.",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Any submitted transactions are IRREVERSIBLE on the blockchain.",
                        color = ColorAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            },
            containerColor = ColorCard,
            confirmButton = {
                Button(
                    onClick = {
                        onDismiss()
                        onConfirm()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                ) {
                    Text("Agree & Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("Disagree", color = Color.White)
                }
            }
        )
    }
}
