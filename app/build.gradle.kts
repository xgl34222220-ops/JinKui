plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xiguli.jinkui"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xiguli.jinkui"
        minSdk = 26
        targetSdk = 34
        versionCode = 23
        versionName = "1.5.0-alpha02"
    }

    // 固定的调试签名：仓库内的 app/debug.jks，口令公开（jinkui-debug），只用于 debug 构建。
    // 这样每次 CI 产出的 Debug APK 签名一致，可以直接覆盖安装、保留数据。
    // 它不是秘密，绝不能用于 release；release 签名需自行配置。
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.jks")
            storePassword = "jinkui-debug"
            keyAlias = "jinkui-debug"
            keyPassword = "jinkui-debug"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric 界面测试需要合并后的资源与清单
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.maxHeapSize = "2g"
            // 测试样例数据按北京时间构造；界面里按系统时区显示时间，统一成同一时区，截图里的“12:00”不会变成 04:00
            it.systemProperty("user.timezone", "Asia/Shanghai")
            // Robolectric 的原生图形（用于界面截图）只支持 x86_64 等平台；其他架构退回 LEGACY，断言照跑
            val arch = System.getProperty("os.arch").lowercase()
            it.systemProperty("robolectric.graphicsMode", if (arch == "amd64" || arch == "x86_64") "NATIVE" else "LEGACY")
        }
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // 安全相关：生物识别、SQLCipher 整库加密
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")
    implementation("net.zetetic:sqlcipher-android:4.6.1@aar")

    // 离线识别相册图片里的二维码（两步验证码），纯 Java，Apache-2.0，不联网
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // 账本页面的界面测试：在 JVM 上用 Robolectric 跑 Compose，不进 APK、不加任何权限或测试用 Activity
    testImplementation(platform("androidx.compose:compose-bom:2024.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("org.json:json:20240303")
    // 只用于在 JVM 上跑数据库迁移测试，不进 APK
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.3")
}
