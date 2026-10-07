import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// The Google Maps key lives in local.properties, which git ignores, or in the
// MAPS_API_KEY environment variable on a build server. It is read here and
// handed to the manifest (for the map) and to BuildConfig (for the routing
// call), so it never appears in a tracked file.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = (localProperties.getProperty("MAPS_API_KEY") ?: "")
    .ifBlank { System.getenv("MAPS_API_KEY") ?: "" }

android {
    namespace = "io.github.jamerlybob.windroute"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "io.github.jamerlybob.windroute"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity)
    implementation(libs.appcompat)
    implementation(libs.coordinatorlayout)
    implementation(libs.material)
    implementation(libs.play.services.maps)
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}