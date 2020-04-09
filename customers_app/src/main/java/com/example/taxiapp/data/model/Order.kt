package com.example.taxiapp.data.model

import androidx.lifecycle.MutableLiveData
import com.example.taxiapp.CheckCabRideStatusResponse

data class Order(
        var id: Int = -1,
        var cabRideStatus: MutableLiveData<Boolean> = MutableLiveData(false),
        var checkCabRideResponse: MutableLiveData<CheckCabRideStatusResponse?> = MutableLiveData(null)
)