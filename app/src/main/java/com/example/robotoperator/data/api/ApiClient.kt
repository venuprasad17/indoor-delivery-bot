package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.LoginResponse
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClient(
    val settingsStore: ServerSettingsStore,
    private val onAuthExpired: () -> Unit
) {

    // Simulation Engine for Safety Simulation Mode / Offline Testing
    val simulationEngine = SimulationEngine(settingsStore)

    // Keep access token in memory per spec
    @Volatile
    var accessToken: String? = null

    // Cookie storage for native httpOnly refresh_token cookie
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = cookieStore.getOrPut(url.host) { mutableListOf() }
            synchronized(list) {
                for (cookie in cookies) {
                    list.removeAll { it.name == cookie.name }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = cookieStore[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                // remove expired
                list.removeAll { it.expiresAt < now }
                return list.toList()
            }
        }
    }

    private val isRefreshing = AtomicBoolean(false)
    private val refreshLock = Any()

    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()

        // If Safety Simulation Mode is active, intercept with local simulation engine
        if (settingsStore.isSimulationMode) {
            val simResponse = simulationEngine.handleRequest(original)
            if (simResponse != null) {
                return@Interceptor simResponse
            }
        }

        val path = original.url.encodedPath

        // Skip auth header on public/health/auth endpoints
        if (path.contains("/auth/login") || path.contains("/health")) {
            return@Interceptor chain.proceed(original)
        }

        val token = accessToken
        val request = if (!token.isNullOrEmpty()) {
            original.newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            original
        }

        val response = chain.proceed(request)

        // Handle 401: try /auth/refresh once and retry request
        if (response.code == 401 && !path.contains("/auth/refresh") && !path.contains("/auth/login")) {
            response.close()
            val refreshed = refreshAccessTokenSync()
            if (refreshed) {
                val newToken = accessToken
                val retryRequest = original.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                return@Interceptor chain.proceed(retryRequest)
            } else {
                onAuthExpired()
            }
        }

        response
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    // Dynamic baseUrl intercepting
    private var currentBaseUrl: String = ""
    private var cachedRetrofit: Retrofit? = null
    private var cachedService: ApiService? = null

    @Synchronized
    fun getService(): ApiService {
        val configuredUrl = settingsStore.serverUrl.trimEnd('/') + "/"
        if (cachedService != null && currentBaseUrl == configuredUrl) {
            return cachedService!!
        }

        currentBaseUrl = configuredUrl
        val retrofit = Retrofit.Builder()
            .baseUrl(configuredUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        cachedRetrofit = retrofit
        val service = retrofit.create(ApiService::class.java)
        cachedService = service
        return service
    }

    /**
     * Refreshes the access token synchronously.
     * Uses cookie jar for the refresh token.
     * // TODO: confirm refresh strategy with backend and store it in expo-secure-store if provided
     */
    fun refreshAccessTokenSync(): Boolean {
        if (settingsStore.isSimulationMode) {
            accessToken = "sim_jwt_token_${System.currentTimeMillis()}"
            return true
        }

        synchronized(refreshLock) {
            return try {
                val refreshUrl = settingsStore.serverUrl.trimEnd('/') + "/auth/refresh"
                val rawClient = OkHttpClient.Builder()
                    .cookieJar(cookieJar)
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build()

                val body = "".toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(refreshUrl)
                    .post(body)
                    .build()

                val response = rawClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseStr = response.body?.string() ?: ""
                    val adapter = moshi.adapter(LoginResponse::class.java)
                    val loginRes = adapter.fromJson(responseStr)
                    if (loginRes != null && loginRes.access_token.isNotEmpty()) {
                        accessToken = loginRes.access_token

                        // TODO: confirm refresh strategy with backend
                        if (!loginRes.refresh_token.isNullOrEmpty()) {
                            settingsStore.savedRefreshToken = loginRes.refresh_token
                        }
                        true
                    } else {
                        false
                    }
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    fun clearAuth() {
        accessToken = null
        cookieStore.clear()
        // TODO: confirm refresh strategy with backend
        settingsStore.savedRefreshToken = null
    }
}
