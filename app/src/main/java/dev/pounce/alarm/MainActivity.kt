package dev.pounce.alarm

import android.Manifest
import android.app.*
import android.content.*
import android.graphics.Color as AndroidColor
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : ComponentActivity() {
 private var foreground by mutableStateOf(false)
 private var originalBrightness = -1f
 private var baselineBrightness = .35f
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  originalBrightness = window.attributes.screenBrightness
  baselineBrightness = if(originalBrightness>=0) originalBrightness else runCatching {Settings.System.getInt(contentResolver,Settings.System.SCREEN_BRIGHTNESS)/255f}.getOrDefault(.35f)
  window.statusBarColor = AndroidColor.rgb(250,246,236)
  window.navigationBarColor = AndroidColor.rgb(250,246,236)
  wakeWindow(AlarmStore.session(this)!=null)
  setContent { PounceTheme { PounceApp(this, foreground) } }
 }
 override fun onResume() { super.onResume(); foreground=true; if(AlarmStore.session(this)!=null) AlarmService.rearmSession(this) }
 override fun onPause() { foreground=false; restoreLight(); super.onPause() }
 override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); wakeWindow(AlarmStore.session(this)!=null) }
 fun wakeWindow(active: Boolean) {
  if(Build.VERSION.SDK_INT>=27) { setShowWhenLocked(active); setTurnScreenOn(active) }
  else if(active) window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
  else window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
  if(active) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); restoreLight() }
 }
 fun light(value: Float) { window.attributes=window.attributes.apply { screenBrightness= maxOf(baselineBrightness,value) } }
 fun restoreLight() { window.attributes=window.attributes.apply { screenBrightness=originalBrightness } }
}


@Composable private fun PounceApp(activity:MainActivity,foreground:Boolean) {
 var session by remember {mutableStateOf(AlarmStore.session(activity))}
 var tick by remember {mutableLongStateOf(0)}
 var celebrate by remember {mutableStateOf(false)}
 val prefs=remember {activity.createDeviceProtectedStorageContext().getSharedPreferences("pounce_ui",Context.MODE_PRIVATE)}
 var reduce by remember {mutableStateOf(prefs.getBoolean("reduce_motion",false))}
 LaunchedEffect(Unit) {while(true){val next=AlarmStore.session(activity);if(session!=null&&next==null&&AlarmStore.records(activity).firstOrNull()?.outcome=="awake")celebrate=true;session=next;tick++;delay(500)}}
 DisposableEffect(session?.token){activity.wakeWindow(session!=null);onDispose{activity.wakeWindow(false)}}
 Surface(Modifier.fillMaxSize()) {
  if(session!=null) WakeScreen(activity,session!!,foreground,reduce)
  else Home(activity,tick,reduce){reduce=it;prefs.edit().putBoolean("reduce_motion",it).apply()}
 }
 if(celebrate) AlertDialog(onDismissRequest={celebrate=false},title={Text("You earned this morning.")},text={Column(horizontalAlignment=Alignment.CenterHorizontally){CatMascot(Modifier.height(180.dp).fillMaxWidth(),reduce,true,CatMood.CELEBRATE);Text("Every mission complete. Pounce is proud of those paws. Keep moving into your day.")}},confirmButton={TextButton(onClick={celebrate=false}){Text("Hello, morning!")}})
}

