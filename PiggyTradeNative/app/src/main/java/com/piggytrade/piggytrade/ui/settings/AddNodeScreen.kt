package com.piggytrade.piggytrade.ui.settings
import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*
import com.piggytrade.piggytrade.ui.home.*
import com.piggytrade.piggytrade.ui.swap.*
import com.piggytrade.piggytrade.ui.wallet.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun AddNodeScreen(
    onBack: () -> Unit,
    allowHttpNodes: Boolean = false
) {
    var nodeUrl by remember { mutableStateOf("") }
    var nodeName by remember { mutableStateOf("") }

    val isHttpUrl = nodeUrl.trimStart().lowercase().startsWith("http://")
    val httpBlocked = isHttpUrl && !allowHttpNodes

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ColorBg)
    ) {
        // Header
        TogaRow(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TogaIconButton(icon = "\uEF7D", onClick = onBack, modifier = Modifier.size(36.dp), radius = 10.dp, bgColor = ColorBlue)
            Text(text = "Add Custom Node", color = ColorText, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp))
        }

        TogaColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 10.dp)
                .androidBorder(radius = 30.dp, borderWidth = 0.dp, bgColor = ColorCard)
                .padding(30.dp)
        ) {
            Text("NODE URL", color = ColorText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
            OutlinedTextField(
                value = nodeUrl,
                onValueChange = { nodeUrl = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = if (httpBlocked || (isHttpUrl && allowHttpNodes)) 5.dp else 20.dp),
                placeholder = { Text("https://...", color = ColorInputHint) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ColorInputBg,
                    unfocusedContainerColor = ColorInputBg,
                    focusedBorderColor = if (httpBlocked) Color(0xFFFF4444) else Color(0xFF535C6E),
                    unfocusedBorderColor = if (httpBlocked) Color(0xFFFF4444) else Color(0xFF535C6E)
                )
            )

            if (httpBlocked) {
                Text(
                    text = "⛔ HTTP nodes are blocked by default. Enable 'Allow HTTP Nodes' in Settings → Advanced to use this URL.",
                    color = Color(0xFFFF4444),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
            } else if (isHttpUrl && allowHttpNodes) {
                Text(
                    text = "⚠️ This is an HTTP (unencrypted) node. Traffic can be intercepted.",
                    color = Color(0xFFFF6B35),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 15.dp)
                )
            }

            Text("NAME (OPTIONAL)", color = ColorText, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 5.dp))
            OutlinedTextField(
                value = nodeName,
                onValueChange = { nodeName = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 30.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = ColorInputBg,
                    unfocusedContainerColor = ColorInputBg,
                    focusedBorderColor = Color(0xFF535C6E),
                    unfocusedBorderColor = Color(0xFF535C6E)
                )
            )

            Button(
                onClick = { /* TODO add node and back */ onBack() },
                enabled = nodeUrl.isNotEmpty() && !httpBlocked,
                modifier = Modifier.fillMaxWidth().height(55.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ColorBlue,
                    disabledContainerColor = Color(0xFF1C1C1C),
                    disabledContentColor = Color(0xFF555555)
                )
            ) {
                Text("Add Node", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
