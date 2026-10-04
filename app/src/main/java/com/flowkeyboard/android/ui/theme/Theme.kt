package com.flowkeyboard.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import com.flowkeyboard.android.model.KeyboardSkin

@Composable
fun FlowKeyboardTheme(
    skin: KeyboardSkin = KeyboardSkin.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when (skin) {
        KeyboardSkin.SYSTEM -> {
            when {
                Build.VERSION.SDK_INT >= 31 && darkTheme -> dynamicDarkColorScheme(context)
                Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
                darkTheme -> ObsidianPureScheme
                else -> GlacierCleanScheme
            }
        }
        KeyboardSkin.OBSIDIAN_PURE -> ObsidianPureScheme
        KeyboardSkin.GLACIER_CLEAN -> GlacierCleanScheme
        KeyboardSkin.CYBER_AURORA -> CyberAuroraScheme
        KeyboardSkin.DUSK_SUNSET -> DuskSunsetScheme
        KeyboardSkin.MORANDI_MINT -> MorandiMintScheme
        KeyboardSkin.SAKURA_BLOSSOM -> SakuraBlossomScheme
        KeyboardSkin.IRIS_TWILIGHT -> IrisTwilightScheme
    }
    MaterialTheme(colorScheme = colors, typography = FlowTypography, content = content)
}