@Composable private fun Home(activity:MainActivity,tick:Long,reduce:Boolean,setReduce:(Boolean)->Unit) {
 var tab by remember {mutableIntStateOf(0)}
 var edit by remember {mutableStateOf<AlarmConfig?>(null)}
 var photo by remember {mutableStateOf(false)}
 var preview by remember {mutableStateOf<MissionKind?>(null)}
 var message by remember {mutableStateOf<String?>(null)}
 var testConfirm by remember {mutableStateOf(false)}
 val alarms=remember(tick){AlarmStore.alarms(activity)}
 val records=remember(tick){AlarmStore.records(activity)}
 val next=alarms.filter{it.enabled}.minByOrNull {AlarmScheduler.nextAt(it)}
 val imports=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)try{val a=RoutineTools.importJson(activity,readRoutine(activity,uri));edit=a;message="Routine imported switched off. Review its tasks and time before enabling."}catch(e:Exception){message=e.message}}
 Scaffold(bottomBar={NavigationBar(containerColor=Cream){listOf("Today","Alarms","Missions","Progress","Settings").forEachIndexed {i,name->NavigationBarItem(selected=tab==i,onClick={tab=i},icon={Text(listOf("☀","◷","✦","▥","⚙")[i],fontSize=23.sp)},label={Text(name,fontSize=10.sp)})}}}) {pad ->
  Column(Modifier.fillMaxSize().padding(pad).statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=22.dp).padding(bottom=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   Row(Modifier.padding(top=12.dp),verticalAlignment=Alignment.CenterVertically){Text(if(tab==0)"pounce" else listOf("Today","Your alarms","Mission club","Your mornings","Make it yours")[tab],fontSize=30.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.weight(1f));Surface(color=Lilac,shape=RoundedCornerShape(20.dp)){Text("CAT NAPS END HERE",fontSize=8.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(10.dp))}}
   when(tab) {
    0 -> {
     Card(colors=CardDefaults.cardColors(containerColor=Peach),shape=RoundedCornerShape(32.dp)) {Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("Big stretch. Fresh start.",fontSize=26.sp,fontWeight=FontWeight.Bold);CatMascot(Modifier.height(195.dp).fillMaxWidth(),reduce);Text(if(next==null)"Your morning adventure starts here." else "NEXT UP",fontSize=12.sp,color=Muted);if(next!=null){Text(timeText(next.hour,next.minute),fontSize=43.sp,fontWeight=FontWeight.ExtraBold);Text(nextLabel(next),fontSize=12.sp,textAlign=TextAlign.Center);Spacer(Modifier.height(12.dp));Text("${next.missions.size} missions"+if(next.followUp)" + stay-awake check" else "",color=Plum,fontWeight=FontWeight.Bold)};Button(onClick={edit=next ?: AlarmConfig()},modifier=Modifier.padding(top=15.dp).fillMaxWidth()){Text(if(next==null)"Build my morning" else "Edit my wake-up")}}}
     Text(listOf("Your blanket has made its case. The cat objects.","One paw out of bed. Then the other.","Your future self called. They want breakfast.","Pounce believes in you. The pillow is biased.")[(java.time.LocalDate.now().dayOfYear)%4],fontSize=17.sp,color=Muted,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
     ReadinessCard(activity,tick)
     Card(colors=CardDefaults.cardColors(containerColor=Lilac),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text("A morning with momentum",fontSize=20.sp,fontWeight=FontWeight.Bold);Text("Move your body. Wake your brain. Finish your mission stack. A delayed check helps catch that sneaky return to bed.");TextButton(onClick={tab=2}){Text("Explore all 10 missions →")}}}
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){FilledTonalButton(onClick={testConfirm=true},modifier=Modifier.weight(1f)){Text("Sound & task test")};OutlinedButton(onClick={tab=3},modifier=Modifier.weight(1f)){Text("My progress")}}
    }
    1 -> {
     Button(onClick={edit=AlarmConfig()},modifier=Modifier.fillMaxWidth()){Text("+ Create alarm")}
     if(alarms.isEmpty())Text("No alarms yet. Choose a time and build a stack of two to five missions.",color=Muted)
     alarms.sortedWith(compareBy<AlarmConfig>{it.hour}.thenBy{it.minute}).forEach {a->Card(onClick={edit=a},colors=CardDefaults.cardColors(containerColor=androidx.compose.ui.graphics.Color.White),shape=RoundedCornerShape(24.dp)){Column(Modifier.padding(20.dp)){Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(timeText(a.hour,a.minute),fontSize=34.sp,fontWeight=FontWeight.ExtraBold);Text(a.label,fontWeight=FontWeight.Medium)};Switch(a.enabled,{AlarmStore.saveAlarm(activity,a.copy(enabled=it));AlarmScheduler.reschedule(activity)})};Text(daysText(a.days),fontSize=12.sp,color=Muted);Spacer(Modifier.height(12.dp));Text(a.missions.joinToString(" → "){it.title},fontSize=13.sp,color=Plum);Text(if(a.followUp)"Stay-awake check on · 2 minutes later" else "All missions required",fontSize=11.sp,color=Muted,modifier=Modifier.padding(top=6.dp))}}}
     OutlinedButton(onClick={imports.launch(arrayOf("application/json","text/plain","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Text("Import a shared routine")}
    }
    2 -> {
     Text("Little challenges. A much bigger morning.",fontSize=18.sp,color=Muted)
     Text("Mix thinking with movement. Try each mission while awake before adding it to an alarm.",fontSize=13.sp,color=Muted)
     MissionKind.entries.toList().chunked(2).forEach {row->Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){row.forEach{k->Card(onClick={preview=k},modifier=Modifier.weight(1f),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=if(k.physical)Peach else Lilac)){Column(Modifier.padding(16.dp).heightIn(min=125.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(k.glyph,fontSize=30.sp,color=Plum);Text(k.title,fontSize=16.sp,fontWeight=FontWeight.Bold);Text(k.hint,fontSize=11.sp,color=Muted)}}}}}
     OutlinedButton(onClick={photo=true},modifier=Modifier.fillMaxWidth()){Text(if(CameraMissionStore.hasReference(activity))"Update my photo destination" else "Register a photo destination")}
    }
    3 -> ProgressScreen(records,reduce)
    4 -> {
     ReadinessCard(activity,tick)
     Text("A cat with good habits",fontSize=22.sp,fontWeight=FontWeight.Bold)
     SettingToggle("Reduce cat motion",reduce,setReduce)
     Text("Sound, light, vibration, difficulty and mission stacks are saved separately for each alarm.",fontSize=13.sp,color=Muted)
     OutlinedButton(onClick={photo=true},modifier=Modifier.fillMaxWidth()){Text("Set up photo destination")}
     Button(onClick={testConfirm=true},modifier=Modifier.fillMaxWidth()){Text("Try my alarm now")}
     val pending=AlarmService.pendingTestAt(activity)
     if(pending>System.currentTimeMillis()){Text("Practice rings in ${((pending-System.currentTimeMillis())/1000).coerceAtLeast(0)}s. Lock your phone.");TextButton(onClick={AlarmService.cancelTest(activity)}){Text("Cancel practice alarm")}}
     else OutlinedButton(onClick={if(!AlarmService.scheduleTest(activity))message="Enable precise alarm timing first."},modifier=Modifier.fillMaxWidth()){Text("Lock-screen test · in 1 minute")}
     Text("Add the Pounce widget from your Android home screen for your next alarm at a glance.",fontSize=13.sp,color=Muted)
     Text("Personal & offline",fontWeight=FontWeight.Bold)
     Text("No account, ads, subscription or internet permission. Routines can be shared as files. Photos and imported audio remain on this device.",fontSize=13.sp,color=Muted)
     Text("Real alarms require the full task sequence. A task that cannot work on this phone can be replaced with two other tasks. There is no snooze or in-app dismiss button. Android system controls remain available.",fontSize=13.sp,color=Muted)
     Text("POUNCE 0.5 · MADE FOR GETTING UP",fontSize=10.sp,color=Muted)
    }
   }
  }
 }
 if(edit!=null)AlarmEditor(activity,edit!!){edit=null}
 if(photo)PhotoMissionRegistration(activity,onRegistered={photo=false;message="Photo destination saved. Practise matching it in the mission library."},onClose={photo=false})
 if(preview!=null)androidx.compose.ui.window.Dialog(onDismissRequest={preview=null},properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){Surface(Modifier.fillMaxSize()){Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){Row(verticalAlignment=Alignment.CenterVertically){Text("MISSION PRACTICE",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton(onClick={preview=null}){Text("Close")}};CatMascot(Modifier.height(110.dp).fillMaxWidth(),reduce,true,CatMood.PLAY);Text(preview!!.title,fontSize=28.sp,fontWeight=FontWeight.Bold);MissionPlayer(activity,preview!!,2,12345L,onComplete={preview=null;message="Mission complete. Those paws are ready!"},onUnavailable={message=it;preview=null})}}}
 if(testConfirm)AlertDialog(onDismissRequest={testConfirm=false},title={Text("Meet your wake-up")},text={Text("Plays your next enabled alarm's sound and task stack immediately. Check your system alarm volume. Practice has a finish button; real alarms require all tasks.")},confirmButton={TextButton(onClick={testConfirm=false;AlarmService.startTest(activity)}){Text("Start practice")}},dismissButton={TextButton(onClick={testConfirm=false}){Text("Later")}})
 if(message!=null)AlertDialog(onDismissRequest={message=null},title={Text("Pounce")},text={Text(message!!)},confirmButton={TextButton(onClick={message=null}){Text("Got it")}})
}

