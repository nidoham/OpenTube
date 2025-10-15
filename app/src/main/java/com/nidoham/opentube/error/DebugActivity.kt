package com.nidoham.opentube.error

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.nidoham.opentube.R

/**
 * Debug Activity to display crash reports and provide recovery options.
 */
class DebugActivity : AppCompatActivity() {

    companion object {
        private const val CRASH_REPORT_KEY = "CRASH_REPORT"
        private const val SUPPORT_EMAIL = "support@opentube.com"
        private const val EMAIL_SUBJECT = "OpenTube Crash Report"
    }

    private lateinit var crashReportText: TextView
    private lateinit var sendReportButton: Button
    private lateinit var copyReportButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        
        initializeViews()
        configureCrashReport()
        setupButtonActions()
    }

    private fun initializeViews() {
        crashReportText = findViewById<TextView>(R.id.tv_crash_report).apply {
            movementMethod = ScrollingMovementMethod()
        }
        sendReportButton = findViewById(R.id.btn_send_report)
        copyReportButton = findViewById(R.id.btn_copy_report)
    }

    private fun configureCrashReport() {
        val crashReport = intent.getStringExtra(CRASH_REPORT_KEY)
        
        if (!crashReport.isNullOrEmpty()) {
            crashReportText.text = crashReport
        } else {
            crashReportText.text = "No crash report available"
            disableReportActions()
        }
    }

    private fun disableReportActions() {
        sendReportButton.isEnabled = false
        copyReportButton.isEnabled = false
    }

    private fun setupButtonActions() {
        sendReportButton.setOnClickListener {
            val crashReport = crashReportText.text.toString()
            sendCrashReportViaEmail(crashReport)
        }
        
        copyReportButton.setOnClickListener {
            val crashReport = crashReportText.text.toString()
            copyReportToClipboard(crashReport)
        }
    }

    private fun sendCrashReportViaEmail(crashReport: String) {
        if (crashReport.isBlank()) {
            showToast("No crash report to send")
            return
        }
        
        val emailIntent = createEmailIntent(crashReport)
        
        try {
            val chooserIntent = Intent.createChooser(emailIntent, "Send crash report via")
            startActivity(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            fallbackToGenericShare(crashReport)
        }
    }

    private fun createEmailIntent(crashReport: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
            putExtra(Intent.EXTRA_TEXT, buildEmailBody(crashReport))
        }
    }

    private fun buildEmailBody(crashReport: String): String {
        return """
            Dear OpenTube Team,
            
            The application encountered an error with the following details:
            
            $crashReport
        """.trimIndent()
    }

    private fun fallbackToGenericShare(crashReport: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, crashReport)
            putExtra(Intent.EXTRA_SUBJECT, EMAIL_SUBJECT)
        }
        
        try {
            val chooserIntent = Intent.createChooser(shareIntent, "Share crash report")
            startActivity(chooserIntent)
        } catch (e: ActivityNotFoundException) {
            showToast("No app found to share report")
        }
    }
    
    private fun copyReportToClipboard(crashReport: String) {
        try {
            val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText(EMAIL_SUBJECT, crashReport)
            
            clipboardManager.setPrimaryClip(clipData)
            showToast("Crash report copied to clipboard")
        } catch (e: Exception) {
            showToast("Failed to copy report")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Close the activity and return to previous state
        super.onBackPressed()
    }
}