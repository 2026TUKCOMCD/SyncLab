plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)

}

android {
    namespace = "com.tukorea.synclab_mobile"
    compileSdk = 35

    packaging {
        jniLibs {
            // JNI 라이브러리를 16KB 페이지 경계에 맞게 정렬하여 빌드
            // Kotlin DSL에서는 packaging 블록을 사용합니다.
            useLegacyPackaging = false
        }
    }
    viewBinding{
        enable = true
    }

    defaultConfig {
        applicationId = "com.tukorea.synclab_mobile"
        minSdk = 26
        targetSdk = 35
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
        // SDK 26(Oreo) 이상을 타겟팅하므로 Java 17을 사용하는 것이 요즘 표준입니다.
        // (Android Studio 최신 버전은 Java 17을 기본으로 요구합니다)
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // ✅ 꼭 추가하세요! S3 주소나 서버 URL을 관리할 때 BuildConfig 클래스가 필요합니다.
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.navigation.fragment)
    // 1. 영상 촬영 (Google 권장 CameraX 라이브러리)
    val camerax_version = "1.3.0"
    implementation("androidx.camera:camera-core:$camerax_version")
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation("androidx.camera:camera-video:$camerax_version")
    implementation("androidx.camera:camera-view:$camerax_version")
    implementation("androidx.camera:camera-lifecycle:$camerax_version")

    // 2. AWS S3 (영상 직접 업로드용)
    implementation("com.amazonaws:aws-android-sdk-s3:2.73.0")

    // 3. NTP 시간 동기화 (정확한 촬영 시작 시간 확보용)
    implementation("com.github.instacart.truetime-android:library:3.5")

    // 4. 서버 통신
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // 5. SDK 35 호환을 위한 버전 고정 (libs.xxx 대신 직접 선언)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    // 6. Compose 및 UI 관련 (중복되는 libs.core/activity/lifecycle 삭제됨)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // 테스트 관련
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(platform("androidx.compose:compose-bom:2024.10.00")) // 안정화된 버전 묶음
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.0")
}
