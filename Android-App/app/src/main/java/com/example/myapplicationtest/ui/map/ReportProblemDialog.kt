package com.example.myapplicationtest.ui.map

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myapplicationtest.model.LocationChoice
import com.example.myapplicationtest.model.RoadProblemType
import com.example.myapplicationtest.model.RoadSide
import com.example.myapplicationtest.model.Severity
import com.example.myapplicationtest.util.bearingToCardinal
import com.google.android.gms.maps.model.LatLng
import java.io.File

@Composable
fun ReportProblemDialog(
    locationChoice: LocationChoice,
    onLocationChoiceChange: (LocationChoice) -> Unit,
    roadSide: RoadSide,
    onRoadSideChange: (RoadSide) -> Unit,
    directionBearing: Float?,
    onDirectionBearingChange: (Float?) -> Unit,
    deviceHeading: Float?,
    onPickDirectionOnMap: () -> Unit,
    problemType: RoadProblemType,
    onProblemTypeChange: (RoadProblemType) -> Unit,
    severity: Severity,
    onSeverityChange: (Severity) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    photoUri: Uri?,
    onPhotoUriChange: (Uri?) -> Unit,
    mapPickedLocation: LatLng?,
    onChooseOnMap: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current

    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempImageUri(): Uri {
        val dir = File(context.cacheDir, "images").also { it.mkdirs() }
        val file = File.createTempFile("report_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            onPhotoUriChange(tempCameraUri)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onPhotoUriChange(uri)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createTempImageUri()
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    val wordCount = if (description.isBlank()) 0 else description.trim().split("\\s+".toRegex()).size
    val charCount = description.length
    val descriptionError = when {
        charCount > 100 -> "Max 100 characters (${charCount}/100)"
        wordCount > 20 -> "Max 20 words (${wordCount}/20)"
        else -> null
    }

    val isDescriptionValid = descriptionError == null
    val canSubmit = when (locationChoice) {
        LocationChoice.CHOOSE_ON_MAP -> mapPickedLocation != null
        else -> true
    } && when (problemType) {
        RoadProblemType.OTHER -> description.isNotBlank() && isDescriptionValid
        else -> isDescriptionValid // optional, but if provided must be within limits
    } && (roadSide == RoadSide.BOTH || directionBearing != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Report a Problem", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Location",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    LocationChoice.entries.forEachIndexed { index, choice ->
                        SegmentedButton(
                            selected = locationChoice == choice,
                            onClick = {
                                onLocationChoiceChange(choice)
                                if (choice == LocationChoice.CHOOSE_ON_MAP && mapPickedLocation == null) {
                                    onChooseOnMap()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = LocationChoice.entries.size
                            ),
                            icon = { }
                        ) {
                            Text(
                                text = when (choice) {
                                    LocationChoice.CURRENT_LOCATION -> "Current location"
                                    LocationChoice.CHOOSE_ON_MAP -> if (mapPickedLocation != null)
                                        "Map location ✓"
                                    else
                                        "Choose on map"
                                },
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Side of the road",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val sideOptions = listOf(RoadSide.BOTH to "Both directions", RoadSide.MY_SIDE to "One direction")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    sideOptions.forEachIndexed { index, (side, label) ->
                        SegmentedButton(
                            selected = if (side == RoadSide.BOTH) roadSide == RoadSide.BOTH else roadSide != RoadSide.BOTH,
                            onClick = { onRoadSideChange(side) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = sideOptions.size
                            ),
                            icon = { }
                        ) {
                            Text(
                                text = label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                if (roadSide == RoadSide.BOTH) {
                    Text(
                        text = "Problem affects traffic in both directions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (directionBearing != null) "➤" else "❓",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.rotate((directionBearing ?: 0f) - 90f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (directionBearing != null)
                                        "Affects ${bearingToCardinal(directionBearing.toDouble())}-bound traffic"
                                    else
                                        "Which way is the affected traffic going?",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onDirectionBearingChange(deviceHeading) },
                                    enabled = deviceHeading != null,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("🧭 My heading", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = onPickDirectionOnMap,
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("🗺 Point on map", maxLines = 1, style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            Text(
                                text = "…or pick a compass direction:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            val cardinals = listOf(
                                "N" to 0f, "NE" to 45f, "E" to 90f, "SE" to 135f,
                                "S" to 180f, "SW" to 225f, "W" to 270f, "NW" to 315f,
                            )
                            cardinals.chunked(4).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    row.forEach { (label, degrees) ->
                                        val isSelected = directionBearing != null &&
                                                bearingToCardinal(directionBearing.toDouble()) == label
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { onDirectionBearingChange(degrees) },
                                            label = {
                                                Text(
                                                    label,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    textAlign = TextAlign.Center,
                                                    style = MaterialTheme.typography.labelMedium
                                                )
                                            },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }

                            if (deviceHeading == null) {
                                Text(
                                    text = "🧭 heading is only available while you are moving",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Problem Type",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RoadProblemType.entries.forEach { type ->
                        val isSelected = problemType == type
                        val typeColor = Color(type.color)
                        OutlinedButton(
                            onClick = { onProblemTypeChange(type) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) typeColor.copy(alpha = 0.15f) else Color.Transparent,
                                contentColor = if (isSelected) typeColor else MaterialTheme.colorScheme.onSurface
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) typeColor else MaterialTheme.colorScheme.outline
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                        ) {
                            Icon(
                                painter = painterResource(type.iconRes),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) typeColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = type.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                            )
                        }
                    }
                }

                val isDescriptionRequired = problemType == RoadProblemType.OTHER
                OutlinedTextField(
                    value = description,
                    onValueChange = { newVal ->
                        if (newVal.length <= 100) {
                            onDescriptionChange(newVal)
                        }
                    },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Description")
                            if (!isDescriptionRequired) {
                                Text(
                                    text = "  (Optional)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    fontStyle = FontStyle.Italic
                                )
                            }
                        }
                    },
                    placeholder = {
                        Text(
                            if (isDescriptionRequired) "Describe the issue…"
                            else "Add a note… (optional)"
                        )
                    },
                    isError = descriptionError != null,
                    supportingText = {
                        Text(
                            text = descriptionError ?: "${wordCount}/20 words · ${charCount}/100 chars",
                            color = if (descriptionError != null)
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Severity",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                val severityRows = Severity.entries.chunked(2)
                severityRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { sev ->
                            val isSelected = severity == sev
                            val sevColor = Color(sev.color)
                            OutlinedButton(
                                onClick = { onSeverityChange(sev) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) sevColor.copy(alpha = 0.2f) else Color.Transparent,
                                    contentColor = if (isSelected) sevColor else MaterialTheme.colorScheme.onSurface
                                ),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) sevColor else MaterialTheme.colorScheme.outline
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(sevColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = sev.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    "Photo (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("📷 Camera", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("🖼 Gallery", maxLines = 1)
                    }
                }

                if (photoUri != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(12.dp)
                            )
                    ) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Selected photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = { onPhotoUriChange(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(28.dp)
                                .background(
                                    MaterialTheme.colorScheme.errorContainer,
                                    CircleShape
                                )
                        ) {
                            Text(
                                "✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmit,
                enabled = canSubmit
            ) {
                Text("Submit")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
