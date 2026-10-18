package com.edde746.plezy.shared

import android.app.Activity
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/** Main-thread, player-owned routing. Horizon tracks the pose; no per-frame pan is needed. */
internal class WindowAudioRouting(private val activity: Activity) {
  private val handler = Handler(Looper.getMainLooper())
  private var sessionId = 0
  private var closed = false
  private val supported = listOf("horizonos", "metavr").any { namespace ->
    try {
      Class.forName("$namespace.app.volumetricwindow.VolumetricWindowManager")
      true
    } catch (_: ClassNotFoundException) { false }
  }
  private val refresh = object : Runnable {
    override fun run() {
      if (closed || sessionId <= 0) return
      QuestSpatialAudio.windowSpace(activity)?.let { QuestSpatialAudio.bind(sessionId, it) }
      // Window metadata and HAS sessions can become ready after AudioTrack creation.
      // Also handles a replacement window space or a restarted Horizon audio service.
      handler.postDelayed(this, 1000)
    }
  }

  fun setSession(id: Int) {
    if (closed) return
    handler.removeCallbacks(refresh)
    QuestSpatialAudio.forget(sessionId)
    sessionId = id
    if (id > 0 && supported) refresh.run()
  }

  fun rebind() = setSession(sessionId)

  /** MPV accepts a caller-owned AudioTrack session ID before native initialization. */
  fun allocateSession(): Int {
    if (closed || !supported) return 0
    val id = try {
      activity.getSystemService(AudioManager::class.java)?.generateAudioSessionId() ?: 0
    } catch (_: Exception) { 0 }
    setSession(id)
    return id.coerceAtLeast(0)
  }

  fun close() {
    closed = true
    handler.removeCallbacks(refresh)
    QuestSpatialAudio.forget(sessionId)
    sessionId = 0
  }
}
