package com.example.drivers_app

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.drivers_app.search_adapter.CarItem
import com.example.drivers_app.search_adapter.CarModelAdapter
import com.example.drivers_app.search_adapter.ColorAdapter
import com.example.drivers_app.search_adapter.ColorItem
import com.example.taxiapp.*
import io.grpc.ManagedChannelBuilder
import io.grpc.Status
import io.grpc.StatusRuntimeException
import io.grpc.okhttp.internal.Platform
import kotlinx.android.synthetic.main.activity_sign_up_search_car.view.*
import kotlinx.android.synthetic.main.activity_sign_up_search_car.view.searchView
import kotlinx.android.synthetic.main.activity_sign_up_search_color.view.*
import kotlinx.android.synthetic.main.activity_sign_up_set_car.view.*
import kotlinx.android.synthetic.main.activity_user_settings.*
import kotlinx.android.synthetic.main.search_car_item.view.*
import kotlinx.android.synthetic.main.search_color_item.view.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.logging.Level

class UserSettingsActivity : AppCompatActivity() {

    private var sPref: SharedPreferences? = null

    private val carBrands: MutableMap<Int, String>? = mutableMapOf()
    private val carModels: MutableList<CarItem> = mutableListOf()
    private val colorsArray: MutableList<ColorItem> = mutableListOf()

    var modelAdapter: CarModelAdapter? = null
    var colorAdapter: ColorAdapter? = null

    private var searchCarDialogView: View? = null
    private var searchColorDialogView: View? = null
    private var searchCarDialog: AlertDialog? = null

    private var setCarDialogView: View? = null
    private var searchModelButton: Button? = null
    private var searchColor: Button? = null
    private var setCarDialog: AlertDialog.Builder? = null
    private var licensePlateEditText: EditText? = null

