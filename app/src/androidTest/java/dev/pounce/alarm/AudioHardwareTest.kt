package dev.pounce.alarm

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Checks packaged decoders and real alarm-stream restoration; does not claim audible loudness. */
@RunWith(AndroidJUnit4::class)
class AudioHardwareTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun verifyBundledAudio(resource: Int) {
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            context.resources.openRawResourceFd(resource).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.isLooping = true
            player.prepare()
            assertTrue("Packaged audio must contain at least half a second of decodable sound", player.duration >= 500)
            assertTrue("Packaged cue must have a bounded duration", player.duration <= 120_000)
            assertTrue(player.isLooping)
        } finally {
            player.release()
        }
    }

    @Test fun sunriseMeowsDecodes() = verifyBundledAudio(R.raw.pounce_melody)
    @Test fun playfulPawsDecodes() = verifyBundledAudio(R.raw.pounce_bright)
    @Test fun breakfastBellsDecodes() = verifyBundledAudio(R.raw.pounce_chime)

    private fun setVolume(audio: AudioManager, target: Int) {
        audio.setStreamVolume(AudioManager.STREAM_ALARM, target, 0)
        val deadline = SystemClock.elapsedRealtime() + 1000
        while (audio.getStreamVolume(AudioManager.STREAM_ALARM) != target && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(20)
        }
        assertEquals("Alarm stream volume did not reach the requested value", target, audio.getStreamVolume(AudioManager.STREAM_ALARM))
    }

    private fun withRestoredVolume(block: (AudioManager, Int) -> Unit) {
        val audio = context.getSystemService(AudioManager::class.java)
        assumeFalse("Fixed-volume devices cannot exercise stream-volume changes", audio.isVolumeFixed)
        val maximum = audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        assumeTrue("Needs distinguishable original, boosted and manual volume levels", maximum >= 3)
        assumeTrue("Do not alter an active alarm's audio during this test", AlarmStore.session(context) == null)
        SoundLibrary.restoreBoost(context)
        val baseline = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        try {
            block(audio, maximum)
        } finally {
            SoundLibrary.restoreBoost(context)
            setVolume(audio, baseline)
        }
    }

    @Test fun boostRestoresOriginalAlarmVolume() = withRestoredVolume { audio, maximum ->
        setVolume(audio, 1)
        val session = WakeSession(alarm = AlarmConfig(boost = true))
        SoundLibrary.applyBoost(context, session)
        val target = maxOf(1, (maximum * .85).toInt())
        assertEquals(target, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertEquals(session.token, AlarmStore.prefs(context).getString("boost_token", null))
        // Reapplying the same session must not overwrite the original baseline with boosted volume.
        SoundLibrary.applyBoost(context, session)
        SoundLibrary.restoreBoost(context)
        assertEquals(1, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertFalse(AlarmStore.prefs(context).contains("boost_token"))
    }

    @Test fun boostRestorationHonorsManualVolumeChange() = withRestoredVolume { audio, maximum ->
        setVolume(audio, 1)
        SoundLibrary.applyBoost(context, WakeSession(alarm = AlarmConfig(boost = true)))
        val boosted = maxOf(1, (maximum * .85).toInt())
        assertEquals(boosted, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        val manual = if (boosted == maximum) maximum - 1 else maximum
        setVolume(audio, manual)
        SoundLibrary.restoreBoost(context)
        assertEquals("Restoration must preserve the user's volume-button adjustment", manual,
            audio.getStreamVolume(AudioManager.STREAM_ALARM))
        assertFalse(AlarmStore.prefs(context).contains("boost_token"))
    }

    @Test fun installedManifestRequestsNoInternetPermission() {
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(Manifest.permission.INTERNET in permissions)
    }
}
