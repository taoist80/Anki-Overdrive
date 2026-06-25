package dev.overdrive.net

import dev.overdrive.profile.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class AuthResult(val userId: String = "", val token: String = "", val profile: Profile = Profile(), val version: Int = 0)

@Serializable
data class ProfileEnvelope(val profile: Profile = Profile(), val version: Int = 0)

@Serializable
data class StoreItem(val id: String = "", val kind: String = "", val name: String = "", val price: Int = 0, val grantsCoins: Int = 0)

@Serializable
private data class StoreCatalog(val items: List<StoreItem> = emptyList())

/**
 * Client for the local OverdriveX backend (server/). Plain HttpURLConnection on Dispatchers.IO —
 * no extra deps. Reach the dev server from the tablet via `adb reverse tcp:8080 tcp:8080`
 * (so 127.0.0.1:8080 on-device tunnels to the Mac). Session token kept in memory for the session.
 */
object BackendClient {
    @Volatile var baseUrl: String = "http://127.0.0.1:8080"
    @Volatile var token: String? = null
    @Volatile var userId: String? = null
    val signedIn: Boolean get() = token != null

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable private data class SignupReq(val username: String, val password: String, val driverName: String)
    @Serializable private data class LoginReq(val username: String, val password: String)
    @Serializable private data class ProfileReq(val profile: Profile)

    suspend fun signup(username: String, password: String, driverName: String): Result<AuthResult> = runCatching {
        val resp = request("POST", "/api/v1/signup", json.encodeToString(SignupReq(username, password, driverName)), auth = false)
        json.decodeFromString<AuthResult>(resp).also { token = it.token; userId = it.userId }
    }

    suspend fun login(username: String, password: String): Result<AuthResult> = runCatching {
        val resp = request("POST", "/api/v1/login", json.encodeToString(LoginReq(username, password)), auth = false)
        json.decodeFromString<AuthResult>(resp).also { token = it.token; userId = it.userId }
    }

    suspend fun pushProfile(profile: Profile): Result<Int> = runCatching {
        val resp = request("PUT", "/api/v1/profile", json.encodeToString(ProfileReq(profile)), auth = true)
        json.decodeFromString<ProfileEnvelope>(resp).version
    }

    suspend fun pullProfile(): Result<Profile> = runCatching {
        json.decodeFromString<ProfileEnvelope>(request("GET", "/api/v1/profile", null, auth = true)).profile
    }

    suspend fun store(): Result<List<StoreItem>> = runCatching {
        json.decodeFromString<StoreCatalog>(request("GET", "/api/v1/store", null, auth = false)).items
    }

    fun logout() { token = null; userId = null }

    private suspend fun request(method: String, path: String, body: String?, auth: Boolean): String =
        withContext(Dispatchers.IO) {
            val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 4000
                readTimeout = 6000
                if (auth) token?.let { setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray()) }
                }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code !in 200..299) throw RuntimeException("HTTP $code: $text")
            text
        }
}
