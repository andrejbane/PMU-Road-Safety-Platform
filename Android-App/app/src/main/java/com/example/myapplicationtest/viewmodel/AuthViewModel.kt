package com.example.myapplicationtest.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationtest.model.User
import com.example.myapplicationtest.network.apiLogin
import com.example.myapplicationtest.network.apiRegister
import com.example.myapplicationtest.network.apiUpdateProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pmu_auth", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _profileUpdateError = MutableStateFlow<String?>(null)
    val profileUpdateError: StateFlow<String?> = _profileUpdateError.asStateFlow()

    private val _profileUpdateSuccess = MutableStateFlow<String?>(null)
    val profileUpdateSuccess: StateFlow<String?> = _profileUpdateSuccess.asStateFlow()

    init {
        val token = prefs.getString("token", null)
        val name = prefs.getString("name", null)
        val email = prefs.getString("email", null)
        val displayUsername = prefs.getString("displayUsername", null)
        val role = prefs.getString("role", "USER") ?: "USER"
        val photoBase64 = prefs.getString("photoBase64", null)
        if (token != null && name != null && email != null) {
            _currentUser.value = User(name, email, token, displayUsername, role, photoBase64)
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = apiLogin(email.trim().lowercase(), password)
                val user = User(result.name, result.email, result.token, result.displayUsername, result.role, result.photoBase64)
                _currentUser.value = user
                saveToPrefs(user)
            } catch (e: Exception) {
                _error.value = when {
                    e.message?.contains("disabled", ignoreCase = true) == true -> "Your account has been disabled by a moderator."
                    else -> "Invalid email or password"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun register(name: String, email: String, password: String, displayUsername: String? = null, photoBase64: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val result = apiRegister(name.trim(), email.trim().lowercase(), password, displayUsername, photoBase64)
                val user = User(result.name, result.email, result.token, result.displayUsername, result.role, result.photoBase64)
                _currentUser.value = user
                saveToPrefs(user)
            } catch (e: Exception) {
                _error.value = e.message ?: "Registration failed"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateDisplayUsername(newUsername: String) {
        val token = _currentUser.value?.token ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _profileUpdateError.value = null
            try {
                val result = apiUpdateProfile(token, newUsername.trim().ifBlank { null }, null, null)
                val updated = User(result.name, result.email, result.token, result.displayUsername, result.role, result.photoBase64)
                _currentUser.value = updated
                saveToPrefs(updated)
                _profileUpdateSuccess.value = "Username updated"
            } catch (e: Exception) {
                _profileUpdateError.value = e.message ?: "Failed to update username"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun changePassword(currentPassword: String, newPassword: String) {
        val token = _currentUser.value?.token ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _profileUpdateError.value = null
            try {
                val result = apiUpdateProfile(token, null, currentPassword, newPassword)
                val updated = User(result.name, result.email, result.token, result.displayUsername, result.role, result.photoBase64)
                _currentUser.value = updated
                saveToPrefs(updated)
                _profileUpdateSuccess.value = "Password changed"
            } catch (e: Exception) {
                _profileUpdateError.value = e.message ?: "Failed to change password"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePhoto(photoBase64: String?) {
        val token = _currentUser.value?.token ?: return
        viewModelScope.launch {
            _isLoading.value = true
            _profileUpdateError.value = null
            try {
                val result = apiUpdateProfile(token, null, null, null, photoBase64 ?: "")
                val updated = User(result.name, result.email, result.token, result.displayUsername, result.role, result.photoBase64)
                _currentUser.value = updated
                saveToPrefs(updated)
                _profileUpdateSuccess.value = if (photoBase64 == null) "Photo removed" else "Photo updated"
            } catch (e: Exception) {
                _profileUpdateError.value = e.message ?: "Failed to update photo"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearProfileUpdateResult() {
        _profileUpdateError.value = null
        _profileUpdateSuccess.value = null
    }

    fun logout() {
        _currentUser.value = null
        prefs.edit().clear().apply()
    }

    fun clearError() {
        _error.value = null
    }

    private fun saveToPrefs(user: User) {
        prefs.edit()
            .putString("token", user.token)
            .putString("name", user.name)
            .putString("email", user.email)
            .putString("displayUsername", user.displayUsername)
            .putString("role", user.role)
            .putString("photoBase64", user.photoBase64)
            .apply()
    }
}
