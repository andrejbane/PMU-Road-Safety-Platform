package com.example.myapplicationtest.network

import com.example.myapplicationtest.model.RoadProblem
import com.example.myapplicationtest.model.RoadProblemType
import com.example.myapplicationtest.model.RoadSide
import com.example.myapplicationtest.model.Severity
import com.example.myapplicationtest.model.UserActivity
import com.example.myapplicationtest.model.UserProfile
import com.example.myapplicationtest.model.Vote
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private const val MOD_TAG = "ModeratorApi"

/** Reads the response body once, logs status + body under [MOD_TAG], and returns the body text. */
private fun Response.readBodyLogged(label: String): String {
    val text = body?.string().orEmpty()
    if (isSuccessful) {
        Log.d(MOD_TAG, "$label -> $code OK (${text.length} chars): ${text.take(500)}")
    } else {
        Log.e(MOD_TAG, "$label -> $code FAIL: ${text.ifBlank { "(empty body)" }}")
    }
    return text
}

data class AuthResult(val token: String, val name: String, val email: String, val displayUsername: String? = null, val role: String = "USER", val photoBase64: String? = null)

suspend fun apiRegister(name: String, email: String, password: String, displayUsername: String? = null, photoBase64: String? = null): AuthResult =
    withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", email)
            .put("name", name)
            .put("email", email)
            .put("password", password)
            .apply {
                displayUsername?.let { put("displayUsername", it) }
                photoBase64?.let { put("photoBase64", it) }
            }
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/register")
            .post(body)
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("Empty response")
        if (!response.isSuccessful) error(responseBody)
        val json = JSONObject(responseBody)
        AuthResult(json.getString("token"), json.optString("name", email), json.getString("email"), json.optString("displayUsername").takeIf { it.isNotEmpty() }, json.optString("role", "USER"), json.optString("photoBase64").takeIf { it.isNotEmpty() })
    }

suspend fun apiLogin(email: String, password: String): AuthResult =
    withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("username", email)
            .put("password", password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/login")
            .post(body)
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("Empty response")
        if (!response.isSuccessful) error(responseBody)
        val json = JSONObject(responseBody)
        AuthResult(json.getString("token"), json.optString("name", email), json.getString("email"), json.optString("displayUsername").takeIf { it.isNotEmpty() }, json.optString("role", "USER"), json.optString("photoBase64").takeIf { it.isNotEmpty() })
    }

private fun JSONObject.toRoadProblem() = RoadProblem(
    id = optString("id").takeIf { it.isNotEmpty() },
    position = LatLng(getDouble("latitude"), getDouble("longitude")),
    title = getString("title"),
    description = getString("description"),
    type = RoadProblemType.valueOf(getString("type")),
    severity = Severity.valueOf(getString("severity")),
    roadSide = RoadSide.from(optString("roadSide").takeIf { it.isNotEmpty() }),
    directionBearing = if (has("directionBearing") && !isNull("directionBearing")) getDouble("directionBearing") else null,
    isUserReport = optBoolean("userReport", false),
    official = optBoolean("official", false),
    reportedBy = optString("reportedBy").takeIf { it.isNotEmpty() },
    upvotes = optInt("upvotes", 0),
    downvotes = optInt("downvotes", 0),
    photoBase64 = optString("photoBase64").takeIf { it.isNotEmpty() },
    createdAt = optString("createdAt").takeIf { it.isNotEmpty() },
    hidden = optBoolean("hidden", false),
)

suspend fun apiGetProblems(): List<RoadProblem> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/problems")
        .get()
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.body?.string() ?: error("Empty response")
    if (!response.isSuccessful) error(body)
    val arr = JSONArray(body)
    (0 until arr.length()).map { i -> arr.getJSONObject(i).toRoadProblem() }
}

suspend fun apiGetMyProblems(token: String): List<RoadProblem> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/problems/my")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.body?.string() ?: error("Empty response")
    if (!response.isSuccessful) error(body)
    val arr = JSONArray(body)
    (0 until arr.length()).map { i -> arr.getJSONObject(i).toRoadProblem() }
}

suspend fun apiDeleteProblem(id: String, token: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/problems/$id")
        .delete()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    if (!response.isSuccessful) error(response.body?.string() ?: "Failed to delete problem")
}

suspend fun apiVoteProblem(problemId: String, voteType: String, token: String): RoadProblem =
    withContext(Dispatchers.IO) {
        val body = JSONObject().put("type", voteType).toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/api/problems/$problemId/vote")
            .post(body)
            .header("Authorization", "Bearer $token")
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("Empty response")
        if (!response.isSuccessful) error(responseBody)
        JSONObject(responseBody).toRoadProblem()
    }

suspend fun apiGetMyVotes(token: String): List<Vote> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/votes/my")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.body?.string() ?: error("Empty response")
    if (!response.isSuccessful) error(body)
    val arr = JSONArray(body)
    (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        Vote(
            id = obj.optString("voteId").takeIf { it.isNotEmpty() },
            voteType = obj.getString("voteType"),
            votedAt = obj.optString("votedAt").takeIf { it.isNotEmpty() },
            problem = obj.optJSONObject("problem")?.toRoadProblem(),
        )
    }
}

