package com.example.taxiapp.ui.main

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taxiapp.*
import com.example.taxiapp.utils.directions_helpers.FetchURL
import com.example.taxiapp.utils.directions_helpers.TaskLoadedCallback
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.GoogleMap.OnCameraIdleListener
import com.google.android.gms.maps.GoogleMap.OnCameraMoveStartedListener
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.TypeFilter
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.internal.Platform.logger
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_order_info.*
import kotlinx.android.synthetic.main.activity_order_options.*
import kotlinx.android.synthetic.main.activity_order_wishes.*
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level


class MainActivity : AppCompatActivity(), OnMapReadyCallback,
        OnCameraIdleListener, OnCameraMoveStartedListener, TaskLoadedCallback {

    /*
    * Class for return statement
    * By default status of order is false
    * And response is null
    */
    private class CheckCabRideStatus(var cabRideStatus: Boolean? = false, var checkCabRideResponse: CheckCabRideStatusResponse? = null)

    private var isTimerActive: Boolean = false
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    private var tripPriceTextView: TextView? = null
    private var tripDurationTextView: TextView? = null
    private var addressTextView: TextView? = null
    private var pinImageView: ImageView? = null
    var optionsDialog: AlertDialog? = null
    private var ridingEndDialog: AlertDialog? = null
    private var wishesDialog: AlertDialog? = null
    private var addressSearch: AutocompleteSupportFragment? = null

    private var sPref: SharedPreferences? = null
    private var orderForAnother: Boolean = false
    private var pendingOrder: Boolean = false
    private var mMap: GoogleMap? = null
    private var errorMessage: String? = ""
    private var commentText: String? = ""

    private var geocoder: Geocoder? = null
    private var addresses: List<Address>? = emptyList()
    private var mCameraPosition: CameraPosition? = null
    private var mLastKnownLocation: Location? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null
    private var currentMarker: Marker? = null
    private var destinationMarker: Marker? = null
    private var currentPolyline: Polyline? = null

    // A default location and default zoom to use when location permission is
    // not granted.
    private val mDefaultLocation = LatLng(56.889035, 60.248630)
    private var mLocationPermissionGranted: Boolean = false
    private val isGeoDisabled: Boolean
        get() {
            val mLocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val mIsGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val mIsNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            return !mIsGPSEnabled && !mIsNetworkEnabled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
//        ForDebugging().turnOnStrictMode() // TODO: test app

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        pinImageView = pinImage
        addressTextView = address

        sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)

        addressSearch = startPlaceSearch as AutocompleteSupportFragment?

        optionsDialog = AlertDialog.Builder(this@MainActivity).setView(this.layoutInflater.inflate(R.layout.activity_order_options, null)).create()
        wishesDialog = AlertDialog.Builder(this@MainActivity).setView(this.layoutInflater.inflate(R.layout.activity_order_wishes, null)).create()
        ridingEndDialog = AlertDialog.Builder(this@MainActivity).setView(this.layoutInflater.inflate(R.layout.activity_order_info, null)).create()

        geocoder = Geocoder(this, Locale.getDefault())

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            mLastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
            mCameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
        }

        // Construct a PlaceDetectionClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        mapFragment!!.view!!.alpha = 0.7F

        addressSearch!!.view!!.onFocusChangeListener = View.OnFocusChangeListener { _, _ ->
            addressSearch!!.view!!.visibility = View.VISIBLE
        }
        addressSearch!!.view!!.alpha = 0F

        startTimer()
    }

    private fun startTimer() {
        if (!isTimerActive) {
            timer = Timer()
            timerTask = object : TimerTask() {
                override fun run() {
                    if (sPref!!.getInt("orderedCabRideId", -1) != -1) {
                        val cabRideStatus = checkCabRideStatus()
                        if (cabRideStatus.cabRideStatus!!) { // Is order is active
                            if (cabRideStatus.checkCabRideResponse!!.firstName.isNotEmpty()) { // Has driver accepted an order?
                                if (cabRideStatus.checkCabRideResponse!!.rideStatus == 2) { // Is order ended?
                                    runOnUiThread { showOrdersEndScreen() }
                                    stopTimer()
                                } else {
                                    runOnUiThread {
                                        setLiveCabRideInfo(cabRideStatus.checkCabRideResponse)
                                        orderButton?.visibility = View.GONE
                                    } // Set riding condition
                                }
                            }
                        } else {
                            stopTimer()
                        }
                    } else {
                        stopTimer()
                    }
                }
            }
            timer!!.scheduleAtFixedRate(timerTask, 0, 5000)
            isTimerActive = true
        }
    }

    private fun stopTimer() {
        if (isTimerActive) {
            timer!!.cancel()
            isTimerActive = false
        }
    }

    private fun showOrdersEndScreen() {
        orderButton?.visibility = View.VISIBLE
        orderButton.setOnClickListener {
            makeOrderOptions(it)
        }
        currentPolyline?.remove()
        ridingEndDialog!!.show()
        ridingEndDialog?.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ridingEndDialog!!.window!!.setGravity(Gravity.BOTTOM)
        addressTextView!!.text = getString(R.string.address)
        changeAddress!!.text = getString(R.string.change_address)
        orderButton!!.text = getString(R.string.make_order)
        orderButton!!.background = ContextCompat.getDrawable(this@MainActivity, R.color.quantum_grey400)
        ridingEndDialog!!.setOnDismissListener {
            sPref!!.edit().putInt("orderedCabRideId", -1).apply()
        }
        ridingEndDialog!!.feedbackButton.setOnClickListener {
            var ratingScore = ridingEndDialog!!.ratingBar.rating
            var feedbackText = ridingEndDialog!!.feedbackButton.text
            ridingEndDialog!!.dismiss()
            // TODO: update cab_ride feedback
            //  TODO: update rating of driver
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setLiveCabRideInfo(checkCabRideResponse: CheckCabRideStatusResponse?) {
        findCab()
        progressBar!!.visibility = View.GONE
        addressTextView!!.setText(R.string.driver_is_coming)
        changeAddress!!.text = checkCabRideResponse!!.brandName + ' ' + checkCabRideResponse.modelName + ", " + checkCabRideResponse.color + ", " + checkCabRideResponse.licensePlate + ", " + checkCabRideResponse.firstName
        // TODO add time and distance to info
    }

    fun makeOrderOptions(view: View) {
        optionsDialog!!.show()
        optionsDialog!!.addressEdit.setText(addressTextView!!.text)
        optionsDialog?.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        optionsDialog!!.window!!.setGravity(Gravity.BOTTOM)
        tripPriceTextView = optionsDialog!!.tripPrice
        tripDurationTextView = optionsDialog!!.tripDuration
        optionsDialog!!.addressEdit.setOnClickListener {
            pinImageView!!.visibility = View.VISIBLE
            with(mMap) {
                this!!.setOnCameraIdleListener(this@MainActivity)
                this.setOnCameraMoveStartedListener(this@MainActivity)
            }
        }

        optionsDialog!!.wishesButton.setOnClickListener {
            wishesDialog!!.show()
            wishesDialog?.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            wishesDialog!!.window!!.setGravity(Gravity.BOTTOM)
            wishesDialog!!.pendingOrderCheck.setOnClickListener {
                wishesDialog!!.pendingOrderCheck!!.isChecked = !wishesDialog!!.pendingOrderCheck!!.isChecked
            }
            wishesDialog!!.orderForAnotherCheck.setOnClickListener {
                wishesDialog!!.orderForAnotherCheck!!.isChecked = !wishesDialog!!.orderForAnotherCheck!!.isChecked
            }
            wishesDialog!!.readyWishesButton.setOnClickListener {
                commentText = wishesDialog!!.commentEdit.text.toString()
                pendingOrder = wishesDialog!!.pendingOrderCheck!!.isChecked
                orderForAnother = wishesDialog!!.orderForAnotherCheck!!.isChecked
                wishesDialog!!.dismiss()
            }
        }

        optionsDialog!!.makeOrderButton.setOnClickListener {
            if (makeOrderRequest()) {
                findCab()
            }
        }
        autocompleteFragmentSetup(destinationEdit as AutocompleteSupportFragment)
        (destinationEdit as AutocompleteSupportFragment).setHint(getString(R.string.destination))
        (destinationEdit as AutocompleteSupportFragment).setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                destinationMarker = mMap!!.addMarker(MarkerOptions().position(place.latLng!!))
                currentMarker!!.isVisible = true
                with(mMap) {
                    this!!.setOnCameraIdleListener(null)
                    this.setOnCameraMoveStartedListener(null)
                }
                pinImageView!!.visibility = View.INVISIBLE
                mMap!!.moveCamera(CameraUpdateFactory
                        .newLatLngZoom(currentMarker!!.position, DEFAULT_ZOOM.toFloat()))
                (destinationEdit as AutocompleteSupportFragment).setText(place.address)
                FetchURL(this@MainActivity).execute(getUrl(currentMarker!!.position, destinationMarker!!.position, "driving"), "driving")

            }

            override fun onError(status: Status) {
                Toast.makeText(this@MainActivity, status.toString(), Toast.LENGTH_SHORT).show()
            }
        })
        if (currentMarker != null && destinationMarker != null) {
            if (currentMarker!!.position != null && destinationMarker!!.position != null) {
                FetchURL(this@MainActivity).execute(getUrl(currentMarker!!.position, destinationMarker!!.position, "driving"), "driving")
            }
        }
    }

    // FIXME: timer issue -> android.view.ViewRootImpl$CalledFromWrongThreadException: Only the original thread that created a view hierarchy can touch its views.
    private fun findCab() {
        pinImageView!!.visibility = View.GONE
        progressBar!!.visibility = View.VISIBLE
        changeAddress!!.text = ""
        startPlaceSearch.view!!.visibility = View.GONE
        optionsDialog!!.dismiss()
        wishesDialog!!.dismiss()
        orderButton!!.setText(R.string.cancel_order)
        orderButton!!.background = ContextCompat.getDrawable(this@MainActivity, R.color.quantum_vanillaredA100)
        orderButton!!.setOnClickListener {
            stopFindingCab()
        }
        with(mMap) {
            this?.setOnCameraIdleListener(null)
            this?.setOnCameraMoveStartedListener(null)
        }
        addressTextView?.setText(R.string.finding_driver)
    }

    private fun stopFindingCab() {
        if (deleteCabRequest()) {
            pinImageView!!.visibility = View.VISIBLE
            progressBar!!.visibility = View.GONE
            addressTextView!!.visibility = View.VISIBLE
            changeAddress!!.visibility = View.VISIBLE
            changeAddress!!.setText(R.string.change_address)
            startPlaceSearch.view!!.visibility = View.VISIBLE
            orderButton.setText(R.string.make_order)
            orderButton!!.background = ContextCompat.getDrawable(this@MainActivity, R.color.default_button_material_light)
            orderButton.setOnClickListener {
                makeOrderOptions(it)
            }
            with(mMap) {
                this!!.setOnCameraIdleListener(this@MainActivity)
                this.setOnCameraMoveStartedListener(this@MainActivity)
            }
            destinationMarker?.remove()
            currentPolyline?.remove()
            getDeviceLocation()
        }
    }

    private fun makeOrderRequest(): Boolean {
        // TODO: Validate not null fields
        // Build connection and rpc objects
        val managedChannel = ManagedChannelBuilder.forAddress(BuildConfig.ServerAddress, BuildConfig.ServerPort).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val cabRide = CabRide.newBuilder()
                .setCustomerId(sPref!!.getInt("customer_id", 0))
                .setStartingPoint(currentMarker!!.position.toString())
                .setEndingPoint(destinationMarker!!.position.toString())
                .setPendingOrder(pendingOrder)
                .setOrderForAnother(orderForAnother)
                .setPaymentTypeId(optionsDialog!!.payTypeSpinner.selectedItemPosition + 1) // +1 because mysql rows starts from 1
        if (optionsDialog!!.entranceEdit!!.text.isNotEmpty()) {
            cabRide.entrance = Integer.parseInt(optionsDialog!!.entranceEdit!!.text.toString())
        }
        if (commentText!!.isNotEmpty()) {
            cabRide.comment = commentText
        }
        cabRide.build()
        val cabRideRequest = CreateCabRideRequest.newBuilder()
                .setApi(BuildConfig.ApiVersion)
                .setCabRide(cabRide)
                .setPrice(tripPriceTextView!!.text.toString().split('.')[0].toInt()) // TODO check price set
                .setAuthToken(sPref!!.getString("auth_token", ""))
                .build()
        val cabRideResponse: CreateCabRideResponse
        try {
            cabRideResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).createCabRide(cabRideRequest) // Запрос на создание
            sPref!!.edit().putInt("orderedCabRideId", cabRideResponse.cabRideId).apply() // OrderId saving into SharedPreferences
            managedChannel.shutdown()
            startTimer()
            return true
        } catch (e: StatusRuntimeException) {
            // Check exceptions
            if (e.status.cause is java.net.ConnectException || e.status.code == io.grpc.Status.DEADLINE_EXCEEDED.code) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
            }
            if (e.status.code == io.grpc.Status.Code.NOT_FOUND || e.status.code == io.grpc.Status.Code.PERMISSION_DENIED) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
            }
            if (e.status.code == io.grpc.Status.Code.UNKNOWN) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
            }
            logger.log(Level.WARNING, "RPC failed: " + e.status)
            managedChannel.shutdown()
            return false
        }
    }

    private fun deleteCabRequest(): Boolean {
        // TODO: Validate not null fields
        // Build connection and rpc objects
        val managedChannel = ManagedChannelBuilder.forAddress(BuildConfig.ServerAddress, BuildConfig.ServerPort).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val deleteCabRideRequest = DeleteCabRideRequest.newBuilder()
                .setApi(BuildConfig.ApiVersion)
                .setCabRideId(sPref!!.getInt("orderedCabRideId", -1))
                .setCustomerId(sPref!!.getInt("customer_id", -1))
                .setAuthToken(sPref!!.getString("auth_token", ""))
                .build()
        val deleteCabRideResponse: DeleteCabRideResponse
        try {
            deleteCabRideResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).deleteCabRide(deleteCabRideRequest) // Запрос на создание
            if (deleteCabRideResponse.isSuccessDeleted) {
                sPref!!.edit().putInt("orderedCabRideId", -1).apply() // OrderId saving into SharedPreferences
                currentPolyline?.remove() // clear map route
            } else {
                throw StatusRuntimeException(io.grpc.Status.UNKNOWN)
            }
            managedChannel.shutdown()
            return true
        } catch (e: StatusRuntimeException) {
            // Check exceptions
            if (e.status.cause is java.net.ConnectException || e.status.code == io.grpc.Status.DEADLINE_EXCEEDED.code) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
            }
            if (e.status.code == io.grpc.Status.Code.NOT_FOUND || e.status.code == io.grpc.Status.Code.PERMISSION_DENIED) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
            }
            if (e.status.code == io.grpc.Status.Code.UNKNOWN) {
                runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
            }
            logger.log(Level.WARNING, "RPC failed: " + e.status)
            managedChannel.shutdown()
            return false
        }
    }

    private fun checkCabRideStatus(): CheckCabRideStatus {
        // TODO: Validate not null fields
        // Build connection and rpc objects
        val managedChannel = ManagedChannelBuilder.forAddress(BuildConfig.ServerAddress, BuildConfig.ServerPort).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val checkCabRideStatusRequest = CheckCabRideStatusRequest.newBuilder()
                .setApi(BuildConfig.ApiVersion)
                .setCabRideId(sPref!!.getInt("orderedCabRideId", -1))
                .setAuthToken(sPref!!.getString("auth_token", ""))
                .build()
        val checkCabRideStatusResponse: CheckCabRideStatusResponse
        return try {
            checkCabRideStatusResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).checkCabRideStatus(checkCabRideStatusRequest) // Запрос на создание
            managedChannel.shutdown()
            CheckCabRideStatus(true, checkCabRideStatusResponse)
        } catch (e: StatusRuntimeException) {
            // Check exceptions
            when {
                e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
            }
            logger.log(Level.WARNING, "RPC failed: " + e.status)
            managedChannel.shutdown()
            CheckCabRideStatus()
        }
    }

    private fun autocompleteFragmentSetup(autocompleteSupportFragment: AutocompleteSupportFragment) {
        // Places SDK initialize
        if (!Places.isInitialized()) {
            Places.initialize(applicationContext, BuildConfig.GoogleMapsKey)
        }
        val placesClient = Places.createClient(this)
        val token = AutocompleteSessionToken.newInstance()
        val bounds: RectangularBounds = when {
            currentMarker != null -> RectangularBounds.newInstance(
                    LatLng(currentMarker!!.position.latitude - 0.5, currentMarker!!.position.longitude - 1),
                    LatLng(currentMarker!!.position.latitude + 0.5, currentMarker!!.position.longitude + 1)
            )
            mLastKnownLocation != null -> RectangularBounds.newInstance(
                    LatLng(mLastKnownLocation!!.latitude - 0.5, mLastKnownLocation!!.longitude - 1),
                    LatLng(mLastKnownLocation!!.latitude + 0.5, mLastKnownLocation!!.longitude + 1)
            )
            else -> RectangularBounds.newInstance(
                    LatLng(mDefaultLocation.latitude - 0.5, mDefaultLocation.longitude - 1),
                    LatLng(mDefaultLocation.latitude + 0.5, mDefaultLocation.longitude + 1)
            )
        }
        autocompleteSupportFragment.setPlaceFields(Arrays.asList(Place.Field.ADDRESS, Place.Field.NAME, Place.Field.LAT_LNG))
        val request = FindAutocompletePredictionsRequest.builder()
                .setLocationRestriction(bounds)
                .setCountry("RU")
                .setTypeFilter(TypeFilter.ADDRESS)
                .setSessionToken(token)
                .setQuery(autocompleteSupportFragment.toString())
                .build()
        placesClient.findAutocompletePredictions(request)
        autocompleteSupportFragment.setOnPlaceSelectedListener(object : PlaceSelectionListener {
            override fun onPlaceSelected(place: Place) {
                addressTextView!!.text = place.name
                currentMarker?.position = place.latLng
                mMap!!.moveCamera(CameraUpdateFactory
                        .newLatLngZoom(currentMarker?.position, DEFAULT_ZOOM.toFloat()))
            }

            override fun onError(status: Status) {
                Toast.makeText(this@MainActivity, status.toString(), Toast.LENGTH_SHORT).show()

            }
        })
    }

    override fun onTaskDone(vararg values: Any) {
        currentPolyline?.remove()
        currentPolyline = mMap!!.addPolyline(values[0] as PolylineOptions?)
    }

    private fun getUrl(origin: LatLng, dest: LatLng, directionMode: String): String {
        // Origin of route
        val strOrigin = "origin=" + origin.latitude + "," + origin.longitude
        // Destination of route
        val strDest = "destination=" + dest.latitude + "," + dest.longitude
        // Mode
        val mode = "mode=$directionMode"
        // Building the parameters to the web service
        val parameters = "$strOrigin&$strDest&$mode"
        // Output format
        val output = "json"
        // Building the url to the web service
        return "https://maps.googleapis.com/maps/api/directions/" + output + "?" + parameters + "&key=" + BuildConfig.GoogleMapsKey
    }

    private fun getLocationPermission() {
        /*
         * Request location permission, so that we can get the location of the
         * device. The result of the permission request is handled by a callback,
         * onRequestPermissionsResult.
         */
        if (ContextCompat.checkSelfPermission(this.applicationContext,
                        android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mLocationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(this,
                    arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                    PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */

    private fun updateLocationUI() {
        if (mMap == null) {
            return
        }
        try {
            if (mLocationPermissionGranted) {
                mMap!!.isMyLocationEnabled = true
                mMap!!.uiSettings.isMyLocationButtonEnabled = true
            } else {
                mMap!!.isMyLocationEnabled = false
                mMap!!.uiSettings.isMyLocationButtonEnabled = false
                mLastKnownLocation = null
                getLocationPermission()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }

    }

    private fun getDeviceLocation() {
        /*
         * Get the best and most recent location of the device, which may be null in rare
         * cases when a location is not available.
         */
        try {
            if (mLocationPermissionGranted) {
                val locationResult = fusedLocationProviderClient!!.lastLocation
                locationResult.addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Set the map's camera position to the current location of the device.
                        mLastKnownLocation = task.result
                        if (mLastKnownLocation != null) {
                            mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                    LatLng(mLastKnownLocation!!.latitude,
                                            mLastKnownLocation!!.longitude), DEFAULT_ZOOM.toFloat()))
                        }
                    } else {
                        Log.d("maps", "Current location is null. Using defaults.")
                        Log.e("maps", "Exception: %s", task.exception)
                        mMap!!.moveCamera(CameraUpdateFactory
                                .newLatLngZoom(mDefaultLocation, DEFAULT_ZOOM.toFloat()))
                        mMap!!.uiSettings.isMyLocationButtonEnabled = false
                    }
                }
            }

        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message)
        }

    }

    override fun onMapReady(map: GoogleMap) {
// Prompt the user for permission.
        mMap = map
        getLocationPermission()

        // Turn on the My Location layer and the related control on the map.
        updateLocationUI()

        // Get the current location of the device and set the position of the map.
        getDeviceLocation()

        with(mMap) {
            this!!.setOnCameraIdleListener(this@MainActivity)
            this.setOnCameraMoveStartedListener(this@MainActivity)
        }

        mMap!!.setOnMyLocationButtonClickListener {
            if (isGeoDisabled) {
                val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(settingsIntent)
            }
            false
        }
        currentMarker = if (mLastKnownLocation != null) {
            mMap!!.addMarker(MarkerOptions().position(LatLng(mLastKnownLocation!!.latitude,
                    mLastKnownLocation!!.longitude)))
        } else {
            mMap!!.addMarker(MarkerOptions().position(mDefaultLocation))
        }

        if (sPref!!.getInt("orderedCabRideId", -1) != -1) {
            val cabRideStatus = checkCabRideStatus()
            if (cabRideStatus.cabRideStatus!!) { // Is order is active
                if (cabRideStatus.checkCabRideResponse!!.firstName.isNotEmpty()) { // Has driver accepted an order?
                    if (cabRideStatus.checkCabRideResponse!!.rideStatus == 2) { // Is order ended?
                        showOrdersEndScreen()
                    } else {
                        setLiveCabRideInfo(cabRideStatus.checkCabRideResponse) // Set riding condition
                    }
                } else {
                    findCab() // Set finding condition
                }
            }
        }
        autocompleteFragmentSetup(startPlaceSearch as AutocompleteSupportFragment)
    }

    override fun onCameraIdle() {
        val layoutParams: ConstraintLayout.LayoutParams = pinImage!!.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = 128
        layoutParams.height = 200
        pinImage!!.layoutParams = layoutParams
        addressTextView!!.visibility = View.VISIBLE
        addressSearch!!.view!!.visibility = View.VISIBLE
        val center: LatLng = mMap!!.cameraPosition.target
        currentMarker = mMap!!.addMarker(MarkerOptions().position(center))
        if (currentMarker != null) {
            try {
                addresses = geocoder!!.getFromLocation(currentMarker!!.position.latitude, currentMarker!!.position.longitude, 1)
            } catch (ioException: IOException) {
                // Catch network or other I/O problems.
                errorMessage = R.string.error_internet_connection.toString()
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
            } catch (illegalArgumentException: IllegalArgumentException) {
                // Catch invalid latitude or longitude values.
                errorMessage = R.string.error_internet_connection.toString()
                Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
            }

            // Handle case where no address was found.
            if (addresses!!.isEmpty()) {
                if (errorMessage!!.isEmpty()) {
                    errorMessage = getString(R.string.warning_address_not_found)
                    Toast.makeText(this@MainActivity, errorMessage, Toast.LENGTH_SHORT).show()
                }
            } else {
                val address = addresses!![0]
                if (address.thoroughfare == null || address.subThoroughfare == null) {
                    addressTextView!!.setText(R.string.warning_unable_to_locate)
                } else {
                    addressTextView!!.text = (address.thoroughfare + ',' + address.subThoroughfare)
                }
                currentMarker!!.isVisible = false
            }
        }
    }

    override fun onCameraMoveStarted(p0: Int) {
        if (currentMarker != null) {
            currentMarker!!.remove()
        }
        addressTextView!!.visibility = View.INVISIBLE
        addressSearch!!.view!!.visibility = View.INVISIBLE
        val layoutParams: ConstraintLayout.LayoutParams = pinImage!!.layoutParams as ConstraintLayout.LayoutParams
        layoutParams.width = 64
        layoutParams.height = 100
        pinImage!!.layoutParams = layoutParams
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                recreate()
            }
        }
    }


    companion object {
        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"
        private const val DEFAULT_ZOOM = 15
        private const val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    }
}
