package dev.pounce.alarm

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
class CameraVisionTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun clearReference(){ CameraMissionStore.clear(context) }
    @After fun removeReference(){ CameraMissionStore.clear(context) }
    private fun texture(seed:Int=19,invert:Boolean=false):Bitmap {
        val random=Random(seed)
        val bitmap=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888)
        for(by in 0..15)for(bx in 0..15) {
            var value=random.nextInt(30,226)
            if(invert)value=255-value
            for(y in by*4 until by*4+4)for(x in bx*4 until bx*4+4)bitmap.setPixel(x,y,Color.rgb(value,value,value))
        }
        return bitmap
    }
    private fun solid(color:Int)=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888).apply{eraseColor(color)}
    @Test fun identicalDetailedImageMatches() {
        val bitmap=texture()
        try {
            val f=CameraMissionVision.features(bitmap)
            assertEquals(259,f.size)
            assertTrue(CameraMissionVision.detailed(f))
            assertTrue(CameraMissionVision.similarity(f,f)>.99f)
        }finally{bitmap.recycle()}
    }
    @Test fun plainAndDarkViewsNeverPassSceneMatch() {
        for(color in listOf(Color.BLACK,Color.WHITE,Color.GRAY)) {
            val bitmap=solid(color)
            try {val f=CameraMissionVision.features(bitmap);assertFalse(CameraMissionVision.detailed(f));assertEquals(0f,CameraMissionVision.similarity(f,f),.001f)}finally{bitmap.recycle()}
        }
    }
    @Test fun invertedAndUnrelatedScenesAreRejected() {
        val original=texture();val inverse=texture(invert=true);val unrelated=texture(77)
        try {
            val reference=CameraMissionVision.features(original)
            assertTrue(CameraMissionVision.similarity(reference,CameraMissionVision.features(inverse))<.67f)
            assertTrue(CameraMissionVision.similarity(reference,CameraMissionVision.features(unrelated))<.67f)
        }finally{original.recycle();inverse.recycle();unrelated.recycle()}
    }
    @Test fun colorHuntUsesActualPixelHue() {
        val pairs=listOf(Color.BLUE to 0,Color.GREEN to 1,Color.RED to 2,Color.YELLOW to 3)
        pairs.forEach { (color,index)->
            val bitmap=solid(color)
            try {
                assertTrue(CameraMissionVision.colorFraction(bitmap,index)>.99f)
                assertTrue(CameraMissionVision.colorFraction(bitmap,(index+1)%4)<.01f)
            }finally{bitmap.recycle()}
        }
    }
    @Test fun unsaturatedAndDarkImagesDoNotPassColorHunt() {
        listOf(Color.WHITE,Color.BLACK,Color.GRAY).forEach {color->val bitmap=solid(color);try{repeat(4){assertEquals(0f,CameraMissionVision.colorFraction(bitmap,it),.001f)}}finally{bitmap.recycle()}}
    }
    @Test fun descriptorRoundTripAndClear() {
        val bitmap=texture()
        try {
            assertFalse(CameraMissionStore.hasReference(context))
            val features=CameraMissionVision.features(bitmap)
            assertTrue(CameraMissionStore.save(context,features))
            assertTrue(CameraMissionStore.hasReference(context))
            assertArrayEquals(features,CameraMissionStore.reference(context),.00001f)
            CameraMissionStore.clear(context)
            assertFalse(CameraMissionStore.hasReference(context))
        }finally{bitmap.recycle()}
    }
    @Test fun corruptDescriptorFailsClosed() {
        val prefs=context.createDeviceProtectedStorageContext().getSharedPreferences("pounce_camera_mission",0)
        prefs.edit().putString("scene","[1,2,3]").commit()
        assertFalse(CameraMissionStore.hasReference(context))
        prefs.edit().putString("scene","not-json").commit()
        assertFalse(CameraMissionStore.hasReference(context))
    }
}
