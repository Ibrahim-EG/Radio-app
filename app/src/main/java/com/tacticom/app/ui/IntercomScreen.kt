package com.tacticom.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun IntercomScreen(
    activePeersCount: Int,
    amplitude: Float,
    isTransmitting: Boolean,
    onPttStart: () -> Unit,
    onPttStop: () -> Unit,
    onRingPeers: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(8.dp).background(Color(0xFF388BFD), CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "TACTICOM NATIVE",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("PEERS ONLINE: $activePeersCount", color = Color(0xFF8B949E), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            Text(if (isTransmitting) "TX ACTIVE" else "RX READY", color = if (isTransmitting) Color(0xFFF85149) else Color(0xFF2EA043), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("INPUT LEVEL", color = Color(0xFF8B949E), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(12.dp)) {
            drawRect(color = Color(0xFF1C212C), size = size)
            drawRect(
                color = if (amplitude > 0.8f) Color(0xFFF85149) else Color(0xFF2EA043),
                size = Size(width = size.width * amplitude, height = size.height)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // PTT Button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(180.dp)
                .background(
                    if (isTransmitting) Color(0xFFF85149) else Color(0xFF141923),
                    CircleShape
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            onPttStart()
                            tryAwaitRelease()
                            onPttStop()
                        }
                    )
                }
        ) {
            Text(
                text = if (isTransmitting) "TRANSMITTING" else "HOLD TO TALK",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Remote Ring Trigger Button
        Button(
            onClick = onRingPeers,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21262D)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text(
                "RING REMOTE DEVICE",
                color = Color(0xFF58A6FF),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
