package com.nodexdata.speechtotext

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "TL_TvActivity"

/**
 * Cross-app "what's playing" visibility on Android TV, via the Notification
 * Listener permission class (same mechanism phones use for media
 * notifications -- just read here instead of acted on). One session spans
 * screen-on to screen-off; every media-session or power event appends to it
 * and re-syncs the whole session so far via NativeAuthStore.postJson, so
 * nothing is lost even if the TV is left on for hours (see postJson's own
 * doc comment for why this works without a Flutter engine running).
 */
class TvActivityListenerService : NotificationListenerService() {
    private var mediaSessionManager: MediaSessionManager? = null
    private var screenReceiver: BroadcastReceiver? = null

    private var deviceSessionId: String? = null
    private var startedAt: String? = null
    private val events = JSONArray()

    // Dedupe: MediaController callbacks fire on every metadata/state change,
    // including no-op repeats -- only append when something actually moved.
    private var lastTitle: String? = null
    private var lastPlaybackState: String? = null

    private val activeSessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            handleActiveSessions(controllers)
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "onListenerConnected")
        mediaSessionManager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, TvActivityListenerService::class.java)
        try {
            mediaSessionManager?.addOnActiveSessionsChangedListener(activeSessionsListener, componentName)
            handleActiveSessions(mediaSessionManager?.getActiveSessions(componentName))
        } catch (e: SecurityException) {
            Log.e(TAG, "onListenerConnected: missing notification access", e)
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> startSession()
                    Intent.ACTION_SCREEN_OFF -> endSession()
                }
            }
        }
        registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        })
        screenReceiver = receiver

        startSession()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "onListenerDisconnected")
        endSession()
        mediaSessionManager?.removeOnActiveSessionsChangedListener(activeSessionsListener)
        screenReceiver?.let { try { unregisterReceiver(it) } catch (e: Exception) {} }
        screenReceiver = null
    }

    private fun startSession() {
        if (deviceSessionId != null) return // already in a session
        deviceSessionId = UUID.randomUUID().toString()
        startedAt = isoNow()
        lastTitle = null
        lastPlaybackState = null
        appendEvent(JSONObject().put("at", startedAt).put("type", "power_on"))
    }

    private fun endSession() {
        if (deviceSessionId == null) return
        appendEvent(JSONObject().put("at", isoNow()).put("type", "power_off"))
        deviceSessionId = null
        startedAt = null
        events.let { while (it.length() > 0) it.remove(0) }
    }

    private fun handleActiveSessions(controllers: List<MediaController>?) {
        val controller = controllers?.firstOrNull() ?: return
        val title = controller.metadata?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        val playbackState = playbackStateLabel(controller.playbackState?.state)
        if (title == null && playbackState == null) return
        if (title == lastTitle && playbackState == lastPlaybackState) return
        lastTitle = title
        lastPlaybackState = playbackState

        if (deviceSessionId == null) startSession()
        appendEvent(
            JSONObject()
                .put("at", isoNow())
                .put("type", "media_session")
                .put("app_package", controller.packageName)
                .put("media_title", title)
                .put("playback_state", playbackState)
        )
    }

    private fun playbackStateLabel(state: Int?): String? = when (state) {
        PlaybackState.STATE_PLAYING -> "playing"
        PlaybackState.STATE_PAUSED -> "paused"
        PlaybackState.STATE_STOPPED -> "stopped"
        PlaybackState.STATE_BUFFERING -> "buffering"
        else -> null
    }

    private fun appendEvent(event: JSONObject) {
        events.put(event)
        syncSession()
    }

    private fun syncSession() {
        val sessionId = deviceSessionId ?: return
        val lastEvent = if (events.length() > 0) events.getJSONObject(events.length() - 1) else null
        val endedAt = if (lastEvent?.optString("type") == "power_off") lastEvent.optString("at") else null
        val body = JSONObject()
            .put("session_id", sessionId)
            .put("started_at", startedAt)
            .put("ended_at", endedAt ?: JSONObject.NULL)
            .put("events", events)
        NativeAuthStore.postJson(applicationContext, "/tv/activity", body.toString())
    }

    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
