package com.example.drivers_app

import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.taxiapp.*
import com.github.pinball83.maskededittext.MaskedEditText
import com.google.protobuf.ByteString
import com.google.protobuf.Timestamp
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.internal.Platform.logger
import kotlinx.android.synthetic.main.activity_sign_up_add_info.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Level


class AddInfoActivity : AppCompatActivity() {

    private var validation: Validation? = null
    private lateinit var passportEdit: MaskedEditText
    private lateinit var drivingLicenseEdit: MaskedEditText

    private lateinit var expiryDateEdit: MaskedEditText

    private var passportPhoto: ByteString? = null
    private var drivingLicensePhoto: ByteString? = null
    private var registrationCertificatePhoto: ByteString? = null

    companion object {
        private const val REQUEST_PASSPORT_IMAGE = 0
        private const val REQUEST_DRIVING_LICENSE_IMAGE = 1
        private const val REQUEST_STS_IMAGE = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up_add_info)
        passportEdit = passportEditText
        drivingLicenseEdit = drivingLicenseEditText
        expiryDateEdit = expiryDate

        validation = Validation(this@AddInfoActivity)

        val datePickerDialog = DatePickerDialog(this@AddInfoActivity)
        datePickerDialog.setOnDateSetListener { _, year, month, dayOfMonth ->
            expiryDateEdit.text = SpannableStringBuilder("${dayOfMonth.toString().padStart(2, '0')}.${month.toString().padStart(2, '0')}.$year") // format: MM.DD.Y
        }
        datePickerDialog.setOnDismissListener {
            validation!!.isDateValid(expiryDateEdit)
        }

        // Validate passport field
        passportEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isPassportValid(passportEdit)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        drivingLicenseEdit.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                validation!!.isDrivingLicenseValid(drivingLicenseEdit)
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        })

        // Show datePicker dialog after editText focus
        expiryDateEdit.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                datePickerDialog.show()
            }
        }

        loadPassportImageButton.setOnClickListener {
            choosePhotoFromGallery(REQUEST_PASSPORT_IMAGE)
        }
        loadDrivingLicenceButton.setOnClickListener {
            choosePhotoFromGallery(REQUEST_DRIVING_LICENSE_IMAGE)
        }
        loadSTSButton.setOnClickListener {
            choosePhotoFromGallery(REQUEST_STS_IMAGE)
        }
    }

    fun registerDriver(view: View) {
        val fieldsValidation = booleanArrayOf(
                validation!!.isPassportValid(passportEdit),
                validation!!.isDrivingLicenseValid(drivingLicenseEdit),
                validation!!.isDateValid(expiryDateEdit),
                passportPhoto != null,
                drivingLicensePhoto != null,
                registrationCertificatePhoto != null
        )
        if (!fieldsValidation.contains(false)) {
            GlobalScope.launch {
                val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address),
                        resources.getInteger(R.integer.server_port)).usePlaintext().build()
                val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
                val birthDateParse = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(intent.getStringExtra("birthDate")).time
                val expiryDateParse = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(expiryDateEdit.text.toString()).time
                val driver = Driver.newBuilder()
                        .setFirstName(intent.getStringExtra("name"))
                        .setSurname(intent.getStringExtra("surname"))
                        .setPartronymic(intent.getStringExtra("patronymic"))
                        .setBirthDate(Timestamp.newBuilder().setSeconds(birthDateParse))
                        .setPhoneNumber(intent.getStringExtra("phone"))
                        .setEmail(intent.getStringExtra("email"))
                        .setPassword(intent.getStringExtra("password"))
                        .build()
                val driverDocuments = DriverDocuments.newBuilder()
                        .setPassportNumber(passportEdit.text.toString())
                        .setPassportImage(passportPhoto)
                        .setDrivingLicenseNumber(drivingLicenseEdit.text.toString())
                        .setExpiryDate(Timestamp.newBuilder().setSeconds(expiryDateParse))
                        .setDrivingLicenseImage(drivingLicensePhoto)
                        .setStsPhoto(registrationCertificatePhoto)
                        .build()
                val createDriverRequest = CreateDriverRequest.newBuilder()
                        .setApi(getString(R.string.api_version))
                        .setDriver(driver)
                        .setDriverDocuments(driverDocuments)
                        .build()
                val createDriverResponse: CreateDriverResponse
                try {
                    createDriverResponse = blockingStub.createDriver(createDriverRequest)
                    Log.v("Response: ", createDriverResponse.authToken)
                    managedChannel.shutdown()
                    val intent = Intent(applicationContext, SignInActivity::class.java)
                    startActivity(intent)
                    finishAffinity()
                    finish()
                } catch (e: StatusRuntimeException) {
                    if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                        runOnUiThread { Toast.makeText(this@AddInfoActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    } else if (e.status == Status.UNKNOWN) {
                        runOnUiThread { Toast.makeText(this@AddInfoActivity, R.string.user_is_already_exists, Toast.LENGTH_LONG).show() }
                    }
                    logger.log(Level.WARNING, "RPC failed: " + e.status)
                    managedChannel.shutdown()
                }
            }
        } else {
            Toast.makeText(this@AddInfoActivity, R.string.error_empty_fields, Toast.LENGTH_SHORT).show()
        }
    }

    private fun choosePhotoFromGallery(requestImage: Int) {
        Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI).also { choosePhotoIntent ->
            choosePhotoIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(choosePhotoIntent, requestImage)
            }
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_CANCELED) {
            return
        } else if (resultCode == AppCompatActivity.RESULT_OK) {
            if (data != null) {
                val contentURI = data.data
                try {
                    // Save data to bitmap
                    val bitmap = MediaStore.Images.Media.getBitmap(this.contentResolver, contentURI)
                    GlobalScope.launch {
                        // Compress bitmap
                        val out = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.WEBP, 70, out)
                        val bitmapByteArray = ByteString.copyFrom(out.toByteArray())
                        when (requestCode) {
                            0 -> {
                                passportPhoto = bitmapByteArray
                                loadPassportImageButton!!.background = ContextCompat.getDrawable(this@AddInfoActivity, R.color.btn_success)
                                arePhotosLoaded()
                            }
                            1 -> {
                                drivingLicensePhoto = bitmapByteArray
                                loadDrivingLicenceButton!!.background = ContextCompat.getDrawable(this@AddInfoActivity, R.color.btn_success)
                                arePhotosLoaded()
                            }
                            2 -> {
                                registrationCertificatePhoto = bitmapByteArray
                                loadSTSButton!!.background = ContextCompat.getDrawable(this@AddInfoActivity, R.color.btn_success)
                                arePhotosLoaded()
                            }
                        }
                    }
                    Toast.makeText(this@AddInfoActivity, R.string.image_saved, Toast.LENGTH_SHORT).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this@AddInfoActivity, R.string.error_failed, Toast.LENGTH_SHORT).show()
                }
            }

        }
    }

    private fun arePhotosLoaded() {
        if (passportPhoto != null && drivingLicensePhoto != null && registrationCertificatePhoto != null) {
            runOnUiThread {
                registerButton.isEnabled = true
            }
        }
    }
}
