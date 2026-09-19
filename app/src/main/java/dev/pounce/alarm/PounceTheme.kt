package dev.pounce.alarm

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cream = Color(0xFFFAF6EC)
val Plum = Color(0xFF211F22)
val Peach = Color(0xFFF7DEB1)
val Lilac = Color(0xFFE8EBDD)
val Muted = Color(0xFF666A5E)
val Leaf = Color(0xFF596851)
@Composable fun PounceTheme(content: @Composable () -> Unit) {
 MaterialTheme(colorScheme = lightColorScheme(primary=Color(0xFFB94F32), onPrimary=Cream, primaryContainer=Peach,
  onPrimaryContainer=Plum, secondary=Leaf, background=Cream, surface=Cream, onSurface=Plum,
  onBackground=Plum, surfaceContainerHigh=Cream, surfaceContainerHighest=Lilac, surfaceContainerLow=Cream, surfaceContainer=Cream, secondaryContainer=Lilac, onSecondaryContainer=Plum, surfaceVariant=Lilac, onSurfaceVariant=Muted, outline=Color(0xFFC5C4B5)), content=content)
}