@Composable private fun ProgressScreen(records:List<WakeRecord>,reduce:Boolean) {
 val real=records.filter{it.outcome=="awake"};val days=real.map{Instant.ofEpochMilli(it.at).atZone(ZoneId.systemDefault()).toLocalDate()}.toSet()
 var date=java.time.LocalDate.now();if(date !in days)date=date.minusDays(1);var streak=0;while(date in days){streak++;date=date.minusDays(1)}
 Card(colors=CardDefaults.cardColors(containerColor=Peach),shape=RoundedCornerShape(28.dp)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){CatMascot(Modifier.height(165.dp).fillMaxWidth(),reduce,true,CatMood.CELEBRATE);Text("$streak day streak",fontSize=32.sp,fontWeight=FontWeight.ExtraBold);Text("${real.size} mornings earned · ${real.sumOf{it.missions}} missions completed",fontSize=13.sp,color=Muted)}}
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){(6 downTo 0).forEach{n->val d=java.time.LocalDate.now().minusDays(n.toLong());Column(horizontalAlignment=Alignment.CenterHorizontally){Surface(color=if(d in days)Plum else Lilac,shape=RoundedCornerShape(50)){Text(if(d in days)"★" else "·",color=if(d in days)Cream else Muted,fontSize=23.sp,modifier=Modifier.padding(10.dp))};Text(d.dayOfWeek.name.take(1),fontSize=11.sp,color=Muted)}}}
 Text("Your trophy shelf",fontSize=22.sp,fontWeight=FontWeight.Bold)
 listOf(1 to "First pounce",7 to "Week of whiskers",30 to "Morning lion").forEach{(target,name)->Text((if(real.size>=target)"★ " else "☆ ")+name+" · $target mornings",color=if(real.size>=target)Plum else Muted)}
 if(real.isNotEmpty())Text("Average completion: ${real.map{it.durationSeconds}.average().toInt()/60}m ${real.map{it.durationSeconds}.average().toInt()%60}s",fontWeight=FontWeight.Medium)
 Text("Wake history",fontSize=22.sp,fontWeight=FontWeight.Bold)
 if(records.isEmpty())Text("Your first completed morning will appear here.",color=Muted)
 records.take(30).forEach{r->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(if(r.outcome=="awake")"Morning earned" else if(r.outcome=="test")"Practice" else r.outcome.replaceFirstChar{it.uppercase()},fontWeight=FontWeight.Medium);Text(DateTimeFormatter.ofPattern("EEE d MMM · h:mm a").format(Instant.ofEpochMilli(r.at).atZone(ZoneId.systemDefault())),fontSize=11.sp,color=Muted)};Text("${r.missions} tasks · ${r.durationSeconds/60}m",fontSize=12.sp,color=Muted)}}
}

