package com.piggytrade.piggytrade.ui.home
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.swap.SwapViewModel
import com.piggytrade.piggytrade.ui.wallet.*
import com.piggytrade.piggytrade.ui.settings.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.piggytrade.piggytrade.R

@Composable
fun PiggyTopBar(isLoading: Boolean, onSettingsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 15.dp)
    ) {
        // Center: Branding
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Piggy", color = ColorText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Image(
                painter = painterResource(id = R.drawable.logo_topbar_and_standard_launcher),
                contentDescription = "Logo",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(120.dp)
                    .padding(horizontal = 4.dp)
            )
            Text(text = "Trade", color = ColorText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        // Right side: Loading and Settings
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ColorAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            TogaIconButton(
                icon = "\uE8B8", // ICON_COG
                onClick = onSettingsClick,
                modifier = Modifier.size(40.dp),
                bgColor = Color.Transparent, 
                radius = 10.dp
            )
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
                color = if (activeTab == "dex") Color.White else ColorTextDim
            )
            Text(
                text = "DEX",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "dex") Color.White else ColorTextDim
            )
        }

        // BANK Tab
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabClick("bank") }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uE84F", // bank icon
                fontFamily = MaterialDesignIcons,
                fontSize = 28.sp,
                color = if (activeTab == "bank") Color.White else ColorTextDim
            )
            Text(
                text = "Stablecoins",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "bank") Color.White else ColorTextDim
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
                color = if (activeTab == "wallet") Color.White else ColorTextDim
            )
            Text(
                text = "Wallet",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "wallet") Color.White else ColorTextDim
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
