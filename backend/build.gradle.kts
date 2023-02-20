plugins {
	kotlin("jvm") version "1.8.0"
	kotlin("kapt") version "1.8.0"
}

val ktorVersion: String by rootProject
val exposedVersion: String by rootProject
val picocliVersion: String by rootProject

dependencies {
	implementation(project("common"))
	
	implementation("org.jetbrains.kotlinx", "kotlinx-datetime", "0.4.0")

	implementation("info.picocli", "picocli", picocliVersion)
	kapt("info.picocli", "picocli-codegen", picocliVersion)

	implementation("org.jetbrains.exposed", "exposed-core", exposedVersion)
	implementation("org.jetbrains.exposed", "exposed-dao", exposedVersion)
	implementation("org.jetbrains.exposed", "exposed-jdbc", exposedVersion)

	implementation("io.ktor", "ktor-server-core", ktorVersion)
        implementation("io.ktor", "ktor-server-netty", ktorVersion)
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("minchat-server.jar")

	from(*configurations.runtimeClasspath.files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())

	manifest {
                attributes["Main-Class"] = "io.minchat.server.MinchatServerCliKt"
        }
}
