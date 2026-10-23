package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.navigation.Screen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.LocalAlarmAccentColor
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun PerimetralBottomBar(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = LocalAlarmAccentColor.current
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(DarkBackground)
            .border(width = 1.dp, color = DarkBorderSubtle)
            .navigationBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp)
            .horizontalScroll(scrollState)
            .testTag("perimetral_bottom_bar"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Screen.values().forEach { screen ->
            val isSelected = screen == currentScreen
            val tabAccent = if (isSelected) accentColor else TextMuted

            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) DarkSurface else DarkBackground)
                    .clickable { onScreenSelected(screen) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .testTag("nav_item_${screen.route}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = screen.icon,
                    contentDescription = screen.title,
                    tint = tabAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = when (screen) {
                        Screen.DASHBOARD -> "Estado"
                        Screen.ZONES -> "Zonas"
                        Screen.EVENTS -> "Eventos"
                        Screen.SCHEDULE -> "Horarios"
                        Screen.NETWORK -> "Red WiFi"
                        Screen.SECURITY -> "Seguridad"
                        Screen.TESTS -> "Pruebas"
                    },
                    color = if (isSelected) TextSecondary else TextMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}
