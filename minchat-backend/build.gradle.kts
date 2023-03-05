plugins {
	kotlin("jvm") version "1.8.0"
	kotlin("kapt") version "1.8.0"
	kotlin("plugin.serialization") version "1.8.0"
}

val ktorVersion: String by rootProject
val exposedVersion: String by rootProject
val picocliVersion: String by rootProject

dependencies {
	implementation(project(":minchat-common"))
	
	implementation("org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0")

	implementation("info.picocli", "picocli", picocliVersion)
	kapt("info.picocli", "picocli-codegen", picocliVersion)

	implementation("org.jetbrains.exposed", "exposed-core", exposedVersion)
	implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion)
	implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion)
	implementation("com.h2database", "h2", "2.1.214")  

	implementation("io.ktor", "ktor-server-core", ktorVersion)
        implementation("io.ktor", "ktor-server-netty", ktorVersion)
	implementation("io.ktor", "ktor-server-content-negotiation", ktorVersion)
	implementation("io.ktor", "ktor-serialization-kotlinx-json", ktorVersion)
	implementation("io.ktor", "ktor-server-status-pages", ktorVersion)
	implementation("io.ktor", "ktor-server-rate-limit", ktorVersion)

	implementation("org.mindrot", "jbcrypt", "0.4")
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("minchat-server.jar")

	from(*configurations.runtimeClasspath.get().files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())

	manifest {
                attributes["Main-Class"] = "io.minchat.server.MinchatServerCliKt"
        }
}
