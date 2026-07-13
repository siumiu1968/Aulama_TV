package top.yogiczy.mytv.tv.ui.screens.quickop.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents

@Composable
fun QuickOpBtn(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    onSelect: () -> Unit = {},
    onLongSelect: () -> Unit = {},
) {
    val buttonShape = RoundedCornerShape(14.dp)
    Button(
        modifier = modifier
            .handleKeyEvents(
                onSelect = onSelect,
                onLongSelect = onLongSelect,
            ),
        onClick = {},
        shape = ButtonDefaults.shape(shape = buttonShape),
        colors = ButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.76f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        scale = ButtonDefaults.scale(focusedScale = 1.045f),
        border = ButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = buttonShape,
            ),
        ),
        content = {
            Icon(icon, contentDescription = null)
            androidx.compose.foundation.layout.Spacer(Modifier.size(ButtonDefaults.IconSpacing))
            Text(title)
        },
    )
}

@Preview
@Composable
private fun QuickOpBtnPreview() {
    MyTVTheme {
        QuickOpBtn(
            icon = androidx.compose.material.icons.Icons.Default.Settings,
            title = "設定",
        )
    }
}
