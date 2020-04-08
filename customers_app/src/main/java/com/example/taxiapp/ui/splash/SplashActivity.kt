package com.example.taxiapp.ui.splash

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.example.taxiapp.R
import com.example.taxiapp.SignInActivity
import com.example.taxiapp.ui.main.MainActivity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity(), SplashNavigator {

    private lateinit var sPref: SharedPreferences
    private lateinit var splashViewModel: SplashViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        sPref = getSharedPreferences("TaxiService", Context.MODE_PRIVATE)
        splashViewModel = SplashViewModel(sPref)
        splashViewModel.requestStatus.observe(this, Observer { status ->
            status?.let {
                splashViewModel.requestStatus.value = null
                Toast.makeText(this@SplashActivity, R.string.error_internet_connection, Toast.LENGTH_LONG).show()
            }
        })
        GlobalScope.launch {
            if (splashViewModel.isTokenValid()) {
                openMainActivity()
            } else {
                openLoginActivity()
            }
        }

    }

    override fun openLoginActivity() {
        intent = Intent(applicationContext, SignInActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun openMainActivity() {
        intent = Intent(applicationContext, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
