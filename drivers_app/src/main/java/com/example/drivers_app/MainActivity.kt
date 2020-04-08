package com.example.drivers_app

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
import android.os.CountDownTimer
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.drivers_app.directions_helpers.FetchURL
import com.example.drivers_app.directions_helpers.TaskLoadedCallback
import com.example.taxiapp.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.navigation.NavigationView
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.internal.Platform
import kotlinx.android.synthetic.main.activity_active_order_dialog.*
import kotlinx.android.synthetic.main.activity_available_order_dialog.*
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.logging.Level

class MainActivity : AppCompatActivity(),
        OnMapReadyCallback,
        NavigationView.OnNavigationItemSelectedListener,
        TaskLoadedCallback {

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_profile -> Toast.makeText(applicationContext, "Profile", Toast.LENGTH_SHORT).show()
            R.id.nav_history -> Toast.makeText(applicationContext, "History", Toast.LENGTH_SHORT).show()
            R.id.nav_settings -> showSettingsActivity()
            R.id.nav_faq -> Toast.makeText(applicationContext, "FAQ", Toast.LENGTH_SHORT).show()
            R.id.nav_support_request -> Toast.makeText(applicationContext, "Make support request", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun showSettingsActivity() {
        val userSettingsIntent = Intent(this@MainActivity, UserSettingsActivity::class.java)
        startActivity(userSettingsIntent)
    }

    /*
    * Class for return statement
    * By default status of order is false
    * And response is null
    */
    private class CheckCabRideStatus(var cabRideStatus: Boolean? = false, var checkCabRideResponse: CheckCabRideStatusResponse? = null)

    private var sPref: SharedPreferences? = null
    private var addressTextView: TextView? = null
    private var mMap: GoogleMap? = null
    private var currentPolyline: Polyline? = null
    var availableOrderDialog: AlertDialog? = null

    private var destinationPoint: LatLng? = null
    private var startPoint: LatLng? = null

    private var activeOrderDialog: AlertDialog? = null

    var progressBar: ProgressBar? = null
    private var orderAcceptButton: Button? = null
    private var orderSkipButton: Button? = null
    private var geocoder: Geocoder? = null
    private var mCameraPosition: CameraPosition? = null
    private var mLastKnownLocation: Location? = null
    private var fusedLocationProviderClient: FusedLocationProviderClient? = null

    // A default location and default zoom to use when location permission is
    // not granted.
    private val mDefaultLocation = LatLng(56.835974, 60.614522)

    private var mLocationPermissionGranted: Boolean = false
    private val isGeoDisabled: Boolean
        get() {
            val mLocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val mIsGPSEnabled = mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val mIsNetworkEnabled = mLocationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            return !mIsGPSEnabled && !mIsNetworkEnabled
        }
    private var navigationBar: NavigationView? = null

    private var ignoredOrders: MutableList<Int> = mutableListOf()

    private var isTimerActive: Boolean = false
    private var timer: Timer? = null
    private var timerTask: TimerTask? = null

    override fun onCreate(savedInstanceState: Bundle?) {
//        ForDebugging().turnOnStrictMode()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        navigationBar = nav_view
        navigationBar!!.setNavigationItemSelectedListener(this@MainActivity)

        addressTextView = address
        sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)


        availableOrderDialog = AlertDialog.Builder(this@MainActivity).setView(this.layoutInflater.inflate(R.layout.activity_available_order_dialog, null)).create()
        activeOrderDialog = AlertDialog.Builder(this@MainActivity).setView(this.layoutInflater.inflate(R.layout.activity_active_order_dialog, null)).create()

//        optionsDialog = AlertDialog.Builder(this@MainActivity).setView(R.layout.activity_order_options).create()
//        wishesDialog = AlertDialog.Builder(this@MainActivity).setView(R.layout.activity_order_wishes).create()
//        ridingEndDialog = AlertDialog.Builder(this@MainActivity).setView(R.layout.activity_order_info).create()
//
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
        mapFragment?.getMapAsync(this@MainActivity)

        mapFragment!!.view!!.alpha = 0.7F

        val startWorkButtonText = resources.getString(R.string.finish_work)

        // Check active shift
        // TODO if has active order show order screen
        // FIXME on first login driver already has shift
        // sPref!!.getInt("active_order_id", -1)
        if (sPref!!.getBoolean("shift_is_active", false)) {
            startWorkButton.background = ContextCompat.getDrawable(this@MainActivity, R.color.quantum_googred600)
            startWorkButton.setText(R.string.finish_work)
            startTimer()
        }

        startWorkButton.setOnClickListener {
            if (startWorkButton.hint != startWorkButtonText && sPref!!.getBoolean("shift_is_active", false)) {
                finishWork()
            } else {
                startWork()
            }

        }

        // Init timer
        timerTask = object : TimerTask() {
            override fun run() {
                runOnUiThread { checkAvailableOrders() }
            }
        }
    }

    private fun startTimer() {
        if (!isTimerActive) {
            timer = Timer()
            timerTask = object : TimerTask() {
                override fun run() {
                    runOnUiThread { checkAvailableOrders() }
                }
            }
            timer!!.scheduleAtFixedRate(timerTask, 5000, 5000)
            isTimerActive = true
        }
    }

    private fun stopTimer() {
        if (isTimerActive) {
            timer!!.cancel()
            isTimerActive = false
        }
    }

    private fun checkAvailableOrders() {
        // TODO: Validate not null fields
        // Build connection and rpc objects
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val checkAvailableOrdersRequest = CheckAvailableOrdersRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .build()
            val checkAvailableOrdersResponse: CheckAvailableOrdersResponse
            try {
                checkAvailableOrdersResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).checkAvailableOrders(checkAvailableOrdersRequest) // Запрос на создание
                managedChannel.shutdown()
                runOnUiThread {
                    Log.i("Calling checkAvailableOrders: ", "call!")
                    showAvailableOrder(checkAvailableOrdersResponse)
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }

    }

    private fun showAvailableOrder(ordersResponse: CheckAvailableOrdersResponse?) {
        stopTimer()
        availableOrderDialog!!.show()
        orderSkipButton = availableOrderDialog!!.skipOrderButton
        orderAcceptButton = availableOrderDialog!!.acceptOrderButton
        progressBar = availableOrderDialog!!.availableOrderProgress
        // Parsed LatLng from response in format lat/lng: (x,y)
        val pattern = "\\(.+\\)".toRegex()

        val ltdLngStringStartPoint = pattern.find(ordersResponse!!.cabRide.startingPoint)
                ?.value?.replace("[()]".toRegex(), "")?.split(',')
        val ltdLngStringDestinationPoint = pattern.find(ordersResponse.cabRide.endingPoint)
                ?.value?.replace("[()]".toRegex(), "")?.split(',')

        if (ltdLngStringStartPoint != null && ltdLngStringDestinationPoint != null) {

            startPoint = LatLng(ltdLngStringStartPoint[0].toDouble(), ltdLngStringStartPoint[1].toDouble())
            destinationPoint = LatLng(ltdLngStringDestinationPoint[0].toDouble(), ltdLngStringDestinationPoint[1].toDouble())

            // Draw route and set time and distance in dialog
            FetchURL(this@MainActivity).execute(
                    getUrl(LatLng(
                            mLastKnownLocation!!.latitude,
                            mLastKnownLocation!!.longitude
                    ), startPoint!!, "driving"),
                    "driving"
            )
            // Move camera to destination marker
            mMap!!.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    startPoint, 13F))
        } else {
            availableOrderDialog!!.toOfferTime!!.text = ordersResponse.cabRide.startingPoint
            availableOrderDialog!!.toOfferDistance!!.text = ordersResponse.cabRide.endingPoint
        }
        var totalProgress = 100
        val countDownTimer = object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                totalProgress -= 10
                progressBar!!.progress = totalProgress
            }

            override fun onFinish() {
                availableOrderDialog!!.dismiss()
                progressBar!!.progress = 100
                currentPolyline?.remove()
                Log.i("Progress bar: ", "is finished")
                startTimer()
            }
        }.start()

        orderAcceptButton!!.setOnClickListener {
            countDownTimer.cancel()
            acceptOrder(ordersResponse)
            progressBar!!.progress = totalProgress
            availableOrderDialog!!.dismiss()
        }

        orderSkipButton!!.setOnClickListener {
            countDownTimer.cancel()
            ignoredOrders.add(ordersResponse.cabRide.id)
            availableOrderDialog!!.dismiss()
            currentPolyline?.remove()
            startTimer()
        }
    }

    // TODO write to spref active cab_ride.id
    private fun acceptOrder(ordersResponse: CheckAvailableOrdersResponse?) {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val acceptOrderRequest = AcceptOrderRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setCabRideId(ordersResponse!!.cabRide.id)
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            val acceptOrderResponse: AcceptOrderResponse
            try {
                acceptOrderResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).acceptOrder(acceptOrderRequest) // Запрос на создание
                managedChannel.shutdown()
                if (acceptOrderResponse.isAccepted) {
                    sPref!!.edit().putInt("cab_ride_id", ordersResponse.cabRide.id).apply()
                    showOrderStartScreen(ordersResponse)
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }

    }

    override fun onTaskDone(vararg values: Any) {
        currentPolyline?.remove()
        currentPolyline = mMap!!.addPolyline(values[0] as PolylineOptions?)
    }

    private fun showOrderStartScreen(ordersResponse: CheckAvailableOrdersResponse) {
        runOnUiThread {

            var geocoder: Geocoder? = null
            val startPointAddresses: MutableList<Address>
            val destinationPointAddresses: MutableList<Address>

            geocoder = Geocoder(this@MainActivity, Locale.getDefault())

            activeOrderDialog!!.setCanceledOnTouchOutside(false)
            activeOrderDialog!!.show()

            activeOrderDialog?.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            activeOrderDialog!!.window!!.setGravity(Gravity.BOTTOM)

            val startPoint = startPoint
            val destinationPoint = destinationPoint

            if (startPoint?.latitude != null && destinationPoint?.latitude != null) {

                startPointAddresses = geocoder.getFromLocation(startPoint.latitude, startPoint.longitude, 1)
                destinationPointAddresses = geocoder.getFromLocation(destinationPoint.latitude, destinationPoint.longitude, 1)

                activeOrderDialog!!.routeStartPoint.text = startPointAddresses[0].getAddressLine(0)
                activeOrderDialog!!.routeDestination.text = destinationPointAddresses[0].getAddressLine(0)
            } else {
                activeOrderDialog!!.routeStartPoint.text = ordersResponse.cabRide.startingPoint
                activeOrderDialog!!.routeDestination.text = ordersResponse.cabRide.endingPoint
            }
            // FIXME button is not clickable
            activeOrderDialog!!.cancelOrderButton.setOnClickListener {
                cancelOrder()
                currentPolyline?.remove()
            }
            activeOrderDialog?.startTripButton?.setOnClickListener {
                activeOrderDialog!!.cancelOrderButton.background = ContextCompat.getDrawable(this@MainActivity, R.color.btn_success)
                activeOrderDialog!!.cancelOrderButton.text = getString(R.string.end_order)
                activeOrderDialog!!.cancelOrderButton.setOnClickListener {
                    endTrip()
                    currentPolyline?.remove()
                }
                val buttonLayout = (activeOrderDialog!!.startTripButton.parent as ViewGroup)
                buttonLayout.removeView(activeOrderDialog!!.startTripButton)
                startTrip()
            }
        }
    }

    private fun startTrip() {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val startTripRequest = StartTripRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setCabRideId(sPref!!.getInt("cab_ride_id", -1))
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            val startTripResponse: StartTripResponse
            try {
                startTripResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).startTrip(startTripRequest) // Запрос на создание
                managedChannel.shutdown()
                if (startTripResponse.isStarted) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.success_order_accepted_message, Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }
    }

    private fun endTrip() {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val endTripRequest = EndTripRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setCabRideId(sPref!!.getInt("cab_ride_id", -1))
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            val endTripResponse: EndTripResponse
            try {
                endTripResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).endTrip(endTripRequest) // Запрос на создание
                managedChannel.shutdown()
                if (endTripResponse.isEnded) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, R.string.success_order_ended_message, Toast.LENGTH_LONG).show()
                    }
                    startTimer()
                    activeOrderDialog!!.dismiss()
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }
    }

    private fun cancelOrder() {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val cancelOrderRequest = CancelOrderRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setCabRideId(sPref!!.getInt("cab_ride_id", -1))
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            val cancelOrderResponse: CancelOrderResponse
            try {
                cancelOrderResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).cancelOrder(cancelOrderRequest) // Запрос на создание
                managedChannel.shutdown()
                if (cancelOrderResponse.isCanceled) {
                    activeOrderDialog!!.dismiss()
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }
    }

    private fun finishWork() {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val stopShiftRequest = StopShiftRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            try {
                val stopShiftResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).stopShift(stopShiftRequest) // Запрос на создание
                managedChannel.shutdown()
                sPref!!.edit().putBoolean("shift_is_active", false).apply()
                runOnUiThread {
                    startWorkButton.background = ContextCompat.getDrawable(this@MainActivity, R.color.btn_success)
                    startWorkButton.setText(R.string.start_work)
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }

    }

    private fun startWork() {
        GlobalScope.launch {
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val startShiftRequest = StartShiftRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .build()
            try {
                val startShiftResponse = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).startShift(startShiftRequest) // Запрос на создание
                managedChannel.shutdown()
                sPref!!.edit().putBoolean("shift_is_active", true).apply()
                runOnUiThread {
                    // Run timer when status of working is changed
                    startTimer()
                    startWorkButton.background = ContextCompat.getDrawable(this@MainActivity, R.color.quantum_googred600)
                    startWorkButton.setText(R.string.finish_work)
                }
            } catch (e: StatusRuntimeException) {
                // Check exceptions
                when {
                    e.status.cause is java.net.ConnectException -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.PERMISSION_DENIED -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_invalid_token, Toast.LENGTH_LONG).show() }
                    e.status.code == io.grpc.Status.Code.UNKNOWN -> runOnUiThread { Toast.makeText(this@MainActivity, R.string.error_message_server, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
                managedChannel.shutdown()
            }
        }
    }

    private fun showOrdersEndScreen() {
/*
        ridingEndDialog!!.show()
        ridingEndDialog?.window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        ridingEndDialog!!.window!!.setGravity(Gravity.BOTTOM)
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
*/
    }

    @SuppressLint("SetTextI18n")
    private fun setLiveCabRideInfo(checkCabRideResponse: CheckCabRideStatusResponse?) {
    }

    private fun checkCabRideStatus(): CheckCabRideStatus {
        // TODO: Validate not null fields
        // Build connection and rpc objects
        val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address), resources.getInteger(R.integer.server_port)).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val checkCabRideStatusRequest = CheckCabRideStatusRequest.newBuilder()
                .setApi(getString(R.string.api_version))
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
            Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
            managedChannel.shutdown()
            CheckCabRideStatus()
        }
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

        mMap!!.setOnMyLocationButtonClickListener {
            if (isGeoDisabled) {
                val settingsIntent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(settingsIntent)
            }
            false
        }

        if (sPref!!.getInt("orderedCabRideId", -1) > -1) {
            val cabRideStatus = checkCabRideStatus()
            if (cabRideStatus.cabRideStatus!!) { // Is order is active
                if (cabRideStatus.checkCabRideResponse!!.firstName.isNotEmpty()) { // Has driver accepted an order?
                    if (cabRideStatus.checkCabRideResponse!!.rideStatus == 2) { // Is order ended?
                        showOrdersEndScreen()
                    } else {
                        setLiveCabRideInfo(cabRideStatus.checkCabRideResponse) // Set riding condition
                    }
                } else {
                }
            }
        }
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
