@file:Suppress("DEPRECATION")
package dev.pounce.alarm

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

/** Stores a small local visual descriptor, never the full photograph. */
object CameraMissionStore {
    private fun prefs(context:Context)=context.createDeviceProtectedStorageContext().getSharedPreferences("pounce_camera_mission",Context.MODE_PRIVATE)
    fun hasReference(context:Context)=reference(context)!=null
    internal fun reference(context:Context):FloatArray? = runCatching {
        val a=JSONArray(prefs(context).getString("scene",null) ?: return null)
        if(a.length()!=259)return null
        FloatArray(a.length()){a.getDouble(it).toFloat()}.takeIf {it.all(Float::isFinite)}
    }.getOrNull()
    internal fun save(context:Context,features:FloatArray):Boolean {
        val a=JSONArray();features.forEach {a.put(it.toDouble())}
        return prefs(context).edit().putString("scene",a.toString()).commit()
    }
    fun clear(context:Context) {prefs(context).edit().remove("scene").commit()}
}

/** Approximate local scene similarity; it is not proof of location or identity. */
internal object CameraMissionVision {
    fun features(bitmap:Bitmap):FloatArray {
        val side=minOf(bitmap.width,bitmap.height)
        val square=Bitmap.createBitmap(bitmap,(bitmap.width-side)/2,(bitmap.height-side)/2,side,side)
        val small=Bitmap.createScaledBitmap(square,16,16,true)
        val f=FloatArray(259)
        for(y in 0..15)for(x in 0..15){val c=small.getPixel(x,y);val r=android.graphics.Color.red(c)/255f;val g=android.graphics.Color.green(c)/255f;val b=android.graphics.Color.blue(c)/255f;f[y*16+x]=.299f*r+.587f*g+.114f*b;f[256]+=r/256;f[257]+=g/256;f[258]+=b/256}
        if(small!==square)small.recycle();if(square!==bitmap)square.recycle()
        return f
    }
    fun detailed(f:FloatArray):Boolean {val mean=f.take(256).average();return sqrt(f.take(256).sumOf{(it-mean)*(it-mean)}/256)>.075}
    fun similarity(a:FloatArray,b:FloatArray):Float {
        if(a.size!=259 || b.size!=259 || !detailed(a) || !detailed(b))return 0f
        val am=a.take(256).average();val bm=b.take(256).average()
        var dot=0.0;var aa=0.0;var bb=0.0
        for(i in 0..255){val av=a[i]-am;val bv=b[i]-bm;dot+=av*bv;aa+=av*av;bb+=bv*bv}
        val correlation=(dot/sqrt(aa*bb)).toFloat().coerceIn(0f,1f)
        val color=(1f-(256..258).sumOf{abs(a[it]-b[it]).toDouble()}.toFloat()/3f).coerceIn(0f,1f)
        return correlation*.85f+color*.15f
    }
    fun colorFraction(bitmap:Bitmap,colorIndex:Int):Float {
        val side=minOf(bitmap.width,bitmap.height);val left=(bitmap.width-side)/2;val top=(bitmap.height-side)/2
        val hsv=FloatArray(3);var hits=0;var count=0
        for(y in 0 until side step maxOf(1,side/48))for(x in 0 until side step maxOf(1,side/48)) {
            android.graphics.Color.colorToHSV(bitmap.getPixel(left+x,top+y),hsv)
            val hue=hsv[0];val hueMatch=when(colorIndex){0->hue>=195 && hue<=255;1->hue>=75 && hue<=165;2->hue<=22 || hue>=340;else->hue>=35 && hue<=70}
            if(hueMatch && hsv[1]>=.35f && hsv[2]>=.18f)hits++
            count++
        }
        return hits.toFloat()/count.coerceAtLeast(1)
    }
}

@Composable fun PhotoMissionRegistration(activity:MainActivity,onRegistered:()->Unit,onClose:()->Unit) {
    var message by remember {mutableStateOf("Choose a fixed, detailed scene outside your bedroom: a sink, bookshelf or breakfast corner. Use similar lighting tomorrow.")}
    var saved by rememberSaveable {mutableStateOf(false)}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
      Surface(modifier=Modifier.fillMaxSize(),color=Cream) {
       Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text("Pounce's photo destination",style=MaterialTheme.typography.headlineSmall)
        Text(message,modifier=Modifier.padding(vertical=12.dp),textAlign=TextAlign.Center)
        if(!saved) MissionCamera(activity,"Save this view",onUnavailable={message=it}) {bitmap ->
            val f=CameraMissionVision.features(bitmap)
            if(!CameraMissionVision.detailed(f))message="This view is too plain or dark. Include clear shapes, textures and edges."
            else if(CameraMissionStore.save(activity,f)){saved=true;message="Saved on this phone. Take the same view during a photo mission.";onRegistered()}
            else message="The photo destination could not be saved. Please try again."
        }
        Text("Only a small visual fingerprint stays on your phone. Photos are discarded. Scene matching is approximate.",style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.padding(8.dp))
        OutlinedButton(onClick=onClose,modifier=Modifier.fillMaxWidth()){Text(if(saved)"Done" else "Back")}
       }
      }
    }
}

