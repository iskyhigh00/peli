import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/** Única fuente de la versión: x.y.z con un dígito por campo (0.0.0 a 9.9.9). Usar scripts/bump-version.sh. */
val appVersion = "1.5.2"
require(Regex("[0-9]\\.[0-9]\\.[0-9]").matches(appVersion)) { "appVersion debe ser x.y.z de un dígito (máx 9.9.9)" }
val (vMajor, vMinor, vPatch) = appVersion.split(".").map { it.toInt() }

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.peli.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.peli.app"
        minSdk = 24
        targetSdk = 34
        versionCode = vMajor * 100 + vMinor * 10 + vPatch
        versionName = appVersion
        val tmdbKey = System.getenv("TMDB_API_KEY") ?: localProps.getProperty("TMDB_API_KEY", "")
        buildConfigField("String", "TMDB_API_KEY", "\"$tmdbKey\"")
        val supabaseUrl = System.getenv("SUPABASE_URL") ?: localProps.getProperty("SUPABASE_URL", "")
        val supabaseKey = System.getenv("SUPABASE_ANON_KEY") ?: localProps.getProperty("SUPABASE_ANON_KEY", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseKey\"")
        val omdbKey = System.getenv("OMDB_API_KEY") ?: localProps.getProperty("OMDB_API_KEY", "")
        buildConfigField("String", "OMDB_API_KEY", "\"$omdbKey\"")
        val googleWebClientId = System.getenv("GOOGLE_WEB_CLIENT_ID") ?: localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    signingConfigs {
        create("stable") {
            val ks = System.getenv("KEYSTORE_FILE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("stable").takeIf { it.storeFile != null }
                ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Reproductor de YouTube dentro de la app (tráilers). Se usan las versiones de lifecycle y de Kotlin del proyecto:
    // las que trae la biblioteca son más nuevas que las del compilador.
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:13.0.0") {
        exclude(group = "androidx.lifecycle")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
    }

    // Inicio de sesión con Google (Credential Manager).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
