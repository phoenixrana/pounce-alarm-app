package dev.pounce.alarm

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.math.sqrt
import kotlin.random.Random

@Composable fun MissionPlayer(activity: MainActivity, kind: MissionKind, difficulty: Int, seed: Long,
    onComplete: () -> Unit, onUnavailable: (String) -> Unit) {
    key(kind, seed) {
        var completed by rememberSaveable { mutableStateOf(false) }
        val latestComplete by rememberUpdatedState(onComplete)
        val finish = { if (!completed) { completed=true; latestComplete() }; Unit }
        val d=difficulty.coerceIn(1,3)
        when(kind) {
            MissionKind.MATH -> MathMission(d,seed,finish)
            MissionKind.MEMORY -> MemoryMission(d,seed,finish)
            MissionKind.TYPING -> TypingMission(d,seed,finish)
            MissionKind.COLORS -> ColorsMission(d,seed,finish)
            MissionKind.TILT, MissionKind.SHAKE, MissionKind.WALK, MissionKind.PUSHUPS -> SensorMission(activity,kind,d,seed,finish,onUnavailable)
            MissionKind.PHOTO, MissionKind.HUNT -> CameraMissionPlayer(activity,kind,d,seed,finish,onUnavailable)
        }
    }
}

@Composable private fun MissionHint(text: String) { Text(text, style=MaterialTheme.typography.bodyLarge, textAlign=TextAlign.Center, modifier=Modifier.fillMaxWidth().padding(vertical=8.dp)) }
@Composable private fun MissionProgress(done: Int, total: Int) {
    val value by animateFloatAsState(done.toFloat()/total, label="mission progress")
    LinearProgressIndicator(progress={value}, modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
    Text("$done / $total", color=Muted, modifier=Modifier.fillMaxWidth(), textAlign=TextAlign.Center)
}

@Composable private fun MathMission(d: Int, seed: Long, done: () -> Unit) {
    var round by rememberSaveable { mutableIntStateOf(0) }
    var answer by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val total=d+2
    val random=remember(seed,round) { Random(seed+round*71) }
    val a=remember(seed,round) { random.nextInt(if(d==1)3 else 11,if(d==1)20 else 50) }
    val b=remember(seed,round) { random.nextInt(2,if(d==3)16 else 10) }
    val multiply=d==3 && round%2==1
    val result=if(multiply)a*b else a+b
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        MissionHint("Help Pounce count breakfast.")
        MissionProgress(round,total)
        Text("$a ${if(multiply)"×" else "+"} $b = ?", fontSize=38.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(12.dp))
        Text(answer.ifEmpty { "…" },fontSize=30.sp,color=MaterialTheme.colorScheme.primary)
        if(error) Text("Almost. Count it once more.",color=MaterialTheme.colorScheme.error)
        for(row in listOf(listOf("1","2","3"),listOf("4","5","6"),listOf("7","8","9"),listOf("⌫","0","Check"))) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) { row.forEach { value ->
                FilledTonalButton(onClick={
                    error=false
                    when(value) { "⌫" -> answer=answer.dropLast(1); "Check" -> if(answer.toIntOrNull()==result) { answer=""; round++; if(round>=total)done() } else {error=true;answer=""}; else -> if(answer.length<5) answer+=value }
                },modifier=Modifier.weight(1f).heightIn(min=52.dp)) {Text(value,fontSize=19.sp)}
            } }
        }
    }
}

@Composable private fun MemoryMission(d:Int,seed:Long,done:()->Unit) {
    val sequence=remember(seed,d) { val r=Random(seed); List(d+3){r.nextInt(9)} }
    var input by rememberSaveable { mutableIntStateOf(0) }
    var replay by rememberSaveable { mutableIntStateOf(0) }
    var flashing by remember { mutableIntStateOf(-1) }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(replay) {
        ready=false; input=0; delay(600)
        sequence.forEach { flashing=it;delay(600);flashing=-1;delay(250) }
        ready=true
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        MissionHint(if(ready)"Your turn! Tap the paw path in order." else "Watch where Pounce puts each paw.")
        MissionProgress(input,sequence.size)
        repeat(3) { row -> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            repeat(3) { col -> val index=row*3+col
                Button(onClick={if(ready){if(sequence[input]==index) {input++;if(input==sequence.size){ready=false;done()}} else {replay++}}},
                    enabled=ready, colors=ButtonDefaults.buttonColors(containerColor=Lilac,contentColor=Plum,disabledContainerColor=if(flashing==index)Peach else Lilac,disabledContentColor=Plum),
                    shape=RoundedCornerShape(20.dp),modifier=Modifier.weight(1f).aspectRatio(1.2f).padding(vertical=4.dp).semantics { contentDescription="Memory tile ${index+1}${if(flashing==index) ", lit" else ""}" }) {Text(if(flashing==index)"🐾" else "${index+1}",fontSize=25.sp)}
            }
        } }
        if(ready) TextButton(onClick={replay++}) {Text("Show the paw path again")}
    }
}

