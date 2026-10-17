import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.dl.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.dl.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("com.google.code.gson:gson:2.11.0")
}

// ---- Downloads the two Google Fonts your HTML uses (Orbitron + Share Tech Mono) into assets/fonts ----
val fontDir = file("src/main/assets/fonts")
val fontFiles = mapOf(
    "Orbitron.ttf" to "https://github.com/google/fonts/raw/main/ofl/orbitron/Orbitron%5Bwght%5D.ttf",
    "ShareTechMono.ttf" to "https://github.com/google/fonts/raw/main/ofl/sharetechmono/ShareTechMono-Regular.ttf"
)
tasks.register("downloadFonts") {
    doLast {
        fontDir.mkdirs()
        fontFiles.forEach { (name, url) ->
            val f = File(fontDir, name)
            if (!f.exists() || f.length() < 5000) {
                try {
                    URI.create(url).toURL().openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                    println("Downloaded font $name")
                } catch (e: Exception) {
                    println("WARNING: could not download $name (${e.message}). Put it in app/src/main/assets/fonts manually.")
                }
            }
        }
    }
}
tasks.whenTaskAdded { if (name == "preBuild") dependsOn("downloadFonts") }
