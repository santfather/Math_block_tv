import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    jacoco
}

android {
    namespace = "com.mathgate"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mathgate"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Compose for TV (D-pad focus handling).
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Storage (D-05).
    implementation("androidx.datastore:datastore-core:1.1.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.datastore:datastore:1.1.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests: pure-Kotlin core/ runs on the JVM (roadmap rule 2).
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    testImplementation("io.kotest:kotest-assertions-core:5.9.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

/** Coverage for the pure-Kotlin `core/` (phase 2 DoD: >= 90%). */
tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates coverage for the pure-Kotlin core."
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    val excluded = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
    )
    classDirectories.setFrom(
        fileTree("${layout.buildDirectory.get()}/tmp/kotlin-classes/debug") { exclude(excluded) },
    )
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(
        fileTree(layout.buildDirectory.get()) {
            include("jacoco/testDebugUnitTest.exec")
            include("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
        },
    )
}
