package com.example.drivers_app

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taxiapp.IsAccountActivatedRequest
import com.example.taxiapp.TokenCheckRequest
import com.example.taxiapp.TokenCheckResponse
import com.example.taxiapp.taxiServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity() {

    private var savedToken: String? = null
    private var savedLogin: String? = null
    private var lastLogin: Long? = null
    private var loginTime: Long? = null
    private var sPref: SharedPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {

//        ForDebugging().turnOnStrictMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        var intent: Intent
        sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)
        savedToken = sPref!!.getString("auth_token", "")
        savedLogin = sPref!!.getString("login", "")
        lastLogin = sPref!!.getLong("last_login", 0)
        loginTime = Date().time
        // TODO check is account verified
        if (savedToken != "" || savedLogin != "" || (loginTime!! - lastLogin!!) / 2592000000.0 < 30) { // If last_login timestamp is older than 30 days
            GlobalScope.launch {
                val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
                val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
                val tokenCheckRequest = TokenCheckRequest.newBuilder()
                        .setApi(getString(R.string.api_version))
                        .setUserType(1)
                        .setAuthToken(savedToken)
                        .setLogin(savedLogin)
                        .build()
                val tokenCheckResponse: TokenCheckResponse
                try {
                    tokenCheckResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).tokenCheck(tokenCheckRequest) // Запрос на создание

                    // Checking is account of driver activated
                    val isAccountActivatedRequest = IsAccountActivatedRequest.newBuilder()
                            .setApi(getString(R.string.api_version))
                            .setDriverLogin(savedLogin)
                            .build()
                    val isAccountActivatedResponse = blockingStub.isAccountActivated(isAccountActivatedRequest)

                    managedChannel.shutdown()
                    intent = when {
                        !isAccountActivatedResponse.isActivated -> Intent(applicationContext, UnverifiedAccountActivity::class.java)
                        tokenCheckResponse.isValidToken -> Intent(applicationContext, MainActivity::class.java)
                        else -> Intent(applicationContext, SignInActivity::class.java)
                    }
                    startActivity(intent)
                    finish()
                } catch (e: StatusRuntimeException) {
                    if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                        runOnUiThread { Toast.makeText(this@SplashScreenActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    }
                    //                                logger.log(Level.WARNING, "RPC failed: " + e.getStatus());
                    managedChannel.shutdown()
                    intent = Intent(applicationContext, SignInActivity::class.java)
                    startActivity(intent)
                    finish()
                }
            }
        } else {
            intent = Intent(applicationContext, SignInActivity::class.java)
            startActivity(intent)
            finish()
        }

    }
}