@Composable fun CameraMissionPlayer(activity:MainActivity,kind:MissionKind,difficulty:Int,seed:Long,onComplete:()->Unit,onUnavailable:(String)->Unit) {
    val colorIndex=remember(seed){Random(seed).nextInt(4)}
    val colorNames=listOf("blue","green","red","yellow")
    val colors=listOf(Color(0xFF255BBA),Color(0xFF287044),Color(0xFFB32829),Color(0xFFE2B23D))
    var message by remember {mutableStateOf(if(kind==MissionKind.PHOTO)"Go to your saved photo destination and frame the same view." else "Find something ${colorNames[colorIndex]} away from your bed. Fill the center square with it.")}
    var completed by rememberSaveable {mutableStateOf(false)}
    val reference=remember {CameraMissionStore.reference(activity)}
    LaunchedEffect(kind) {if(kind==MissionKind.PHOTO && reference==null)onUnavailable("Register a photo destination in setup before using this mission.")}
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        if(kind==MissionKind.HUNT)Surface(color=colors[colorIndex],shape=RoundedCornerShape(20.dp),modifier=Modifier.fillMaxWidth().height(54.dp)){Box(contentAlignment=Alignment.Center){Text("Find ${colorNames[colorIndex]}",color=if(colorIndex==3)Plum else Color.White)}}
        Text(message,modifier=Modifier.padding(vertical=12.dp),textAlign=TextAlign.Center)
        if(!completed && (kind!=MissionKind.PHOTO || reference!=null)) MissionCamera(activity,if(kind==MissionKind.PHOTO)"Match my destination" else "Check this color",onUnavailable) {bitmap ->
            if(kind==MissionKind.PHOTO) {
                val similarity=CameraMissionVision.similarity(reference!!,CameraMissionVision.features(bitmap))
                if(similarity >= .67f+(difficulty-1)*.025f){completed=true;onComplete()}else message="Not a close enough match yet. Use the same angle, distance and lighting as your saved view."
            } else {
                val fraction=CameraMissionVision.colorFraction(bitmap,colorIndex)
                if(fraction >= .24f+(difficulty-1)*.08f){completed=true;onComplete()}else message="Pounce needs more ${colorNames[colorIndex]} in the center square. Move closer and use steady room light."
            }
        }
        Text(if(kind==MissionKind.HUNT)"Checks visible color on this phone; it does not identify objects or verify location." else "Matches the visible scene on this phone. It does not verify your location.",style=MaterialTheme.typography.bodySmall,color=Muted,modifier=Modifier.padding(8.dp))
    }
}

@Composable private fun MissionCamera(activity:MainActivity,label:String,onUnavailable:(String)->Unit,onImage:(Bitmap)->Unit) {
    var granted by remember {mutableStateOf(ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var requested by rememberSaveable {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var retry by remember {mutableIntStateOf(0)}
    val latestImage by rememberUpdatedState(onImage)
    val latestUnavailable by rememberUpdatedState(onUnavailable)
    val camera=remember(retry,granted) {MissionCameraController(activity,{bitmap->try{latestImage(bitmap)}finally{bitmap.recycle()}},{error=it;latestUnavailable(it)})}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it;requested=true;if(!it)latestUnavailable("Camera permission is needed for this mission.")}
    DisposableEffect(camera) {
        val observer=LifecycleEventObserver {_,event->
            if(event==Lifecycle.Event.ON_RESUME){granted=ContextCompat.checkSelfPermission(activity,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED;camera.active(granted)}
            else if(event==Lifecycle.Event.ON_PAUSE)camera.active(false)
        }
        activity.lifecycle.addObserver(observer);camera.active(granted && activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose{activity.lifecycle.removeObserver(observer);camera.dispose()}
    }
    if(!granted) {
        Text("Allow camera access to check this mission. Images stay on your phone.",textAlign=TextAlign.Center)
        Button(onClick={if(requested && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA))activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+activity.packageName)))else launcher.launch(Manifest.permission.CAMERA)}){Text(if(requested)"Camera permission settings" else "Allow camera")}
    } else if(error!=null) {
        Text(error!!,color=MaterialTheme.colorScheme.error)
        TextButton(onClick={error=null;retry++}){Text("Retry camera")}
    } else {
        Box(Modifier.width(240.dp).height(320.dp).background(Color.Black),contentAlignment=Alignment.Center) {
            key(camera){AndroidView(factory={SurfaceView(it).also(camera::attach)},modifier=Modifier.fillMaxSize())}
            Box(Modifier.size(240.dp).border(3.dp,Peach,RoundedCornerShape(20.dp)))
        }
        Button(onClick={camera.capture()},modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){Text(label)}
    }
}

