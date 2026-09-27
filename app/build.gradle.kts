plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

android {
    namespace = "com.amit152116.riffle"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.amit152116.riffle"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
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

room {
    // Room Gradle Plugin: configures the Room compiler's schema output AND
    // automatically adds the exported schema into the androidTest resource
    // sources for MigrationTestHelper (since Room Gradle Plugin 2.7.0-alpha07)
    // - no manual sourceSets wiring needed, per the official docs.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.palette.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}

// Confirmed via `./gradlew :app:dependencyInsight --dependency <name>
// --configuration debugAndroidTestRuntimeClasspath`:
// - kotlinx-serialization-json already resolves correctly to 1.8.1 (Room's
//   room-migration-jvm:2.8.5 requests it directly; nothing strict-pins json
//   itself, so normal conflict resolution already picks 1.8.1). Not touched.
// - kotlinx-serialization-core is the only actual conflict: Compose BOM
//   strictly pins it to 1.7.3, but json 1.8.1's own module metadata wants
//   core 1.8.1 - the resulting 1.7.3/1.8.1 pairing throws AbstractMethodError
//   on GeneratedSerializer.typeParametersSerializers() at runtime (used by
//   Room's MigrationTestHelper). Per Gradle's docs on competing `strictly`
//   declarations, a same-level `constraints {}` block can't win here (two
//   same-level strictly ranges that don't overlap fail the build outright
//   rather than picking the higher one) - force()/eachDependency{useVersion()}
//   are the only two ways a project can override a platform's strictly.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            (requested.name == "kotlinx-serialization-core" || requested.name == "kotlinx-serialization-core-jvm")
        ) {
            useVersion("1.8.1")
            because("Match the core version kotlinx-serialization-json 1.8.1 (required by Room 2.8.5's room-migration) actually needs; Compose BOM's strict 1.7.3 pin is incompatible with it.")
        }
    }
}

