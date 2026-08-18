package top.yogiczy.mytv.tv.ui.screens.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.Border
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import top.yogiczy.mytv.tv.ui.rememberChildPadding
import top.yogiczy.mytv.tv.ui.screens.settings.LocalSettings
import top.yogiczy.mytv.tv.ui.screens.settings.SettingsCategories
import top.yogiczy.mytv.tv.ui.theme.MyTVTheme
import top.yogiczy.mytv.tv.ui.utils.focusOnLaunchedSaveable
import top.yogiczy.mytv.tv.ui.utils.handleKeyEvents
import top.yogiczy.mytv.tv.ui.utils.ifElse
import top.yogiczy.mytv.tv.ui.utils.saveFocusRestorer

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsCategoryList(
    modifier: Modifier = Modifier,
    currentCategoryProvider: () -> SettingsCategories = { SettingsCategories.entries.first() },
    onCategorySelected: (SettingsCategories) -> Unit = {},
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier.ifElse(
            LocalSettings.current.uiFocusOptimize,
            Modifier.saveFocusRestorer(),
        ),
    ) {
        itemsIndexed(SettingsCategories.entries) { index, category ->
            val isSelected by remember { derivedStateOf { currentCategoryProvider() == category } }

            SettingsCategoryItem(
                modifier = Modifier.ifElse(index == 0, Modifier.focusOnLaunchedSaveable()),
                icon = category.icon,
                title = category.title,
                isSelectedProvider = { isSelected },
                onCategorySelected = { onCategorySelected(category) },
            )
        }
    }
}

@Composable
private fun SettingsCategoryItem(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    isSelectedProvider: () -> Boolean = { false },
    onCategorySelected: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val itemShape = RoundedCornerShape(14.dp)

    ListItem(
        shape = ListItemDefaults.shape(
            shape = itemShape,
        ),
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.28f),
            selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            focusedSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            focusedSelectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        scale = ListItemDefaults.scale(focusedScale = 1f),
        border = ListItemDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                shape = itemShape,
            ),
        ),
        selected = isSelectedProvider(),
        onClick = { },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(21.dp),
                tint = if (isSelectedProvider()) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
            )
        },
        trailingContent = {
            if (isSelectedProvider()) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            color = MaterialTheme.colorScheme.tertiary,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        },
        modifier = modifier
            .height(50.dp)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused || it.hasFocus
                if (isFocused) onCategorySelected()
            }
            .handleKeyEvents(
                isFocused = { isFocused },
                focusRequester = focusRequester,
                onSelect = { focusManager.moveFocus(FocusDirection.Right) },
            ),
    )
}

@Preview
@Composable
private fun SettingsCategoryItemPreview() {
    MyTVTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SettingsCategoryItem(
                icon = SettingsCategories.ABOUT.icon,
                title = SettingsCategories.ABOUT.title,
            )

            SettingsCategoryItem(
                icon = SettingsCategories.ABOUT.icon,
                title = SettingsCategories.ABOUT.title,
                isSelectedProvider = { true },
            )
        }
    }
}

@Preview
@Composable
private fun SettingsCategoryListPreview() {
    MyTVTheme {
        SettingsCategoryList()
    }
}
