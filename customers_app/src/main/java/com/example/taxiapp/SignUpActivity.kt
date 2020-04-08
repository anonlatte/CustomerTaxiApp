package com.example.taxiapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.taxiapp.utils.Validation
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import kotlinx.android.synthetic.main.activity_sign_up.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

// TODO convert as a fragment
class SignUpActivity : AppCompatActivity() {

    private var validation: Validation? = null
    private var nameEditText: EditText? = null
    private var phoneEditText: EditText? = null
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        ForDebugging().turnOnStrictMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        nameEditText = nameEdit
        phoneEditText = phoneEdit
        emailEditText = emailEdit
        passwordEditText = passwordEdit
        validation = Validation(this@SignUpActivity)
//        countryCodePicker.registerPhoneNumberTextView(phoneEditText)
        val policy = Html.fromHtml(getString(R.string.agree_terms_privacy), Html.FROM_HTML_MODE_LEGACY)
        val termsOfUse = agree_terms_privacy
        termsOfUse.text = policy
        termsOfUse.movementMethod = LinkMovementMethod.getInstance()


        // Validate name field
        nameEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isNameValid(nameEditText)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        // Validate password field
        passwordEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {}
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validation!!.isPasswordValid(passwordEditText, true)
            }
        })
        passwordEditText!!.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                passwordEditText!!.background.clearColorFilter()
            }
        }

        // Validate phone field
        phoneEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isPhoneValid(phoneEditText)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })


    }

    fun register(view: View) {
        val fieldsValidation = mapOf(
                "name" to validation!!.isNameValid(nameEditText),
                "phone" to validation!!.isPhoneValid(phoneEditText),
                "password" to validation!!.isPasswordValid(passwordEditText, true),
                "email" to validation!!.isEmailValid(emailEditText)
        )
        if (!fieldsValidation.values.contains(false)) {
            GlobalScope.launch {
                val managedChannel = ManagedChannelBuilder.forAddress(BuildConfig.ServerAddress, BuildConfig.ServerPort).usePlaintext().build()
                val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
                val phoneText = countryCodePicker.selectedCountryCode + phoneEditText!!.text
                val customer = Customer.newBuilder()
                        .setName(nameEditText!!.text.toString())
                        .setPhoneNumber(phoneText)
                        .setEmail(emailEditText!!.text.toString())
                        .setPassword(passwordEditText!!.text.toString())
                        .build()
                val createCustomerRequest = CreateCustomerRequest.newBuilder()
                        .setApi(BuildConfig.ApiVersion)
                        .setCustomer(customer)
                        .build()
                val createCustomerResponse: CreateCustomerResponse
                try {
                    createCustomerResponse = blockingStub.createCustomer(createCustomerRequest) // Запрос на создание
                    Log.v("Response", createCustomerResponse.authToken)
                    managedChannel.shutdown()
                    val intent = Intent(applicationContext, SignInActivity::class.java)
                    startActivity(intent)
                    finish()
                } catch (e: StatusRuntimeException) {
                    if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                        runOnUiThread { Toast.makeText(this@SignUpActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    } else if (e.status.code == Status.UNKNOWN.code) {
                        runOnUiThread { Toast.makeText(this@SignUpActivity, R.string.user_is_already_exists, Toast.LENGTH_LONG).show() }
                    }
                    //logger.log(Level.WARNING, "RPC failed: " + e.getStatus());
                    managedChannel.shutdown()
                }
            }

        }
    }


    fun changeForm(view: View) {
        redirectToLogin.setTextColor(ContextCompat.getColor(this@SignUpActivity, R.color.colorAccent))
        val intent = Intent(applicationContext, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

}