package com.example.myapplicationtest.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplicationtest.model.DirectionsResponse
import com.example.myapplicationtest.model.NavigationStep
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.Vote
import com.example.myapplicationtest.network.apiDeleteProblem
import com.example.myapplicationtest.network.apiRemoveVote
import com.example.myapplicationtest.network.apiGetMyVotes
import com.example.myapplicationtest.network.apiGetProblems
import com.example.myapplicationtest.network.apiPostProblem
import com.example.myapplicationtest.network.apiVoteProblem
import com.example.myapplicationtest.util.fetchDirections
import com.example.myapplicationtest.util.findProblemsNearRoute
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel : ViewModel() {

    private val _problems = MutableStateFlow<List<RoadProblem>>(emptyList())
    val problems: StateFlow<List<RoadProblem>> = _problems.asStateFlow()

    private val _myVotes = MutableStateFlow<List<Vote>>(emptyList())
    val myVotes: StateFlow<List<Vote>> = _myVotes.asStateFlow()

    private val _isLoadingProblems = MutableStateFlow(false)
    val isLoadingProblems: StateFlow<Boolean> = _isLoadingProblems.asStateFlow()

    private val _routePoints = MutableStateFlow<List<LatLng>>(emptyList())
    val routePoints: StateFlow<List<LatLng>> = _routePoints.asStateFlow()

    private val _routeDistance = MutableStateFlow<String?>(null)
    val routeDistance: StateFlow<String?> = _routeDistance.asStateFlow()

    private val _routeDuration = MutableStateFlow<String?>(null)
    val routeDuration: StateFlow<String?> = _routeDuration.asStateFlow()

    private val _routeDestination = MutableStateFlow<LatLng?>(null)
    val routeDestination: StateFlow<LatLng?> = _routeDestination.asStateFlow()

    private val _isLoadingRoute = MutableStateFlow(false)
    val isLoadingRoute: StateFlow<Boolean> = _isLoadingRoute.asStateFlow()

    private val _routeLoadedEvent = MutableStateFlow(0)
    val routeLoadedEvent: StateFlow<Int> = _routeLoadedEvent.asStateFlow()

    private val _routeError = MutableStateFlow<String?>(null)
    val routeError: StateFlow<String?> = _routeError.asStateFlow()

    private val _navigationSteps = MutableStateFlow<List<NavigationStep>>(emptyList())
    val navigationSteps: StateFlow<List<NavigationStep>> = _navigationSteps.asStateFlow()

    private val _isNavigating = MutableStateFlow(false)
    val isNavigating: StateFlow<Boolean> = _isNavigating.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _distanceToNextTurn = MutableStateFlow(0.0)
    val distanceToNextTurn: StateFlow<Double> = _distanceToNextTurn.asStateFlow()

    private val _userBearing = MutableStateFlow(0f)
    val userBearing: StateFlow<Float> = _userBearing.asStateFlow()

    private val _isNavExpanded = MutableStateFlow(false)
    val isNavExpanded: StateFlow<Boolean> = _isNavExpanded.asStateFlow()

    private val _arrivedEvent = MutableStateFlow(false)
    val arrivedEvent: StateFlow<Boolean> = _arrivedEvent.asStateFlow()

    private val _currentUserLocation = MutableStateFlow<LatLng?>(null)
    val currentUserLocation: StateFlow<LatLng?> = _currentUserLocation.asStateFlow()

    private val _problemsOnRoute = MutableStateFlow<List<RoadProblem>>(emptyList())
    val problemsOnRoute: StateFlow<List<RoadProblem>> = _problemsOnRoute.asStateFlow()

    private val _avoidedProblems = MutableStateFlow<List<RoadProblem>>(emptyList())
    val avoidedProblems: StateFlow<List<RoadProblem>> = _avoidedProblems.asStateFlow()

    private val _allEverAvoided = MutableStateFlow<List<RoadProblem>>(emptyList())

    private val _isRerouting = MutableStateFlow(false)
    val isRerouting: StateFlow<Boolean> = _isRerouting.asStateFlow()

    private val _rerouteMessage = MutableStateFlow<String?>(null)
    val rerouteMessage: StateFlow<String?> = _rerouteMessage.asStateFlow()

    init {
        loadProblems()
    }

    fun loadProblems() {
        viewModelScope.launch {
            _isLoadingProblems.value = true
            try {
                _problems.value = apiGetProblems()
            } catch (_: Exception) { }
            finally {
                _isLoadingProblems.value = false
            }
        }
    }

    fun loadMyVotes(token: String) {
        viewModelScope.launch {
            try { _myVotes.value = apiGetMyVotes(token) } catch (_: Exception) { }
        }
    }

    fun voteProblem(problemId: String, voteType: String, token: String) {
        viewModelScope.launch {
            try {
                val updated = apiVoteProblem(problemId, voteType, token)
                _problems.value = _problems.value.map { if (it.id == problemId) updated else it }
                loadMyVotes(token)
            } catch (_: Exception) { }
        }
    }

    fun removeVote(problemId: String, token: String) {
        viewModelScope.launch {
            try {
                val updated = apiRemoveVote(problemId, token)
                _problems.value = _problems.value.map { if (it.id == problemId) updated else it }
                loadMyVotes(token)
            } catch (_: Exception) { }
        }
    }

    fun deleteProblem(id: String, token: String) {
        viewModelScope.launch {
            try {
                apiDeleteProblem(id, token)
                _problems.value = _problems.value.filter { it.id != id }
            } catch (_: Exception) { }
        }
    }

    fun addProblem(problem: RoadProblem, token: String?) {
        _problems.value = _problems.value + problem
        if (token != null) {
            viewModelScope.launch {
                try {
                    val saved = apiPostProblem(problem, token)
                    _problems.value = _problems.value.map { if (it === problem) saved else it }
                } catch (_: Exception) { }
            }
        }
    }

    fun removeProblemLocally(id: String) {
        _problems.value = _problems.value.filter { it.id != id }
    }

    fun fetchRoute(origin: LatLng, dest: LatLng, apiKey: String) {
        viewModelScope.launch {
            _isNavigating.value = false
            _currentStepIndex.value = 0
            _routeDestination.value = dest
            _isLoadingRoute.value = true
            _problemsOnRoute.value = emptyList()
            _avoidedProblems.value = emptyList()
            _allEverAvoided.value = emptyList()

            when (val response = fetchDirections(origin, dest, apiKey)) {
                is DirectionsResponse.Success -> {
                    val result = response.result
                    _routePoints.value = result.points
                    _routeDistance.value = result.distance
                    _routeDuration.value = result.duration
                    _navigationSteps.value = result.steps
                    _problemsOnRoute.value = findProblemsNearRoute(result.points, _problems.value)
                    _routeLoadedEvent.value++
                }
                is DirectionsResponse.Error -> {
                    _routeDestination.value = null
                    _routeError.value = response.message
                }
            }
            _isLoadingRoute.value = false
        }
    }

    fun reroute(origin: LatLng, dest: LatLng, apiKey: String) {
        viewModelScope.launch {
            _isRerouting.value = true
            val cumulative = (_allEverAvoided.value + _avoidedProblems.value).distinct()
            _allEverAvoided.value = cumulative

            when (val response = fetchDirections(origin, dest, apiKey, problemsToAvoid = cumulative)) {
                is DirectionsResponse.Success -> {
                    val result = response.result
                    _routePoints.value = result.points
                    _routeDistance.value = result.distance
                    _routeDuration.value = result.duration
                    _navigationSteps.value = result.steps
                    _problemsOnRoute.value = findProblemsNearRoute(result.points, _problems.value)
                    val stillOnRoute = cumulative.count { p -> _problemsOnRoute.value.contains(p) }
                    _avoidedProblems.value = emptyList()
                    _routeLoadedEvent.value++
                    _rerouteMessage.value = if (stillOnRoute == 0)
                        "Route updated — all problems avoided! ✅"
                    else
                        "Best route found — $stillOnRoute problem(s) could not be avoided"
                }
                is DirectionsResponse.Error -> {
                    _rerouteMessage.value = "Reroute failed: ${response.message}"
                }
            }
            _isRerouting.value = false
        }
    }

    fun startNavigation() {
        _currentStepIndex.value = 0
        _distanceToNextTurn.value = _navigationSteps.value.firstOrNull()?.distanceMeters?.toDouble() ?: 0.0
        _isNavigating.value = true
    }

    fun endNavigation() {
        _isNavigating.value = false
        _isNavExpanded.value = false
        clearRoute()
    }

    fun clearRoute() {
        _routePoints.value = emptyList()
        _routeDistance.value = null
        _routeDuration.value = null
        _routeDestination.value = null
        _navigationSteps.value = emptyList()
        _currentStepIndex.value = 0
        _problemsOnRoute.value = emptyList()
        _avoidedProblems.value = emptyList()
        _allEverAvoided.value = emptyList()
    }

    fun toggleAvoidProblem(problem: RoadProblem) {
        val current = _avoidedProblems.value.toMutableList()
        if (problem in current) current.remove(problem) else current.add(problem)
        _avoidedProblems.value = current
    }

    fun toggleNavExpanded() {
        _isNavExpanded.value = !_isNavExpanded.value
    }

    fun updateUserLocation(location: Location) {
        val userLatLng = LatLng(location.latitude, location.longitude)
        _currentUserLocation.value = userLatLng
        if (location.hasBearing()) _userBearing.value = location.bearing

        if (!_isNavigating.value) return
        val steps = _navigationSteps.value
        val stepIdx = _currentStepIndex.value
        if (steps.isEmpty() || stepIdx >= steps.size) return

        val dist = SphericalUtil.computeDistanceBetween(userLatLng, steps[stepIdx].endLocation)
        _distanceToNextTurn.value = dist

        if (dist < 30.0 && stepIdx < steps.size - 1) {
            _currentStepIndex.value = stepIdx + 1
        } else if (stepIdx == steps.size - 1 && dist < 50.0) {
            _isNavigating.value = false
            _arrivedEvent.value = true
        }
    }

    fun setUserLocation(latLng: LatLng) {
        _currentUserLocation.value = latLng
    }

    fun clearArrivedEvent() {
        _arrivedEvent.value = false
    }

    fun clearRouteError() {
        _routeError.value = null
    }

    fun clearRerouteMessage() {
        _rerouteMessage.value = null
    }

    fun clearUserData() {
        _myVotes.value = emptyList()
        loadProblems()
    }
}
