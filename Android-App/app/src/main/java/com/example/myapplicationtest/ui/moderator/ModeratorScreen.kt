package com.example.myapplicationtest.ui.moderator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.RoadProblemType
import com.example.myapplicationtest.model.Severity
import com.example.myapplicationtest.model.UserActivity
import com.example.myapplicationtest.model.UserProfile
import com.example.myapplicationtest.model.Vote
import com.example.myapplicationtest.ui.common.ProblemDetailDialog
import com.example.myapplicationtest.util.formatCreatedAt
import com.example.myapplicationtest.util.getApiKeyFromManifest
import com.example.myapplicationtest.util.reverseGeocode
import com.example.myapplicationtest.viewmodel.ModeratorViewModel
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.CameraPosition
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState

@Composable
fun ModeratorScreen(
    modifier: Modifier = Modifier,
    viewModel: ModeratorViewModel,
    token: String,
    canManageUsers: Boolean = true,
    onProblemDeleted: (String) -> Unit = {},
) {
    val users by viewModel.users.collectAsState()
    val problems by viewModel.problems.collectAsState()
    val selectedActivity by viewModel.selectedActivity.collectAsState()
    val problemVotes by viewModel.problemVotes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val tabs = if (canManageUsers) listOf("Users", "Reports") else listOf("Reports")
    var selectedTab by remember(canManageUsers) { mutableIntStateOf(0) }
    val currentTab = tabs[selectedTab.coerceIn(0, tabs.size - 1)]

    LaunchedEffect(currentTab) {
        if (currentTab == "Users") viewModel.loadUsers(token)
        else viewModel.loadProblems(token)
    }

    if (selectedActivity != null) {
        UserActivityDialog(
            activity = selectedActivity!!,
            token = token,
            onDismiss = { viewModel.clearSelectedActivity() },
            onDisable = { viewModel.disableUser(token, it) },
            onEnable = { viewModel.enableUser(token, it) },
            onDeleteUser = { viewModel.deleteUser(token, it) },
            onDeleteProblem = { id -> viewModel.deleteProblem(token, id); onProblemDeleted(id) },
            onDeleteVote = { voteId -> viewModel.deleteVote(token, voteId, "") },
        )
    }

    error?.let {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Error") },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = "Moderator Panel",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            when (currentTab) {
                "Users" -> UsersTab(
                    users = users,
                    onViewActivity = { viewModel.loadUserActivity(token, it.id) },
                    onDisable = { viewModel.disableUser(token, it) },
                    onEnable = { viewModel.enableUser(token, it) },
                    onDelete = { viewModel.deleteUser(token, it) },
                )
                else -> ReportsTab(
                    problems = problems,
                    problemVotes = problemVotes,
                    onLoadVotes = { viewModel.loadProblemVotes(token, it) },
                    onDeleteProblem = { id -> viewModel.deleteProblem(token, id); onProblemDeleted(id) },
                    onDeleteVote = { voteId, problemId -> viewModel.deleteVote(token, voteId, problemId) },
                )
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<UserProfile>,
    onViewActivity: (UserProfile) -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredUsers = if (searchQuery.isBlank()) users else {
        val q = searchQuery.trim().lowercase()
        users.filter {
            it.username.lowercase().contains(q) ||
            it.email.lowercase().contains(q) ||
            it.name.lowercase().contains(q) ||
            it.displayUsername?.lowercase()?.contains(q) == true
        }
    }

    if (confirmDeleteId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("Delete User") },
            text = { Text("This will permanently delete the user and all their reports and votes. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDelete(confirmDeleteId!!); confirmDeleteId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search users") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        }
        items(filteredUsers, key = { it.id }) { user ->
            UserCard(
                user = user,
                onViewActivity = { onViewActivity(user) },
                onDisable = { onDisable(user.id) },
                onEnable = { onEnable(user.id) },
                onDelete = { confirmDeleteId = user.id },
            )
        }
    }
}

