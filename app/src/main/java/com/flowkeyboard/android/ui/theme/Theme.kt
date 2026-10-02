package com.flowkeyboard.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun FlowKeyboardTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme(primary = FlowMint, background = FlowInk)
        else -> lightColorScheme(primary = FlowGreen, primaryContainer = FlowMint, background = FlowCream)
    }
    MaterialTheme(colorScheme = colors, typography = FlowTypography, content = content)
}
