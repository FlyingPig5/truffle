package com.piggytrade.piggytrade.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed

@Composable
fun MainScreen(
    viewModel: SwapViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToTokenSelector: (String) -> Unit,
    onNavigateToWalletSelector: () -> Unit,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToWalletInfo: (String) -> Unit,
    onSubmit: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
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

    if (uiState.syncProgress != null) {
        SyncProgressPopup(uiState.syncProgress!!, onDismiss = { viewModel.dismissSyncPopup() })
    }

    Scaffold(
        topBar = {
            PiggyTopBar(
                isLoading = uiState.isLoadingQuote,
                onSettingsClick = onNavigateToSettings
            )
        },
        containerColor = ColorBg
    ) { paddingValues ->
        TogaColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(ColorBg)
                .graphicsLayer {
                    if (uiState.activeSelector != null) {
                        renderEffect = null // Fallback
                    }
                }
                .blur(if (uiState.activeSelector != null) 10.dp else 0.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp)) // Minimal top gap

            // Wallet Card - Single Box Redesign
            WalletCard {
                Spacer(modifier = Modifier.height(10.dp)) // Padding at top of wallet card interior

                // Sub-Row for interactivity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .height(60.dp)
                        .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorInputBg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Wallet Selection Zone
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { viewModel.setActiveSelector("wallet") } // RESTORED SELECTOR
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.padding(start = 0.dp)
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
                    }

                    // RIGHT: Actions (Add & Info)
                    Row(
                        modifier = Modifier.padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TogaIconButton(
                            icon = "\uE145", // PLUS
                            onClick = onNavigateToAddWallet,
                            modifier = Modifier.size(38.dp),
                            radius = 8.dp,
                            borderColor = ColorBorder,
                            bgColor = Color.Transparent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TogaIconButton(
                            icon = "\uF8FF", // WALLET
                            onClick = { 
                                if (uiState.selectedWallet.isNotEmpty() && uiState.selectedWallet != "Select Wallet") {
                                    onNavigateToWalletInfo(uiState.selectedWallet)
                                }
                            },
                            modifier = Modifier.size(38.dp),
                            radius = 8.dp,
                            borderColor = ColorBorder,
                            bgColor = Color.Transparent,
                            enabled = uiState.selectedWallet.isNotEmpty() && uiState.selectedWallet != "Select Wallet"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp)) // Minimal middle gap

            // Trade Card - Extended Dashboard Sheet
            TradeCard(modifier = Modifier.weight(1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Column {
                        // FROM section
                        TogaColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                        ) {
                            // Favorites: Single Line Scrollable (Moved and Resized)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(uiState.favorites.take(uiState.numFavorites)) { index, fav ->
                                    FavoriteButton(
                                        index = index,
                                        fav = fav,
                                        isSelected = uiState.firstFavoriteSelectedIndex == index,
                                        onClick = { viewModel.handleFavClick(index, fav) },
                                        onLongClick = {
                                            if (index != 0) {
                                                viewModel.startEditingFavorite(index)
                                                viewModel.setActiveSelector("fav")
                                            }
                                        },
                                        vm = viewModel
                                    )
                                }
                            }
                            // pulse logic
                            var fromFlash by remember { mutableStateOf(false) }
                            LaunchedEffect(uiState.fromPulseTrigger) {
                                if (uiState.fromPulseTrigger > 0) {
                                    fromFlash = true
                                    kotlinx.coroutines.delay(200)
                                    fromFlash = false
                                }
                            }
                            val fromBgColor by animateColorAsState(
                                targetValue = if (fromFlash) Color(0xFF1B4D3E) else ColorInputBg,
                                animationSpec = tween(durationMillis = 200)
                            )

                            // from_top_row (Merged Amount + Token Selector + Balance)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp)
                                    .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = fromBgColor), // Removed border
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BasicTextField(
                                            value = uiState.fromAmount,
                                            onValueChange = { viewModel.setFromAmount(it) },
                                            modifier = Modifier.weight(1f),
                                            textStyle = TextStyle(fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold),
                                            cursorBrush = SolidColor(Color.White),
                                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                                            decorationBox = { innerTextField ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (uiState.fromAmount.isEmpty()) {
                                                        Text("0.0", color = ColorInputHint, fontSize = 18.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )

                                        // btn_from
                                        TogaRow(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .height(42.dp) // Reduced height
                                                .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg)
                                                .clickable { viewModel.setActiveSelector("from") }
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            TokenImage(tokenId = viewModel.getTokenId(uiState.fromAsset), modifier = Modifier.size(24.dp).padding(end = 5.dp))
                                            val baseName = if (uiState.fromAsset.isNotEmpty()) viewModel.getTokenName(viewModel.getTokenId(uiState.fromAsset)) else "SELECT"
                                            Text(text = baseName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Icon(painter = painterResource(id = android.R.drawable.arrow_down_float), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp).padding(start = 4.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp)) // Reduced spacing

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Balance: ${uiState.fromBalance}", color = ColorTextDim, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        Text(
                                            text = "MAX",
                                            color = ColorAccent,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable { viewModel.setMaxAmount() }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp)) // Small gap for the overlay button

                        // TO section
                        TogaColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(bottom = 10.dp)
                        ) {
                            var toFlash by remember { mutableStateOf(false) }
                            LaunchedEffect(uiState.toPulseTrigger) {
                                if (uiState.toPulseTrigger > 0) {
                                    toFlash = true
                                    kotlinx.coroutines.delay(200)
                                    toFlash = false
                                }
                            }
                            val toBgColor by animateColorAsState(
                                targetValue = if (toFlash) Color(0xFF1B4D3E) else ColorInputBg,
                                animationSpec = tween(durationMillis = 200)
                            )

                            // to_top_row
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(85.dp) // Increased height
                                    .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = toBgColor), // Removed border
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = uiState.toQuote,
                                            color = ColorAccent,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.weight(1f)
                                        )

                                        // btn_to
                                        TogaRow(
                                            modifier = Modifier
                                                .width(140.dp)
                                                .height(42.dp) // Reduced height
                                                .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg)
                                                .clickable { viewModel.setActiveSelector("to") }
                                                .padding(horizontal = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            TokenImage(tokenId = viewModel.getTokenId(uiState.toAsset), modifier = Modifier.size(24.dp).padding(end = 5.dp))
                                            val baseName = if (uiState.toAsset.isNotEmpty()) viewModel.getTokenName(viewModel.getTokenId(uiState.toAsset)) else "SELECT"
                                            Text(text = baseName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Icon(painter = painterResource(id = android.R.drawable.arrow_down_float), contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp).padding(start = 4.dp))
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp)) // Reduced spacing

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = "Balance: ${uiState.toBalance}", color = ColorTextDim, fontSize = 10.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        if (uiState.isToAssetFavorite) {
                                            Text(text = "FAVORITE", color = ColorAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // SWAP BUTTON OVERLAY
                    TogaIconButton(
                        icon = "\uE8D5", // Material swap_vert
                        onClick = { viewModel.swapDirection() },
                        modifier = Modifier
                            .size(40.dp)
                            .offset(y = 24.dp), // Positions button over the seam
                        iconSize = 24.dp,
                        iconColor = Color.White,
                        radius = 20.dp,
                        borderWidth = 0.dp,
                        bgColor = ColorCard
                    )
                }

                // Status Box - Expanded Quote Details
                TogaColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorSelectionBg.copy(alpha = 0.8f))
                        .padding(15.dp)
                ) {
                    Text(text = "ORDER DETAILS", color = ColorAccent, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                    
                    TogaRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Rate:", color = ColorText, fontSize = 11.sp)
                        Text(text = "1 ${uiState.fromAsset} = ${String.format("%.4f", if (uiState.priceImpact > 0) 1.234 else 0.0)} ${uiState.toAsset}", color = Color.White, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TogaRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Price Impact:", color = ColorText, fontSize = 11.sp)
                        val impactColor = if (uiState.priceImpact < 1.0) Color(0xFF00D18B) else if (uiState.priceImpact > 5.0) Color.Red else Color.White
                        Text(
                            text = "${String.format("%.2f", uiState.priceImpact)}%",
                            color = impactColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TogaRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Miner Fee:", color = ColorText, fontSize = 11.sp)
                        Text(
                            text = "${String.format("%.3f", uiState.minerFee)} ERG",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Slider(
                        value = uiState.minerFee.toFloat(),
                        onValueChange = { viewModel.setMinerFee(it.toDouble()) },
                        valueRange = 0.001f..0.2f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TogaRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Slow", color = ColorTextDim, fontSize = 9.sp)
                        Text(text = "Fast", color = ColorTextDim, fontSize = 9.sp)
                    }
                }

                if (uiState.debugMode) {
                    TogaRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewModel.setSimulationMode(true) },
                            modifier = Modifier
                                .weight(1f)
                                .height(45.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (uiState.isSimulation) ColorAccent else ColorSelectionBg,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Check TX", fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = { viewModel.setSimulationMode(false) },
                            modifier = Modifier
                                .weight(1f)
                                .height(45.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!uiState.isSimulation) Color(0xFF9E1F1F) else ColorSelectionBg,
                                contentColor = Color.White
                            )
                        ) {
                            Text("LIVE", fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f)) // Push button to bottom of card

                // Swap Button
                Button(
                    onClick = {
                        showBetaDisclaimer = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp) // Adjusted for card interior
                        .height(55.dp),
                    enabled = !uiState.isBuildingTx && isSwapValid,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSwapValid) Color(0xFF00D18B) else ColorSelectionBg,
                        disabledContainerColor = ColorSelectionBg.copy(alpha = 0.5f)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (uiState.isBuildingTx) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp).padding(end = 8.dp),
                                color = Color.White.copy(alpha = 0.7f),
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = when {
                                uiState.isBuildingTx -> "Building transaction..."
                                uiState.isSimulation -> "SIMULATE"
                                !isSwapValid && fromAmountValue > fromBalanceValue -> "INSUFFICIENT BALANCE"
                                else -> "SWAP"
                            },
                            color = if (uiState.isBuildingTx) Color.White.copy(alpha = 0.7f) else if (isSwapValid) ColorBg else ColorTextDim,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Animated Popup Selector
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
                            SelectorScreen(
                                title = "Select Token",
                                items = uiState.tokens,
                                onSelect = { token ->
                                    viewModel.finalizeSelection(token)
                                    viewModel.setActiveSelector(null)
                                },
                                onBack = { viewModel.setActiveSelector(null) },
                                getName = { viewModel.getTokenName(viewModel.getTokenId(it)) },
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
                                getId = { viewModel.getWalletAddress(it) }, // Pass real address
                                getBalance = { null },
                                idLabel = "Addr: "
                            )
                        }
                    }
                }
            }
        }

        if (showBetaDisclaimer) {
            AlertDialog(
                onDismissRequest = { showBetaDisclaimer = false },
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
                            color = ColorAccent, // Using neon green/accent for emphasis
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                containerColor = ColorCard,
                confirmButton = {
                    Button(
                        onClick = {
                            showBetaDisclaimer = false
                            viewModel.prepareSwap(
                                onSuccess = {
                                    onSubmit()
                                },
                                onError = { err ->
                                    android.widget.Toast.makeText(context, err, android.widget.Toast.LENGTH_LONG).show()
                                }
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                    ) {
                        Text("Agree & Continue", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { 
                            showBetaDisclaimer = false 
                        }
                    ) {
                        Text("Disagree", color = Color.White)
                    }
                }
            )
        }
    }
}
@Composable
fun PiggyTopBar(isLoading: Boolean, onSettingsClick: () -> Unit) {
    TogaRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ColorAccent,
                    strokeWidth = 2.dp
                )
            }
        }

        TogaRow(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Piggy", color = ColorText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Image(
                painter = painterResource(id = R.drawable.piggytrade),
                contentDescription = "Logo",
                modifier = Modifier
                    .size(32.dp)
                    .padding(horizontal = 2.dp)
            )
            Text(text = "Trade", color = ColorText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        TogaRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(
                icon = "\uE8B8", // ICON_COG
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp),
                bgColor = Color.Transparent, // No background as requested for transparent top bar
                radius = 10.dp
            )
        }
    }
}



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FavoriteButton(index: Int, fav: String, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, vm: SwapViewModel) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) ColorAccent else ColorInputBg,
        animationSpec = tween(durationMillis = 200)
    )
    
    Box(
        modifier = Modifier
            .size(width = 75.dp, height = 42.dp) // Approx 1/3rd of 115dp asset height
            .androidBorder(radius = 10.dp, borderWidth = 0.dp, bgColor = bgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (fav == "?") {
            // Blank box
            Box(modifier = Modifier.fillMaxSize())
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenImage(tokenId = vm.getTokenId(fav), modifier = Modifier.size(if (index == 0) 24.dp else 22.dp))
                if (fav != "ERG") {
                    val displayName = vm.getTokenName(vm.getTokenId(fav))
                    Text(
                        text = if (displayName.length > 5) displayName.take(5) + "." else displayName,
                        color = Color.White,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
