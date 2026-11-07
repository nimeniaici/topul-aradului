import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "ro.hack4arad.topul"
    compileSdk = 35

    defaultConfig {
        applicationId = "ro.hack4arad.topul"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.3"

        // Client ID OAuth de tip „Web application” (același ca GOOGLE_CLIENT_IDS pe server)
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "").trim()}\"")
        val mapsKey = localProps.getProperty("MAPS_API_KEY", "").trim()
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        // aceeași cheie e folosită și pentru pozele locurilor (Places API New)
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsKey\"")
        // fără cheie, aplicația arată o listă de locuri în loc de hartă
        buildConfigField("boolean", "HAS_MAPS_KEY", (mapsKey.isNotEmpty() && mapsKey != "pune_cheia_aici").toString())
        buildConfigField(
            "String", "BASE_URL",
            "\"${(project.findProperty("baseUrl") as String?) ?: localProps.getProperty("BASE_URL", "http://10.0.2.2:8000/")}\""
        )
    }

    buildTypes {
        release {
            // versiunea rapidă pentru telefoane (fără verificările de depanare); semnată cu aceeași cheie
            // ca versiunea de test, ca să se instaleze peste ea și ca Google Sign-In să meargă (același SHA-1)
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.maps.compose)
    implementation(libs.zxing.core) // codul QR al biletului digital (demo)
    implementation(libs.maps.compose.utils)
    implementation(libs.maps.utils)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.coil.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.googleid)
    implementation(libs.places)

    debugImplementation(libs.androidx.ui.tooling)
}
