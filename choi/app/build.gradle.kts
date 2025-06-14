plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("org.jetbrains.kotlin.kapt")
    id("kotlin-parcelize")
    // id("com.android.application")
    id("com.google.gms.google-services")
    //  id("org.jetbrains.kotlin.android")
    //  id("org.jetbrains.kotlin.android")
    // id("org.jetbrains.kotlin.android")
    // id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.englishapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.englishapp"
        minSdk = 24
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }

}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity:1.10.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // Retrofit
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation ("com.google.android.material:material:1.12.0")

    // Glide
    implementation("com.github.bumptech.glide:glide:4.14.2")
    kapt("com.github.bumptech.glide:compiler:4.14.2")

    // Dagger
    implementation("com.google.dagger:dagger:2.48.1")
    kapt("com.google.dagger:dagger-compiler:2.48.1")

    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // DataStore

    // DataStore 버전 다운그레이드
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // firebase연결용
    implementation(platform("com.google.firebase:firebase-bom:33.13.0"))
    implementation("com.google.firebase:firebase-analytics")

    // Firebase Authentication (익명 인증에 필요)
    implementation("com.google.firebase:firebase-auth-ktx")

    // Cloud Firestore (닉네임 등 사용자 데이터 저장에 필요)
    implementation("com.google.firebase:firebase-firestore-ktx")

    // FCM 메시지 수신하고 토큰 관리
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Firebase의 Cloud Functions를 직접 호출하려면 필요
    implementation("com.google.firebase:firebase-functions-ktx")

    implementation ("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    //  HttpLoggingInterceptor를 사용하기 위한 필수 라이브러리
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // 스와이프 UI 데코레이션 라이브러리
    implementation("it.xabaras.android:recyclerview-swipedecorator:1.4")



    // Firebase BoM (Bill of Materials) - 버전을 한 곳에서 관리
    implementation(platform("com.google.firebase:firebase-bom:33.1.1"))

    // 필요한 Firebase 라이브러리들
    implementation("com.google.firebase:firebase-auth-ktx")        // 인증
    implementation("com.google.firebase:firebase-storage-ktx")      // 스토리지 (이미지 파일 저장)
    implementation("com.google.firebase:firebase-firestore-ktx")    // Firestore (이미지 URL 저장)

    // 이미지 로딩 및 원형 이미지 뷰 라이브러리
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")


    // 전체 단어보기
    implementation ("com.google.code.gson:gson:2.10.1") //Gson
    implementation ("org.jetbrains.kotlin:kotlin-parcelize-runtime:1.9.0") // Parcelize


}