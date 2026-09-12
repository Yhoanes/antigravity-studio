package com.antigravity.studio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.studio.model.AntigravityModel
import com.antigravity.studio.model.AntigravityModelCatalog
import com.antigravity.studio.theme.BorderObsidian
import com.antigravity.studio.theme.NeonCyan
import com.antigravity.studio.theme.TextMuted
import com.antigravity.studio.theme.TextPrimary
import com.antigravity.studio.theme.TextSecondary

private val SheetBackgroundColor = Color(0xFF0F172A)
private val TagBadgeBorderColor = Color(0xFF334155)

/**
 * Modal Bottom Sheet allowing the developer to inspect and switch the active generative model.
 * Matches Cyber-Obsidian styling for Antigravity Studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectionSheet(
    selectedModel: AntigravityModel,
    onSelectModel: (AntigravityModel) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    models: List<AntigravityModel> = AntigravityModelCatalog.models
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = SheetBackgroundColor,
        contentColor = TextPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderObsidian.copy(alpha = 0.9f))
                )
            }
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            // Sheet Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Model",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(
                color = BorderObsidian.copy(alpha = 0.6f),
                thickness = 0.5.dp
            )

            // Models List
            LazyColumn(
                modifier = Modifier.fillMaxWidth()
            ) {
                items(models, key = { it.id }) { model ->
                    val isSelected = model.id == selectedModel.id
                    ModelRowItem(
                        model = model,
                        isSelected = isSelected,
                        onClick = {
                            onSelectModel(model)
                        }
                    )
                }
            }

            HorizontalDivider(
                color = BorderObsidian.copy(alpha = 0.6f),
                thickness = 0.5.dp
            )

            // Footer: View Usage external link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://antigravity.google/g1-credits")
                            ).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignored if browser not installed
                        }
                    }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "View Usage",
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "View Usage Link",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Single row inside the ModelSelectionSheet representing a generative engine.
 */
@Composable
private fun ModelRowItem(
    model: AntigravityModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Model displayName + tags
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = model.displayName,
                color = if (isSelected) TextPrimary else TextPrimary.copy(alpha = 0.92f),
                fontSize = 14.5.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
            )

            model.tags.forEach { tag ->
                ModelTagBadge(tag = tag)
            }
        }

        // Right: Checkmark (if active) + subtle chevron '>'
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NeonCyan,
                    modifier = Modifier.size(18.dp)
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Capsule tag badge for performance tags (e.g. "High", "Fast", "Thinking").
 */
@Composable
private fun ModelTagBadge(
    tag: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BorderObsidian)
            .border(0.5.dp, TagBadgeBorderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = tag,
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            lineHeight = 12.sp
        )
    }
}
