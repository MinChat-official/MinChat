import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	kotlin("jvm") version "1.8.0"
	kotlin("kapt") version "1.8.0"
	kotlin("plugin.serialization") version "1.8.0"
}

val ktorVersion: String by project

allprojects {
	apply(plugin = "org.jetbrains.kotlin.jvm")

	repositories {
		mavenCentral()
		mavenLocal()
		maven("https://oss.sonatype.org/content/repositories/snapshots")
		maven("https://jitpack.io")
	}

	dependencies {
	}

	tasks.withType<JavaCompile> {
		sourceCompatibility = "1.8"
		targetCompatibility = "1.8"
	}

	tasks.withType<KotlinCompile> {
		kotlinOptions {
			jvmTarget = "1.8"
			freeCompilerArgs += arrayOf(
				"-Xcontext-receivers"
			)
		}
	}
}
