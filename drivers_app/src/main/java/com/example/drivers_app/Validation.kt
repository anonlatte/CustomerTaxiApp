package com.example.drivers_app

import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.github.pinball83.maskededittext.MaskedEditText
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.regex.Pattern

class Validation(private val activity: AppCompatActivity) {

    fun isFieldValid(editText: EditText?): Boolean {
        val text = editText!!.text.toString()
        val pattern = Pattern.compile("[a-zА-Я]+", Pattern.CASE_INSENSITIVE)
        /*
         * If not empty field is empty and don't matches to pattern
         * then set error and return false
         */
        return if (!pattern.matcher(text).matches()) {
            editText.error = activity.getString(R.string.error_invalid_value)
            false
        } else {
            editText.error = null
            true
        }
    }

    fun isEmailValid(emailEdit: EditText?): Boolean {
        val pattern = android.util.Patterns.EMAIL_ADDRESS
        val text = emailEdit!!.text.toString()
        /*
         * If not empty field is empty and don't matches to pattern
         * then set error and return false
         */
        if (text.isNotEmpty()) {
            if (pattern.matcher(text).matches()) {
                emailEdit.error = null
                return true
            }
        }
        emailEdit.error = activity.getString(R.string.error_invalid_value)
        return false

    }

    fun isPasswordValid(passwordEdit: EditText?, checkStrength: Boolean = false): Boolean {
        val password = passwordEdit!!.text.toString()
        val length = password.length

        val disallowedSymbols = Pattern.compile("[а-я]+", Pattern.CASE_INSENSITIVE)

        // Checking disallowed symbols in password

        if (length == 0) {
            passwordEdit.error = activity.getString(R.string.error_invalid_value)
            return false
        }
        if (disallowedSymbols.matcher(password).find()) {
            passwordEdit.error = activity.getString(R.string.use_latin_and_next_symbols)
            return false
        }
        // Checking passwords strength
        else if (checkStrength) {
            // Minimal length check
            if (length < 6) {
                passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_vanillaredA400), PorterDuff.Mode.ADD)
                passwordEdit.error = activity.getString(R.string.warning_weak_password)
                return false
            }
            var strengthPass = 0
            val matchedCases = arrayOf("[$@$!%*#?&]+", "[A-Z]+", "\\d+", "[a-z]+")
            matchedCases.forEach {
                if (Pattern.compile(it).matcher(password).find()) {
                    strengthPass++
                }
            }
            /* FIXME: improve checking passwords strength
            * quantum_vanillaredA400 - for weak password
            * quantum_yellowA700 - for medium strength password
            * quantum_googgreen - for strong password
            */
            when (strengthPass) {
                0 -> return false
                1 -> {
                    when (length) {
                        in 16..31 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_vanillaredA400), PorterDuff.Mode.ADD)
                        in 32..63 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_yellowA700), PorterDuff.Mode.ADD)
                        in 64..128 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_googgreen), PorterDuff.Mode.ADD)
                    }
                    return true
                }
                2 -> {
                    when (length) {
                        in 8..24 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_vanillaredA400), PorterDuff.Mode.ADD)
                        in 25..48 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_yellowA700), PorterDuff.Mode.ADD)
                        in 49..128 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_googgreen), PorterDuff.Mode.ADD)
                    }
                    return true
                }
                3 -> {
                    when (length) {
                        in 8..12 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_vanillaredA400), PorterDuff.Mode.ADD)
                        in 13..20 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_yellowA700), PorterDuff.Mode.ADD)
                        in 21..128 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_googgreen), PorterDuff.Mode.ADD)
                    }
                    return true
                }
                4 -> {
                    when (length) {
                        in 6..10 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_vanillaredA400), PorterDuff.Mode.ADD)
                        in 11..15 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_yellowA700), PorterDuff.Mode.ADD)
                        in 16..128 -> passwordEdit.background.colorFilter = PorterDuffColorFilter(activity.getColor(R.color.quantum_googgreen), PorterDuff.Mode.ADD)
                    }
                    return true
                }

            }
        }
        return true
    }

    fun isPhoneValid(phoneEdit: EditText?): Boolean {
        val length = phoneEdit!!.text.length
        return if (length < 10) {
            phoneEdit.error = activity.getString(R.string.error_invalid_value)
            false

        } else {
            phoneEdit.error = null
            true
        }
    }

    // TODO: check expiryDate is greater than now
    // TODO: check birthDate is greater than 18
    fun isDateValid(dateEdit: MaskedEditText?): Boolean {
        val pattern = Pattern.compile("^\\d{2}.\\d{2}.\\d{4}$")
        val text = dateEdit!!.text.toString()
        val simpleDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val parsedDate = simpleDateFormat.parse(text)
        return if (text.isNotEmpty() && pattern.matcher(text).matches() && parsedDate > Date()) {
            dateEdit.error = null
            true
        } else {
            dateEdit.error = activity.getString(R.string.error_invalid_value)
            false
        }
    }

    fun isBirthDateValid(dateEdit: MaskedEditText?): Boolean {
        val pattern = Pattern.compile("^\\d{2}.\\d{2}.\\d{4}$")
        val text = dateEdit!!.text
        val parsedDate = LocalDate.parse(text, DateTimeFormatter.ofPattern("dd.MM.yyyy"))

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -18)
        return if (calendar.time.before(Date.from(parsedDate.atStartOfDay(ZoneId.systemDefault()).toInstant()))) {
            dateEdit.error = activity.getString(R.string.error_under_eighteen_years)
            false
        } else if (text!!.isNotEmpty() && pattern.matcher(text).matches()) {
            dateEdit.error = null
            true
        } else {
            dateEdit.error = activity.getString(R.string.error_invalid_value)
            false
        }
    }


    fun isPassportValid(passportEdit: MaskedEditText?): Boolean {
        val pattern = Pattern.compile("^\\d{4}\\s\\d{6}$")
        val text = passportEdit!!.text
        return if (text!!.isNotEmpty() && pattern.matcher(text).matches()) {
            passportEdit.error = null
            true
        } else {
            passportEdit.error = activity.getString(R.string.error_invalid_value)
            false
        }

    }

    fun isDrivingLicenseValid(drivingLicenseEdit: MaskedEditText?): Boolean {
        val pattern = Pattern.compile("^\\d{2}\\s\\d{2}\\s\\d{6}$")
        val text = drivingLicenseEdit!!.text
        return if (text!!.isNotEmpty() && pattern.matcher(text).matches()) {
            drivingLicenseEdit.error = null
            true
        } else {
            drivingLicenseEdit.error = activity.getString(R.string.error_invalid_value)
            false
        }

    }
}