suspend fun apiPostProblem(problem: RoadProblem, token: String) = withContext(Dispatchers.IO) {
    val body = JSONObject()
        .put("latitude", problem.position.latitude)
        .put("longitude", problem.position.longitude)
        .put("title", problem.title)
        .put("description", problem.description)
        .put("type", problem.type.name)
        .put("severity", problem.severity.name)
        .put("roadSide", problem.roadSide.name)
        .apply {
            problem.directionBearing?.let { put("directionBearing", it) }
            problem.photoBase64?.let { put("photoBase64", it) }
        }
        .toString()
        .toRequestBody(JSON_MEDIA_TYPE)
    val request = Request.Builder()
        .url("$BASE_URL/api/problems")
        .post(body)
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val responseBody = response.body?.string() ?: error("Empty response")
    if (!response.isSuccessful) error(responseBody)
    JSONObject(responseBody).toRoadProblem()
}

suspend fun apiRemoveVote(problemId: String, token: String): RoadProblem =
    withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/api/problems/$problemId/vote")
            .delete()
            .header("Authorization", "Bearer $token")
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("Empty response")
        if (!response.isSuccessful) error(responseBody)
        JSONObject(responseBody).toRoadProblem()
    }

suspend fun apiUpdateProfile(token: String, displayUsername: String?, currentPassword: String?, newPassword: String?, photoBase64: String? = null): AuthResult =
    withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            displayUsername?.let { put("displayUsername", it) }
            currentPassword?.let { put("currentPassword", it) }
            newPassword?.let { put("newPassword", it) }
            photoBase64?.let { put("photoBase64", it) }
        }.toString().toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url("$BASE_URL/api/auth/profile")
            .put(body)
            .header("Authorization", "Bearer $token")
            .build()
        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: error("Empty response")
        if (!response.isSuccessful) error(responseBody)
        val json = JSONObject(responseBody)
        AuthResult(
            json.getString("token"),
            json.optString("name", ""),
            json.getString("email"),
            json.optString("displayUsername").takeIf { it.isNotEmpty() },
            json.optString("role", "USER"),
            json.optString("photoBase64").takeIf { it.isNotEmpty() },
        )
    }

private fun JSONObject.toUserProfile() = UserProfile(
    id = getString("id"),
    username = optString("username"),
    email = optString("email"),
    name = optString("name"),
    displayUsername = optString("displayUsername").takeIf { it.isNotEmpty() },
    role = optString("role", "USER"),
    disabled = optBoolean("disabled", false),
)

suspend fun apiGetAllUsers(token: String): List<UserProfile> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/users")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("GET /api/moderator/users")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "(empty)" }}")
    val arr = JSONArray(body)
    (0 until arr.length()).map { i -> arr.getJSONObject(i).toUserProfile() }
}

suspend fun apiGetUserActivity(token: String, userId: String): UserActivity = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/users/$userId")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("GET /api/moderator/users/$userId")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "(empty)" }}")
    val json = JSONObject(body)
    val user = json.getJSONObject("user").toUserProfile()
    val problems = json.getJSONArray("problems").let { arr ->
        (0 until arr.length()).map { i -> arr.getJSONObject(i).toRoadProblem() }
    }
    val votes = json.getJSONArray("votes").let { arr ->
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            Vote(
                id = obj.optString("id").takeIf { it.isNotEmpty() },
                voteType = obj.getString("voteType"),
                votedAt = obj.optString("createdAt").takeIf { it.isNotEmpty() },
                votedBy = obj.optString("votedBy").takeIf { it.isNotEmpty() },
            )
        }
    }
    UserActivity(user, problems, votes)
}

suspend fun apiDisableUser(token: String, userId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/users/$userId/disable")
        .put("".toRequestBody(null))
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("PUT /api/moderator/users/$userId/disable")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "Failed" }}")
}

suspend fun apiEnableUser(token: String, userId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/users/$userId/enable")
        .put("".toRequestBody(null))
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("PUT /api/moderator/users/$userId/enable")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "Failed" }}")
}

suspend fun apiDeleteUser(token: String, userId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/users/$userId")
        .delete()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("DELETE /api/moderator/users/$userId")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "Failed" }}")
}

suspend fun apiGetAllProblemsModertor(token: String): List<RoadProblem> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/problems")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("GET /api/moderator/problems")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "(empty)" }}")
    val arr = JSONArray(body)
    (0 until arr.length()).map { i -> arr.getJSONObject(i).toRoadProblem() }
}

suspend fun apiModDeleteProblem(token: String, problemId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/problems/$problemId")
        .delete()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("DELETE /api/moderator/problems/$problemId")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "Failed" }}")
}

suspend fun apiGetProblemVotes(token: String, problemId: String): List<Vote> = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/problems/$problemId/votes")
        .get()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("GET /api/moderator/problems/$problemId/votes")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "(empty)" }}")
    val arr = JSONArray(body)
    (0 until arr.length()).map { i ->
        val obj = arr.getJSONObject(i)
        Vote(
            id = obj.optString("id").takeIf { it.isNotEmpty() },
            voteType = obj.getString("voteType"),
            votedAt = obj.optString("createdAt").takeIf { it.isNotEmpty() },
            votedBy = obj.optString("votedBy").takeIf { it.isNotEmpty() },
        )
    }
}

suspend fun apiModDeleteVote(token: String, voteId: String) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("$BASE_URL/api/moderator/votes/$voteId")
        .delete()
        .header("Authorization", "Bearer $token")
        .build()
    val response = httpClient.newCall(request).execute()
    val body = response.readBodyLogged("DELETE /api/moderator/votes/$voteId")
    if (!response.isSuccessful) error("HTTP ${response.code}: ${body.ifBlank { "Failed" }}")
}
