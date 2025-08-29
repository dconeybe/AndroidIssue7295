// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.android) apply false
  alias(libs.plugins.kotlin.compose) apply false
  id("com.google.gms.google-services") version "4.4.3" apply false
  id("com.diffplug.spotless") version "7.1.0"
}

spotless {
  val ktfmtVersion = "0.58"
  kotlin {
    target("**/*.kt")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
  kotlinGradle {
    target("**/*.gradle.kts")
    targetExclude("build/")
    ktfmt(ktfmtVersion).googleStyle()
  }
}
