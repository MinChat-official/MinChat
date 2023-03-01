plugins {
	kotlin("jvm") version "1.8.0"
	kotlin("kapt") version "1.8.0"
	kotlin("plugin.serialization") version "1.8.0"
}

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
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("minchat-cli.jar")

	from(*configurations.runtimeClasspath.files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())

	manifest {
                attributes["Main-Class"] = "io.minchat.cli.MinchatClientCliKt"
        }
}
