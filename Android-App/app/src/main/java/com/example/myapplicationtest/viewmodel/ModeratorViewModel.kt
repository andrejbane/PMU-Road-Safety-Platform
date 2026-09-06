package com.example.myapplicationtest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.UserActivity
import com.example.myapplicationtest.model.UserProfile
import com.example.myapplicationtest.model.Vote
import com.example.myapplicationtest.network.apiDeleteUser
import com.example.myapplicationtest.network.apiDisableUser
import com.example.myapplicationtest.network.apiEnableUser
import com.example.myapplicationtest.network.apiGetAllProblemsModertor
import com.example.myapplicationtest.network.apiGetAllUsers
import com.example.myapplicationtest.network.apiGetProblemVotes
import com.example.myapplicationtest.network.apiGetUserActivity
import com.example.myapplicationtest.network.apiModDeleteProblem
import com.example.myapplicationtest.network.apiModDeleteVote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModeratorViewModel(application: Application) : AndroidViewModel(application) {

    private val _users = MutableStateFlow<List<UserProfile>>(emptyList())
    val users: StateFlow<List<UserProfile>> = _users.asStateFlow()

    private val _problems = MutableStateFlow<List<RoadProblem>>(emptyList())
    val problems: StateFlow<List<RoadProblem>> = _problems.asStateFlow()

    private val _selectedActivity = MutableStateFlow<UserActivity?>(null)
    val selectedActivity: StateFlow<UserActivity?> = _selectedActivity.asStateFlow()

    private val _problemVotes = MutableStateFlow<Map<String, List<Vote>>>(emptyMap())
    val problemVotes: StateFlow<Map<String, List<Vote>>> = _problemVotes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadUsers(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _users.value = apiGetAllUsers(token)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load users"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadProblems(token: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _problems.value = apiGetAllProblemsModertor(token)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load problems"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadUserActivity(token: String, userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _selectedActivity.value = apiGetUserActivity(token, userId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load user activity"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSelectedActivity() {
        _selectedActivity.value = null
    }

    fun loadProblemVotes(token: String, problemId: String) {
        viewModelScope.launch {
            try {
                val votes = apiGetProblemVotes(token, problemId)
                _problemVotes.value = _problemVotes.value + (problemId to votes)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load votes"
            }
        }
    }

    fun disableUser(token: String, userId: String) {
        viewModelScope.launch {
            try {
                apiDisableUser(token, userId)
                _users.value = _users.value.map {
                    if (it.id == userId) it.copy(disabled = true) else it
                }
                _selectedActivity.value?.let { activity ->
                    if (activity.user.id == userId) {
                        _selectedActivity.value = activity.copy(user = activity.user.copy(disabled = true))
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to disable user"
            }
        }
    }

    fun enableUser(token: String, userId: String) {
        viewModelScope.launch {
            try {
                apiEnableUser(token, userId)
                _users.value = _users.value.map {
                    if (it.id == userId) it.copy(disabled = false) else it
                }
                _selectedActivity.value?.let { activity ->
                    if (activity.user.id == userId) {
                        _selectedActivity.value = activity.copy(user = activity.user.copy(disabled = false))
                    }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to enable user"
            }
        }
    }

    fun deleteUser(token: String, userId: String) {
        viewModelScope.launch {
            try {
                apiDeleteUser(token, userId)
                _users.value = _users.value.filter { it.id != userId }
                if (_selectedActivity.value?.user?.id == userId) {
                    _selectedActivity.value = null
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete user"
            }
        }
    }

    fun deleteProblem(token: String, problemId: String) {
        viewModelScope.launch {
            try {
                apiModDeleteProblem(token, problemId)
                _problems.value = _problems.value.filter { it.id != problemId }
                _problemVotes.value = _problemVotes.value - problemId
                _selectedActivity.value?.let { activity ->
                    _selectedActivity.value = activity.copy(
                        problems = activity.problems.filter { it.id != problemId }
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete problem"
            }
        }
    }

    fun deleteVote(token: String, voteId: String, problemId: String) {
        viewModelScope.launch {
            try {
                apiModDeleteVote(token, voteId)
                _problemVotes.value = _problemVotes.value.mapValues { (pid, votes) ->
                    if (pid == problemId) votes.filter { it.id != voteId } else votes
                }
                _selectedActivity.value?.let { activity ->
                    _selectedActivity.value = activity.copy(
                        votes = activity.votes.filter { it.id != voteId }
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to delete vote"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
