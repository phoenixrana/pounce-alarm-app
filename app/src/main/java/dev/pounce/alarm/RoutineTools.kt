package dev.pounce.alarm
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Share only routine settings. Private camera references, audio files and wake history never leave the phone. */
object RoutineTools {
 fun share(context: Context, alarm: AlarmConfig) {
  val json = JSONObject().put("format", "pounce-routine").put("version", 2)
   .put("hour", alarm.hour).put("minute", alarm.minute)
   .put("days", org.json.JSONArray(alarm.days.sorted())).put("label", alarm.label)
   .put("volume", alarm.volume.toDouble()).put("vibration", alarm.vibration).put("light", alarm.light)
   .put("missions",org.json.JSONArray(alarm.missions.map {it.name})).put("difficulty",alarm.difficulty).put("followUp",alarm.followUp)
   .put("sound",if(alarm.sound.startsWith("custom-")) "melody" else alarm.sound)
  val dir=File(context.cacheDir,"shared").apply { mkdirs() }
  val file=File(dir,"pounce-routine.json").apply { writeText(json.toString(2)) }
  val uri=FileProvider.getUriForFile(context,context.packageName+".files",file)
  val intent=Intent(Intent.ACTION_SEND).apply {
   type="application/json";putExtra(Intent.EXTRA_STREAM,uri)
   clipData=android.content.ClipData.newUri(context.contentResolver,"Pounce routine",uri)
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  context.startActivity(Intent.createChooser(intent,"Share alarm routine"))
 }
 fun importJson(context:Context,text:String):AlarmConfig {
  require(text.toByteArray(Charsets.UTF_8).size<=65536) { "Routine file is too large." }
  val data=JSONObject(text)
  require(data.optString("format")=="pounce-routine" && data.optInt("version") in 1..2) { "Choose a Pounce routine file." }
  val hour=data.getInt("hour");val minute=data.getInt("minute")
  require(hour in 0..23 && minute in 0..59) { "The routine has an invalid time." }
  val days=data.getJSONArray("days")
  require(days.length()<=7) { "Invalid repeat days." }
  val selected=(0 until days.length()).map { days.getInt(it) }
  require(selected.all { it in 1..7 }) { "Invalid repeat days." }
  val volume=data.optDouble("volume",.85).toFloat()
  require(volume.isFinite() && volume in .15f..1f) { "Invalid sound volume." }
  val alarm=AlarmConfig(id=UUID.randomUUID().toString(),hour=hour,minute=minute,days=selected.toSet(),
   enabled=false,label=data.optString("label","Morning, sunshine").take(80),
   volume=volume,vibration=data.optBoolean("vibration",true),light=data.optBoolean("light",true),commitment=false,
   missions=MissionRules.stack(data.optJSONArray("missions")?.let { ms -> (0 until ms.length()).map {MissionKind.valueOf(ms.getString(it))} } ?: listOf(MissionKind.WALK,MissionKind.MEMORY,MissionKind.MATH)),
   difficulty=data.optInt("difficulty",2).coerceIn(1,3),followUp=data.optBoolean("followUp",true),
   sound=data.optString("sound","melody").takeIf { id -> SoundLibrary.choices.any {it.first==id} } ?: "melody")
  AlarmStore.saveAlarm(context,alarm)
  return alarm
 }
}