@Composable
private fun UserCard(
    user: UserProfile,
    onViewActivity: () -> Unit,
    onDisable: () -> Unit,
    onEnable: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(user.name.ifBlank { user.username }, fontWeight = FontWeight.SemiBold)
                    Text(user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    user.displayUsername?.let {
                        Text("@$it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RoleBadge(user.role)
                    if (user.disabled) StatusBadge("Disabled", Color(0xFFE53935))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onViewActivity, modifier = Modifier.weight(1f)) {
                    Text("Activity", fontSize = 12.sp)
                }
                if (user.disabled) {
                    OutlinedButton(onClick = onEnable, modifier = Modifier.weight(1f)) {
                        Text("Enable", fontSize = 12.sp)
                    }
                } else {
                    OutlinedButton(onClick = onDisable, modifier = Modifier.weight(1f)) {
                        Text("Disable", fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontSize = 12.sp)
                }
            }
        }
    }
}

private enum class ReportSort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    MOST_UPVOTED("▲ Upvotes"),
    MOST_DOWNVOTED("▼ Downvotes"),
    SEVERITY("Severity"),
}

@Composable
private fun ReportsTab(
    problems: List<RoadProblem>,
    problemVotes: Map<String, List<Vote>>,
    onLoadVotes: (String) -> Unit,
    onDeleteProblem: (String) -> Unit,
    onDeleteVote: (voteId: String, problemId: String) -> Unit,
) {
    var confirmDeleteProblemId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<RoadProblemType?>(null) }
    var severityFilter by remember { mutableStateOf<Severity?>(null) }
    var sourceFilter by remember { mutableStateOf<Boolean?>(null) } // null = all, true = official
    var sortOption by remember { mutableStateOf(ReportSort.NEWEST) }
    var selectedReport by remember { mutableStateOf<RoadProblem?>(null) }

    val context = LocalContext.current
    val apiKey = remember { getApiKeyFromManifest(context) }
    // Resolved street addresses, keyed by report id (or coordinates when unsaved).
    val addressCache = remember { mutableStateMapOf<String, String>() }
    fun addressKey(p: RoadProblem) = p.id ?: "${p.position.latitude},${p.position.longitude}"

    val filtered = problems
        .filter { p ->
            searchQuery.isBlank() || searchQuery.trim().lowercase().let { q ->
                p.title.lowercase().contains(q) ||
                p.description.lowercase().contains(q) ||
                p.reportedBy?.lowercase()?.contains(q) == true
            }
        }
        .filter { typeFilter == null || it.type == typeFilter }
        .filter { severityFilter == null || it.severity == severityFilter }
        .filter { sourceFilter == null || it.official == sourceFilter }
        .let { list ->
            when (sortOption) {
                ReportSort.NEWEST -> list.sortedByDescending { it.createdAt ?: "" }
                ReportSort.OLDEST -> list.sortedBy { it.createdAt ?: "" }
                ReportSort.MOST_UPVOTED -> list.sortedByDescending { it.upvotes }
                ReportSort.MOST_DOWNVOTED -> list.sortedByDescending { it.downvotes }
                ReportSort.SEVERITY -> list.sortedByDescending { it.severity.ordinal }
            }
        }

    if (confirmDeleteProblemId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteProblemId = null },
            title = { Text("Delete Report") },
            text = { Text("This will permanently delete this report and all its votes.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteProblem(confirmDeleteProblemId!!); confirmDeleteProblemId = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteProblemId = null }) { Text("Cancel") } }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search reports (title, description, reporter)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { typeFilter = null },
                    label = { Text("All types", fontSize = 12.sp) },
                )
                RoadProblemType.entries.forEach { type ->
                    FilterChip(
                        selected = typeFilter == type,
                        onClick = { typeFilter = if (typeFilter == type) null else type },
                        label = { Text(type.label, fontSize = 12.sp) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = sourceFilter == null,
                    onClick = { sourceFilter = null },
                    label = { Text("All sources", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = sourceFilter == true,
                    onClick = { sourceFilter = if (sourceFilter == true) null else true },
                    label = { Text("✔ Official", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = sourceFilter == false,
                    onClick = { sourceFilter = if (sourceFilter == false) null else false },
                    label = { Text("👥 Community", fontSize = 12.sp) },
                )
                FilterChip(
                    selected = severityFilter == null,
                    onClick = { severityFilter = null },
                    label = { Text("Any severity", fontSize = 12.sp) },
                )
                Severity.entries.forEach { sev ->
                    FilterChip(
                        selected = severityFilter == sev,
                        onClick = { severityFilter = if (severityFilter == sev) null else sev },
                        label = { Text(sev.label, fontSize = 12.sp) },
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Sort:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp),
                )
                ReportSort.entries.forEach { sort ->
                    FilterChip(
                        selected = sortOption == sort,
                        onClick = { sortOption = sort },
                        label = { Text(sort.label, fontSize = 12.sp) },
                    )
                }
            }
        }
        item {
            Text(
                text = "${filtered.size} of ${problems.size} report(s)",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "No reports match your filters",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        items(filtered, key = { it.id ?: it.hashCode() }) { problem ->
            val key = addressKey(problem)
            LaunchedEffect(key) {
                if (addressCache[key] == null) {
                    reverseGeocode(context, problem.position, apiKey)
                        ?.let { addressCache[key] = it }
                }
            }
            ProblemCard(
                problem = problem,
                address = addressCache[key],
                votes = problem.id?.let { problemVotes[it] },
                onOpen = { selectedReport = problem },
                onLoadVotes = { problem.id?.let { onLoadVotes(it) } },
                onDelete = { confirmDeleteProblemId = problem.id },
                onDeleteVote = { voteId -> problem.id?.let { onDeleteVote(voteId, it) } },
            )
        }
    }

    selectedReport?.let { report ->
        ProblemDetailDialog(
            problem = report,
            onDismiss = { selectedReport = null },
            extraContent = {
                addressCache[addressKey(report)]?.let { address ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", modifier = Modifier.padding(end = 6.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    cameraPositionState = rememberCameraPositionState(key = addressKey(report)) {
                        position = CameraPosition.fromLatLngZoom(report.position, 15f)
                    },
                    googleMapOptionsFactory = { GoogleMapOptions().liteMode(true) },
                    uiSettings = MapUiSettings(
                        zoomControlsEnabled = false,
                        mapToolbarEnabled = false,
                    ),
                ) {
                    Marker(state = MarkerState(position = report.position))
                }
            },
            footerStart = {
                Text(
                    text = "👍 ${report.upvotes}  👎 ${report.downvotes}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                if (report.id != null) {
                    TextButton(
                        onClick = {
                            confirmDeleteProblemId = report.id
                            selectedReport = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                }
            },
        )
    }
}

@Composable
private fun ProblemCard(
    problem: RoadProblem,
    address: String?,
    votes: List<Vote>?,
    onOpen: () -> Unit,
    onLoadVotes: () -> Unit,
    onDelete: () -> Unit,
    onDeleteVote: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(modifier = Modifier
        .fillMaxWidth()
        .clickable(onClick = onOpen)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(problem.title, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${problem.type.name.replace("_", " ")} • ${problem.severity.name}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "📍 ${address ?: "Locating…"}",
                        fontSize = 12.sp,
                        color = if (address != null) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    problem.reportedBy?.let {
                        Text("By: $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    problem.createdAt?.let {
                        Text(formatCreatedAt(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        Text("▲ ${problem.upvotes}", fontSize = 12.sp, color = Color(0xFF43A047))
                        Text("▼ ${problem.downvotes}", fontSize = 12.sp, color = Color(0xFFE53935))
                        if (problem.official) StatusBadge("✔ Official", Color(0xFF2E7D32))
                        else StatusBadge("👥 Community", Color(0xFF1565C0))
                        if (problem.hidden) StatusBadge("Hidden", Color(0xFFE53935))
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete", fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        expanded = !expanded
                        if (expanded && votes == null) onLoadVotes()
                    }) { Text(if (expanded) "Hide votes" else "Votes", fontSize = 12.sp) }
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                if (votes == null) {
                    Text("Loading votes…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (votes.isEmpty()) {
                    Text("No votes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    votes.forEach { vote ->
                        VoteRow(vote = vote, onDelete = { vote.id?.let { onDeleteVote(it) } })
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteRow(vote: Vote, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = if (vote.voteType == "UPVOTE") Color(0xFF43A047) else Color(0xFFE53935)
        val icon = if (vote.voteType == "UPVOTE") "▲" else "▼"
        Text("$icon ${vote.votedBy ?: "unknown"}", fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
        vote.votedAt?.let {
            Text(formatCreatedAt(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
        }
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) { Text("Remove", fontSize = 12.sp) }
    }
}

@Composable
private fun UserActivityDialog(
    activity: UserActivity,
    token: String,
    onDismiss: () -> Unit,
    onDisable: (String) -> Unit,
    onEnable: (String) -> Unit,
    onDeleteUser: (String) -> Unit,
    onDeleteProblem: (String) -> Unit,
    onDeleteVote: (String) -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete User") },
            text = { Text("This will permanently delete the user and all their reports and votes. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { onDeleteUser(activity.user.id); confirmDelete = false; onDismiss() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(activity.user.name.ifBlank { activity.user.username })
                    Text(activity.user.email, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoleBadge(activity.user.role)
            }
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activity.user.disabled) {
                            StatusBadge("Disabled", Color(0xFFE53935))
                            OutlinedButton(onClick = { onEnable(activity.user.id) }) { Text("Enable", fontSize = 12.sp) }
                        } else {
                            OutlinedButton(onClick = { onDisable(activity.user.id) }) { Text("Disable", fontSize = 12.sp) }
                        }
                        OutlinedButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Delete", fontSize = 12.sp) }
                    }
                }

                item {
                    Text("Reports (${activity.problems.size})", fontWeight = FontWeight.SemiBold)
                }
                if (activity.problems.isEmpty()) {
                    item { Text("No reports", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(activity.problems, key = { it.id ?: it.hashCode() }) { problem ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(problem.title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Text(
                                    "${problem.type.name.replace("_", " ")} • ${problem.severity.name}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("▲ ${problem.upvotes}", fontSize = 11.sp, color = Color(0xFF43A047))
                                    Text("▼ ${problem.downvotes}", fontSize = 11.sp, color = Color(0xFFE53935))
                                    if (problem.hidden) StatusBadge("Hidden", Color(0xFFE53935))
                                }
                            }
                            TextButton(
                                onClick = { problem.id?.let { onDeleteProblem(it) } },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete", fontSize = 12.sp) }
                        }
                    }
                }

                item {
                    Spacer(Modifier.height(4.dp))
                    Text("Votes cast (${activity.votes.size})", fontWeight = FontWeight.SemiBold)
                }
                if (activity.votes.isEmpty()) {
                    item { Text("No votes", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(activity.votes, key = { it.id ?: it.hashCode() }) { vote ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val color = if (vote.voteType == "UPVOTE") Color(0xFF43A047) else Color(0xFFE53935)
                        val icon = if (vote.voteType == "UPVOTE") "▲ Upvote" else "▼ Downvote"
                        Text(icon, fontSize = 13.sp, color = color, modifier = Modifier.weight(1f))
                        vote.votedAt?.let {
                            Text(formatCreatedAt(it), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 4.dp))
                        }
                        TextButton(
                            onClick = { vote.id?.let { onDeleteVote(it) } },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) { Text("Remove", fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun RoleBadge(role: String) {
    val (text, color) = when (role) {
        "MODERATOR" -> "MOD" to Color(0xFF6A1B9A)
        "REPORT_MODERATOR" -> "REPORT MOD" to Color(0xFF00695C)
        else -> return
    }
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
