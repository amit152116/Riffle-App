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
// --configuration <config>`, rule below temporarily disabled to see the raw
// graph:
// - kotlinx-serialization-json already resolves correctly to 1.8.1 on
//   debugAndroidTestRuntimeClasspath (Room's room-migration-jvm:2.8.5 requests
//   it directly; nothing strict-pins json itself). Not touched.
// - kotlinx-serialization-core is the only actual conflict, and it is NOT a
//   Compose BOM pin: the BOM's own POM has no serialization entry at all
//   (checked both 2026.02.01 and 2026.09.00). On the MAIN debugRuntimeClasspath
//   it resolves to 1.7.3 by ordinary (non-strict) conflict resolution - four
//   plain edges (lifecycle-viewmodel-compose-android, lifecycle-viewmodel-
//   savedstate-android, savedstate-android, kotlinx-serialization-bom, all
//   Compose-BOM-version-aligned) agree on 1.7.3 because nothing on main asks
//   for anything higher; `grep strictly` on that output finds nothing.
// - On debugAndroidTestRuntimeClasspath, AGP's "consistent resolution"
//   feature turns that same 1.7.3 result into a STRICT requirement (its
//   whole purpose: keep the test and app classpaths from silently diverging).
//   Room's room-migration-jvm - androidTestImplementation-only - needs
//   kotlinx-serialization-json:1.8.1, whose own module metadata wants core
//   1.8.1. 1.7.3-strict vs 1.8.1-wanted throws AbstractMethodError on
//   GeneratedSerializer.typeParametersSerializers() at runtime (used by
//   Room's MigrationTestHelper). Per Gradle's docs on competing `strictly`
//   declarations, a same-level `constraints {}` block can't win here (two
//   same-level strictly ranges that don't overlap fail the build outright
//   rather than picking the higher one) - force()/eachDependency{useVersion()}
//   are the only two ways a project can override it.
// - This rule is intentionally left unscoped (configurations.all, not just
//   androidTest*) for a reason specific to Android instrumentation, not
//   because the same strict pin exists on main (it doesn't - main's 1.7.3 is
//   ordinary resolution, not strict): androidTest runs IN THE SAME PROCESS as
//   the app under test (androidx.test.core.app.ApplicationProvider returns
//   the app's own Application - see any *Test.kt setUp()), so whichever core
//   version the MAIN app APK actually ships is what's loaded at runtime for
//   BOTH app and test code, regardless of what androidTest's own classpath
//   declares. Scoping this rule to androidTest only would leave main shipping
//   1.7.3 while the test code's compiled expectations assume 1.8.1 - the same
//   crash, just moved. This is a real, visible effect on the release build:
//   it moves the app's shipped kotlinx-serialization-core from the
//   naturally-resolved 1.7.3 to 1.8.1. Nothing in this app's own code uses
//   kotlinx.serialization today, so no current behavior depends on which of
//   the two is loaded - but this is a real, deliberate change to the release
//   classpath, not a test-only shim.
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" &&
            (requested.name == "kotlinx-serialization-core" || requested.name == "kotlinx-serialization-core-jvm")
        ) {
            useVersion("1.8.1")
            because(
                "kotlinx-serialization-core naturally resolves to 1.7.3 on the main classpath " +
                    "(ordinary conflict resolution among Compose-BOM-aligned lifecycle/savedstate " +
                    "deps, not a strict BOM pin); AGP's consistent-resolution then makes 1.7.3 " +
                    "strict on debugAndroidTestRuntimeClasspath, conflicting with the 1.8.1 core " +
                    "that Room 2.8.5's room-migration (via kotlinx-serialization-json:1.8.1) needs. " +
                    "Applied unscoped because instrumented tests share the app's own classpath/" +
                    "process, so androidTest-only would leave main shipping the mismatched version."
            )
        }
    }
}