@Composable private fun TypingMission(d:Int,seed:Long,done:()->Unit) {
    val phrases=when(d) {1->listOf("My paws are on the floor.","Good morning, little Pounce.");2->listOf("I am standing up and starting my morning.","Pounce and I are ready for a bright new day.");else->listOf("My feet are on the floor. I will walk into the light and begin my day.","Good morning, Pounce. I am leaving my cozy bed and making breakfast now.")}
    val phrase=remember(seed,d) {phrases[Random(seed).nextInt(phrases.size)]}
    var typed by rememberSaveable {mutableStateOf("")}
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        MissionHint("Type Pounce's morning promise.")
        Text(phrase,fontSize=24.sp,fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,modifier=Modifier.padding(16.dp))
        OutlinedTextField(value=typed,onValueChange={typed=it.take(200)},label={Text("Type the sentence")},keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.Sentences,autoCorrect=false),modifier=Modifier.fillMaxWidth(),minLines=3)
        Text("Match the words and punctuation. Capital letters do not matter.",color=Muted,modifier=Modifier.padding(8.dp))
        Button(onClick=done,enabled=typed.trim().equals(phrase,ignoreCase=true),modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("Promise made")}
    }
}

@Composable private fun ColorsMission(d:Int,seed:Long,done:()->Unit) {
    val names=listOf("RED","BLUE","GREEN","PURPLE")
    val colors=listOf(Color(0xFFB32829),Color(0xFF255BBA),Color(0xFF287044),Color(0xFF85369E))
    var round by rememberSaveable {mutableIntStateOf(0)}
    var wrong by remember {mutableStateOf(false)}
    val ink=remember(seed,round) {Random(seed+round*131).nextInt(4)}
    val word=remember(seed,round) {(ink+Random(seed+round*173).nextInt(1,4))%4}
    val total=d+3
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        MissionHint("Choose the INK color. Ignore the word.")
        MissionProgress(round,total)
        Text(names[word],color=colors[ink],fontSize=46.sp,fontWeight=FontWeight.Black,modifier=Modifier.padding(24.dp))
        if(wrong)Text("Look at the color of the letters.",color=MaterialTheme.colorScheme.error)
        names.forEachIndexed {index,name -> OutlinedButton(onClick={if(index==ink){wrong=false;round++;if(round>=total)done()}else{wrong=true}},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(name,fontSize=18.sp)} }
        Text("Choose a different mission in alarm setup if color recognition is difficult.",style=MaterialTheme.typography.bodySmall,color=Muted)
    }
}

