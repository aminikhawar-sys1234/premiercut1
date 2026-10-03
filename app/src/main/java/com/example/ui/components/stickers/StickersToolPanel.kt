package com.example.ui.components.stickers

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.presets.StickerPresetItem
import com.example.data.presets.StickersCatalog
import com.example.ui.StudioViewModel
import kotlinx.coroutines.delay

// Modern Studio Palette (Deep obsidian surface with vibrant emerald/cyan accents)
private val PanelDarkBg = Color(0xFF090E18)
private val PanelBorder = Color(0xFF1B2A42)
private val CardBg = Color(0xFF0F1829)
private val CardBorder = Color(0xFF17283E)
private val EmeraldAccent = Color(0xFF00D1B2)
private val TextWhite = Color(0xFFFFFFFF)
private val TextMuted = Color(0xFF94A3B8)
private val CategoryInactiveBg = Color(0xFF131D2E)
private val CategoryInactiveBorder = Color(0xFF1E2F48)

@Composable
fun StickersToolPanel(
    viewModel: StudioViewModel,
    onClose: () -> Unit = { viewModel.setActiveToolbarTab(null) },
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var addedFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(addedFeedbackText) {
        if (addedFeedbackText != null) {
            delay(1200)
            addedFeedbackText = null
        }
    }

    Surface(
        color = PanelDarkBg,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        border = BorderStroke(1.dp, PanelBorder),
        shadowElevation = 16.dp,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {} // Consume all touches so timeline underneath isn't triggered
            .testTag("stickers_panel")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .navigationBarsPadding()
        ) {
            // ---------------------------------------------------------------------------------
            // 1. DRAG HANDLE & SLIM COMPACT HEADER (~38dp)
            // ---------------------------------------------------------------------------------
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF2C3E5B))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Stickers",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Stickers",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextWhite,
                            fontSize = 15.sp
                        )
                    )

                    // Subtle temporary feedback pill when sticker is added
                    AnimatedVisibility(
                        visible = addedFeedbackText != null,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = EmeraldAccent.copy(alpha = 0.2f),
                            border = BorderStroke(1.dp, EmeraldAccent)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = EmeraldAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = addedFeedbackText ?: "",
                                    color = EmeraldAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("stickers_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ---------------------------------------------------------------------------------
            // 2. CATEGORIES ROW (All | Reactions | Emotes | Social | Arrows | Celebration | Symbols | Badges)
            // ---------------------------------------------------------------------------------
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StickersCatalog.CATEGORIES.forEach { categoryName ->
                    val isSelected = categoryName.equals(selectedCategory, ignoreCase = true)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) EmeraldAccent else CategoryInactiveBg,
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) EmeraldAccent else CategoryInactiveBorder
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { selectedCategory = categoryName }
                            .testTag("sticker_cat_${categoryName.lowercase()}")
                    ) {
                        Text(
                            text = categoryName,
                            color = if (isSelected) Color.Black else TextWhite,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // ---------------------------------------------------------------------------------
            // 3. REAL STICKERS GRID (Lightweight, Tap-To-Add, 5-6 Columns)
            // ---------------------------------------------------------------------------------
            val stickers = remember(selectedCategory) {
                StickersCatalog.getItemsForCategory(selectedCategory)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 52.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stickers, key = { it.id }) { stickerItem ->
                        StickerCardItem(
                            item = stickerItem,
                            onClick = {
                                if (stickerItem.badgeType != null) {
                                    viewModel.timelineEngine.addBadge(stickerItem.badgeType)
                                } else {
                                    viewModel.timelineEngine.addStickerClip(
                                        emojiOrAsset = stickerItem.symbolOrAsset,
                                        category = stickerItem.category
                                    )
                                }
                                addedFeedbackText = "Added ${stickerItem.symbolOrAsset}"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerCardItem(
    item: StickerPresetItem,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = CardBg,
        border = BorderStroke(1.dp, CardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("sticker_item_${item.id}")
    ) {
        if (item.badgeType != null) {
            // Badges display with pill background & icon
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = item.symbolOrAsset,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(item.badgeType.primaryColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.name,
                        fontSize = 8.5.sp,
                        color = TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Emojis/Stickers display with large, clear symbol & compact label
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.symbolOrAsset,
                    fontSize = 26.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.name,
                    color = TextMuted,
                    fontSize = 8.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
