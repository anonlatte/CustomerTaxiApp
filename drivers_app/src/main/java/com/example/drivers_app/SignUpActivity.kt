package com.example.drivers_app

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.pinball83.maskededittext.MaskedEditText
import kotlinx.android.synthetic.main.activity_sign_up.*


class SignUpActivity : AppCompatActivity() {

    private var phoneText: String? = null
    private var validation: Validation? = null
    private var nameEditText: EditText? = null
    private var surnameEditText: EditText? = null
    private var patronymicEditText: EditText? = null
    private var phoneEditText: EditText? = null
    private var birthDateEditText: MaskedEditText? = null
    private var emailEditText: EditText? = null
    private var passwordEditText: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        ForDebugging().turnOnStrictMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)

        nameEditText = nameEdit
        surnameEditText = surnameEdit
        patronymicEditText = patronymicEdit
        birthDateEditText = birthDate
        phoneEditText = phoneEdit
        emailEditText = emailEdit
        passwordEditText = passwordEdit
        validation = Validation(this@SignUpActivity)

        val datePickerDialog = DatePickerDialog(this@SignUpActivity)
        datePickerDialog.setOnDateSetListener { _, year, month, dayOfMonth ->
            birthDateEditText!!.text = SpannableStringBuilder("${dayOfMonth.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}.$year") // format: MM.DD.Y
        }
        datePickerDialog.setOnDismissListener {
            validation!!.isBirthDateValid(birthDateEditText)
        }

        val policy = Html.fromHtml(getString(R.string.agree_terms_privacy), Html.FROM_HTML_MODE_LEGACY)
        val termsOfUse = agree_terms_privacy
        termsOfUse.text = policy
        termsOfUse.movementMethod = LinkMovementMethod.getInstance()

        // Validate name field
        nameEditText!!.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isFieldValid(nameEditText)
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

        birthDate.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                datePickerDialog.show()
            }
        }

    }

    fun continueRegistration(view: View) {
        val fieldsValidation = mapOf(
                "name" to validation!!.isFieldValid(nameEditText),
                "surname" to validation!!.isFieldValid(surnameEditText),
                "patronymic" to validation!!.isFieldValid(patronymicEditText),
                "birthDate" to validation!!.isBirthDateValid(birthDateEditText),
                "phone" to validation!!.isPhoneValid(phoneEditText),
                "password" to validation!!.isPasswordValid(passwordEditText, true),
                "email" to validation!!.isEmailValid(emailEditText)
        )
        if (!fieldsValidation.values.contains(false)) {
            phoneText = countryCodePicker.selectedCountryCode + phoneEditText!!.text
            addDriversInfo()
        }
    }

    private fun addDriversInfo() {
        val intent = Intent(applicationContext, AddInfoActivity::class.java)
        intent.putExtra("name", nameEditText!!.text.toString())
        intent.putExtra("surname", surnameEditText!!.text.toString())
        intent.putExtra("patronymic", patronymicEditText!!.text.toString())
        intent.putExtra("birthDate", birthDateEditText!!.text.toString())
        intent.putExtra("phone", phoneText)
        intent.putExtra("password", passwordEditText!!.text.toString())
        intent.putExtra("email", emailEditText!!.text.toString())
        startActivity(intent)
    }

    fun changeForm(view: View) {
        redirectToLogin.setTextColor(ContextCompat.getColor(this@SignUpActivity, R.color.colorAccent))
        val intent = Intent(applicationContext, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }
}