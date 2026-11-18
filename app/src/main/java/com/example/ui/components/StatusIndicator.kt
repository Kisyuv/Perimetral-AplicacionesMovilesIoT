package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.AccentAlarm
import com.example.ui.theme.DotoFamily
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import androidx.compose.ui.text.font.FontWeight
import com.example.ui.theme.SystemAlarmState

@Composable
fun StatusIndicator(
    systemState: SystemAlarmState,
    modifier: Modifier = Modifier
) {
    // Estado en texto + punto: ARMADA = punto lleno, DESARMADA = punto hueco, ALARMA = punto rojo
    val accentColor = if (systemState == SystemAlarmState.ALARM) AccentAlarm else TextPrimary
    val dotModifier = Modifier.size(8.dp).clip(CircleShape)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
            .border(1.dp, DarkBorder, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("status_indicator_badge"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = if (systemState == SystemAlarmState.DISARMED) dotModifier.border(1.dp, TextSecondary, CircleShape)
            else dotModifier.background(accentColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = systemState.label,
            color = accentColor,
            fontFamily = DotoFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp
        )
    }
}
