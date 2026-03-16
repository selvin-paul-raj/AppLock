package dev.pranav.applock.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity that stores a single intruder attempt log.
 *
 * Each row is created when the guest (decoy) password is used to unlock an app.
 * The monitoring service fills in the media file paths after capturing them.
 */
@Entity(tableName = "intruder_logs")
data class IntruderLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Unix epoch milliseconds when the attempt was recorded. */
    val timestamp: Long,

    /** Package name of the app that was unlocked with the guest password. */
    val appName: String,

    /** Absolute path to the captured front-camera photo, or null if unavailable. */
    val photoPath: String? = null,

    /** Absolute path to the captured camera video, or null if unavailable. */
    val videoPath: String? = null,

    /** Absolute path to the screen recording, or null if unavailable. */
    val screenRecordingPath: String? = null
)