    private var carModelRecyclerView: RecyclerView? = null
    private var carColorRecyclerView: RecyclerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_settings)
        sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)

        // fill brands array on background
        GlobalScope.launch {
            loadCarBrands()
            loadCarModels()
        }
        GlobalScope.launch {
            loadCarColor()
        }

        addCar.setOnClickListener {
            setCarDialog()
        }

        setCarDialogView = this@UserSettingsActivity.layoutInflater.inflate(R.layout.activity_sign_up_set_car, null)
        setCarDialog = AlertDialog.Builder(this@UserSettingsActivity).setView(setCarDialogView)

        searchCarDialogView = this.layoutInflater.inflate(R.layout.activity_sign_up_search_car, null)
        searchColorDialogView = this.layoutInflater.inflate(R.layout.activity_sign_up_search_color, null)

        searchCarDialog = AlertDialog.Builder(this@UserSettingsActivity).setView(searchCarDialogView).create()


        carModelRecyclerView = searchCarDialogView!!.carModelItems
        carColorRecyclerView = searchColorDialogView!!.carColorItems

        carModelRecyclerView!!.layoutManager = LinearLayoutManager(searchCarDialogView!!.context)
        carColorRecyclerView!!.layoutManager = LinearLayoutManager(searchCarDialogView!!.context)

        searchModelButton = setCarDialogView!!.searchModel
        searchColor = setCarDialogView!!.chooseColorButton
        licensePlateEditText = setCarDialogView!!.licensePlate

        searchModelButton!!.setOnClickListener {
            searchCarDialog = AlertDialog.Builder(this@UserSettingsActivity).setView(searchCarDialogView).create()
            modelAdapter = CarModelAdapter(carModels)
            carModelRecyclerView!!.adapter = modelAdapter
            searchCarDialog!!.show()
        }
        searchColor!!.setOnClickListener {
            searchCarDialog = AlertDialog.Builder(this@UserSettingsActivity).setView(searchColorDialogView).create()
            colorAdapter = ColorAdapter(colorsArray)
            carColorRecyclerView!!.adapter = colorAdapter
            searchCarDialog!!.show()
        }

        searchCarDialogView!!.searchView.imeOptions = EditorInfo.IME_ACTION_DONE
        searchCarDialogView!!.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener, android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                modelAdapter!!.filter.filter(newText)
                return false
            }
        })
        searchColorDialogView!!.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener, android.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                colorAdapter!!.filter.filter(newText)
                return false
            }
        })

        setCarDialog!!.setView(setCarDialogView)
                .setPositiveButton(android.R.string.ok) { dialog, _ ->
                    if (licensePlateEditText!!.text.isNotEmpty() && searchColor!!.text.isNotEmpty() && searchModelButton!!.text.isNotEmpty()) {
                        val spinnerArrayAdapter = ArrayAdapter<String>(this,
                                R.layout.support_simple_spinner_dropdown_item,
                                arrayListOf(SpannableStringBuilder("${searchModelButton!!.text} ${licensePlateEditText!!.text} ${searchColor!!.text}").toString())
                        )
                        carList.adapter = spinnerArrayAdapter
                        addCarToDriver()
                    }
                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.cancel()
                }
    }

    private fun addCarToDriver() {
        if (licensePlateEditText!!.text.isNotEmpty() &&
                searchModelButton!!.text != getString(R.string.find_car_model) &&
                searchColor!!.text != getString(R.string.color)) {
            val carArray = searchModelButton!!.text.toString().split('.')
            val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address),
                    resources.getInteger(R.integer.server_port)).usePlaintext().build()
            val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
            val createCabRequest = CreateCabRequest.newBuilder()
                    .setApi(getString(R.string.api_version))
                    .setLicensePlate(licensePlateEditText!!.text.toString())
                    .setDriverId(sPref!!.getInt("driver_id", -1))
                    .setColorDescription(searchColor!!.text.toString())
                    .setCarModelName(carArray[0].trim())
                    .setCarBrandName(carArray[1].trim())
                    .build()
            val createCabResponse: CreateCabResponse
            try {
                createCabResponse = blockingStub.createCab(createCabRequest)
                Log.i("Cab was created: id = ", createCabResponse.cabId.toString())
            } catch (e: StatusRuntimeException) {
                if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                    runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
                } else if (e.status == Status.UNKNOWN) {
                    runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_cannot_load_car_brands, Toast.LENGTH_LONG).show() }
                }
                Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
            }
            managedChannel.shutdown()
        }
    }

    private fun setCarDialog() {
        // FIXME: java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        setCarDialog!!.show()
    }

    fun chooseColor(view: View) {
        searchColor!!.text = SpannableStringBuilder("${view.colorItem.text}")
        searchCarDialog!!.dismiss()
    }

    fun chooseModel(view: View) {
        searchModelButton!!.text = SpannableStringBuilder("${view.carModel.text}. ${view.carBrand.text}")
        searchCarDialog!!.dismiss()
    }

    // Fill carBrands [0: name1, 1: name2, ...]
    private fun loadCarBrands() {
        val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address),
                resources.getInteger(R.integer.server_port)).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val readAllCarBrandsRequest = ReadAllCarBrandsRequest.newBuilder()
                .setApi(getString(R.string.api_version))
                .build()
        val readAllCarBrandsResponse: ReadAllCarBrandsResponse
        try {
            readAllCarBrandsResponse = blockingStub.readAllCarBrands(readAllCarBrandsRequest)
            readAllCarBrandsResponse.carBrandList.forEach {
                carBrands!![it.id] = it.brandName
            }

        } catch (e: StatusRuntimeException) {
            if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
            } else if (e.status == Status.UNKNOWN) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_cannot_load_car_brands, Toast.LENGTH_LONG).show() }
            }
            Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
        }
        managedChannel.shutdown()
    }

    // Fill carModels {brand -> [name1, name2, name3, ...], brand2 -> [...]}
    private fun loadCarModels() {
        val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address),
                resources.getInteger(R.integer.server_port)).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        var readAllCarModelsRequest = ReadAllCarModelsRequest.newBuilder().build()
        var readAllCarModelsResponse: ReadAllCarModelsResponse
        try {
            carBrands!!.forEach { carBrand ->
                readAllCarModelsRequest = readAllCarModelsRequest.toBuilder()
                        .setApi(getString(R.string.api_version))
                        .setCarBrandId(carBrand.key)
                        .build()
                readAllCarModelsResponse = blockingStub.readAllCarModels(readAllCarModelsRequest)
                readAllCarModelsResponse.carModelsList.forEach { carModel ->
                    carModels.add(CarItem(carModel.modelName, carBrand.value))
                }
            }
        } catch (e: StatusRuntimeException) {
            if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
            } else if (e.status == Status.UNKNOWN) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_cannot_load_car_models, Toast.LENGTH_LONG).show() }
            }
            Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
        }

        managedChannel.shutdown()
    }

    private fun loadCarColor() {
        val managedChannel = ManagedChannelBuilder.forAddress(getString(R.string.server_address),
                resources.getInteger(R.integer.server_port)).usePlaintext().build()
        val blockingStub = taxiServiceGrpc.newBlockingStub(managedChannel)
        val readAllColorsRequest = ReadAllColorsRequest.newBuilder()
                .setApi(getString(R.string.api_version))
                .build()
        val readAllColorsResponse: ReadAllColorsResponse
        try {
            readAllColorsResponse = blockingStub.getColors(readAllColorsRequest)
            readAllColorsResponse.colorList.forEach { color ->
                colorsArray.add(ColorItem(color.description))
            }

        } catch (e: StatusRuntimeException) {
            if (e.status.cause is java.net.ConnectException || e.status.code == Status.DEADLINE_EXCEEDED.code) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show() }
            } else if (e.status == Status.UNKNOWN) {
                runOnUiThread { Toast.makeText(this@UserSettingsActivity, R.string.error_cannot_load_car_brands, Toast.LENGTH_LONG).show() }
            }
            Platform.logger.log(Level.WARNING, "RPC failed: " + e.status)
        }
        managedChannel.shutdown()

    }


}
