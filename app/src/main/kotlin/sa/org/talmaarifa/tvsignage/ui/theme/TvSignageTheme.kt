package sa.org.talmaarifa.tvsignage.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

private val SignageColorScheme = darkColorScheme()

@Composable
fun TvSignageTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SignageColorScheme, content = content)
}
