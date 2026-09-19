import java.util.Properties
plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
 namespace = "dev.pounce.alarm"
 compileSdk = 35
 defaultConfig {
  applicationId = "dev.pounce.alarm.next"
  minSdk = 26
  targetSdk = 35
  versionCode = 500
  versionName = "0.5.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 buildFeatures { compose = true; buildConfig = true }
 composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 signingConfigs {
 create("personal") {
  val properties = Properties()
  val signingFile = rootProject.file("private/signing.properties")
  if(signingFile.exists()) signingFile.inputStream().use { properties.load(it) }
  storeFile = rootProject.file("private/pounce-next.jks")
  storePassword = properties.getProperty("storePassword")
  keyAlias = "pounce"
  keyPassword = properties.getProperty("storePassword")
 }
}
buildTypes { release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("personal") } }
}
dependencies {
 implementation("androidx.core:core-ktx:1.13.1")
 implementation("androidx.activity:activity-compose:1.9.3")
 implementation("androidx.compose.ui:ui-android:1.7.5")
 implementation("androidx.compose.foundation:foundation-android:1.7.5")
 implementation("androidx.compose.material3:material3-android:1.3.1")
 implementation("androidx.compose.animation:animation-android:1.7.5")
 testImplementation("junit:junit:4.13.2")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 androidTestImplementation("androidx.test:runner:1.6.2")
}
