package com.edde746.plezy.shared

import android.app.Activity
import android.content.Intent
import android.media.AudioManager
import android.os.ParcelFileDescriptor
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.edde746.plezy.exoplayer.ExoPlayerCore
import com.edde746.plezy.mpv.MpvPlayerCore
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies actual Horizon EPS routing through both production playback cores. */
@RunWith(AndroidJUnit4::class)
class WindowAudioRoutingTest {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()

  @Test fun exoPlayerTracksWindowAcrossAudioTrackRecreation() = withWindow { activity, media ->
    lateinit var core: ExoPlayerCore
    lateinit var player: ExoPlayer
    instrumentation.runOnMainSync {
      core = ExoPlayerCore(activity)
      assertTrue(core.initialize(tunnelingEnabled = false))
      player = ExoPlayerCore::class.java.getDeclaredField("exoPlayer").apply { isAccessible = true }.get(core) as ExoPlayer
      player.repeatMode = Player.REPEAT_MODE_ONE
    }
    try {
      repeat(2) { generation ->
        instrumentation.runOnMainSync {
          player.setAudioSessionId(activity.getSystemService(AudioManager::class.java).generateAudioSessionId())
          core.open(media.absolutePath, null, 0, true, generation + 1)
        }
        awaitSpatial { var id = 0; instrumentation.runOnMainSync { id = player.audioSessionId }; id }
        instrumentation.runOnMainSync { player.stop() }
      }
    } finally { instrumentation.runOnMainSync { core.dispose() } }
  }

  @Test fun mpvUsesOwnedSessionForWindowAudio() = withWindow { activity, media ->
    lateinit var core: MpvPlayerCore
    val initialized = CountDownLatch(1)
    var success = false
    instrumentation.runOnMainSync {
      core = MpvPlayerCore(activity, false, true, 1f, "warn")
      core.initialize { success = it; initialized.countDown() }
    }
    try {
      assertTrue(initialized.await(30, TimeUnit.SECONDS))
      assertTrue(success)
      val loaded = CountDownLatch(1)
      instrumentation.runOnMainSync {
        core.setProperty("loop-file", "inf")
        core.command(arrayOf("loadfile", media.absolutePath)) { loaded.countDown() }
        core.setProperty("pause", "no")
      }
      assertTrue(loaded.await(15, TimeUnit.SECONDS))
      awaitSpatial { core.getProperty("options/audiotrack-session-id")?.toIntOrNull() ?: 0 }
    } finally {
      val disposed = CountDownLatch(1)
      instrumentation.runOnMainSync { core.dispose(onComplete = disposed::countDown) }
      assertTrue(disposed.await(30, TimeUnit.SECONDS))
    }
  }

  private fun withWindow(test: (Activity, File) -> Unit) {
    assumeTrue("Requires Horizon OS", listOf("horizonos", "metavr").any {
      try { Class.forName("$it.app.volumetricwindow.VolumetricWindowManager"); true }
      catch (_: ClassNotFoundException) { false }
    })
    val context = instrumentation.targetContext
    val media = File.createTempFile("window-audio-", ".flac", context.cacheDir)
    instrumentation.context.assets.open("ffmpeg/stereo.flac").use { input ->
      media.outputStream().use(input::copyTo)
    }
    val intent = Intent().setClassName(context.packageName, "com.edde746.plezy.mpv.MpvLifecycleTestActivity")
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
      val activity = instrumentation.startActivitySync(intent)
      try { test(activity, media) }
      finally { instrumentation.runOnMainSync { activity.finish() } }
    } finally { media.delete() }
  }

  private fun awaitSpatial(session: () -> Int) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
    var dump = ""
    var id = 0
    while (System.nanoTime() < deadline) {
      id = session()
      dump = instrumentation.uiAutomation.executeShellCommand("dumpsys media.has").use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
      }
      if (id > 0 && Regex("session: $id \\([^\\n]*\\)\\s*<SPATIALIZER: EPS>\\s*engine stream spatial state:\\s+ON")
          .containsMatchIn(dump)) return
      Thread.sleep(250)
    }
    File(instrumentation.targetContext.cacheDir, "window-audio-failure.txt").writeText(dump)
    throw AssertionError("Session $id did not reach Horizon EPS ON; see window-audio-failure.txt")
  }
}
