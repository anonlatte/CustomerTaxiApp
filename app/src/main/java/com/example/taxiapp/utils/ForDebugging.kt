package com.example.taxiapp.utils

import android.os.StrictMode
import com.example.taxiapp.BuildConfig

class ForDebugging {
    fun turnOnStrictMode() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
//                    .penaltyDeath()
                    .build())
        }
    }
}