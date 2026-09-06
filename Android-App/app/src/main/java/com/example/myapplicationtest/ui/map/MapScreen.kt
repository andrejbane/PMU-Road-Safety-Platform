package com.example.myapplicationtest.ui.map

import android.Manifest
import android.annotation.SuppressLint
import android.net.Uri
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.myapplicationtest.R
import com.example.myapplicationtest.model.LocationChoice
import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.RoadProblemType
import com.example.myapplicationtest.model.RoadSide
import com.example.myapplicationtest.model.Severity
import com.example.myapplicationtest.ui.common.ProblemDetailDialog
import com.example.myapplicationtest.util.compressAndEncodeToBase64
import com.example.myapplicationtest.util.createCustomMarkerIcon
import com.example.myapplicationtest.util.createDirectionOriginIcon
import com.example.myapplicationtest.util.decodeBase64ToBytes
import com.example.myapplicationtest.util.GeocodeResult
import com.example.myapplicationtest.util.formatCreatedAt
import com.example.myapplicationtest.util.geocodeAddress
import com.example.myapplicationtest.util.geocodeSuggestions
import com.example.myapplicationtest.util.getApiKeyFromManifest
import com.example.myapplicationtest.util.maneuverToEmoji
import com.example.myapplicationtest.util.resolveResultPosition
import com.example.myapplicationtest.util.roadSideDescription
import com.example.myapplicationtest.viewmodel.MapViewModel
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.SphericalUtil
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@SuppressLint("MissingPermission")
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel,
    isLoggedIn: Boolean = false,
    userName: String? = null,
    userEmail: String? = null,
    userDisplayUsername: String? = null,
    userToken: String? = null,
    userRole: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    val problems by viewModel.problems.collectAsState()
    val routePoints by viewModel.routePoints.collectAsState()
    val routeDistance by viewModel.routeDistance.collectAsState()
    val routeDuration by viewModel.routeDuration.collectAsState()
    val routeDestination by viewModel.routeDestination.collectAsState()
    val isLoadingRoute by viewModel.isLoadingRoute.collectAsState()
    val navigationSteps by viewModel.navigationSteps.collectAsState()
    val isNavigating by viewModel.isNavigating.collectAsState()
    val currentStepIndex by viewModel.currentStepIndex.collectAsState()
    val distanceToNextTurn by viewModel.distanceToNextTurn.collectAsState()
    val userBearing by viewModel.userBearing.collectAsState()
    val isNavExpanded by viewModel.isNavExpanded.collectAsState()
    val currentUserLocation by viewModel.currentUserLocation.collectAsState()
    val problemsOnRoute by viewModel.problemsOnRoute.collectAsState()
    val avoidedProblems by viewModel.avoidedProblems.collectAsState()
    val isRerouting by viewModel.isRerouting.collectAsState()
    val routeLoadedEvent by viewModel.routeLoadedEvent.collectAsState()
    val routeError by viewModel.routeError.collectAsState()
    val arrivedEvent by viewModel.arrivedEvent.collectAsState()
    val rerouteMessage by viewModel.rerouteMessage.collectAsState()

    var locationPermissionGranted by remember { mutableStateOf(false) }
    var hasMovedToUserLocation by rememberSaveable { mutableStateOf(false) }

    var problemFilter by rememberSaveable { mutableStateOf(ProblemFilter.ALL) }

    val visibleProblemsOnRoute = problemsOnRoute.filter(problemFilter::matches)
    LaunchedEffect(problemFilter) {
        avoidedProblems
            .filterNot(problemFilter::matches)
            .forEach { viewModel.toggleAvoidProblem(it) }
    }

    val keyboardController = LocalSoftwareKeyboardController.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isSearchingLocation by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<GeocodeResult>>(emptyList()) }
    var lastPickedSuggestion by remember { mutableStateOf<String?>(null) }
    val defaultLocation = LatLng(37.4220, -122.0841)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 14f)
    }
    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.length < 3 || query == lastPickedSuggestion) {
            searchSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(400) // debounce typing
        val near = currentUserLocation ?: cameraPositionState.position.target
        searchSuggestions = geocodeSuggestions(
            context, query, near,
            apiKey = getApiKeyFromManifest(context),
        )
    }

    fun routeToSearchResult(result: GeocodeResult) {
        lastPickedSuggestion = result.address
        searchQuery = result.address
        searchSuggestions = emptyList()
        keyboardController?.hide()
        val apiKey = getApiKeyFromManifest(context)
        if (apiKey == null) {
            Toast.makeText(context, "API key not found", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            val position = resolveResultPosition(result, apiKey)
            if (position == null) {
                Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val origin = currentUserLocation
            if (origin != null) {
                Toast.makeText(context, "Routing to ${result.address}", Toast.LENGTH_SHORT).show()
                viewModel.fetchRoute(origin, position, apiKey)
            } else {
                Toast.makeText(
                    context,
                    "Found ${result.address} — your location is unknown, showing it on the map",
                    Toast.LENGTH_LONG
                ).show()
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(position, 15f))
            }
        }
    }

    fun performLocationSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty() || isSearchingLocation) return
        val apiKey = getApiKeyFromManifest(context)
        if (apiKey == null) {
            Toast.makeText(context, "API key not found", Toast.LENGTH_LONG).show()
            return
        }
        lastPickedSuggestion = query
        searchSuggestions = emptyList()
        keyboardController?.hide()
        scope.launch {
            isSearchingLocation = true
            val result = geocodeAddress(
                context, query, apiKey,
                near = currentUserLocation ?: cameraPositionState.position.target,
            )
            isSearchingLocation = false
            val position = result?.position
            when {
                result == null || position == null ->
                    Toast.makeText(context, "Location not found", Toast.LENGTH_SHORT).show()
                currentUserLocation != null -> {
                    Toast.makeText(context, "Routing to ${result.address}", Toast.LENGTH_SHORT).show()
                    viewModel.fetchRoute(currentUserLocation!!, position, apiKey)
                }
                else -> {
                    Toast.makeText(
                        context,
                        "Found ${result.address} — your location is unknown, showing it on the map",
                        Toast.LENGTH_LONG
                    ).show()
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(position, 15f))
                }
            }
        }
    }

    var showDialog by remember { mutableStateOf(false) }
    var isChoosingOnMap by remember { mutableStateOf(false) }
    var isChoosingDirection by remember { mutableStateOf(false) }
    var mapPickedLocation by remember { mutableStateOf<LatLng?>(null) }

    var dialogLocationChoice by remember { mutableStateOf(LocationChoice.CURRENT_LOCATION) }
    var dialogRoadSide by remember { mutableStateOf(RoadSide.BOTH) }
    var dialogBearing by remember { mutableStateOf<Float?>(null) }
    var dialogDeviceHeading by remember { mutableStateOf<Float?>(null) }
    var dialogProblemType by remember { mutableStateOf(RoadProblemType.WORK_ON_ROAD) }
    var dialogSeverity by remember { mutableStateOf(Severity.MEDIUM) }
    var dialogDescription by remember { mutableStateOf("") }
    var dialogPhotoUri by remember { mutableStateOf<Uri?>(null) }

    var selectedProblemRef by remember { mutableStateOf<RoadProblem?>(null) }
    val selectedProblem = selectedProblemRef?.let { ref ->
        if (ref.id != null) problems.find { it.id == ref.id } else ref
    }
    val myVotes by viewModel.myVotes.collectAsState()

    LaunchedEffect(routeLoadedEvent) {
        if (routePoints.isNotEmpty()) {
            val boundsBuilder = LatLngBounds.Builder()
            routePoints.forEach { boundsBuilder.include(it) }
            currentUserLocation?.let { boundsBuilder.include(it) }
            routeDestination?.let { boundsBuilder.include(it) }
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100))
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(currentUserLocation, isNavigating) {
        if (isNavigating && currentUserLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(currentUserLocation!!)
                        .zoom(17f).tilt(45f).bearing(userBearing)
                        .build()
                ), 300
            )
        }
    }

    LaunchedEffect(routeError) {
        routeError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearRouteError()
        }
    }
    LaunchedEffect(arrivedEvent) {
        if (arrivedEvent) {
            Toast.makeText(context, "You have arrived! 🎉", Toast.LENGTH_LONG).show()
            viewModel.clearArrivedEvent()
        }
    }
    LaunchedEffect(rerouteMessage) {
        rerouteMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearRerouteMessage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (locationPermissionGranted && !hasMovedToUserLocation) {
            scope.launch {
                try {
                    val location = fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        CancellationTokenSource().token
                    ).await()
                    if (location != null) {
                        val userLatLng = LatLng(location.latitude, location.longitude)
                        viewModel.setUserLocation(userLatLng)
                        cameraPositionState.animate(
                            CameraUpdateFactory.newLatLngZoom(userLatLng, 15f)
                        )
                        hasMovedToUserLocation = true
                    }
                } catch (_: Exception) { }
            }
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        )
    }

    DisposableEffect(isNavigating, locationPermissionGranted) {
        if (!isNavigating || !locationPermissionGranted) {
            onDispose { }
        } else {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY, 2000L
            ).setMinUpdateIntervalMillis(1000L).build()

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val loc = result.lastLocation ?: return
                    viewModel.updateUserLocation(loc)
                }
            }

            @SuppressLint("MissingPermission")
            fun startTracking() {
                fusedLocationClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper()
                )
            }

            startTracking()

            onDispose {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = if (isLoggedIn) "Hi, ${userName ?: "User"} 👋" else "Hi, Guest 👋",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 16.dp, bottom = 16.dp)
            )

            if (isChoosingOnMap || isChoosingDirection) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isChoosingDirection)
                            "➤ Tap the map in the direction the affected traffic is moving (from the problem 📍)"
                        else
                            "📍 Tap on the map to choose a location",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
              Box(modifier = Modifier.fillMaxSize()) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = locationPermissionGranted
                    ),
                    onMapClick = { latLng ->
                        if (isChoosingDirection) {
                            val reference = when (dialogLocationChoice) {
                                LocationChoice.CHOOSE_ON_MAP -> mapPickedLocation ?: currentUserLocation ?: defaultLocation
                                LocationChoice.CURRENT_LOCATION -> currentUserLocation ?: defaultLocation
                            }
                            val heading = SphericalUtil.computeHeading(reference, latLng)
                            dialogBearing = ((heading % 360.0 + 360.0) % 360.0).toFloat()
                            isChoosingDirection = false
                            showDialog = true
                        } else if (isChoosingOnMap) {
                            mapPickedLocation = latLng
                            isChoosingOnMap = false
                            showDialog = true
                        }
                    },
                    onMapLongClick = { latLng ->
                        Log.d("NavDebug", "Long press at: $latLng")
                        Log.d("NavDebug", "currentUserLocation: $currentUserLocation")
                        Log.d("NavDebug", "isChoosingOnMap: $isChoosingOnMap")
                        val origin = currentUserLocation
                        if (origin != null && !isChoosingOnMap) {
                            val apiKey = getApiKeyFromManifest(context)
                            Log.d("NavDebug", "API key: ${apiKey?.take(10)}...")
                            if (apiKey != null) {
                                Log.d("NavDebug", "Fetching directions from $origin to $latLng")
                                viewModel.fetchRoute(origin, latLng, apiKey)
                            } else {
                                Toast.makeText(context, "API key not found", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    // Icons cached by type, severity, and affected direction (rounded to 45°);
                    // the direction arrow is drawn as a badge on the pin itself.
                    val markerIconCache = remember {
                        mutableMapOf<Triple<RoadProblemType, Severity, Int>, BitmapDescriptor>()
                    }

                    val visibleProblems = problems.filter(problemFilter::matches)
                    visibleProblems.forEach { problem ->
                        val hasPhoto = problem.photoUri != null || problem.photoBase64 != null
                        val cardinalIdx = problem.affectedBearing
                            ?.let { ((Math.round(it / 45.0).toInt() % 8) + 8) % 8 }
                            ?: -1
                        val icon = markerIconCache.getOrPut(
                            Triple(problem.type, problem.severity, cardinalIdx)
                        ) {
                            createCustomMarkerIcon(
                                context, problem.type, problem.severity,
                                cardinalIdx.takeIf { it >= 0 }?.let { it * 45f },
                            )
                        }
                        Marker(
                            state = MarkerState(position = problem.position),
                            title = problem.title,
                            snippet = buildString {
                                append("${problem.severity.label} · ${problem.description}")
                                append("\n🛣 ${roadSideDescription(problem)}")
                                formatCreatedAt(problem.createdAt).takeIf { it.isNotEmpty() }
                                    ?.let { append("\n🕐 $it") }
                                problem.reportedBy?.takeIf { it.isNotEmpty() }
                                    ?.let { append("\n👤 $it") }
                                if (hasPhoto) append("\n📷 Photo attached")
                                append("\n👍 ${problem.upvotes}  👎 ${problem.downvotes}  •  Tap for details")
                            },
                            icon = icon,
                            onInfoWindowClick = {
                                selectedProblemRef = problem
                            },
                        )
                    }
                    if (isChoosingDirection) {
                        val directionOrigin = when (dialogLocationChoice) {
                            LocationChoice.CHOOSE_ON_MAP -> mapPickedLocation ?: currentUserLocation ?: defaultLocation
                            LocationChoice.CURRENT_LOCATION -> currentUserLocation ?: defaultLocation
                        }
                        val directionOriginIcon = remember { createDirectionOriginIcon(context) }
                        Marker(
                            state = MarkerState(position = directionOrigin),
                            title = "Problem location",
                            snippet = "Tap the map in the direction the affected traffic is moving",
                            icon = directionOriginIcon,
                        )
                    } else {
                        mapPickedLocation?.let { loc ->
                            if (isChoosingOnMap || showDialog) {
                                Marker(
                                    state = MarkerState(position = loc),
                                    title = "Selected location",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN),
                                )
                            }
                        }
                    }

                    if (routePoints.isNotEmpty()) {
                        Polyline(
                            points = routePoints,
                            color = Color(0xFF4285F4),
                            width = 14f,
                        )
                    }

                    routeDestination?.let { dest ->
                        Marker(
                            state = MarkerState(position = dest),
                            title = "Destination",
                            snippet = routeDistance?.let { d -> routeDuration?.let { t -> "$d · $t" } },
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "🔍",
                            modifier = Modifier
                                .clickable { performLocationSearch() }
                                .padding(end = 8.dp)
                        )
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { performLocationSearch() }),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search destination…",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        if (isSearchingLocation) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clickable {
                                        searchQuery = ""
                                        lastPickedSuggestion = null
                                    }
                                    .padding(start = 8.dp)
                            )
                        }
                    }
                }

                if (searchSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                        shadowElevation = 4.dp,
                    ) {
                        Column {
                            searchSuggestions.forEachIndexed { index, suggestion ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { routeToSearchResult(suggestion) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                ) {
                                    Text(text = "📍", modifier = Modifier.padding(end = 8.dp))
                                    Text(
                                        text = suggestion.address,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val dist = suggestion.distanceMeters
                                        ?: suggestion.position?.let { pos ->
                                            currentUserLocation?.let { userLoc ->
                                                SphericalUtil.computeDistanceBetween(userLoc, pos)
                                            }
                                        }
                                    if (dist != null) {
                                        Text(
                                            text = if (dist >= 1000)
                                                String.format(java.util.Locale.US, "%.1f km", dist / 1000)
                                            else
                                                "${dist.toInt()} m",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                                if (index < searchSuggestions.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ProblemFilter.entries.forEach { filter ->
                            val selected = problemFilter == filter
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else Color.Transparent
                                    )
                                    .clickable { problemFilter = filter },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = filter.compact,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
              }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        FloatingActionButton(
            onClick = {
                if (!isLoggedIn) {
                    Toast.makeText(context, "Please log in to report a problem", Toast.LENGTH_SHORT).show()
                    return@FloatingActionButton
                }
                dialogLocationChoice = LocationChoice.CURRENT_LOCATION
                dialogRoadSide = RoadSide.BOTH
                dialogBearing = null
                dialogDeviceHeading = if (isNavigating) userBearing else null
                dialogProblemType = RoadProblemType.WORK_ON_ROAD
                dialogSeverity = Severity.MEDIUM
                dialogDescription = ""
                dialogPhotoUri = null
                mapPickedLocation = null
                showDialog = true
                if (locationPermissionGranted) {
                    scope.launch {
                        try {
                            val location = fusedLocationClient.getCurrentLocation(
                                Priority.PRIORITY_HIGH_ACCURACY,
                                CancellationTokenSource().token
                            ).await()
                            if (location != null && location.hasBearing()) {
                                dialogDeviceHeading = location.bearing
                            }
                        } catch (_: Exception) { }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp),
            containerColor = MaterialTheme.colorScheme.primary,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = "Report problem",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (isLoadingRoute || routePoints.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                when {
                    isLoadingRoute -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Finding route…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    isNavigating && navigationSteps.isNotEmpty() -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val currentStep = navigationSteps.getOrNull(currentStepIndex)
                            if (currentStep != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .clickable { viewModel.toggleNavExpanded() }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = maneuverToEmoji(currentStep.maneuver),
                                        style = MaterialTheme.typography.headlineLarge,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = currentStep.instruction,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        val distText = if (distanceToNextTurn > 1000) {
                                            String.format("%.1f km", distanceToNextTurn / 1000)
                                        } else {
                                            "${distanceToNextTurn.toInt()} m"
                                        }
                                        Text(
                                            text = "In $distText",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                if (isNavExpanded) {
                                    val listState = rememberLazyListState()
                                    LaunchedEffect(currentStepIndex) {
                                        listState.animateScrollToItem(
                                            (currentStepIndex).coerceAtMost(navigationSteps.size - 1)
                                        )
                                    }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                    ) {
                                        itemsIndexed(navigationSteps) { index, step ->
                                            val isCurrent = index == currentStepIndex
                                            val isPast = index < currentStepIndex
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(
                                                        if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                                                        else Color.Transparent
                                                    )
                                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = maneuverToEmoji(step.maneuver),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    modifier = Modifier.padding(end = 12.dp),
                                                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.onSurface
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = step.instruction,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        maxLines = 2,
                                                        color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Text(
                                                    text = step.distance,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isPast) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            if (index < navigationSteps.size - 1) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "📏 ${routeDistance ?: ""}  ·  ⏱ ${routeDuration ?: ""}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "Step ${currentStepIndex + 1} of ${navigationSteps.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                                Button(
                                    onClick = { viewModel.endNavigation() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("End")
                                }
                            }
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Route Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "📏 ${routeDistance ?: ""}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "⏱ ${routeDuration ?: ""}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    Text(
                                        text = "${navigationSteps.size} steps",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }

                            if (visibleProblemsOnRoute.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "⚠️ ${visibleProblemsOnRoute.size} problem(s) on this route",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Toggle to avoid, then tap Reroute",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                visibleProblemsOnRoute.forEach { problem ->
                                    val isAvoided = problem in avoidedProblems
                                    val bgColor = if (isAvoided)
                                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(bgColor)
                                            .clickable { viewModel.toggleAvoidProblem(problem) }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = when (problem.type) {
                                                RoadProblemType.WORK_ON_ROAD -> "🚧"
                                                RoadProblemType.PROBLEM_ON_ROAD -> "⛔"
                                                RoadProblemType.OTHER -> "ℹ️"
                                            },
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = problem.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = problem.description,
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Text(
                                                text = "🛣 ${roadSideDescription(problem)}",
                                                style = MaterialTheme.typography.labelSmall,
                                                maxLines = 1,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                        }
                                        Switch(
                                            checked = isAvoided,
                                            onCheckedChange = { viewModel.toggleAvoidProblem(problem) },
                                            modifier = Modifier.height(24.dp),
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.error,
                                                checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        )
                                    }
                                }

                                if (avoidedProblems.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val origin = currentUserLocation
                                            val dest = routeDestination
                                            if (origin != null && dest != null) {
                                                val apiKey = getApiKeyFromManifest(context)
                                                if (apiKey != null) {
                                                    viewModel.reroute(origin, dest, apiKey)
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = !isRerouting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        )
                                    ) {
                                        if (isRerouting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                color = MaterialTheme.colorScheme.onError,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Rerouting…")
                                        } else {
                                            Text("🔄 Reroute avoiding ${avoidedProblems.size} problem(s)")
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.startNavigation() },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("▶ Start Navigation")
                                }
                                OutlinedButton(
                                    onClick = { viewModel.clearRoute() },
                                ) {
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        ReportProblemDialog(
            locationChoice = dialogLocationChoice,
            onLocationChoiceChange = { dialogLocationChoice = it },
            roadSide = dialogRoadSide,
            onRoadSideChange = { dialogRoadSide = it },
            directionBearing = dialogBearing,
            onDirectionBearingChange = { dialogBearing = it },
            deviceHeading = dialogDeviceHeading,
            onPickDirectionOnMap = {
                showDialog = false
                isChoosingDirection = true
            },
            problemType = dialogProblemType,
            onProblemTypeChange = { dialogProblemType = it },
            severity = dialogSeverity,
            onSeverityChange = { dialogSeverity = it },
            description = dialogDescription,
            onDescriptionChange = { dialogDescription = it },
            photoUri = dialogPhotoUri,
            onPhotoUriChange = { dialogPhotoUri = it },
            mapPickedLocation = mapPickedLocation,
            onChooseOnMap = {
                showDialog = false
                isChoosingOnMap = true
            },
            onDismiss = {
                showDialog = false
                mapPickedLocation = null
            },
            onSubmit = {
                val location = when (dialogLocationChoice) {
                    LocationChoice.CURRENT_LOCATION -> currentUserLocation ?: defaultLocation
                    LocationChoice.CHOOSE_ON_MAP -> mapPickedLocation ?: defaultLocation
                }
                val title = dialogProblemType.label
                val desc = if (dialogProblemType == RoadProblemType.OTHER)
                    dialogDescription.ifBlank { "Other issue" }
                else
                    dialogProblemType.label

                val photoBase64 = dialogPhotoUri?.let { compressAndEncodeToBase64(context, it) }
                val newProblem = RoadProblem(
                    position = location,
                    title = title,
                    description = desc,
                    type = dialogProblemType,
                    severity = dialogSeverity,
                    roadSide = dialogRoadSide,
                    directionBearing = if (dialogRoadSide != RoadSide.BOTH)
                        dialogBearing?.toDouble()
                    else null,
                    photoUri = dialogPhotoUri,
                    photoBase64 = photoBase64,
                    isUserReport = true,
                    official = userRole == "MODERATOR" || userRole == "REPORT_MODERATOR",
                    reportedBy = userDisplayUsername ?: userEmail,
                    createdAt = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                )
                viewModel.addProblem(newProblem, userToken)
                showDialog = false
                mapPickedLocation = null

                scope.launch {
                    cameraPositionState.animate(
                        CameraUpdateFactory.newLatLngZoom(location, 15f)
                    )
                }
            }
        )
    }

    selectedProblem?.let { problem ->
        val currentVote = myVotes.find { it.problem?.id == problem.id }
        val isOwnReport = problem.reportedBy != null && (
            problem.reportedBy == userEmail ||
            (userDisplayUsername != null && problem.reportedBy == userDisplayUsername)
        )
        val canVote = isLoggedIn && !isOwnReport && problem.id != null

        ProblemDetailDialog(
            problem = problem,
            onDismiss = { selectedProblemRef = null },
            footerStart = {
                if (canVote) {
                    TextButton(
                        onClick = { viewModel.voteProblem(problem.id!!, "UPVOTE", userToken!!) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = if (currentVote?.voteType == "UPVOTE")
                            ButtonDefaults.textButtonColors(contentColor = Color(0xFF4CAF50))
                        else ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                    ) { Text("👍 ${problem.upvotes}", style = MaterialTheme.typography.labelMedium) }
                    TextButton(
                        onClick = { viewModel.voteProblem(problem.id!!, "DOWNVOTE", userToken!!) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        colors = if (currentVote?.voteType == "DOWNVOTE")
                            ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        else ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ),
                    ) { Text("👎 ${problem.downvotes}", style = MaterialTheme.typography.labelMedium) }
                } else {
                    Text(
                        text = "👍 ${problem.upvotes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    Text(
                        text = "👎 ${problem.downvotes}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                    if (isOwnReport) {
                        Text(
                            text = "your report",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    } else if (!isLoggedIn) {
                        Text(
                            text = "log in to vote",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            },
        )
    }
}

enum class ProblemFilter(val label: String, val compact: String) {
    ALL("All", "All"),
    OFFICIAL("✔ Official", "✔"),
    COMMUNITY("👥 Community", "👥");

    fun matches(problem: RoadProblem): Boolean = when (this) {
        ALL -> true
        OFFICIAL -> problem.official
        COMMUNITY -> !problem.official
    }
}
