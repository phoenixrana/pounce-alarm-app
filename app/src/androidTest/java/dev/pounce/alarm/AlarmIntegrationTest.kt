package dev.pounce.alarm
import android.content.Context
import android.content.Intent
import android.app.AlarmManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class AlarmIntegrationTest {
 private lateinit var context:Context
 @Before fun clean() {
  context=InstrumentationRegistry.getInstrumentation().targetContext
  context.stopService(Intent(context,AlarmService::class.java));cancelRecovery();AlarmStore.saveSession(context,null)
  AlarmService.cancelTest(context);AlarmStore.alarms(context).forEach{AlarmStore.deleteAlarm(context,it.id)};AlarmScheduler.reschedule(context)
  AlarmStore.prefs(context).edit().clear().commit()
 }
 @After fun stop(){cancelRecovery();AlarmStore.saveSession(context,null);context.stopService(Intent(context,AlarmService::class.java));AlarmService.cancelTest(context);AlarmStore.alarms(context).forEach{AlarmStore.deleteAlarm(context,it.id)};AlarmScheduler.reschedule(context)}
 private fun cancelRecovery() {
  val s=AlarmStore.session(context) ?: return
  val intent=Intent(context,AlarmReceiver::class.java).apply {action=AlarmService.ACTION_RECOVER;data=android.net.Uri.parse("pounce://recovery/"+android.net.Uri.encode(s.token));putExtra("sessionToken",s.token)}
  val pi=android.app.PendingIntent.getBroadcast(context,41,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
  context.getSystemService(AlarmManager::class.java).cancel(pi)
 }
 private fun saved(follow:Boolean=false):WakeSession {
  val s=WakeSession(alarm=AlarmConfig(missions=listOf(MissionKind.MATH,MissionKind.TYPING),followUp=follow));AlarmStore.saveSession(context,s);return s
 }
 private fun complete(s:WakeSession)=AlarmService.completeMission(context,s.token,s.missionIndex,s.completed,s.revision())
 @Test fun configurationSurvivesFreshContext(){val a=AlarmConfig(missions=listOf(MissionKind.HUNT,MissionKind.WALK,MissionKind.COLORS),difficulty=3,boost=true,sound="bright");AlarmStore.saveAlarm(context,a);assertEquals(a,AlarmStore.alarms(context.createDeviceProtectedStorageContext()).single())}
 @Test fun legacyQrAlarmMigratesToMovementStack(){val a=AlarmStore.decode(org.json.JSONObject("""{"id":"old","commitment":true,"hour":6,"minute":30}"""));assertFalse(a.commitment);assertEquals(listOf(MissionKind.WALK,MissionKind.MEMORY,MissionKind.MATH),a.missions);assertTrue(a.followUp)}
 @Test fun ordinaryAndEmergencyDismissalCannotEndRealAlarm(){val s=saved();for(outcome in listOf("awake","rescued","test"))AlarmService.finish(context,s.token,outcome);assertEquals(s,AlarmStore.session(context))}
 @Test fun eachTaskRequiredAndStaleCallbackRejected(){val s=saved();assertTrue(complete(s));assertFalse(complete(s));val next=AlarmStore.session(context)!!;assertEquals(1,next.completed);assertTrue(complete(next));assertNull(AlarmStore.session(context));assertEquals(2,AlarmStore.records(context).single().missions);assertFalse(complete(next))}
 @Test fun oldTaskCannotCompleteReplacement(){val s=saved();AlarmService.replaceMission(context,s.token,s.missionIndex,s.revision());assertFalse(complete(s));var next=AlarmStore.session(context)!!;assertEquals(MissionKind.MATH,next.current());assertTrue(complete(next));next=AlarmStore.session(context)!!;assertEquals(MissionKind.TYPING,next.current());assertEquals(0,next.missionIndex);assertTrue(complete(next));assertEquals(1,AlarmStore.session(context)!!.missionIndex)}
 @Test fun staleReplacementDoesNotAffectFollowUp(){val old=saved(true);complete(old);complete(AlarmStore.session(context)!!);val check=AlarmStore.session(context)!!;AlarmService.replaceMission(context,old.token,0,old.revision());assertEquals(check,AlarmStore.session(context));assertFalse(complete(check));AlarmStore.saveSession(context,check.copy(checkAt=System.currentTimeMillis()-1));assertTrue(complete(AlarmStore.session(context)!!))}
 @Test fun waitingAndProgressSurviveFreshContext(){val s=saved(true);complete(s);complete(AlarmStore.session(context)!!);val fresh=AlarmStore.session(context.createDeviceProtectedStorageContext())!!;assertTrue(fresh.waiting(System.currentTimeMillis()));assertEquals(2,fresh.completed);assertEquals(1,fresh.followUpStage)}
 @Test fun finalCheckRequiredBeforeWakeRecord(){var s=saved(true);complete(s);complete(AlarmStore.session(context)!!);s=AlarmStore.session(context)!!.copy(checkAt=System.currentTimeMillis()-1);AlarmStore.saveSession(context,s);assertTrue(AlarmStore.records(context).isEmpty());complete(s);complete(AlarmStore.session(context)!!);assertNull(AlarmStore.session(context));assertEquals(4,AlarmStore.records(context).single().missions)}
 @Test fun staleSessionCannotEndNewAlarm(){val old=saved();val newer=saved();assertFalse(complete(old));AlarmService.replaceMission(context,old.token,0,old.revision());assertEquals(newer,AlarmStore.session(context))}
 @Test fun practiceHasSeparateFinish(){val s=saved().copy(isTest=true);AlarmStore.saveSession(context,s);AlarmService.finish(context,s.token,"test");assertNull(AlarmStore.session(context));assertEquals("test",AlarmStore.records(context).single().outcome)}
 @Test fun routineImportRequiresReviewAndDoesNotImportPrivateAudio(){val a=RoutineTools.importJson(context,"""{"format":"pounce-routine","version":2,"hour":6,"minute":30,"days":[1,2],"missions":["WALK","HUNT"],"sound":"custom-123","boost":true}""");assertFalse(a.enabled);assertFalse(a.boost);assertEquals("melody",a.sound);assertEquals(listOf(MissionKind.WALK,MissionKind.HUNT),a.missions)}
 @Test fun malformedRoutineCannotCreateAlarm(){for(text in listOf("""{"format":"pounce-routine","version":2,"hour":25,"minute":3,"days":[]}""","{}","["))assertTrue(runCatching{RoutineTools.importJson(context,text)}.isFailure);assertTrue(AlarmStore.alarms(context).isEmpty())}
 @Test fun deletingAlarmCancelsSystemSchedule() {
  Assume.assumeTrue(AlarmScheduler.canSchedule(context))
  val a=AlarmConfig();AlarmStore.saveAlarm(context,a);AlarmScheduler.reschedule(context)
  val manager=context.getSystemService(AlarmManager::class.java)
  assertNotNull(manager.nextAlarmClock)
  AlarmStore.deleteAlarm(context,a.id);AlarmScheduler.reschedule(context)
  assertNull(manager.nextAlarmClock)
 }
 @Test fun cancellingRehearsalClearsSystemScheduleAndMetadata() {
  Assume.assumeTrue(AlarmScheduler.canSchedule(context))
  assertTrue(AlarmService.scheduleTest(context,60))
  assertTrue(AlarmService.pendingTestAt(context)>System.currentTimeMillis())
  AlarmService.cancelTest(context)
  assertEquals(0L,AlarmService.pendingTestAt(context))
  assertNull(context.getSystemService(AlarmManager::class.java).nextAlarmClock)
 }
 @Test fun repeatedBootSchedulingPreservesCatchUpInsteadOfSkippingToTomorrow() {
  Assume.assumeTrue(AlarmScheduler.canSchedule(context))
  val now=java.time.ZonedDateTime.now()
  val a=AlarmConfig(hour=now.hour,minute=now.minute,days=emptySet())
  AlarmStore.saveAlarm(context,a)
  AlarmStore.prefs(context).edit()
   .putStringSet("scheduled_ids",setOf(a.id))
   .putLong("scheduled_"+a.id,System.currentTimeMillis()-20_000)
   .putString("scheduled_config_"+a.id,AlarmStore.encode(a).toString()+"|"+java.time.ZoneId.systemDefault().id).commit()
  AlarmScheduler.reschedule(context)
  val catchup=AlarmStore.prefs(context).getLong("scheduled_"+a.id,0)
  assertTrue(catchup<System.currentTimeMillis()+5000)
  AlarmScheduler.reschedule(context)
  assertEquals(catchup,AlarmStore.prefs(context).getLong("scheduled_"+a.id,0))
 }
 @Test fun alarmScreenIsAvailableBeforeFirstUnlock() {
  val info=context.packageManager.getActivityInfo(android.content.ComponentName(context,MainActivity::class.java),android.content.pm.PackageManager.MATCH_DIRECT_BOOT_AWARE or android.content.pm.PackageManager.MATCH_DIRECT_BOOT_UNAWARE)
  assertTrue(info.directBootAware)
 }
}
