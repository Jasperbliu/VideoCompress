import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// 强制 Java 编译使用 UTF-8 读取源码。
// 中文 Windows 默认 file.encoding=MS936(GBK)，会把 UTF-8 中文注释解析成乱码，
// 进而出现 "Unresolved reference"、"Unclosed comment" 等假报错。
// Kotlin 编译器的 file.encoding 由 gradle.properties 中的 kotlin.daemon.jvmargs 控制。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// 构建时间（构建配置阶段一次性求值，写入 BuildConfig 与 assets 文件）
val buildTimeString: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("Asia/Shanghai")
}.format(Date())

// 将构建时间写入 assets/build_time.txt，供 app 在启动后顶部显示
val writeBuildTimeFile by tasks.registering {
    val outputFile = layout.projectDirectory.file("src/main/assets/build_time.txt").asFile
    val time = buildTimeString
    outputs.file(outputFile)
    doLast {
        outputFile.parentFile.mkdirs()
        outputFile.writeText(time)
    }
}

android {
    namespace = "com.cloudwinbuddy.videocompress"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cloudwinbuddy.videocompress"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "BUILD_TIME", "\"$buildTimeString\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.jks")
            storePassword = "videocompress"
            keyAlias = "release"
            keyPassword = "videocompress"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// 在资源被打包前先写入构建时间文件
androidComponents {
    onVariants { variant ->
        val mergeAssetsTaskName = "merge${variant.name.replaceFirstChar { it.uppercase() }}Assets"
        tasks.matching { it.name == mergeAssetsTaskName }.configureEach {
            dependsOn(writeBuildTimeFile)
        }
    }
}

// preBuild 也依赖一次，确保 IDE 同步与命令行构建都会触发
tasks.named("preBuild").configure {
    dependsOn(writeBuildTimeFile)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.android.material)

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
