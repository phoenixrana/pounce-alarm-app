package dev.pounce.alarm

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.*
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.min

/** One foreground owner coordinates audio and haptics. Persisted sessions survive process death. */
class AlarmService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var melody: MediaPlayer? = null
    private var instruction: MediaPlayer? = null
    private lateinit var audio: AudioManager
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var focusRequest: AudioFocusRequest? = null
    private var focusGranted = false
    private var focusPending = false
    private var focusInterrupted = false
    private var nextFocusAttempt = 0L
    private var nextVoice = 0L
    private var nextVibration = 0L
    private var activeToken: String? = null
    private var elapsedDeadline = Long.MAX_VALUE
    private var voicePlaying = false
    private var useFallbackMelody = false
    private var nextPlayerAttempt = 0L

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> { focusGranted = true; focusPending = true; focusInterrupted = false }
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusGranted = false; focusPending = false; focusInterrupted = true
                nextFocusAttempt = SystemClock.elapsedRealtime() + 20_000
                silenceForFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                focusGranted = false; focusInterrupted = true
                silenceForFocus()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
        vibrator = if (Build.VERSION.SDK_INT >= 31)
            getSystemService(VibratorManager::class.java).defaultVibrator
        else getSystemService(Vibrator::class.java)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Pounce wake-up alarms",
            NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Full-screen wake-up mission alarm"
            setSound(null, null); enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = AlarmStore.session(this)
        if (session == null) { stopSelf(); return START_NOT_STICKY }
        // Required before any audio-focus request on targetSdk 35.
        startForeground(NOTIFICATION_ID, notification(session))
        if (WakeRules.expired(session, System.currentTimeMillis())) {
            end(this, session.token, "timeout"); return START_NOT_STICKY
        }
        if (activeToken != session.token) {
            releasePlayback()
            activeToken = session.token
            val remaining = if(session.isTest) (WakeRules.MAX_RING_MS - (System.currentTimeMillis() - session.startedAt)).coerceIn(1,WakeRules.MAX_RING_MS) else Long.MAX_VALUE / 4
            elapsedDeadline = SystemClock.elapsedRealtime() + remaining
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Pounce:Alarm").apply {
                    setReferenceCounted(false); acquire(10*60*1000L)
                }
            SoundLibrary.applyBoost(this,session)
            nextVoice = SystemClock.elapsedRealtime() + 8_000
            nextVibration = 0
            nextFocusAttempt = 0
            useFallbackMelody = false; nextPlayerAttempt = 0
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    private fun notification(s: WakeSession): Notification {
        val launch = PendingIntent.getActivity(this, 11, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sessionToken", s.token)
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (s.isTest) "Pounce rehearsal" else "Time to greet the morning")
            .setContentText(if(s.waiting(System.currentTimeMillis())) "Stay up. Your final check is coming." else "Complete your wake-up missions. ${s.current()?.title.orEmpty()}")
            .setContentIntent(launch).setFullScreenIntent(launch, true)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setOngoing(true)
            .setAutoCancel(false).build()
    }

    private val tick = object : Runnable {
        override fun run() {
            val s = AlarmStore.session(this@AlarmService)
            if (s == null || s.token != activeToken) { stopSelf(); return }
            val now = System.currentTimeMillis()
            val elapsed = SystemClock.elapsedRealtime()
            if (WakeRules.expired(s, now) || elapsed >= elapsedDeadline) {
                end(this@AlarmService, s.token, "timeout"); return
            }
            if(wakeLock?.isHeld==false) wakeLock?.acquire(10*60*1000L)
            val quiet = WakeRules.quiet(s, now)
            if(quiet) { silenceForFocus(); handler.postDelayed(this,500); return }
            if(s.checkAt>0 && now>=s.checkAt) {
                AlarmStore.saveSession(this@AlarmService,s.copy(checkAt=0))
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID,notification(s))
                rearmSession(this@AlarmService)
            }
            if (quiet && voicePlaying) {
                instruction?.release(); instruction = null; voicePlaying = false
            }
            val callInProgress = audio.mode == AudioManager.MODE_IN_CALL ||
                audio.mode == AudioManager.MODE_IN_COMMUNICATION
            if (callInProgress) {
                silenceForFocus()
            } else {
                if (!focusGranted && !focusPending && elapsed >= nextFocusAttempt) requestFocus()
                if (focusGranted) {
                    if (melody == null && elapsed >= nextPlayerAttempt) {
                        melody = makePlayer(R.raw.pounce_melody, loop = true)
                        nextPlayerAttempt = elapsed + 5000
                    }
                    val gain = gain(s, now) * if (voicePlaying) 0.18f else 1f
                    melody?.let { p ->
                        runCatching { p.setVolume(gain, gain); if (!p.isPlaying) p.start() }
                            .onFailure { failMelody(p) }
                    }
                    if (!quiet && elapsed >= nextVoice) playInstruction()
                }
                if (s.alarm.vibration && !quiet && !focusInterrupted && elapsed >= nextVibration) {
                    vibrate(now - s.startedAt)
                    nextVibration = elapsed + 5000
                }
            }
            if (quiet || focusInterrupted || callInProgress) vibrator?.cancel()
            handler.postDelayed(this, 500)
        }
    }

    private fun gain(s: WakeSession, now: Long): Float {
        val progression = ((now - s.startedAt).coerceAtLeast(0) / 45_000f).coerceIn(0f, 1f)
        val normal = s.alarm.volume.coerceIn(0.15f, 1f) * (0.3f + 0.7f * progression)
        return if (WakeRules.quiet(s, now)) normal * 0.25f else normal
    }

    private fun requestFocus() {
        if (focusRequest == null) focusRequest =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(ALARM_AUDIO)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(focusListener, handler).build()
        val result = audio.requestAudioFocus(focusRequest!!)
        focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusGranted) focusInterrupted = false
        focusPending = focusGranted || result == AudioManager.AUDIOFOCUS_REQUEST_DELAYED
        val age = AlarmStore.session(this)?.let { System.currentTimeMillis() - it.startedAt } ?: 6000
        nextFocusAttempt = SystemClock.elapsedRealtime() + if (age < 5000) 500 else 20_000
    }

    private fun failMelody(player: MediaPlayer) {
        if (melody !== player) return
        melody = null
        runCatching { player.release() }
        useFallbackMelody = true
        nextPlayerAttempt = SystemClock.elapsedRealtime() + 5000
    }

    private fun makePlayer(resource: Int, loop: Boolean): MediaPlayer? {
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(ALARM_AUDIO)
            if (loop && useFallbackMelody) {
                // Bundled audio remains available even when the system default alarm is unset.
                resources.openRawResourceFd(R.raw.pounce_melody).use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            } else if(loop && SoundLibrary.file(this,AlarmStore.session(this)?.alarm?.sound ?: "melody")!=null) {
                player.setDataSource(SoundLibrary.file(this,AlarmStore.session(this)!!.alarm.sound)!!.absolutePath)
            } else resources.openRawResourceFd(if(loop) SoundLibrary.resource(AlarmStore.session(this)?.alarm?.sound ?: "melody") else resource).use {
                player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
            }
            player.isLooping = loop
            player.prepare()
            if (loop) player.setOnErrorListener { failed, _, _ -> failMelody(failed); true }
            player
        } catch (_: Exception) {
            player.release()
            if (loop) useFallbackMelody = true
            null
        }
    }
    private fun playInstruction() {
        nextVoice = SystemClock.elapsedRealtime() + 20_000
        instruction?.release()
        instruction = makePlayer(R.raw.pounce_instruction, false)
        instruction?.let { player ->
            val s = AlarmStore.session(this) ?: return
            voicePlaying = true
            melody?.setVolume(gain(s, System.currentTimeMillis()) * 0.18f,
                gain(s, System.currentTimeMillis()) * 0.18f)
            val volume = s.alarm.volume.coerceIn(0.15f, 1f)
            player.setVolume(volume, volume)
            player.setOnCompletionListener { if (instruction === it) voicePlaying = false }
            player.setOnErrorListener { failed, _, _ -> if (instruction === failed) voicePlaying = false; true }
            runCatching { player.start() }.onFailure { voicePlaying = false }
        }
    }

    private fun vibrate(age: Long) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val timings = if (age < 15_000) longArrayOf(0, 130, 200, 160)
            else longArrayOf(0, 150, 160, 150, 220, 350)
        val strength = when { age < 15_000 -> 140; age < 45_000 -> 200; else -> 255 }
        val effect = if (v.hasAmplitudeControl())
            VibrationEffect.createWaveform(timings, IntArray(timings.size) { if (it % 2 == 0) 0 else strength }, -1)
        else VibrationEffect.createWaveform(timings, -1)
        if (Build.VERSION.SDK_INT >= 33)
            v.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_ALARM).build())
        else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, ALARM_AUDIO)
        }
    }

    private fun silenceForFocus() {
        runCatching { melody?.pause() }
        runCatching { instruction?.pause() }
        instruction?.release(); instruction = null; voicePlaying = false
        vibrator?.cancel()
    }
    private fun releasePlayback() {
        handler.removeCallbacks(tick)
        runCatching { melody?.release() }; melody = null
        runCatching { instruction?.release() }; instruction = null; voicePlaying = false
        vibrator?.cancel()
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null; focusGranted = false; focusPending = false; focusInterrupted = false
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
    override fun onDestroy() { SoundLibrary.restoreBoost(this); releasePlayback(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy() }
    override fun onBind(intent: Intent?) = null

    companion object {
        private const val CHANNEL = "pounce_alarm_v4"
        private const val NOTIFICATION_ID = 44
        internal const val ACTION_RECOVER = "dev.pounce.alarm.RECOVER"
        internal const val ACTION_TEST = "dev.pounce.alarm.TEST"
        private val ALARM_AUDIO = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()

        private fun launch(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, AlarmService::class.java))

        fun start(context: Context, alarmId: String) {
            val alarm = AlarmStore.alarms(context).find { it.id == alarmId } ?: return
            startScheduled(context, alarm)
        }
        internal fun startScheduled(context: Context, alarm: AlarmConfig) = synchronized(AlarmStore.lock) {
            val old = AlarmStore.session(context)
            if (old != null && !WakeRules.expired(old, System.currentTimeMillis())) {
                launch(context); return@synchronized
            }
            if (old != null) record(context, old, "timeout")
            AlarmStore.saveSession(context, WakeSession(alarm = alarm))
            launch(context)
        }
        fun startTest(context: Context) = synchronized(AlarmStore.lock) {
            val old = AlarmStore.session(context)
            if (old != null && !WakeRules.expired(old, System.currentTimeMillis())) {
                launch(context); return@synchronized
            }
            if (old != null) record(context, old, "timeout")
            val configured = AlarmStore.alarms(context).firstOrNull { it.enabled } ?: AlarmConfig()
            AlarmStore.saveSession(context, WakeSession(alarm = configured, isTest = true))
            launch(context)
        }
        fun completeMission(context:Context,token:String,index:Int,completed:Int,revision:String):Boolean = synchronized(AlarmStore.lock) {
            val s=valid(context,token) ?: return@synchronized false
            if(s.revision()!=revision || s.missionIndex!=index || s.completed!=completed || s.waiting(System.currentTimeMillis()))return@synchronized false
            val next=MissionRules.advance(s,System.currentTimeMillis())
            if(next==null) end(context,token,if(s.isTest) "test" else "awake",s.completed+1)
            else { AlarmStore.saveSession(context,next); if(next.checkAt>0) scheduleCheck(context,next) }
            true
        }
        fun replaceMission(context:Context,token:String,index:Int,revision:String) = synchronized(AlarmStore.lock) {
            val s=valid(context,token) ?: return@synchronized
            if(s.revision()==revision && s.missionIndex==index && !s.waiting(System.currentTimeMillis())) AlarmStore.saveSession(context,MissionRules.replace(s))
        }
        // Only rehearsal has a dismiss action. Real alarms end through completed mission transitions.
        fun finish(context:Context,sessionToken:String,outcome:String) = synchronized(AlarmStore.lock) {
            val s=valid(context,sessionToken) ?: return@synchronized
            if(s.isTest && outcome=="test")end(context,sessionToken,"test")
        }
        private fun scheduleCheck(context:Context,s:WakeSession) {
            if(!AlarmScheduler.canSchedule(context))return
            val show=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching {context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(s.checkAt,show),recoveryPending(context,s.token))}
        }
        private fun valid(context: Context, token: String): WakeSession? {
            val s = AlarmStore.session(context) ?: return null
            if (s.token != token || WakeRules.expired(s, System.currentTimeMillis())) return null
            return s
        }
        private fun record(context: Context, session: WakeSession, outcome: String, missions:Int=session.completed) =
            AlarmStore.addRecord(context, WakeRecord(System.currentTimeMillis(),
                if (session.isTest && outcome == "awake") "test" else outcome,
                ((System.currentTimeMillis() - session.startedAt) / 1000).coerceAtLeast(0),missions))

        private fun end(context: Context, token: String, outcome: String, missions:Int?=null) = synchronized(AlarmStore.lock) {
            val s = AlarmStore.session(context) ?: return@synchronized
            if (s.token != token) return@synchronized
            record(context, s, outcome,missions ?: s.completed)
            AlarmStore.saveSession(context, null)
            context.getSystemService(AlarmManager::class.java).cancel(recoveryPending(context, token))
            context.stopService(Intent(context, AlarmService::class.java))
            SoundLibrary.restoreBoost(context)
            MorningWidget.updateAll(context)
        }

        private fun recoveryPending(context: Context, token: String) =
            PendingIntent.getBroadcast(context, 41, Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_RECOVER; data = Uri.parse("pounce://recovery/" + Uri.encode(token))
                putExtra("sessionToken", token)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        fun rearmSession(context: Context) = synchronized(AlarmStore.lock) {
            val s = AlarmStore.session(context) ?: return@synchronized
            if (WakeRules.expired(s, System.currentTimeMillis())) {
                record(context, s, "timeout"); AlarmStore.saveSession(context, null)
                return@synchronized
            }
            if (!AlarmScheduler.canSchedule(context)) return@synchronized
            if(s.waiting(System.currentTimeMillis())) {scheduleCheck(context,s);return@synchronized}
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            runCatching { context.getSystemService(AlarmManager::class.java).setAlarmClock(
                AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 2000, show), recoveryPending(context, s.token)) }
        }
        internal fun recover(context: Context, token: String) = synchronized(AlarmStore.lock) {
            if (valid(context, token) != null) launch(context)
        }

        fun scheduleTest(context: Context, delaySeconds: Int = 60): Boolean = synchronized(AlarmStore.lock) {
            if (!AlarmScheduler.canSchedule(context)) return@synchronized false
            val at = System.currentTimeMillis() + delaySeconds.coerceIn(5, 300) * 1000L
            val pi = PendingIntent.getBroadcast(context, 42, Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TEST; putExtra("at", at)
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            AlarmStore.prefs(context).edit().putLong("test_at", at).commit()
            runCatching { context.getSystemService(AlarmManager::class.java)
                .setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi); true }.getOrDefault(false)
        }
        fun pendingTestAt(context: Context): Long = AlarmStore.prefs(context).getLong("test_at", 0)
        fun cancelTest(context: Context) = synchronized(AlarmStore.lock) {
            val pi = PendingIntent.getBroadcast(context, 42, Intent(context, AlarmReceiver::class.java).apply {
                action = ACTION_TEST
            }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            context.getSystemService(AlarmManager::class.java).cancel(pi)
            AlarmStore.prefs(context).edit().remove("test_at").commit()
        }
        internal fun fireTest(context: Context, at: Long) = synchronized(AlarmStore.lock) {
            if (at == 0L || AlarmStore.prefs(context).getLong("test_at", 0) != at) return@synchronized
            AlarmStore.prefs(context).edit().remove("test_at").commit()
            startTest(context)
        }
    }
}





