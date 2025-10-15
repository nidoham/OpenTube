package com.nidoham.opentube

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private companion object {
        private const val SPLASH_DELAY = 1500L
        private const val TEST = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler().postDelayed({
            
            val intent = Intent(applicationContext, MainActivity::class.java)
            intent.putExtra("debug", TEST);
            startActivity(intent)

            overridePendingTransition(0, 0)
            finish()
        }, SPLASH_DELAY)
    }
}