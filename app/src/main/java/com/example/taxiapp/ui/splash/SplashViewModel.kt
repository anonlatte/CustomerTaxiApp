package com.example.taxiapp.ui.splash

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.taxiapp.BuildConfig
import com.example.taxiapp.TokenCheckRequest
import com.example.taxiapp.TokenCheckResponse
import com.example.taxiapp.taxiServiceGrpc
import com.squareup.okhttp.internal.Internal
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class SplashViewModel(sPref: SharedPreferences) : ViewModel() {

    private val savedLogin: String? = sPref.getString("login", "")
    private val savedToken: String? = sPref.getString("auth_token", "")
    private val lastLogin: Long = sPref.getLong("last_login", 0)
    private val loginTime: Long = Date().time
    var requestStatus = MutableLiveData<Int>()

    fun isTokenValid(): Boolean {
        // If last_login timestamp is older than 30 days
        if (savedToken != "" || savedLogin != "" || (loginTime - lastLogin) / 2592000000.0 < 30) {
            // TODO separate api request
            val managedChannel = ManagedChannelBuilder.forAddress(BuildConfig.ServerAddress, BuildConfig.ServerPort).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val tokenCheckRequest = TokenCheckRequest.newBuilder()
                    .setApi(BuildConfig.ApiVersion)
                    .setUserType(0)
                    .setAuthToken(savedToken)
                    .setLogin(savedLogin)
                    .build()
            val tokenCheckResponse: TokenCheckResponse
            return try {
                tokenCheckResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).tokenCheck(tokenCheckRequest) // Запрос на создание
                managedChannel.shutdown()
                tokenCheckResponse.isValidToken
            } catch (e: StatusRuntimeException) {
                if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                    Log.w("Error", e.status.cause.toString())
                }
                Internal.logger.log(Level.WARNING, "RPC failed: " + e.status);
                requestStatus.postValue(404)
                managedChannel.shutdown()
                false
            }
        } else {
            requestStatus.postValue(401)
            Log.w("Warning", "Unauthorized")
            return false
        }
    }
}