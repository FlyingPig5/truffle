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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

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
                // Wallet Card
                WalletSelectorRow(uiState, viewModel, onNavigateToAddWallet, onNavigateToWalletInfo)

                Spacer(modifier = Modifier.height(10.dp)) // Minimal middle gap

                // Tab Content
                AnimatedContent(
                    targetState = uiState.activeTab,
                    transitionSpec = {
                        if (targetState == "wallet") {
                            // DEX -> Wallet: DEX slides out to left, Wallet slides in from right
                            slideInHorizontally(animationSpec = tween(400)) { it } togetherWith 
                            slideOutHorizontally(animationSpec = tween(400)) { -it }
                        } else {
                            // Wallet -> DEX: Wallet slides out to right, DEX slides in from left
                            slideInHorizontally(animationSpec = tween(400)) { -it } togetherWith 
                            slideOutHorizontally(animationSpec = tween(400)) { it }
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    label = "TabTransition"
                ) { tab ->
                    if (tab == "dex") {
                        TradeCard(modifier = Modifier.fillMaxSize()) {
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
                                        .offset(y = 24.dp), // Positions button over the seam
                                    iconSize = 24.dp,
                                    iconColor = Color.White,
                                    radius = 20.dp,
                                    borderWidth = 0.dp,
                                    bgColor = ColorCard
                                )
                            }

                            // Status Box - Expanded Quote Details
                            OrderDetailsPanel(uiState, viewModel)

                            if (uiState.debugMode) {
                                DebugModeButtons(uiState, viewModel)
                            }

                            Spacer(modifier = Modifier.weight(1f)) // Push button to bottom of card

                            // Swap Button
                            SwapButton(uiState, isSwapValid, fromAmountValue, fromBalanceValue, { showBetaDisclaimer = true })
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp)) {
                            WalletInfoContent(
                                walletName = uiState.selectedWallet,
                                viewModel = viewModel,
                                onDeleteComplete = null,
                                showTitle = true,
                                onNavigateToAddWallet = onNavigateToAddWallet
                            )
                        }
                    }
                }
            }

            // Animated Popup Selector - OUTSIDE BLURRED COLUMN
            TokenSelectorPopup(uiState, viewModel)

            if (showBetaDisclaimer) {
                BetaDisclaimerDialog(showBetaDisclaimer, viewModel, context, onSubmit, { showBetaDisclaimer = false })
            }
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

@Composable
fun BottomMenuBar(
    activeTab: String,
    onTabClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(ColorCard)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DEX Tab
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabClick("dex") }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uE933", // hex value for circle with arrows or similar
                fontFamily = MaterialDesignIcons,
                fontSize = 28.sp,
                color = if (activeTab == "dex") ColorAccent else ColorTextDim
            )
            Text(
                text = "DEX",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "dex") ColorAccent else ColorTextDim
            )
        }

        // Wallet Tab
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabClick("wallet") }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uF8FF", // wallet
                fontFamily = MaterialDesignIcons,
                fontSize = 28.sp,
                color = if (activeTab == "wallet") ColorAccent else ColorTextDim
            )
            Text(
                text = "Wallet",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "wallet") ColorAccent else ColorTextDim
            )
        }
    }
}

// ─── Extracted Composables ───────────────────────────────────────────────────

@Composable
private fun WalletSelectorRow(
    uiState: SwapState,
    viewModel: SwapViewModel,
    onNavigateToAddWallet: () -> Unit,
    onNavigateToWalletInfo: (String) -> Unit
) {
            WalletCard {
                // Spacer(modifier = Modifier.height(10.dp)) // Padding at top of wallet card interior

                // Sub-Row for interactivity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 2.dp)
                        .height(50.dp)
                        .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = ColorInputBg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT: Wallet Selection Zone
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable { /* Handled by local state */ }
                            .padding(start = 0.dp),
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
}

@Composable
private fun FromTokenPanel(uiState: SwapState, viewModel: SwapViewModel) {
    TogaColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
    ) {
        // Favorites: Single Line Scrollable
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

        // from_top_row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(85.dp)
                .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = fromBgColor),
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
                            .height(42.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

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
}
@Composable
private fun ToTokenPanel(uiState: SwapState, viewModel: SwapViewModel) {
    TogaColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
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
                .height(85.dp)
                .androidBorder(radius = 12.dp, borderWidth = 0.dp, bgColor = toBgColor),
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
                            .height(42.dp)
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

                Spacer(modifier = Modifier.height(6.dp))

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
@Composable
private fun OrderDetailsPanel(uiState: SwapState, viewModel: SwapViewModel) {
    TogaColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 5.dp)
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
}
@Composable
private fun DebugModeButtons(uiState: SwapState, viewModel: SwapViewModel) {
    if (uiState.debugMode) {
        TogaRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
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
}
@Composable
private fun SwapButton(
    uiState: SwapState,
    isSwapValid: Boolean,
    fromAmountValue: Double,
    fromBalanceValue: Double,
    onShowDisclaimer: () -> Unit
) {
    Button(
        onClick = {
            onShowDisclaimer()
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
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

@Composable
private fun TokenSelectorPopup(uiState: SwapState, viewModel: SwapViewModel) {
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
private fun BetaDisclaimerDialog(
    showBetaDisclaimer: Boolean,
    viewModel: SwapViewModel,
    context: android.content.Context,
    onSubmit: () -> Unit,
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
                        onDismiss()
                    }
                ) {
                    Text("Disagree", color = Color.White)
                }
            }
        )
    }
}

