package com.example.drivers_app


import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.Toast
import com.example.drivers_app.MainActivity
import com.example.taxiapp.LoginRequest
import com.example.taxiapp.LoginResponse
import com.example.taxiapp.taxiServiceGrpc
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.android.synthetic.main.activity_sign_in.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit

class SignInActivity : AppCompatActivity() {
    private var phoneEditText: EditText? = null
    private var passwordEditText: EditText? = null
    private var authToken = ""
    private var validation: Validation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ForDebugging().turnOnStrictMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_in)
        phoneEditText = phoneEdit
        passwordEditText = passwordEdit
        validation = Validation(this@SignInActivity)

//        countryCodePicker.registerPhoneNumberTextView(phoneEditText)

        // Validate password field
        passwordEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validation!!.isPasswordValid(passwordEditText)
            }
        })
        // Validate phone field
        phoneEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isPhoneValid(phoneEditText)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })
    }

    fun signIn(view: View) {
        val fieldsValidation = mapOf(
                "phone" to validation!!.isPhoneValid(phoneEditText),
                "password" to validation!!.isPasswordValid(passwordEditText))

        if (!fieldsValidation.values.contains(false)) {
            GlobalScope.launch {
                // Build connection and rpc objects
                val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
                val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
                val phoneText = countryCodePicker.selectedCountryCode + phoneEditText!!.text
                val loginRequest = LoginRequest.newBuilder()
                        .setApi(getString(R.string.api_version))
                        .setLogin(phoneText)
                        .setPassword(passwordEditText!!.text.toString())
                        .setUserType(1)
                        .build()
                val loginResponse: LoginResponse
                try {
                    loginResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).loginUser(loginRequest) // Запрос на создание
                    authToken = loginResponse.authToken
                    managedChannel.shutdown()
                    if (authToken.isNotEmpty()) {
                        // Save data to preferences and start new activity
                        val sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)
                        sPref.edit().putLong("last_login", Date().time).apply() // Token saving into SharedPreferences
                        sPref.edit().putInt("driver_id", loginResponse.userId).apply() // Token saving into SharedPreferences
                        sPref.edit().putString("auth_token", authToken).apply() // Token saving into SharedPreferences
                        sPref.edit().putString("login", phoneText).apply() // Token saving into SharedPreferences
                        val intent = Intent(applicationContext, MainActivity::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        phoneEditText!!.error = ""
                        passwordEditText!!.error = ""
                    }
                } catch (e: StatusRuntimeException) {
                    // Check exceptions
                    if (e.status.cause is java.net.ConnectException) {
                        runOnUiThread { Toast.makeText(this@SignInActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    } else if (e.status.code == Status.Code.NOT_FOUND || e.status.code == Status.Code.PERMISSION_DENIED) {
                        runOnUiThread { Toast.makeText(this@SignInActivity, R.string.error_wrong_data, Toast.LENGTH_LONG).show() }
                    } else if (e.status.code == Status.Code.UNKNOWN) {
                        runOnUiThread { Toast.makeText(this@SignInActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                    }
                    //                                logger.log(Level.WARNING, "RPC failed: " + e.getStatus());
                    managedChannel.shutdown()
                }
            }
        }
    }

    fun changeForm(view: View) {
        redirectToRegister.setTextColor(ContextCompat.getColor(this@SignInActivity, R.color.colorAccent))
        val intent = Intent(applicationContext, SignUpActivity::class.java)
        startActivity(intent)
        finish()
    }
}

