package dev.pranav.applock.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.pranav.applock.R
import dev.pranav.applock.data.database.IntruderDatabase
import dev.pranav.applock.data.model.IntruderLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service that silently captures intruder evidence when the guest (decoy)
 * password is used to unlock a protected app.
 *
 * Responsibilities:
 * 1. Start as a foreground service (required from Android 8+).
 * 2. Capture a front-camera photo using CameraX [ImageCapture].
 * 3. Optionally record the device screen via [MediaProjection] (if a projection result
 *    is passed through the starting intent) for [SCREEN_RECORD_DURATION_MS] milliseconds.
 * 4. Save all files under [getIntruderDir].
 * 5. Create a [IntruderLog] entry in the Room database.
 *
 * Start this service via [buildIntent] to ensure all required extras are present.
 */
class IntruderMonitoringService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Screen recording state
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var screenRecordingPath: String? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appName = intent?.getStringExtra(EXTRA_APP_NAME) ?: "Unknown"
        val timestamp = System.currentTimeMillis()

        // Create the intruder storage directory
        val intruderDir = getIntruderDir(this)

        // Insert a placeholder log so we have an ID to update later
        serviceScope.launch {
            val db = IntruderDatabase.getInstance(applicationContext)
            val logId = db.intruderDao().insert(
                IntruderLog(
                    timestamp = timestamp,
                    appName = appName
                )
            )

            // Capture front-camera photo
            val photoPath = capturePhoto(intruderDir, timestamp)

            // Retrieve MediaProjection data (passed from MediaProjectionCaptureActivity)
            val projectionResultCode = intent?.getIntExtra(EXTRA_PROJECTION_RESULT_CODE, -1) ?: -1
            val projectionData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_PROJECTION_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_PROJECTION_DATA)
            }

            var screenPath: String? = null
            if (projectionResultCode != -1 && projectionData != null) {
                screenPath = startScreenRecording(intruderDir, timestamp, projectionResultCode, projectionData)
            }

            // Update the log with captured paths (videoPath is reserved for a future
            // camera video capture feature and is not yet implemented)
            db.intruderDao().update(
                IntruderLog(
                    id = logId,
                    timestamp = timestamp,
                    appName = appName,
                    photoPath = photoPath,
                    videoPath = null,
                    screenRecordingPath = screenPath
                )
            )
            Log.i(TAG, "Intruder log saved: id=$logId, app=$appName, photo=$photoPath")
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScreenRecording()
        cameraExecutor.shutdown()
        serviceJob.cancel()
    }

    // -------------------------------------------------------------------------
    // Photo capture via CameraX
    // -------------------------------------------------------------------------

    /**
     * Captures a single JPEG using the front-facing camera.
     * Returns the absolute path of the saved file, or null on failure.
     */
    private suspend fun capturePhoto(dir: File, timestamp: Long): String? {
        return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            try {
                val cameraProviderFuture = ProcessCameraProvider.getInstance(applicationContext)
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        val imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()

                        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                        // Use a no-op LifecycleOwner so CameraX binds without a real UI lifecycle
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            NoOpLifecycleOwner(),
                            cameraSelector,
                            imageCapture
                        )

                        val photoFile = File(dir, "intruder_photo_$timestamp.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        imageCapture.takePicture(
                            outputOptions,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                    cameraProvider.unbindAll()
                                    Log.d(TAG, "Photo saved: ${photoFile.absolutePath}")
                                    if (cont.isActive) cont.resume(photoFile.absolutePath) {}
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    cameraProvider.unbindAll()
                                    Log.e(TAG, "Photo capture failed", exception)
                                    if (cont.isActive) cont.resume(null) {}
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "CameraX bind failed", e)
                        cont.resume(null) {}
                    }
                }, ContextCompat.getMainExecutor(applicationContext))
            } catch (e: Exception) {
                Log.e(TAG, "CameraX init failed", e)
                cont.resume(null) {}
            }
        }
    }

    // -------------------------------------------------------------------------
    // Screen recording via MediaProjection
    // -------------------------------------------------------------------------

    /**
     * Starts screen recording using the given [MediaProjection] token.
     * Automatically stops after [SCREEN_RECORD_DURATION_MS] milliseconds.
     * Returns the absolute path of the recording, or null on failure.
     */
    private fun startScreenRecording(
        dir: File,
        timestamp: Long,
        resultCode: Int,
        data: Intent
    ): String? {
        return try {
            val projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val outputFile = File(dir, "intruder_screen_$timestamp.mp4")
            screenRecordingPath = outputFile.absolutePath

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder!!.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoSize(width, height)
                setVideoFrameRate(30)
                setVideoEncodingBitRate(5_000_000)
                setOutputFile(outputFile.absolutePath)
                prepare()
            }

            virtualDisplay = mediaProjection!!.createVirtualDisplay(
                "IntruderCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null, null
            )

            mediaRecorder!!.start()

            // Stop recording after the configured duration
            handler.postDelayed({
                stopScreenRecording()
                // Stop the service once all tasks are complete
                stopSelf()
            }, SCREEN_RECORD_DURATION_MS)

            Log.d(TAG, "Screen recording started: ${outputFile.absolutePath}")
            outputFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Screen recording failed to start", e)
            null
        }
    }

    private fun stopScreenRecording() {
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) { /* recording may not have started */ }
        try {
            mediaRecorder?.release()
        } catch (_: Exception) {}
        mediaRecorder = null

        virtualDisplay?.release()
        virtualDisplay = null

        mediaProjection?.stop()
        mediaProjection = null
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Security Monitor",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Used for silent security monitoring"
            setShowBadge(false)
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Running security check…")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // -------------------------------------------------------------------------
    // Storage helpers
    // -------------------------------------------------------------------------

    companion object {
        private const val TAG = "IntruderMonitoringService"
        private const val CHANNEL_ID = "intruder_monitoring_channel"
        private const val NOTIFICATION_ID = 9001

        /** Duration (ms) for which the screen is recorded. */
        private const val SCREEN_RECORD_DURATION_MS = 20_000L

        const val EXTRA_APP_NAME = "extra_app_name"
        const val EXTRA_PROJECTION_RESULT_CODE = "extra_projection_result_code"
        const val EXTRA_PROJECTION_DATA = "extra_projection_data"

        /**
         * Returns the directory where intruder files are stored.
         * Uses app-specific external storage so no extra permission is required.
         */
        fun getIntruderDir(context: Context): File {
            val dir = File(context.getExternalFilesDir(null), "intruders")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        /** Convenience builder to create a correctly populated start [Intent]. */
        fun buildIntent(
            context: Context,
            appName: String,
            projectionResultCode: Int = -1,
            projectionData: Intent? = null
        ): Intent = Intent(context, IntruderMonitoringService::class.java).apply {
            putExtra(EXTRA_APP_NAME, appName)
            if (projectionResultCode != -1 && projectionData != null) {
                putExtra(EXTRA_PROJECTION_RESULT_CODE, projectionResultCode)
                putExtra(EXTRA_PROJECTION_DATA, projectionData)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// No-op LifecycleOwner so CameraX can be used from a Service context
// ---------------------------------------------------------------------------

private class NoOpLifecycleOwner : androidx.lifecycle.LifecycleOwner {

    private val registry = androidx.lifecycle.LifecycleRegistry(this)

    init {
        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: androidx.lifecycle.Lifecycle get() = registry
}
