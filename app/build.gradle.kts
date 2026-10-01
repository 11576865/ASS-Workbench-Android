plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val baseVersionName = "0.29.0"
val rendererVersion = providers.gradleProperty("asswb.rendererVersion").getOrElse("0.3.0")
val rendererProvider = providers.gradleProperty("asswb.rendererProvider").getOrElse("none")
val rendererExperimental = providers.gradleProperty("asswb.rendererExperimental").map(String::toBoolean).getOrElse(false)
val buildCommit = providers.environmentVariable("GITHUB_SHA").getOrElse("local").take(12)
val buildRunNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").getOrElse("local")

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
        versionCode = 33
        versionName = if (rendererExperimental) "$baseVersionName-fontconfig" else baseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = if (rendererExperimental) "ASS Workbench FC" else "ASS Workbench"
        buildConfigField("String", "ASSWB_RENDERER_FONT_PROVIDER", "\"$rendererProvider\"")
        buildConfigField("String", "ASSWB_RENDERER_VERSION", "\"$rendererVersion\"")
        buildConfigField("boolean", "ASSWB_RENDERER_EXPERIMENTAL", rendererExperimental.toString())
        buildConfigField("String", "ASSWB_BUILD_COMMIT", "\"$buildCommit\"")
        buildConfigField("String", "ASSWB_BUILD_NUMBER", "\"$buildRunNumber\"")
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
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")

    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive:1.2.0")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("io.github.yuroyami:libmpvkt-compose:$rendererVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
