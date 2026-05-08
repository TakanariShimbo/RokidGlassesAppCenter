plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.rokidglassesappcenter.host"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.rokidglassesappcenter.host"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Hi Rokid (global) 経由で CXR-L を使うラッパー。
    implementation("com.example.cxrglobal:lib:0.1.0-SNAPSHOT")

    // Caps シリアライザ (com.rokid.cxr.Caps) を借りるために残す。
    implementation("com.rokid.cxr:client-l:1.0.1")
}

// ── Client APK 自動同梱 ────────────────────────────────────────
// Client (../glass) を composite build として取り込み、その debug APK を
// assets/client.apk に毎ビルドコピーする。ユーザーは手動で cp する必要がない。
val clientDir = rootDir.parentFile.resolve("glass")
val clientApkPath = clientDir.resolve("app/build/outputs/apk/debug/app-debug.apk")

val bundleClient = tasks.register<Copy>("bundleClient") {
    description = "Builds Client and copies its debug APK into assets/client.apk"
    dependsOn(gradle.includedBuild("glass").task(":app:assembleDebug"))
    from(clientApkPath)
    into(projectDir.resolve("src/main/assets"))
    rename { "client.apk" }
}

androidComponents {
    onVariants { variant ->
        // mergeAssets は assets ソースディレクトリを取り込むタスク。それより前に
        // bundleClient で配置を完了させる必要があるので、依存を貼る。
        afterEvaluate {
            tasks.findByName("merge${variant.name.replaceFirstChar { it.uppercase() }}Assets")
                ?.dependsOn(bundleClient)
        }
    }
}
