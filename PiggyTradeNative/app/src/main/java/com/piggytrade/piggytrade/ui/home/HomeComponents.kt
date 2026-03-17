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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ColorCard)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Piggy icon
        Image(
            painter = painterResource(id = R.drawable.piggy_alone),
            contentDescription = "PiggyTrade",
            modifier = Modifier.size(32.dp)
        )

        // Right: Loading and Settings
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            .height(50.dp)
            .background(ColorCard)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
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

        // Portfolio Tab
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabClick("portfolio") }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uE6B1", // pie_chart icon
                fontFamily = MaterialDesignIcons,
                fontSize = 28.sp,
                color = if (activeTab == "portfolio") Color.White else ColorTextDim
            )
            Text(
                text = "Portfolio",
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "portfolio") Color.White else ColorTextDim
            )
        }

        // Ecosystem Tab
        Column(
            modifier = Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onTabClick("ecosystem") }
                ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "\uE80B", // public / globe icon
                fontFamily = MaterialDesignIcons,
                fontSize = 28.sp,
                color = if (activeTab == "ecosystem") Color.White else ColorTextDim
            )
            Text(
                text = "Ecosystem",
                fontSize = 7.sp,
                fontWeight = FontWeight.Bold,
                color = if (activeTab == "ecosystem") Color.White else ColorTextDim
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