@Composable private fun AlarmEditor(activity:MainActivity,initial:AlarmConfig,onClose:()->Unit) {
 var alarm by remember(initial.id){mutableStateOf(initial)}
 var error by remember{mutableStateOf("")}
 var delete by remember{mutableStateOf(false)}
 val soundImport=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)try{alarm=alarm.copy(sound=SoundLibrary.import(activity,uri))}catch(e:Exception){error=e.message ?: "Could not import sound"}}
 androidx.compose.ui.window.Dialog(onDismissRequest=onClose,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){Surface(Modifier.fillMaxSize()){Column(Modifier.systemBarsPadding().imePadding()) {
  Row(Modifier.padding(horizontal=18.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onClose){Text("Cancel")};Text("Build your morning",fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f),textAlign=TextAlign.Center);TextButton(onClick={if(alarm.missions.size<2)error="Choose at least two missions." else if(MissionKind.PHOTO in alarm.missions&&!CameraMissionStore.hasReference(activity))error="Register your photo destination in Missions first." else{AlarmStore.saveAlarm(activity,alarm.copy(label=alarm.label.ifBlank{"Up with Pounce"}));AlarmScheduler.reschedule(activity);onClose()}}){Text("Save")}}
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Card(onClick={TimePickerDialog(activity,{_,h,m->alarm=alarm.copy(hour=h,minute=m)},alarm.hour,alarm.minute,false).show()},colors=CardDefaults.cardColors(containerColor=Peach),shape=RoundedCornerShape(28.dp)){Column(Modifier.fillMaxWidth().padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally){Text("TIME TO POUNCE",fontSize=11.sp,letterSpacing=2.sp);Text(timeText(alarm.hour,alarm.minute),fontSize=44.sp,fontWeight=FontWeight.ExtraBold);Text("Tap to choose a time",fontSize=12.sp,color=Muted)}}
   OutlinedTextField(alarm.label,{alarm=alarm.copy(label=it.take(60))},label={Text("Alarm name")},singleLine=true,modifier=Modifier.fillMaxWidth())
   Text("Repeat",fontWeight=FontWeight.Bold)
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){(1..7).forEach{d->Surface(onClick={alarm=alarm.copy(days=if(d in alarm.days)alarm.days-d else alarm.days+d)},shape=RoundedCornerShape(13.dp),color=if(d in alarm.days)Plum else Lilac,modifier=Modifier.size(40.dp)){Box(contentAlignment=Alignment.Center){Text(listOf("M","T","W","T","F","S","S")[d-1],color=if(d in alarm.days)Cream else Plum,fontWeight=FontWeight.Bold)}}}}
   Text(daysText(alarm.days),fontSize=12.sp,color=Muted)
   Text("Your mission stack · ${alarm.missions.size}/5",fontSize=21.sp,fontWeight=FontWeight.Bold)
   Text("Choose 2–5. They run in the order you add them. Include walking or a photo destination to leave the bed.",fontSize=13.sp,color=Muted)
   alarm.missions.forEachIndexed{i,k->Row(verticalAlignment=Alignment.CenterVertically){Text("${i+1}. ${k.title}",modifier=Modifier.weight(1f),fontWeight=FontWeight.Medium);if(i>0)TextButton(onClick={val ms=alarm.missions.toMutableList();ms[i]=ms[i-1];ms[i-1]=k;alarm=alarm.copy(missions=ms)}){Text("↑")};TextButton(onClick={alarm=alarm.copy(missions=alarm.missions-k)}){Text("Remove")}}}
   MissionKind.entries.filter{it !in alarm.missions}.chunked(2).forEach{row->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){row.forEach{k->OutlinedButton(enabled=alarm.missions.size<5,onClick={alarm=alarm.copy(missions=alarm.missions+k)},modifier=Modifier.weight(1f)){Text("+ ${k.title}",fontSize=11.sp)}}}}
   Text("Difficulty",fontWeight=FontWeight.Bold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Gentle","Focused","Determined").forEachIndexed{i,label->FilterChip(selected=alarm.difficulty==i+1,onClick={alarm=alarm.copy(difficulty=i+1)},label={Text(label,fontSize=11.sp)})}}
   SettingToggle("Stay-awake check",alarm.followUp){alarm=alarm.copy(followUp=it)}
   Text("After the stack, take 2 minutes to start your morning. Then complete another walking and typing check. The alarm is only finished after that check.",fontSize=12.sp,color=Muted)
   Text("Wake-up sound",fontSize=21.sp,fontWeight=FontWeight.Bold)
   SoundLibrary.choices.forEach{(id,title)->Row(verticalAlignment=Alignment.CenterVertically){RadioButton(alarm.sound==id,{alarm=alarm.copy(sound=id)});Text(title)}}
   if(alarm.sound.startsWith("custom-"))Text("Selected: your imported audio",color=Plum)
   OutlinedButton(onClick={soundImport.launch(arrayOf("audio/*"))}){Text("Import my own sound")}
   Text("Sound level · ${(alarm.volume*100).toInt()}%",fontWeight=FontWeight.Medium)
   Slider(alarm.volume,{alarm=alarm.copy(volume=it)},valueRange=.25f..1f)
   Text("Melody builds over 45 seconds. This level is relative to your system alarm volume.",fontSize=12.sp,color=Muted)
   SettingToggle("Boost system alarm volume",alarm.boost){alarm=alarm.copy(boost=it)}
   Text("Raises the alarm stream to at least 85% while ringing, then restores it. Your volume-button changes remain respected.",fontSize=12.sp,color=Muted)
   SettingToggle("Escalating vibration",alarm.vibration){alarm=alarm.copy(vibration=it)}
   SettingToggle("Steady wake light",alarm.light){alarm=alarm.copy(light=it)}
   Text("Task completion is the only in-app way to finish a real alarm. Practise your selected missions before bedtime.",fontSize=12.sp,color=Muted)
   if(error.isNotBlank())Text(error,color=Plum,fontWeight=FontWeight.Bold)
   TextButton(onClick={try{RoutineTools.share(activity,alarm)}catch(e:Exception){error=e.message.orEmpty()}}){Text("Share this routine")}
   TextButton(onClick={delete=true}){Text("Delete alarm")}
  }
 }}}
 if(delete)AlertDialog(onDismissRequest={delete=false},title={Text("Delete alarm?")},confirmButton={TextButton(onClick={AlarmStore.deleteAlarm(activity,alarm.id);AlarmScheduler.reschedule(activity);onClose()}){Text("Delete")}},dismissButton={TextButton(onClick={delete=false}){Text("Keep")}})
}
@Composable private fun SettingToggle(label:String,value:Boolean,onChange:(Boolean)->Unit){Row(verticalAlignment=Alignment.CenterVertically){Text(label,modifier=Modifier.weight(1f));Switch(value,onChange)}}

