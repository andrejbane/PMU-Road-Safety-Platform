package com.example.myapplicationtest.ui.profile

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplicationtest.R
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.User
import com.example.myapplicationtest.model.Vote
import com.example.myapplicationtest.ui.common.ProblemDetailDialog
import com.example.myapplicationtest.ui.theme.MyApplicationTestTheme
import com.example.myapplicationtest.util.compressAndEncodeToBase64
import com.example.myapplicationtest.util.decodeBase64ToBytes
import com.example.myapplicationtest.util.formatCreatedAt

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    user: User = User("John Doe", "john.doe@example.com"),
    userReports: List<RoadProblem> = emptyList(),
    myVotes: List<Vote> = emptyList(),
    onLogout: () -> Unit = {},
    onDeleteReport: (String) -> Unit = {},
    onVoteChange: (problemId: String, newVoteType: String) -> Unit = { _, _ -> },
    onVoteRemove: (problemId: String) -> Unit = {},
    onUpdateDisplayUsername: (String) -> Unit = {},
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit = { _, _ -> },
    onUpdatePhoto: (String?) -> Unit = {},
    profileUpdateError: String? = null,
    profileUpdateSuccess: String? = null,
    onClearProfileUpdate: () -> Unit = {},
) {
    val context = LocalContext.current
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    fun createTempImageUri(): Uri {
        val dir = File(context.cacheDir, "images").also { it.mkdirs() }
        val file = File.createTempFile("profile_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempCameraUri?.let { uri ->
            compressAndEncodeToBase64(context, uri)?.let { onUpdatePhoto(it) }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val encoded = compressAndEncodeToBase64(context, it)
            if (encoded != null) onUpdatePhoto(encoded)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val uri = createTempImageUri()
            tempCameraUri = uri
            takePictureLauncher.launch(uri)
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Profile Photo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("📷  Take Photo") }
                    OutlinedButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🖼  Choose from Gallery") }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Cancel") } },
        )
    }

    var showReports by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<RoadProblem?>(null) }
    var showVotes by remember { mutableStateOf(false) }
    var selectedVote by remember { mutableStateOf<Vote?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    var newDisplayUsername by remember { mutableStateOf(user.displayUsername ?: "") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    LaunchedEffect(user.displayUsername) { newDisplayUsername = user.displayUsername ?: "" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        val avatarBytes = remember(user.photoBase64) { user.photoBase64?.let { decodeBase64ToBytes(it) } }
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiaryContainer),
            contentAlignment = Alignment.Center
        ) {
            if (avatarBytes != null) {
                AsyncImage(
                    model = avatarBytes,
                    contentDescription = "Profile photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_account_box),
                    contentDescription = "Profile",
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = user.displayUsername ?: user.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        ExpandableCard(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_marker_problem),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            iconBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            title = "My Reports",
            subtitle = "${userReports.size} report${if (userReports.size != 1) "s" else ""}",
            expanded = showReports,
            onToggle = { showReports = !showReports },
        ) {
            if (userReports.isEmpty()) {
                Text(
                    text = "You haven't reported any problems yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                userReports.forEachIndexed { index, report ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReport = report }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(Color(report.severity.color), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = report.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${report.severity.label} · ${report.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val dateStr = formatCreatedAt(report.createdAt)
                            if (dateStr.isNotEmpty()) {
                                Text(
                                    text = dateStr,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "👍 ${report.upvotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                            Text(
                                text = "👎 ${report.downvotes}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                    if (index < userReports.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExpandableCard(
            icon = { Text("👍", style = MaterialTheme.typography.titleMedium) },
            iconBackground = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
            title = "My Votes",
            subtitle = "${myVotes.size} vote${if (myVotes.size != 1) "s" else ""}",
            expanded = showVotes,
            onToggle = { showVotes = !showVotes },
        ) {
            if (myVotes.isEmpty()) {
                Text(
                    text = "You haven't voted on any reports yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                myVotes.forEachIndexed { index, vote ->
                    val problem = vote.problem ?: return@forEachIndexed
                    val isUpvote = vote.voteType == "UPVOTE"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedVote = vote }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isUpvote) "👍" else "👎",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(problem.severity.color), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = problem.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "${problem.severity.label} · ${problem.description}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val votedDateStr = formatCreatedAt(vote.votedAt)
                            if (votedDateStr.isNotEmpty()) {
                                Text(
                                    text = "Voted: $votedDateStr",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                    if (index < myVotes.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ExpandableCard(
            icon = {
                Icon(
                    painter = painterResource(R.drawable.ic_account_box),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            },
            iconBackground = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
            title = "Account Settings",
            subtitle = "Photo · Username · Password",
            expanded = showSettings,
            onToggle = { showSettings = !showSettings; onClearProfileUpdate() },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                profileUpdateSuccess?.let {
                    Text(
                        text = "✓ $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                profileUpdateError?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = "Profile Photo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val previewBytes = remember(user.photoBase64) { user.photoBase64?.let { decodeBase64ToBytes(it) } }
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBytes != null) {
                            AsyncImage(
                                model = previewBytes,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.ic_account_box),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { showPhotoSourceDialog = true }) {
                            Text("Choose Photo")
                        }
                        if (user.photoBase64 != null) {
                            OutlinedButton(
                                onClick = { onUpdatePhoto(null) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) {
                                Text("Remove Photo")
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Text(
                    text = "Display Username",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Shown on your reports instead of your email.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                OutlinedTextField(
                    value = newDisplayUsername,
                    onValueChange = { newDisplayUsername = it },
                    label = { Text("Username") },
                    placeholder = { Text(user.email) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = { onUpdateDisplayUsername(newDisplayUsername); onClearProfileUpdate() },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Save Username") }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                Text(
                    text = "Change Password",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text("Current password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("New password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    isError = confirmPassword.isNotEmpty() && confirmPassword != newPassword,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && confirmPassword != newPassword)
                            Text("Passwords do not match")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Button(
                    onClick = {
                        onChangePassword(currentPassword, newPassword)
                        currentPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                        onClearProfileUpdate()
                    },
                    enabled = currentPassword.isNotBlank() && newPassword.isNotBlank() && newPassword == confirmPassword,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Change Password") }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        ) {
            Text("Log Out", fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    selectedReport?.let { report ->
        ProblemDetailDialog(
            problem = report,
            onDismiss = { selectedReport = null },
            footerStart = {
                Text(
                    text = "👍 ${report.upvotes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Text(
                    text = "👎 ${report.downvotes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (report.id != null) {
                    TextButton(
                        onClick = { onDeleteReport(report.id); selectedReport = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
            },
        )
    }

    selectedVote?.let { vote ->
        val problem = vote.problem ?: return@let
        val isUpvote = vote.voteType == "UPVOTE"

        ProblemDetailDialog(
            problem = problem,
            onDismiss = { selectedVote = null },
            extraContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isUpvote) Color(0xFFE8F5E9)
                        else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = "Your vote: ${if (isUpvote) "👍 Upvote" else "👎 Downvote"}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isUpvote) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    formatCreatedAt(vote.votedAt).takeIf { it.isNotEmpty() }?.let {
                        Text(
                            text = "Voted: $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                    }
                }
            },
            footerStart = {
                Text(
                    text = "👍 ${problem.upvotes}  👎 ${problem.downvotes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (problem.id != null) {
                    TextButton(
                        onClick = {
                            onVoteChange(problem.id, if (isUpvote) "DOWNVOTE" else "UPVOTE")
                            selectedVote = null
                        }
                    ) {
                        Text(
                            text = "Change to ${if (isUpvote) "👎" else "👍"}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = { onVoteRemove(problem.id); selectedVote = null },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove", style = MaterialTheme.typography.labelMedium)
                    }
                }
            },
        )
    }
}

@Composable
private fun ExpandableCard(
    icon: @Composable () -> Unit,
    iconBackground: Color,
    title: String,
    subtitle: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) { icon() }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
                Text(
                    text = if (expanded) "▲" else "▼",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                content()
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ProfileScreenPreview() {
    MyApplicationTestTheme {
        ProfileScreen()
    }
}
