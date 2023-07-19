buildscript {
	dependencies {
		classpath("com.jakewharton.mosaic", "mosaic-gradle-plugin", "0.7.1")
	}
}

plugins {
	kotlin("jvm") version "1.8.22"
	kotlin("kapt") version "1.8.22"
	kotlin("plugin.serialization") version "1.8.22"
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

apply(plugin = "com.jakewharton.mosaic")

val ktorVersion: String by rootProject
val picocliVersion: String by rootProject

dependencies {
	implementation(project(":minchat-rest"))
	implementation(project(":minchat-common"))

	implementation("io.ktor", "ktor-client-core", ktorVersion)
	implementation("io.ktor", "ktor-client-cio", ktorVersion)
	implementation("io.ktor", "ktor-client-content-negotiation", ktorVersion)
	implementation("io.ktor", "ktor-serialization-kotlinx-json", ktorVersion)

	implementation("info.picocli", "picocli", picocliVersion)
	kapt("info.picocli", "picocli-codegen", picocliVersion)

	implementation("org.jline", "jline-terminal", "3.23.0")
	runtimeOnly("org.jline", "jline-terminal-jna", "3.23.0")
}

tasks.shadowJar {
	archiveFileName.set("minchat-cli.jar")
	manifest.attributes["Main-Class"] = "io.minchat.cli.MinchatClientCliKt"
	from(sourceSets.main.get().output)
}
