plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val baseVersionName = "0.16.0"
val rendererVersion = providers.gradleProperty("asswb.rendererVersion").getOrElse("0.3.0")
val rendererProvider = providers.gradleProperty("asswb.rendererProvider").getOrElse("none")
val rendererExperimental = !rendererProvider.equals("none", ignoreCase = true)

android {
    namespace = "io.github.assworkbench.app"
    compileSdk = 36

    defaultConfig {
        applicationId = if (rendererExperimental) {
            "io.github.assworkbench.app.fontconfig"
        } else {
            "io.github.assworkbench.app"
        }
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = if (rendererExperimental) "$baseVersionName-fontconfig" else baseVersionName
        manifestPlaceholders["appLabel"] = if (rendererExperimental) "ASS Workbench FC" else "ASS Workbench"
        buildConfigField("String", "ASSWB_RENDERER_FONT_PROVIDER", "\"$rendererProvider\"")
        buildConfigField("String", "ASSWB_RENDERER_VERSION", "\"$rendererVersion\"")
        buildConfigField("boolean", "ASSWB_RENDERER_EXPERIMENTAL", rendererExperimental.toString())
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:fonts"))
    implementation(project(":core:container"))
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("io.github.yuroyami:libmpvkt-compose:$rendererVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
