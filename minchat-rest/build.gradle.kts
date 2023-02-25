plugins {
	kotlin("jvm") version "1.8.0"
	kotlin("plugin.serialization") version "1.8.0"
}

val ktorVersion: String by rootProject

dependencies {
	implementation(project(":minchat-common"))

	implementation("io.ktor", "ktor-client-core", ktorVersion)
	implementation("io.ktor", "ktor-client-cio", ktorVersion)

	implementation("org.mindrot", "jbcrypt", "0.4")
}
