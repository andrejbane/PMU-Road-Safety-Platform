package com.example.myapplicationtest.ui.auth

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.myapplicationtest.R
import com.example.myapplicationtest.util.compressAndEncodeToBase64
import com.example.myapplicationtest.util.decodeBase64ToBytes
import com.example.myapplicationtest.viewmodel.AuthViewModel
import java.io.File

@Composable
fun LoginRegisterScreen(
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel,
) {
    val context = LocalContext.current
    var isRegisterMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var photoBase64 by remember { mutableStateOf<String?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    val isLoading by viewModel.isLoading.collectAsState()
    val vmError by viewModel.error.collectAsState()
    val errorMessage = localError ?: vmError

    fun createTempImageUri(): Uri {
        val dir = File(context.cacheDir, "images").also { it.mkdirs() }
        val file = File.createTempFile("profile_", ".jpg", dir)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) tempCameraUri?.let { photoBase64 = compressAndEncodeToBase64(context, it) }
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { photoBase64 = compressAndEncodeToBase64(context, it) }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        if (isRegisterMode) {
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable { showPhotoSourceDialog = true },
                    contentAlignment = Alignment.Center,
                ) {
                    val photoBytes = remember(photoBase64) { photoBase64?.let { decodeBase64ToBytes(it) } }
                    if (photoBytes != null) {
                        AsyncImage(
                            model = photoBytes,
                            contentDescription = "Profile photo",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_account_box),
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = if (photoBase64 != null) "Tap to change photo" else "Add profile photo (optional)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = "App icon",
                modifier = Modifier.size(100.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isRegisterMode) "Create Account" else "Welcome Back",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (isRegisterMode) "Sign up to report road problems" else "Log in to continue",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isRegisterMode) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; localError = null; viewModel.clearError() },
                label = { Text("Full Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it; localError = null; viewModel.clearError() },
                label = { Text("Username") },
                placeholder = { Text("Shown on your reports") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; localError = null; viewModel.clearError() },
            label = { Text("Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; localError = null; viewModel.clearError() },
            label = { Text("Password") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            visualTransformation = PasswordVisualTransformation(),
        )

        if (isRegisterMode) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; localError = null; viewModel.clearError() },
                label = { Text("Confirm Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
            )
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (isRegisterMode) {
                    when {
                        name.isBlank() -> localError = "Please enter your name"
                        email.isBlank() -> localError = "Please enter your email"
                        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> localError = "Please enter a valid email"
                        password.length < 6 -> localError = "Password must be at least 6 characters"
                        password != confirmPassword -> localError = "Passwords do not match"
                        else -> viewModel.register(name, email, password, username.trim().ifBlank { null }, photoBase64)
                    }
                } else {
                    when {
                        email.isBlank() -> localError = "Please enter your email"
                        password.isBlank() -> localError = "Please enter your password"
                        else -> viewModel.login(email, password)
                    }
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            } else {
                Text(
                    text = if (isRegisterMode) "Create Account" else "Log In",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isRegisterMode) "Already have an account?" else "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            TextButton(onClick = {
                isRegisterMode = !isRegisterMode
                localError = null
                viewModel.clearError()
                name = ""; username = ""; email = ""; password = ""; confirmPassword = ""; photoBase64 = null
            }) {
                Text(text = if (isRegisterMode) "Log In" else "Register", fontWeight = FontWeight.Bold)
            }
        }
    }
}
