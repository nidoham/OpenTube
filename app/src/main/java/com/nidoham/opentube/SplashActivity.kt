package com.nidoham.opentube

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private companion object {
        private const val SPLASH_DELAY = 1500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler().postDelayed({
            
            val intent = Intent(applicationContext, OnboardActivity::class.java)
            startActivity(intent)

            overridePendingTransition(0, 0)
            finish()
        }, SPLASH_DELAY)
    }
}