@Composable private fun SensorMission(activity:MainActivity,kind:MissionKind,d:Int,seed:Long,done:()->Unit,unavailable:(String)->Unit) {
    var count by rememberSaveable {mutableIntStateOf(0)}
    var x by remember {mutableFloatStateOf(0f)}
    var y by remember {mutableFloatStateOf(0f)}
    var ready by remember {mutableStateOf(false)}
    var started by rememberSaveable {mutableStateOf(false)}
    var sensorMode by remember {mutableStateOf("")}
    var error by remember {mutableStateOf<String?>(null)}
    var revision by remember {mutableIntStateOf(0)}
    val total=when(kind){MissionKind.WALK->15+d*10;MissionKind.SHAKE->8+d*4;MissionKind.PUSHUPS->3+d*2;else->d+3}
    val latestDone by rememberUpdatedState(done)
    val latestUnavailable by rememberUpdatedState(unavailable)
    val tracker=remember(kind,seed) {MotionTracker(activity,kind,{
        if(count<total) {count++;if(count>=total)latestDone()}
    },{a,b->x=a;y=b})}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {revision++}
    DisposableEffect(tracker,revision,started) {
        fun begin() {
            if(!started)return
            ready=tracker.start();sensorMode=tracker.mode
            if(!ready) { error=if(kind==MissionKind.PUSHUPS)"This phone has no usable proximity sensor. Choose another movement mission." else "This phone's motion sensor is unavailable."; latestUnavailable(error!!) }
        }
        val observer=LifecycleEventObserver {_,event-> if(event==Lifecycle.Event.ON_RESUME)begin() else if(event==Lifecycle.Event.ON_PAUSE){ready=false;tracker.stop()} }
        activity.lifecycle.addObserver(observer)
        if(activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) begin()
        onDispose { activity.lifecycle.removeObserver(observer);tracker.stop() }
    }
    val targets=remember(seed) { val r=Random(seed); var previous=-1; List(6) { var next=r.nextInt(4); while(next==previous) next=r.nextInt(4); previous=next; Offset(if(next%2==0) -.65f else .65f, if(next<2) -.65f else .65f) } }
    var dwell by remember {mutableFloatStateOf(0f)}
    LaunchedEffect(ready,count,kind) {
        if(kind==MissionKind.TILT && ready && count<total) {
            val target=targets[count]
            while(true) {
                delay(50)
                val distance=sqrt((x-target.x)*(x-target.x)+(y-target.y)*(y-target.y))
                dwell=if(distance<.27f)dwell+.05f else 0f
                if(dwell>=.7f){dwell=0f;count++;if(count>=total)latestDone();break}
            }
        }
    }
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        MissionHint(when(kind) {
            MissionKind.WALK->"Get out of bed and walk around your room with the phone in your hand."
            MissionKind.SHAKE->"Stand up, hold the phone securely, and give it gentle, deliberate shakes."
            MissionKind.PUSHUPS->"Place the phone on a stable surface. Move near the sensor at the top of the screen, then move away for each repetition. Use wall push-ups if comfortable."
            else->"Hold the phone face up. Tilt to guide the yarn ball into each golden circle and hold it there."
        })
        MissionProgress(count,total)
        if(kind==MissionKind.TILT) {
            val target=targets[count.coerceAtMost(targets.lastIndex)]
            Canvas(Modifier.fillMaxWidth().height(230.dp).background(Lilac,RoundedCornerShape(24.dp)).semantics {contentDescription="Tilt maze. Target ${count+1} of $total"}) {
                fun point(a:Float,b:Float)=Offset(size.width/2+a*size.width*.38f,size.height/2+b*size.height*.38f)
                drawCircle(Peach,32.dp.toPx(),point(target.x,target.y))
                drawCircle(Leaf,32.dp.toPx(),point(target.x,target.y),style=Stroke(3.dp.toPx()))
                drawCircle(Color(0xFFC7673A),18.dp.toPx(),point(x,y))
                drawCircle(Cream,10.dp.toPx(),point(x,y),style=Stroke(2.dp.toPx()))
            }
            Text("${if(target.x<0)"Left" else "Right"} · ${if(target.y<0)"far edge" else "near edge"}",color=Muted)
        } else {
            val scale by animateFloatAsState(if(count%2==0)1f else 1.1f,label="motion count")
            Text("$count",fontSize=(64*scale).sp,fontWeight=FontWeight.Black,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(12.dp))
        }
        if(!started) Button(onClick={started=true;if(kind==MissionKind.WALK && Build.VERSION.SDK_INT>=29)permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text("I'm ready · start moving")}
        if(sensorMode.isNotEmpty())Text(sensorMode,color=Muted,style=MaterialTheme.typography.bodySmall)
        if(kind==MissionKind.PUSHUPS) Text("Counts near/far movements, not exercise form. A hand movement also works if exercise is unsuitable.",style=MaterialTheme.typography.bodySmall,color=Muted)
        if(kind==MissionKind.WALK && sensorMode.contains("estimate"))Text("This phone estimates steps from motion; it cannot verify your location.",style=MaterialTheme.typography.bodySmall,color=Muted)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error);TextButton(onClick={error=null;revision++}){Text("Retry sensor")} }
    }
}

