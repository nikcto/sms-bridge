plugins { id("com.android.application"); kotlin("android") }
android {
    namespace="com.example.smsbridge"
    compileSdk=35
    defaultConfig { applicationId="com.example.smsbridge"; minSdk=26; targetSdk=35; versionCode=1; versionName="1.0" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
kotlin { jvmToolchain(17) }
dependencies { implementation("androidx.core:core-ktx:1.15.0"); implementation("androidx.activity:activity-ktx:1.10.1"); implementation("androidx.work:work-runtime-ktx:2.10.0"); implementation("com.squareup.okhttp3:okhttp:4.12.0"); implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0") }
