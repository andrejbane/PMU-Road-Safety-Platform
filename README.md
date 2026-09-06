# PMU Road Safety Platform

PMU is a full-stack road-hazard reporting and navigation application. The
Android client displays road problems on Google Maps, lets users submit and
vote on reports, calculates routes around hazards, and provides profile and
moderation screens. The Spring Boot backend handles authentication,
authorization, persistence, voting, and report moderation.

## Contents

- [Project structure](#project-structure)
- [Architecture and data flow](#architecture-and-data-flow)
- [Features](#features)
- [Requirements](#requirements)
- [Configuration](#configuration)
- [Quick start](#quick-start)
- [Backend API](#backend-api)
- [Authentication and roles](#authentication-and-roles)
- [Development commands](#development-commands)
- [Data model and moderation rules](#data-model-and-moderation-rules)
- [Security notes](#security-notes)
- [Current limitations](#current-limitations)

## Project structure

| Path | Purpose |
|---|---|
| `Android-App\app\src\main\java\com\example\myapplicationtest\ui\` | Jetpack Compose screens for authentication, maps, profiles, and moderation. |
| `Android-App\app\src\main\java\com\example\myapplicationtest\viewmodel\` | Authentication, map/navigation, and moderator state management. |
| `Android-App\app\src\main\java\com\example\myapplicationtest\network\` | OkHttp client and backend API calls. |
| `Android-App\app\src\main\java\com\example\myapplicationtest\model\` | User, report, vote, route, and navigation models. |
| `Android-App\app\src\main\java\com\example\myapplicationtest\util\` | Directions, geocoding, marker, and image helpers. |
| `Android-App\local.properties.example` | Safe template for the Android SDK path and Google Maps key. |
| `SpringBoot-App\src\main\java\tripkovic\andrej\pmu\controller\` | Authentication, road-problem, vote, and moderation REST endpoints. |
| `SpringBoot-App\src\main\java\tripkovic\andrej\pmu\security\` | JWT creation/validation and Spring Security integration. |
| `SpringBoot-App\src\main\java\tripkovic\andrej\pmu\model\` | MongoDB user, road-problem, and vote documents. |
| `SpringBoot-App\src\main\java\tripkovic\andrej\pmu\repository\` | Spring Data MongoDB repositories. |
| `SpringBoot-App\src\main\java\tripkovic\andrej\pmu\config\` | Security configuration and startup data seeding. |
| `SpringBoot-App\.env.example` | Backend environment-variable template. |

## Architecture and data flow

```text
Android application
  Jetpack Compose screens
          |
          v
  ViewModels + StateFlow
          |
          v
  OkHttp / JSON API client
          |
          | HTTP + Bearer JWT
          v
Spring Boot REST API :8080
  Controllers
          |
          +--> Spring Security + JWT
          |
          +--> Problem enrichment and moderation rules
          |
          v
  Spring Data MongoDB
          |
          v
MongoDB database

Android map features also call:
  Google Maps SDK
  Google Places API
  Google Geocoding API
  Google Directions API
  Photon geocoding fallback
```

The Android Emulator connects to the backend through
`http://10.0.2.2:8080`. On Android emulators, `10.0.2.2` is an alias for the
development computer's loopback interface.

## Features

### Map and navigation

- Displays road problems as custom markers on Google Maps.
- Uses the device's current location.
- Searches for destinations with place suggestions and geocoding fallbacks.
- Calculates routes, distance, duration, and turn-by-turn steps.
- Detects reported problems near a route.
- Attempts to reroute around selected road hazards.
- Advances navigation steps based on the current device location.
- Supports problem filtering and detailed marker dialogs.

### User accounts

- Registration and login.
- Stateless JWT authentication.
- BCrypt password hashing.
- Persistent Android login state.
- Profile display-name, password, and photo updates.
- Camera or gallery selection for profile and report photos.
- User report and voting history.

### Road-problem reporting

- Latitude and longitude.
- Title and description.
- Problem types:
  - `WORK_ON_ROAD`
  - `PROBLEM_ON_ROAD`
  - `OTHER`
- Severity levels:
  - `LOW`
  - `MEDIUM`
  - `HIGH`
  - `CANT_PASS`
- Affected road side:
  - `BOTH`
  - `MY_SIDE`
  - `OPPOSITE`
- Optional direction bearing.
- Optional Base64-encoded photo.
- User and official/moderator reports.
- Owner-controlled report deletion.

### Voting and moderation

- Upvote or downvote a road problem.
- Change or remove an existing vote.
- Automatically hide heavily downvoted reports.
- Inspect personal voting history.
- Review all reports, including hidden reports.
- Inspect and delete individual votes.
- Delete reports.
- View user activity.
- Enable, disable, or delete user accounts.

## Requirements

### Backend

- Java **25**, as requested by the Gradle toolchain.
- MongoDB running locally or available through a connection URI.
- Network access to Maven Central for the first build.
- A Base64-encoded JWT signing key containing at least 32 random bytes.

The included Gradle wrapper downloads the required Gradle version, so a
separate Gradle installation is not necessary.

### Android application

- Android Studio with a JDK compatible with Android Gradle Plugin 9.1.
- Android SDK API 36/36.1.
- Android device or emulator running API 26 or newer.
- A Google Maps Platform API key.
- A running backend reachable from the Android device.

The map and routing functionality uses Google Maps, Places, Geocoding, and
Directions services. Enable the required APIs for the configured key and apply
appropriate Android application/API restrictions in Google Cloud.

## Configuration

### Backend environment

`SpringBoot-App\.env.example` documents all supported variables. Spring Boot
does **not** automatically load this file; configure the variables in the
shell, IDE run configuration, container platform, or secret manager.

| Variable | Required | Purpose |
|---|---|---|
| `JWT_SECRET` | Yes | Base64-encoded key used to sign and verify JWTs. Use at least 32 random bytes before encoding. |
| `MONGODB_URI` | No | MongoDB connection URI. Defaults to `mongodb://localhost:27017/pmu`. |
| `BOOTSTRAP_MODERATOR_EMAIL` | No | Email and login name for the initial full moderator. |
| `BOOTSTRAP_MODERATOR_PASSWORD` | No | Password for the initial full moderator. |
| `BOOTSTRAP_REPORT_MODERATOR_EMAIL` | No | Email and login name for the initial report moderator. |
| `BOOTSTRAP_REPORT_MODERATOR_PASSWORD` | No | Password for the initial report moderator. |

Both the email and password must be supplied for a bootstrap account. If both
are omitted, that account is not created. Supplying only one causes startup to
fail instead of creating an incomplete account.

Generate a development JWT secret in PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

On Linux or macOS:

```bash
export JWT_SECRET="$(openssl rand -base64 32)"
```

Keep the same secret across backend restarts if existing JWTs should remain
valid. Never commit its real value.

Optional development variables in PowerShell:

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017/pmu"
$env:BOOTSTRAP_MODERATOR_EMAIL = "moderator@example.invalid"
$env:BOOTSTRAP_MODERATOR_PASSWORD = "<choose-a-strong-password>"
$env:BOOTSTRAP_REPORT_MODERATOR_EMAIL = "report-moderator@example.invalid"
$env:BOOTSTRAP_REPORT_MODERATOR_PASSWORD = "<choose-a-different-strong-password>"
```

### Android local configuration

Create the ignored local configuration from its template:

```powershell
Set-Location Android-App
Copy-Item local.properties.example local.properties
```

Edit `local.properties`:

```properties
sdk.dir=C\:\\path\\to\\Android\\Sdk
MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
```

Use the real Android SDK path and Maps key only in `local.properties`. The
Secrets Gradle Plugin injects `MAPS_API_KEY` into the application manifest.
`local.properties` is intentionally ignored by Git.

### Backend address

The Android client currently defines its API address in:

```text
Android-App\app\src\main\java\com\example\myapplicationtest\network\ApiClient.kt
```

Its default value is:

```kotlin
const val BASE_URL = "http://10.0.2.2:8080"
```

Use this value when the backend runs on the same computer as the Android
Emulator. For a physical device, replace it with the computer's reachable LAN
address, such as `http://192.168.1.100:8080`, and update
`res\xml\network_security_config.xml` if cleartext HTTP is still required for
local development.

## Quick start

### 1. Start MongoDB

Use an existing MongoDB installation on port 27017, or start a temporary Docker
container:

```bash
docker run --name pmu-mongodb -p 27017:27017 -d mongo
```

### 2. Start the backend on Windows

Open PowerShell in the repository root:

```powershell
Set-Location SpringBoot-App

$env:MONGODB_URI = "mongodb://localhost:27017/pmu"

$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
$rng.Dispose()

.\gradlew.bat bootRun
```

The API starts on:

```text
http://localhost:8080
```

### 3. Configure the Android app

In another PowerShell terminal:

```powershell
Set-Location Android-App
Copy-Item local.properties.example local.properties
```

Replace both placeholders in `local.properties`.

### 4. Run the Android app

Start an Android Emulator in Android Studio, then either run the `app`
configuration from the IDE or install a debug build:

```powershell
.\gradlew.bat installDebug
```

Open the application on the emulator. It will connect to the backend through
`10.0.2.2:8080`.

## Backend API

Public routes do not require an `Authorization` header. Authenticated routes
expect:

```http
Authorization: Bearer <jwt-token>
```

### Authentication

| Method | Route | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a user and return profile data plus a JWT. |
| `POST` | `/api/auth/login` | Public | Authenticate and return profile data plus a JWT. |
| `PUT` | `/api/auth/profile` | Authenticated | Update display username, password, or profile photo and return a refreshed JWT. |

### Road problems

| Method | Route | Access | Purpose |
|---|---|---|---|
| `GET` | `/api/problems` | Public | List non-hidden problems, newest first. |
| `GET` | `/api/problems/{id}` | Public | Get one problem by ID. |
| `GET` | `/api/problems/my` | Authenticated | List the current user's reports. |
| `POST` | `/api/problems` | Authenticated | Create a road-problem report. |
| `DELETE` | `/api/problems/{id}` | Owner | Delete one of the current user's reports. |

### Votes

| Method | Route | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/problems/{id}/vote` | Authenticated | Add, toggle, or replace the current user's vote. |
| `DELETE` | `/api/problems/{id}/vote` | Authenticated | Remove the current user's vote. |
| `GET` | `/api/votes/my` | Authenticated | List the current user's votes and associated reports. |

### Moderation

| Method | Route | Required role | Purpose |
|---|---|---|---|
| `GET` | `/api/moderator/users` | `MODERATOR` | List users. |
| `GET` | `/api/moderator/users/{id}` | `MODERATOR` | View a user's reports and votes. |
| `PUT` | `/api/moderator/users/{id}/disable` | `MODERATOR` | Disable a user account. |
| `PUT` | `/api/moderator/users/{id}/enable` | `MODERATOR` | Enable a user account. |
| `DELETE` | `/api/moderator/users/{id}` | `MODERATOR` | Delete a user and their related content. |
| `GET` | `/api/moderator/problems` | `MODERATOR` or `REPORT_MODERATOR` | List all reports, including hidden reports. |
| `DELETE` | `/api/moderator/problems/{id}` | `MODERATOR` or `REPORT_MODERATOR` | Delete a report and its votes. |
| `GET` | `/api/moderator/problems/{id}/votes` | `MODERATOR` or `REPORT_MODERATOR` | Inspect votes for a report. |
| `DELETE` | `/api/moderator/votes/{id}` | `MODERATOR` or `REPORT_MODERATOR` | Delete a vote and update report totals. |

## Authentication and roles

The backend uses Spring Security with stateless JWT authentication:

- Passwords are stored as BCrypt hashes.
- Tokens expire after 24 hours.
- CSRF is disabled because the API does not use server-side sessions.
- Authentication routes and public problem reads are open.
- All write operations require authentication.

Supported roles:

| Role | Capabilities |
|---|---|
| `USER` | Manage their profile, submit/delete their reports, and vote. |
| `REPORT_MODERATOR` | User capabilities plus report and vote moderation. |
| `MODERATOR` | Full moderation, including user enable/disable/delete actions. |

The Android bottom navigation displays the moderation area only for moderator
roles. The Users tab is available only to full moderators.

## Development commands

### Spring Boot

From `SpringBoot-App`:

| Task | Windows | Linux/macOS |
|---|---|---|
| Run backend | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| Build | `.\gradlew.bat build` | `./gradlew build` |
| Run tests | `.\gradlew.bat test` | `./gradlew test` |
| Compile Java | `.\gradlew.bat compileJava` | `./gradlew compileJava` |
| Clean | `.\gradlew.bat clean` | `./gradlew clean` |

`JWT_SECRET` must be configured for application startup and context-based
tests. MongoDB must also be reachable when the application context starts.

### Android

From `Android-App`:

| Task | Windows | Linux/macOS |
|---|---|---|
| Build debug APK | `.\gradlew.bat assembleDebug` | `./gradlew assembleDebug` |
| Install debug APK | `.\gradlew.bat installDebug` | `./gradlew installDebug` |
| Run local tests | `.\gradlew.bat test` | `./gradlew test` |
| Run device tests | `.\gradlew.bat connectedAndroidTest` | `./gradlew connectedAndroidTest` |
| Clean | `.\gradlew.bat clean` | `./gradlew clean` |

The debug APK is written under:

```text
Android-App\app\build\outputs\apk\debug\
```

## Data model and moderation rules

MongoDB stores three document types:

| Collection | Main data |
|---|---|
| `users` | Login identity, email, name, display username, BCrypt password, role, disabled state, and profile photo. |
| `road_problems` | Coordinates, report metadata, direction, reporter, official/hidden state, vote totals, photo, and creation time. |
| `votes` | Problem ID, voter, vote type, and creation time. |

At startup, the backend inserts five official sample road problems only when
the road-problem collection is empty.

Voting follows these rules:

1. A user has at most one effective vote per problem.
2. Repeating the same vote removes it.
3. Sending the opposite vote replaces the previous vote.
4. Vote totals are recalculated after each change.
5. A report becomes hidden when recent downvotes minus recent upvotes is at
   least five within the last 24 hours.

Public problem lists exclude hidden reports. Moderators can still inspect them
through the moderation API.

## Security notes

- Real API keys, JWT secrets, passwords, and connection credentials must remain
  outside the repository.
- Keep `Android-App\local.properties` local; commit only
  `local.properties.example`.
- Keep backend secrets in environment variables or a deployment secret
  manager; commit only `.env.example`.
- Restrict the Google key to the required APIs, Android package name, and
  signing-certificate fingerprints.
- Use HTTPS and a production backend URL outside local development.
- The Android app currently stores its JWT in ordinary `SharedPreferences`.
  Production deployments should use encrypted storage.
- Changing `JWT_SECRET` invalidates all previously issued tokens.

## Current limitations

- The Android backend URL is hardcoded rather than selected through build
  variants or environment-specific configuration.
- Cleartext HTTP is allowed only for the emulator host address in the current
  network security configuration.
- Browser clients require additional CORS configuration on the backend.
- Request DTOs do not currently provide comprehensive validation annotations.
- Problem type, severity, road side, and vote type are represented as strings
  in the backend instead of validated enums.
- Photos are embedded as Base64 JSON fields and stored directly in MongoDB.
- The Android networking layer manually constructs and parses JSON.
- There is no token refresh flow.
- Navigation is client-side and uses distance thresholds rather than a full
  background navigation service.
- Route avoidance is best-effort and may not avoid every reported problem.
- The project currently contains only basic generated/template automated tests.
