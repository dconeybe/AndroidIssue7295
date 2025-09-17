import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.services)
  alias(libs.plugins.ksp)
  alias(libs.plugins.room)
}

android {
  namespace = "com.example.firebaselocalsample"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example.firebaselocalsample"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    buildConfigField("boolean", "USE_FIRESTORE_EMULATOR", "true")
    buildConfigField("boolean", "IS_DELETE_DOCUMENTS_IN_SETUP_ENABLED", "true")
    buildConfigField("boolean", "IS_INACTIVE_TEST_ENABLED", "true")
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("debug")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    buildConfig = true
    compose = true
  }
}

kotlin {
  compilerOptions {
    jvmTarget = JvmTarget.JVM_11
    optIn.add("kotlin.time.ExperimentalTime")
  }
}

dependencies {
  implementation("com.google.firebase:firebase-firestore:26.0.0")

  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.kotlinx.datetime)
  ksp(libs.androidx.room.compiler)
  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)
}

room {
  schemaDirectory("$projectDir/room")
  generateKotlin = true
}
