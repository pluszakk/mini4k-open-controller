pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "mini4k-open-controller"

// Pure Kotlin protocol library: builds and tests anywhere, no Android SDK needed.
include(":duml")

// Android app: needs the Android SDK. Set SKIP_ANDROID=1 to build only the protocol library.
if (System.getenv("SKIP_ANDROID") == null) {
    include(":app")
}
