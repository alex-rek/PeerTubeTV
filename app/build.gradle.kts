plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("org.jetbrains.kotlin.plugin.compose") }

val releaseSigningValues = listOf("KEYSTORE_PATH", "KEYSTORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")
    .associateWith { System.getenv(it)?.takeIf(String::isNotBlank) }
val hasAnyReleaseSigningValue = releaseSigningValues.values.any { it != null }
val hasAllReleaseSigningValues = releaseSigningValues.values.all { it != null }
if (hasAnyReleaseSigningValue && !hasAllReleaseSigningValues) {
    throw GradleException("Set all release signing environment variables: ${releaseSigningValues.keys.joinToString()}")
}

android {
    namespace = "app.peertube.tv"
    compileSdk = 35
    defaultConfig { applicationId = "app.peertube.tv"; minSdk = 23; targetSdk = 35; versionCode = 6; versionName = "1.0.6" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    if (hasAllReleaseSigningValues) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("KEYSTORE_PATH")!!)
                storePassword = releaseSigningValues.getValue("KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasAllReleaseSigningValues) signingConfig = signingConfigs.getByName("release")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui:1.7.6")
    implementation("androidx.compose.animation:animation:1.7.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.7.6")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended:1.7.6")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-datasource-okhttp:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.tvprovider:tvprovider:1.0.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.mlkit:language-id:17.0.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    testImplementation("junit:junit:4.13.2")
}
