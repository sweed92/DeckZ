import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

android {
    namespace = "fr.deckz"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.deckz"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        // Sur GitHub, le numéro de build est ajouté automatiquement (ex. 1.0.12).
        versionName = (findProperty("deckzVersionName") as String?) ?: "1.0"
    }

    // Clé de signature commune : un APK construit sur GitHub s'installe par-dessus le précédent
    // sans désinstaller (et donc sans perdre les notes). La clé n'est jamais dans le dépôt :
    // sur GitHub elle vient des secrets, sur un PC d'un fichier keystore.properties (non versionné).
    // Sans elle, Android Studio garde sa clé de debug habituelle.
    val localKeys = rootProject.file("keystore.properties").takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    val storePath = System.getenv("DECKZ_KEYSTORE") ?: localKeys?.getProperty("storeFile")
    val storePw = System.getenv("DECKZ_KEYSTORE_PASSWORD") ?: localKeys?.getProperty("password")
    val shared = if (storePath != null && storePw != null) signingConfigs.create("shared") {
        storeFile = rootProject.file(storePath)
        storePassword = storePw
        keyAlias = "deckz"
        keyPassword = storePw
    } else null
    buildTypes {
        getByName("debug") { shared?.let { signingConfig = it } }
    }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

ksp { arg("room.generateKotlin", "true") }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    testImplementation("junit:junit:4.13.2")
}
