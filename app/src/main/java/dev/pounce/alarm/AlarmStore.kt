package dev.pounce.alarm

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Alarm state lives in device-protected storage so a reboot does not require unlocking first. */
object AlarmStore {
    internal val lock = Any()
    internal fun prefs(context: Context) = context.createDeviceProtectedStorageContext()
        .getSharedPreferences("pounce_state", Context.MODE_PRIVATE)

    private fun array(context: Context, key: String) =
        runCatching { JSONArray(prefs(context).getString(key, "[]")) }.getOrDefault(JSONArray())

    internal fun encode(a: AlarmConfig) = JSONObject().apply {
        put("id", a.id); put("hour", a.hour); put("minute", a.minute)
        put("days", JSONArray(a.days.sorted())); put("enabled", a.enabled); put("label", a.label)
        put("volume", a.volume.toDouble()); put("vibration", a.vibration)
        put("light", a.light); put("commitment", false)
        put("missions", JSONArray(MissionRules.stack(a.missions).map { it.name }));put("difficulty",a.difficulty)
        put("followUp",a.followUp);put("sound",a.sound);put("boost",a.boost)
    }

    internal fun decode(o: JSONObject): AlarmConfig {
        val ds = o.optJSONArray("days") ?: JSONArray()
        return AlarmConfig(
            id = o.getString("id"), hour = o.optInt("hour", 7).coerceIn(0, 23),
            minute = o.optInt("minute", 0).coerceIn(0, 59),
            days = (0 until ds.length()).map { ds.optInt(it) }.filter { it in 1..7 }.toSet(),
            enabled = o.optBoolean("enabled", true), label = o.optString("label", "Morning, sunshine").take(80),
            volume = o.optDouble("volume", 0.85).toFloat().let { if (it.isFinite()) it.coerceIn(0.15f, 1f) else 0.85f },
            vibration = o.optBoolean("vibration", true), light = o.optBoolean("light", true),
            commitment = false,
            missions = MissionRules.stack(o.optJSONArray("missions")?.let { ms -> (0 until ms.length()).mapNotNull { runCatching { MissionKind.valueOf(ms.getString(it)) }.getOrNull() } } ?: listOf(MissionKind.WALK,MissionKind.MEMORY,MissionKind.MATH)),
            difficulty=o.optInt("difficulty",2).coerceIn(1,3),followUp=o.optBoolean("followUp",true),
            sound=o.optString("sound","melody").take(100),boost=o.optBoolean("boost",false)
        )
    }

    fun alarms(context: Context): List<AlarmConfig> = synchronized(lock) {
        val data = array(context, "alarms")
        (0 until data.length()).mapNotNull { runCatching { decode(data.getJSONObject(it)) }.getOrNull() }
    }
    fun saveAlarm(context: Context, alarm: AlarmConfig) = synchronized(lock) {
        val list = alarms(context).filterNot { it.id == alarm.id } + decode(encode(alarm))
        check(prefs(context).edit().putString("alarms", JSONArray(list.map(::encode)).toString()).commit())
    }
    fun deleteAlarm(context: Context, id: String) = synchronized(lock) {
        check(prefs(context).edit().putString("alarms", JSONArray(alarms(context).filterNot { it.id == id }.map(::encode)).toString()).commit())
    }
    fun spot(context: Context): MorningSpot? = synchronized(lock) {
        runCatching {
            val data = JSONObject(prefs(context).getString("spot", null) ?: return@synchronized null)
            MorningSpot(data.getString("token"), data.optString("name", "Balcony door"), data.optBoolean("verified"))
        }.getOrNull()
    }
    fun saveSpot(context: Context, spot: MorningSpot) = synchronized(lock) {
        check(prefs(context).edit().putString("spot", JSONObject().apply {
            put("token", spot.token); put("name", spot.name.take(60)); put("verified", spot.verified)
        }.toString()).commit())
    }
    fun session(context: Context): WakeSession? = synchronized(lock) {
        runCatching {
            val data = JSONObject(prefs(context).getString("session", null) ?: return@synchronized null)
            WakeSession(token = data.getString("token"), alarm = decode(data.getJSONObject("alarm")),
                startedAt = data.getLong("startedAt"), graceUntil = data.optLong("graceUntil"),
                graceUsed = data.optBoolean("graceUsed"), scanUntil = data.optLong("scanUntil"),
                scanUsed = data.optBoolean("scanUsed"), isTest = data.optBoolean("isTest"),
                missionIndex=data.optInt("missionIndex",0).coerceAtLeast(0),completed=data.optInt("completed",0).coerceAtLeast(0),
                followUpStage=data.optInt("followUpStage",0).coerceIn(0,1),checkAt=data.optLong("checkAt",0),
                replacement=data.optJSONArray("replacement")?.let { ms -> (0 until ms.length()).mapNotNull { runCatching {MissionKind.valueOf(ms.getString(it))}.getOrNull() } } ?: emptyList(),
                replacementIndex=data.optInt("replacementIndex",0).coerceAtLeast(0))
        }.getOrNull()
    }
    fun saveSession(context: Context, session: WakeSession?) = synchronized(lock) {
        val edit = prefs(context).edit()
        if (session == null) edit.remove("session") else edit.putString("session", JSONObject().apply {
            put("token", session.token); put("alarm", encode(session.alarm)); put("startedAt", session.startedAt)
            put("graceUntil", session.graceUntil); put("graceUsed", session.graceUsed)
            put("scanUntil", session.scanUntil); put("scanUsed", session.scanUsed); put("isTest", session.isTest)
            put("missionIndex",session.missionIndex);put("completed",session.completed);put("followUpStage",session.followUpStage);put("checkAt",session.checkAt)
            put("replacement",JSONArray(session.replacement.map {it.name}));put("replacementIndex",session.replacementIndex)
        }.toString())
        check(edit.commit())
    }
    fun records(context: Context): List<WakeRecord> = synchronized(lock) {
        val data = array(context, "records")
        (0 until data.length()).mapNotNull {
            runCatching { data.getJSONObject(it).let { r ->
                WakeRecord(r.getLong("at"), r.getString("outcome"), r.getLong("durationSeconds"),r.optInt("missions",0))
            } }.getOrNull()
        }
    }
    fun addRecord(context: Context, record: WakeRecord) = synchronized(lock) {
        val list = (listOf(record) + records(context)).take(100)
        check(prefs(context).edit().putString("records", JSONArray(list.map {
            JSONObject().put("at", it.at).put("outcome", it.outcome).put("durationSeconds", it.durationSeconds).put("missions",it.missions)
        }).toString()).commit())
    }
}