@Composable private fun WakeScreen(activity:MainActivity,s:WakeSession,foreground:Boolean,reduce:Boolean) {
 var dim by remember(s.token){mutableStateOf(false)}
 var now by remember{mutableLongStateOf(System.currentTimeMillis())}
 var unavailable by remember(s.token,s.completed){mutableStateOf<String?>(null)}
 BackHandler { /* Mission completion owns the alarm lifecycle. */ }
 LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();delay(250)}}
 LaunchedEffect(s.token,foreground,dim,s.checkAt){if(!foreground||dim||!s.alarm.light||s.waiting(System.currentTimeMillis())){activity.restoreLight();return@LaunchedEffect};while(true){activity.light(.12f+.73f*((System.currentTimeMillis()-s.startedAt).coerceAtLeast(0)/20000f).coerceIn(0f,1f));delay(100)}}
 DisposableEffect(s.token){onDispose{activity.restoreLight()}}
 Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(22.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Text(if(s.isTest)"POUNCE · PRACTICE" else if(s.followUpStage>0)"POUNCE · STAY AWAKE" else "POUNCE · RISE & SHINE",fontSize=11.sp,letterSpacing=2.sp,fontWeight=FontWeight.Bold)
  CatMascot(Modifier.fillMaxWidth().height(if(s.waiting(now))190.dp else 112.dp),reduce,true,if(s.waiting(now))CatMood.CELEBRATE else if(s.current()?.physical==true)CatMood.WALK else CatMood.THINK)
  if(s.waiting(now)) {
   Text("Keep those paws moving.",fontSize=30.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center)
   Text("${((s.checkAt-now)/1000).coerceAtLeast(0)/60}:${String.format("%02d",((s.checkAt-now)/1000).coerceAtLeast(0)%60)}",fontSize=56.sp,fontWeight=FontWeight.ExtraBold)
   Text("Your first stack is complete. Start breakfast or head into the daylight. Pounce will ring for a final walking and typing check when this timer ends.",textAlign=TextAlign.Center,color=Muted)
   Text("${s.completed} missions earned so far",fontWeight=FontWeight.Bold,color=Plum)
  } else {
   Text("MISSION ${s.missionIndex+1} / ${s.stack().size}"+if(s.replacement.isNotEmpty())" · REPLACEMENT ${s.replacementIndex+1}/2" else "",fontSize=11.sp,color=Muted)
   LinearProgressIndicator(progress={s.missionIndex.toFloat()/s.stack().size},modifier=Modifier.fillMaxWidth(),color=Plum,trackColor=Lilac)
   Text(s.current()?.title ?: "Morning missions",fontSize=27.sp,fontWeight=FontWeight.ExtraBold,textAlign=TextAlign.Center)
   key(s.token,s.completed,s.replacement) {s.current()?.let{k->MissionPlayer(activity,k,s.alarm.difficulty,s.token.hashCode().toLong()+s.completed*719L,onComplete={AlarmService.completeMission(activity,s.token,s.missionIndex,s.completed,s.revision())},onUnavailable={unavailable=it})}}
   TextButton(onClick={unavailable="If this task cannot work for you, complete two replacement tasks instead. This does not dismiss the alarm."}){Text("Task not working?",fontSize=12.sp,color=Muted)}
  }
  Row {TextButton(onClick={dim=!dim;if(dim)activity.restoreLight()}){Text(if(dim)"Restore light" else "Dim screen",fontSize=12.sp)};if(s.isTest)TextButton(onClick={AlarmService.finish(activity,s.token,"test")}){Text("Finish practice")}}
 }
 if(unavailable!=null)AlertDialog(onDismissRequest={unavailable=null},title={Text("Keep the morning moving")},text={Text(unavailable!!+"\n\nReplacement: calculations and typing. Your remaining missions still follow.")},confirmButton={TextButton(onClick={AlarmService.replaceMission(activity,s.token,s.missionIndex,s.revision());unavailable=null}){Text("Do replacement tasks")}},dismissButton={TextButton(onClick={unavailable=null}){Text("Try this task again")}})
}
@Composable private fun ReadinessCard(activity: MainActivity, tick: Long) {
 val exact=remember(tick) { Build.VERSION.SDK_INT<31 || activity.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
 val notification=remember(tick) { activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled() }
 val full=remember(tick) { Build.VERSION.SDK_INT<34 || activity.getSystemService(NotificationManager::class.java).canUseFullScreenIntent() }
 val audible=remember(tick) { activity.getSystemService(AudioManager::class.java).getStreamVolume(AudioManager.STREAM_ALARM)>0 }
 val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
 val all=exact&&notification&&full&&audible
 var expanded by remember {mutableStateOf(false)}
 Card(colors=CardDefaults.cardColors(containerColor=if(all) androidx.compose.ui.graphics.Color(0xFFE2EBDC) else androidx.compose.ui.graphics.Color(0xFFFFEBCB)),shape=RoundedCornerShape(22.dp)) {
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
   Text(if(all) "Your phone's alarm checks look good" else "A quick check before bedtime",fontWeight=FontWeight.Bold,fontSize=17.sp)
   if(!all || expanded) {
   ReadinessRow("Precise alarm timing",exact) { if(Build.VERSION.SDK_INT>=31) safeOpen(activity,Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${activity.packageName}"))) }
   ReadinessRow("Alarm notifications",notification) { if(Build.VERSION.SDK_INT>=33 && !notification) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else safeOpen(activity,Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,activity.packageName)) }
   ReadinessRow("Show over the lock screen",full) { if(Build.VERSION.SDK_INT>=34) safeOpen(activity,Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${activity.packageName}"))) }
   ReadinessRow("System alarm volume",audible) { safeOpen(activity,Intent(Settings.ACTION_SOUND_SETTINGS)) }
   Text("Also test with your usual Do Not Disturb and Bluetooth settings. Some phones need unrestricted battery access.",fontSize=12.sp,color=Muted)
   TextButton(onClick={safeOpen(activity,Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}")))},contentPadding=PaddingValues(0.dp)) { Text("Open Pounce's system settings",fontSize=12.sp) }
   } else Text("Timing, notifications, lock screen and alarm volume checked. Rehearse once on your phone.",fontSize=12.sp,color=Muted)
   if(all) TextButton(onClick={expanded=!expanded},contentPadding=PaddingValues(0.dp)) {Text(if(expanded)"Hide phone checks" else "Review phone checks",fontSize=12.sp)}
  }
 }
}
@Composable private fun ReadinessRow(label:String,ready:Boolean,onFix:()->Unit) {
 Row(verticalAlignment=Alignment.CenterVertically) { Text(if(ready) "✓" else "!",color=if(ready) Leaf else Plum,fontWeight=FontWeight.Bold,modifier=Modifier.width(22.dp)); Text(label,fontSize=13.sp,modifier=Modifier.weight(1f)); if(!ready) TextButton(onClick=onFix,contentPadding=PaddingValues(0.dp),modifier=Modifier.height(32.dp)) {Text("Set up",fontSize=12.sp)} }
}
private fun safeOpen(context:Context,intent:Intent) { try { context.startActivity(intent) } catch(_:Exception) { try { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) } catch(_:Exception) {} } }
private fun timeText(hour:Int,minute:Int):String = String.format(java.util.Locale.getDefault(),"%d:%02d %s",if(hour%12==0)12 else hour%12,minute,if(hour<12)"AM" else "PM")
private fun daysText(days:Set<Int>):String = if(days.size==7) "Every day" else if(days.isEmpty()) "Next occurrence only" else days.sorted().joinToString(" · ") { listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")[it-1] }
private fun nextLabel(alarm:AlarmConfig):String = WakeRules.nextTime(alarm,java.time.ZonedDateTime.now()).format(DateTimeFormatter.ofPattern("EEEE, d MMM"))+" · "+alarm.label

private fun readRoutine(context:Context,uri:Uri):String {
 return context.contentResolver.openInputStream(uri)?.use { input ->
  val output=java.io.ByteArrayOutputStream()
  val buffer=ByteArray(4096)
  while(true) {
   val count=input.read(buffer)
   if(count<0)break
   require(output.size()+count<=65536){"Routine files must be smaller than 64 KB."}
   output.write(buffer,0,count)
  }
  output.toString("UTF-8")
 } ?: error("This file is unavailable. Choose a local routine file.")
}