private class MissionCameraController(private val activity:MainActivity,private val result:(Bitmap)->Unit,private val error:(String)->Unit):SurfaceHolder.Callback {
    private var holder:SurfaceHolder?=null
    private var camera:Camera?=null
    private var running=false
    private var disposed=false
    private var ready=false
    private var frame:ByteArray?=null
    private var width=0
    private var height=0
    private var rotation=90
    fun attach(view:SurfaceView){holder=view.holder;view.holder.addCallback(this)}
    fun active(value:Boolean){running=value;if(value)start()else stop()}
    fun dispose(){disposed=true;stop();holder?.removeCallback(this)}
    override fun surfaceCreated(value:SurfaceHolder){holder=value;ready=true;start()}
    override fun surfaceChanged(value:SurfaceHolder,format:Int,width:Int,height:Int)=Unit
    override fun surfaceDestroyed(value:SurfaceHolder){ready=false;stop()}
    private fun start(){
        if(disposed || !running || !ready || camera!=null)return
        try {
            val id=(0 until Camera.getNumberOfCameras()).firstOrNull {val info=Camera.CameraInfo();Camera.getCameraInfo(it,info);info.facing==Camera.CameraInfo.CAMERA_FACING_BACK}?:throw IllegalStateException()
            val opened=Camera.open(id);camera=opened
            val params=opened.parameters
            val size=params.supportedPreviewSizes.minByOrNull{abs(it.width-640)+abs(it.width.toFloat()/it.height-4f/3f)*1000}
            if(size!=null)params.setPreviewSize(size.width,size.height)
            params.previewFormat=ImageFormat.NV21
            if(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE in params.supportedFocusModes.orEmpty())params.focusMode=Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
            opened.parameters=params
            val info=Camera.CameraInfo();Camera.getCameraInfo(id,info)
            val degrees=when(activity.windowManager.defaultDisplay.rotation){Surface.ROTATION_90->90;Surface.ROTATION_180->180;Surface.ROTATION_270->270;else->0}
            rotation=(info.orientation-degrees+360)%360
            opened.setDisplayOrientation(rotation);opened.setPreviewDisplay(holder)
            width=opened.parameters.previewSize.width;height=opened.parameters.previewSize.height
            opened.setPreviewCallback {bytes,_->frame?.fill(0);frame=bytes?.copyOf()}
            opened.startPreview()
            if(opened.parameters.focusMode==Camera.Parameters.FOCUS_MODE_AUTO)runCatching{opened.autoFocus { _, _ -> }}
        }catch(_:Exception){stop();error("Camera unavailable. Check camera access or close another app using it, then retry.")}
    }
    private fun stop(){ val closing=camera; camera=null; runCatching{closing?.setPreviewCallback(null)}; runCatching{closing?.stopPreview()}; runCatching{closing?.release()}; frame?.fill(0);frame=null }
    fun capture(){
        if(!running || disposed)return
        val bytes=frame ?: return
        try {
            val stream=ByteArrayOutputStream()
            YuvImage(bytes,ImageFormat.NV21,width,height,null).compressToJpeg(Rect(0,0,width,height),90,stream)
            val decoded=BitmapFactory.decodeByteArray(stream.toByteArray(),0,stream.size())?:return
            val rotated=Bitmap.createBitmap(decoded,0,0,decoded.width,decoded.height,Matrix().apply{postRotate(rotation.toFloat())},true)
            if(rotated!==decoded)decoded.recycle()
            result(rotated)
        }catch(_:Exception){error("Could not read this camera frame. Try again in steady light.")}
    }
}



