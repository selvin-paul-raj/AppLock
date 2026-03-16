package dev.pranav.applock.features.intruder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import dev.pranav.applock.services.IntruderMonitoringService

/**
 * Transparent helper activity that requests the MediaProjection permission required for
 * screen recording and then immediately starts [IntruderMonitoringService] with the result.
 *
 * This activity finishes itself as soon as the permission prompt is handled, so it is
 * invisible to the user except for the system-level screen-capture consent dialog.
 */
class MediaProjectionCaptureActivity : ComponentActivity() {

    private val screenCaptureLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Unknown"
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.d(TAG, "Screen capture permission granted, starting monitoring service")
                val serviceIntent = IntruderMonitoringService.buildIntent(
                    applicationContext,
                    appName,
                    result.resultCode,
                    result.data!!
                )
                ContextCompat.startForegroundService(applicationContext, serviceIntent)
            } else {
                // Permission denied – still start the service without screen recording
                Log.w(TAG, "Screen capture permission denied; starting service without screen recording")
                val serviceIntent = IntruderMonitoringService.buildIntent(applicationContext, appName)
                ContextCompat.startForegroundService(applicationContext, serviceIntent)
            }
            finish()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Make the window fully transparent so it doesn't flash on screen
        window.decorView.alpha = 0f

        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        try {
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch screen capture intent", e)
            // Fallback: start service without screen recording
            val appName = intent.getStringExtra(EXTRA_APP_NAME) ?: "Unknown"
            val serviceIntent = IntruderMonitoringService.buildIntent(applicationContext, appName)
            ContextCompat.startForegroundService(applicationContext, serviceIntent)
            finish()
        }
    }

    companion object {
        private const val TAG = "MediaProjectionCapture"
        const val EXTRA_APP_NAME = "extra_app_name"

        /** Build an [Intent] to start this transparent helper activity. */
        fun buildIntent(context: Context, appName: String): Intent =
            Intent(context, MediaProjectionCaptureActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                            Intent.FLAG_ACTIVITY_NO_HISTORY
                )
                putExtra(EXTRA_APP_NAME, appName)
            }
    }
}
