// Plugin versions live here, on the root build classpath, so the Android plugin can be left out
// when building only the pure Kotlin protocol library (SKIP_ANDROID=1, no Android SDK needed).
buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        if (System.getenv("SKIP_ANDROID") == null) {
            classpath("com.android.tools.build:gradle:8.7.3")
            classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        }
    }
}
