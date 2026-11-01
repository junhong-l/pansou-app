import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 从 keystore.properties 读取 release 签名信息
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.pansou.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pansou.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 9
        versionName = "1.8"
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "pansou.keystore"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // 正式发布签名（keystore.properties 配置）
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// 仅依赖 AndroidX / Material 组件，网络用框架自带 HttpURLConnection
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
