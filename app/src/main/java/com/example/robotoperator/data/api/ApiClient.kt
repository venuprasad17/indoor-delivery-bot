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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ApiClient(private val settingsStore: ServerSettingsStore) {

    // Access token is held in-memory ONLY
    @Volatile
    private var accessToken: String? = null

    var onAuthRevoked: (() -> Unit)? = null

    // In-memory cookie jar to retain httpOnly refresh_token cookie
    private val cookieJar = ScopedCookieJar()

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
                val request = requestBuilder.build()
                val longRunningDeliveryCall = request.url.encodedPath.matches(
                    Regex(".*/deliveries/[^/]+/(dispatch|confirm-pickup|confirm-loading)")
                )
                if (longRunningDeliveryCall) {
                    chain.withReadTimeout(130, TimeUnit.SECONDS).proceed(request)
                } else {
                    chain.proceed(request)
                }
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
        val baseUrl = settingsStore.requireServerUrl().removeSuffix("/") + "/"
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
        cookieJar.clear()
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
            val refreshUrl = "${settingsStore.requireServerUrl().removeSuffix("/")}/auth/refresh"
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

/** In-memory OkHttp cookie jar that applies OkHttp's host, path, Secure, and expiry rules. */
internal class ScopedCookieJar : CookieJar {
    private val lock = Any()
    private val cookies = mutableListOf<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = synchronized(lock) {
        cookies.forEach { cookie ->
            this.cookies.removeAll {
                it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path
            }
            if (cookie.matches(url) && cookie.expiresAt > System.currentTimeMillis()) this.cookies.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        cookies.filter { it.matches(url) }
    }

    fun clear() = synchronized(lock) { cookies.clear() }
}
