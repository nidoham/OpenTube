plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nidoham.opentube"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nidoham.opentube"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("app/my-release-key.keystore")
            storePassword = "813263"
            keyAlias = "my-key-alias"
            keyPassword = "813263"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }

        getByName("debug") {
            isMinifyEnabled = false
            isDebuggable = true
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

dependencies {
    // Core library desugaring for Java 17 features
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.0.4")

    // =====================================================
    // CORE ANDROID
    // =====================================================
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.preference:preference:1.2.1")

    // =====================================================
    // UI COMPONENTS
    // =====================================================
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // =====================================================
    // VIDEO PLAYER (Media3 DASH ONLY)
    // =====================================================
    val media3Version = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    
    implementation("androidx.media:media:1.7.1")

    // =====================================================
    // DATA PROCESSING
    // =====================================================
    implementation("com.google.code.gson:gson:2.11.0")

    // =====================================================
    // NETWORKING
    // =====================================================
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // =====================================================
    // VIDEO EXTRACTION
    // =====================================================
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.8")
    implementation("org.ocpsoft.prettytime:prettytime:5.0.8.Final")

    // =====================================================
    // IMAGE PROCESSING
    // =====================================================
    implementation("com.github.bumptech.glide:glide:4.16.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.16.0")

    // =====================================================
    // LIFECYCLE & ARCHITECTURE
    // =====================================================
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // =====================================================
    // COROUTINES
    // =====================================================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    
    implementation("io.reactivex.rxjava3:rxjava:3.1.8")
    implementation("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation("com.jakewharton.rxbinding4:rxbinding:4.0.0")
    // =====================================================
    // ANDROID-TV SUPPORT
    // =====================================================
    implementation("androidx.leanback:leanback:1.0.0")

    // =====================================================
    // TESTING
    // =====================================================
    // LeakCanary dependency যোগ করুন
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
    
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
