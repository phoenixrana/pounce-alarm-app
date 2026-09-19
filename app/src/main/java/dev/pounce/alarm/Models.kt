package dev.pounce.alarm

enum class MissionKind(val title:String,val hint:String,val glyph:String,val physical:Boolean=false) {
 WALK("Paw patrol", "Walk away from your bed", "↟",true),
 MATH("Cat calculations", "Solve a fresh set of sums", "+"),
 MEMORY("Paw print memory", "Remember the glowing paw sequence", "▦"),
 TYPING("Morning mantra", "Type a fresh wake-up phrase", "Aa"),
 COLORS("Color confusion", "Choose the ink, not the word", "●"),
 TILT("Yarn maze", "Tilt the yarn through each checkpoint", "◎",true),
 SHAKE("Shake the sleepies", "Make distinct, gentle shakes", "≈",true),
 PUSHUPS("Rise & repeat", "Move near and away from the phone sensor", "↕",true),
 PHOTO("Photo destination", "Match a scene you registered while awake", "▣",true),
 HUNT("Color hunt", "Find a real object of the requested color", "◉",true)
}
data class AlarmConfig(
 val id:String=java.util.UUID.randomUUID().toString(), val hour:Int=7,val minute:Int=0,
 val days:Set<Int> =(1..7).toSet(),val enabled:Boolean=true,val label:String="Up with Pounce",
 val volume:Float=.9f,val vibration:Boolean=true,val light:Boolean=true,
 val commitment:Boolean=false, // legacy 0.4 field ignored by mission mode
 val missions:List<MissionKind> = listOf(MissionKind.WALK,MissionKind.MEMORY,MissionKind.MATH),
 val difficulty:Int=2,val followUp:Boolean=true,val sound:String="melody",val boost:Boolean=false
)
data class MorningSpot(val token:String,val name:String="",val verified:Boolean=false) // legacy data only

data class WakeSession(
 val token:String=java.util.UUID.randomUUID().toString(),val alarm:AlarmConfig,
 val startedAt:Long=System.currentTimeMillis(),val graceUntil:Long=0,val graceUsed:Boolean=false,
 val scanUntil:Long=0,val scanUsed:Boolean=false,val isTest:Boolean=false,
 val missionIndex:Int=0,val completed:Int=0,val followUpStage:Int=0,val checkAt:Long=0,
 val replacement:List<MissionKind> = emptyList(),val replacementIndex:Int=0
) {
 fun revision():String = "$missionIndex:$completed:$followUpStage:${replacement.joinToString()}:$replacementIndex"
 fun stack():List<MissionKind> = if(followUpStage>0) listOf(MissionKind.WALK,MissionKind.TYPING) else MissionRules.stack(alarm.missions)
 fun current():MissionKind? = replacement.getOrNull(replacementIndex) ?: stack().getOrNull(missionIndex)
 fun waiting(now:Long) = checkAt>now
}
data class WakeRecord(val at:Long,val outcome:String,val durationSeconds:Long,val missions:Int=0)
object MissionRules {
 fun stack(kinds:List<MissionKind>):List<MissionKind> {
  val valid=kinds.distinct().take(5).toMutableList()
  if(valid.isEmpty()) valid.add(MissionKind.WALK)
  if(valid.size<2) valid.add(if(valid[0]==MissionKind.MATH) MissionKind.WALK else MissionKind.MATH)
  return valid
 }
 fun advance(s:WakeSession,now:Long):WakeSession? {
  if(s.waiting(now))return s
  if(s.replacement.isNotEmpty() && s.replacementIndex<s.replacement.lastIndex)
   return s.copy(completed=s.completed+1,replacementIndex=s.replacementIndex+1)
  val next=s.missionIndex+1
  if(next<s.stack().size)return s.copy(missionIndex=next,completed=s.completed+1,replacement=emptyList(),replacementIndex=0)
  if(s.followUpStage==0 && s.alarm.followUp && !s.isTest)
   return s.copy(missionIndex=0,completed=s.completed+1,followUpStage=1,checkAt=now+120_000,replacement=emptyList(),replacementIndex=0)
  return null
 }
 fun replace(s:WakeSession):WakeSession = if(s.replacement.isNotEmpty())s else s.copy(replacement=listOf(MissionKind.MATH,MissionKind.TYPING),replacementIndex=0)
}
object WakeRules {
 const val MAX_RING_MS=20*60*1000L // rehearsal expiry only; real alarms require tasks
 const val GRACE_MS=30_000L
 const val SCAN_MS=20_000L
 fun expired(session:WakeSession,now:Long)=session.isTest && now-session.startedAt>=MAX_RING_MS
 fun quiet(session:WakeSession,now:Long)=session.waiting(now)
 fun nextTime(alarm:AlarmConfig,now:java.time.ZonedDateTime):java.time.ZonedDateTime {
  for(offset in 0..7) {
   val candidate=now.toLocalDate().plusDays(offset.toLong()).atTime(alarm.hour,alarm.minute).atZone(now.zone)
   if(candidate.isAfter(now)&&(alarm.days.isEmpty()||candidate.dayOfWeek.value in alarm.days))return candidate
  }
  error("No next alarm")
 }
}
