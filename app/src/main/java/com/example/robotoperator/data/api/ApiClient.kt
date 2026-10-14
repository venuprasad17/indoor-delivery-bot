package com.example.robotoperator.data.api

import com.example.robotoperator.data.model.LoginResponse
import com.example.robotoperator.data.model.RefreshResponse
import com.example.robotoperator.data.prefs.ServerSettingsStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClient(private val settingsStore: ServerSettingsStore) {

    // Access token is held in-memory ONLY
    @Volatile
    private var accessToken: String? = null

    var onAuthRevoked: (() -> Unit)? = null

    // In-memory cookie jar to retain httpOnly refresh_token cookie
    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

    private val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(list) {
                cookies.forEach { cookie ->
                    list.removeAll { it.name == cookie.name }
                    list.add(cookie)
                }
            }
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val list = cookieStore[url.host] ?: return emptyList()
            val now = System.currentTimeMillis()
            synchronized(list) {
                list.removeAll { it.expiresAt < now }
                return list.toList()
            }
        }
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val isRefreshing = AtomicBoolean(false)

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                val token = accessToken
                if (!token.isNullOrBlank() && original.header("Authorization") == null) {
                    requestBuilder.header("Authorization", "Bearer $token")
                }
                chain.proceed(requestBuilder.build())
            }
            .authenticator { _, response ->
                if (responseCount(response) >= 2) {
                    notifyAuthRevoked()
                    return@authenticator null
                }

                val path = response.request.url.encodedPath
                if (path.contains("auth/login") || path.contains("auth/refresh")) {
                    notifyAuthRevoked()
                    return@authenticator null
                }

                val refreshed = performSyncRefresh()
                if (refreshed && !accessToken.isNullOrBlank()) {
                    response.request.newBuilder()
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                } else {
                    notifyAuthRevoked()
                    null
                }
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private var currentBaseUrl: String = ""
    private var cachedApiService: ApiService? = null

    fun getService(): ApiService {
        val baseUrl = settingsStore.serverUrl.removeSuffix("/") + "/"
        if (cachedApiService == null || currentBaseUrl != baseUrl) {
            currentBaseUrl = baseUrl
            val retrofit = Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(okHttpClient)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
            cachedApiService = retrofit.create(ApiService::class.java)
        }
        return cachedApiService!!
    }

    fun setAuth(loginResponse: LoginResponse) {
        this.accessToken = loginResponse.access_token
    }

    fun setAccessToken(token: String?) {
        this.accessToken = token
    }

    fun getAccessToken(): String? = accessToken

    fun clearAuth() {
        accessToken = null
        cookieStore.clear()
    }

    suspend fun refreshAuth(): Boolean = withContext(Dispatchers.IO) {
        performSyncRefresh()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun performSyncRefresh(): Boolean {
        if (!isRefreshing.compareAndSet(false, true)) {
            Thread.sleep(500)
            return !accessToken.isNullOrBlank()
        }

        return try {
            val refreshUrl = "${settingsStore.serverUrl.removeSuffix("/")}/auth/refresh"
            val request = Request.Builder()
                .url(refreshUrl)
                .post(RequestBody.create(null, ByteArray(0)))
                .build()

            val refreshClient = okHttpClient.newBuilder().authenticator(Authenticator.NONE).build()
            val resp = refreshClient.newCall(request).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val adapter = moshi.adapter(RefreshResponse::class.java)
                    val refreshResp = adapter.fromJson(body)
                    if (refreshResp != null && refreshResp.access_token.isNotBlank()) {
                        accessToken = refreshResp.access_token
                        return true
                    }
                }
            }
            clearAuth()
            false
        } catch (e: Exception) {
            clearAuth()
            false
        } finally {
            isRefreshing.set(false)
        }
    }

    private fun notifyAuthRevoked() {
        clearAuth()
        onAuthRevoked?.invoke()
    }
}
