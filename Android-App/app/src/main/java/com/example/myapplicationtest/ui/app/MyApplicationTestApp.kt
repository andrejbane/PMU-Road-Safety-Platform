package com.example.myapplicationtest.ui.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.myapplicationtest.navigation.AppDestinations
import com.example.myapplicationtest.ui.auth.LoginRegisterScreen
import com.example.myapplicationtest.ui.map.MapScreen
import com.example.myapplicationtest.ui.moderator.ModeratorScreen
import com.example.myapplicationtest.ui.profile.ProfileScreen
import com.example.myapplicationtest.viewmodel.AuthViewModel
import com.example.myapplicationtest.viewmodel.MapViewModel
import com.example.myapplicationtest.viewmodel.ModeratorViewModel

@Composable
fun MyApplicationTestApp(
    authViewModel: AuthViewModel = viewModel(),
    mapViewModel: MapViewModel = viewModel(),
    moderatorViewModel: ModeratorViewModel = viewModel(),
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.MAP) }

    val currentUser by authViewModel.currentUser.collectAsState()
    val isModerator = currentUser?.role == "MODERATOR" || currentUser?.role == "REPORT_MODERATOR"
    val canManageUsers = currentUser?.role == "MODERATOR"
    val visibleDestinations = if (isModerator) AppDestinations.entries
                              else AppDestinations.entries.filter { it != AppDestinations.MODERATOR }
    val problems by mapViewModel.problems.collectAsState()
    val myVotes by mapViewModel.myVotes.collectAsState()
    val profileUpdateError by authViewModel.profileUpdateError.collectAsState()
    val profileUpdateSuccess by authViewModel.profileUpdateSuccess.collectAsState()

    LaunchedEffect(currentUser) {
        val user = currentUser
        if (user != null) {
            mapViewModel.loadMyVotes(user.token)
        } else {
            mapViewModel.clearUserData()
        }
        if (!isModerator && currentDestination == AppDestinations.MODERATOR) {
            currentDestination = AppDestinations.MAP
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            FloatingBottomNav(
                destinations = visibleDestinations,
                current = currentDestination,
                onSelect = { currentDestination = it },
            )
        }
    ) { innerPadding ->
        when (currentDestination) {
            AppDestinations.MAP -> MapScreen(
                modifier = Modifier.padding(innerPadding),
                viewModel = mapViewModel,
                isLoggedIn = currentUser != null,
                userName = currentUser?.name,
                userEmail = currentUser?.email,
                userDisplayUsername = currentUser?.displayUsername,
                userToken = currentUser?.token,
                userRole = currentUser?.role,
            )
            AppDestinations.MODERATOR -> {
                if (currentUser != null && isModerator) {
                    val user = currentUser
                    ModeratorScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = moderatorViewModel,
                        token = user!!.token,
                        canManageUsers = canManageUsers,
                        onProblemDeleted = { mapViewModel.removeProblemLocally(it) },
                    )
                }
            }
            AppDestinations.PROFILE -> {
                if (currentUser != null) {
                    ProfileScreen(
                        modifier = Modifier.padding(innerPadding),
                        user = currentUser!!,
                        userReports = problems.filter {
                            it.reportedBy == currentUser!!.email ||
                            it.reportedBy == currentUser!!.displayUsername ||
                            (it.isUserReport && it.id == null)
                        },
                        myVotes = myVotes,
                        onLogout = { authViewModel.logout() },
                        onDeleteReport = { id ->
                            currentUser?.token?.let { mapViewModel.deleteProblem(id, it) }
                        },
                        onVoteChange = { problemId, newType ->
                            currentUser?.token?.let { mapViewModel.voteProblem(problemId, newType, it) }
                        },
                        onVoteRemove = { problemId ->
                            currentUser?.token?.let { mapViewModel.removeVote(problemId, it) }
                        },
                        onUpdateDisplayUsername = { authViewModel.updateDisplayUsername(it) },
                        onChangePassword = { cur, new -> authViewModel.changePassword(cur, new) },
                        onUpdatePhoto = { authViewModel.updatePhoto(it) },
                        profileUpdateError = profileUpdateError,
                        profileUpdateSuccess = profileUpdateSuccess,
                        onClearProfileUpdate = { authViewModel.clearProfileUpdateResult() },
                    )
                } else {
                    LoginRegisterScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = authViewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingBottomNav(
    destinations: List<AppDestinations>,
    current: AppDestinations,
    onSelect: (AppDestinations) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 20.dp, top = 6.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            shadowElevation = 10.dp,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                destinations.forEach { dest ->
                    val selected = dest == current
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onSelect(dest) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            painter = painterResource(dest.icon),
                            contentDescription = dest.label,
                            modifier = Modifier.size(20.dp),
                            tint = if (selected) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = dest.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
