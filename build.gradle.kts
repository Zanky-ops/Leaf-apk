plugins {
    // AGP 9 несе Kotlin у собі, окремий плагін org.jetbrains.kotlin.android більше не потрібен.
    id("com.android.application") version "9.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
}
