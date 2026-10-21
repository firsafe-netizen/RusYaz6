package ru.shkola.russkiy6.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Цвета для отметки верных и неверных ответов. */
object Marks {
    val good = Color(0xFF2E7D32)
    val goodBg = Color(0x332E7D32)
    val bad = Color(0xFFC62828)
    val badBg = Color(0x33C62828)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // Android 15+: всегда доступны динамические цвета Material You
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    MaterialTheme(colorScheme = scheme, content = content)
}
