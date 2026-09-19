package dev.pounce.alarm
import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import java.io.File
import java.util.UUID
object SoundLibrary {
 val choices=listOf("melody" to "Sunrise meows","bright" to "Playful paws","chime" to "Breakfast bells")
 fun resource(id:String)=when(id){"bright"->R.raw.pounce_bright;"chime"->R.raw.pounce_chime;else->R.raw.pounce_melody}
 fun file(context:Context,id:String):File? = if(id.matches(Regex("custom-[a-f0-9-]+"))) File(context.createDeviceProtectedStorageContext().filesDir,"sounds/$id.audio").takeIf {it.isFile} else null
 fun title(id:String)=choices.find {it.first==id}?.second ?: "My imported sound"
 fun import(context:Context,uri:Uri):String {
  val id="custom-"+UUID.randomUUID();val dir=File(context.createDeviceProtectedStorageContext().filesDir,"sounds").apply {mkdirs()};val f=File(dir,"$id.audio")
  try {
   context.contentResolver.openInputStream(uri)?.use { input -> f.outputStream().use {out -> val b=ByteArray(8192);var total=0;while(true){val n=input.read(b);if(n<0)break;total+=n;require(total<=20*1024*1024){"Choose audio smaller than 20 MB."};out.write(b,0,n)} } } ?: error("Cannot read this audio file.")
   val player=MediaPlayer();try{player.setDataSource(f.absolutePath);player.prepare();require(player.duration>500){"Choose a sound longer than half a second."}} finally {player.release()}
   return id
  } catch(e:Exception){f.delete();throw e}
 }
 fun applyBoost(context:Context,s:WakeSession) {
  if(!s.alarm.boost)return
  val prefs=AlarmStore.prefs(context);val audio=context.getSystemService(AudioManager::class.java)
  if(prefs.getString("boost_token",null)==s.token)return
  restoreBoost(context)
  val original=audio.getStreamVolume(AudioManager.STREAM_ALARM);val target=maxOf(original,(audio.getStreamMaxVolume(AudioManager.STREAM_ALARM)*.85).toInt())
  prefs.edit().putString("boost_token",s.token).putInt("boost_before",original).putInt("boost_applied",target).commit()
  runCatching {audio.setStreamVolume(AudioManager.STREAM_ALARM,target,0)}
 }
 fun restoreBoost(context:Context) {
  val p=AlarmStore.prefs(context);if(!p.contains("boost_token"))return
  val a=context.getSystemService(AudioManager::class.java)
  if(a.getStreamVolume(AudioManager.STREAM_ALARM)==p.getInt("boost_applied",-1)) runCatching {a.setStreamVolume(AudioManager.STREAM_ALARM,p.getInt("boost_before",1),0)}
  p.edit().remove("boost_token").remove("boost_before").remove("boost_applied").commit()
 }
}
