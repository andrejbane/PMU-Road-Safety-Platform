package com.example.myapplicationtest.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.util.decodeBase64ToBytes
import com.example.myapplicationtest.util.formatCreatedAt
import com.example.myapplicationtest.util.roadSideDescription

/**
 * The problem detail popup used on the map, reusable from other screens.
 * Dismissed with the ✕ in the top corner. [extraContent] is appended to the
 * scrollable body (after the photo); [footerStart] fills the bottom bar,
 * which is omitted when null.
 */
@Composable
fun ProblemDetailDialog(
    problem: RoadProblem,
    onDismiss: () -> Unit,
    extraContent: @Composable ColumnScope.() -> Unit = {},
    footerStart: (@Composable RowScope.() -> Unit)? = null,
) {
    val imageModel: Any? = problem.photoUri
        ?: problem.photoBase64?.let { decodeBase64ToBytes(it) }?.takeIf { it.isNotEmpty() }
    var imageFailed by remember(problem.id ?: problem.title) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Box {
            Column {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 24.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(Color(problem.type.color), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(problem.type.iconRes),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = problem.title,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(Color(problem.severity.color), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = problem.severity.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                formatCreatedAt(problem.createdAt).takeIf { it.isNotEmpty() }?.let {
                                    Text(
                                        text = "  ·  $it",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val badgeBg = if (problem.official) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.secondaryContainer
                        val badgeFg = if (problem.official) Color(0xFF2E7D32)
                        else MaterialTheme.colorScheme.onSecondaryContainer
                        Surface(shape = RoundedCornerShape(50), color = badgeBg) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (problem.official) "✔" else "👥",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = badgeFg
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (problem.official) "Official" else "Community",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeFg
                                )
                            }
                        }
                        problem.reportedBy?.takeIf { it.isNotEmpty() }?.let {
                            Text(
                                text = "by $it",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            problem.affectedBearing?.let { bearing ->
                                Text(
                                    text = "➤",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate(bearing.toFloat() - 90f)
                                )
                            } ?: Text("🛣", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = roadSideDescription(problem),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (problem.description.isNotBlank() && problem.description != problem.title) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = problem.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (imageModel != null && !imageFailed) {
                        AsyncImage(
                            model = imageModel,
                            contentDescription = "Report photo",
                            onError = { imageFailed = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    extraContent()
                }

                if (footerStart != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        footerStart()
                    }
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                    .clickable(onClick = onDismiss),
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            }
        }
    }
}
