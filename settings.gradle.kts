rootProject.name = "minchat"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
		maven("https://jitpack.io")
	}
}

include(
	"minchat-client",
	"minchat-backend",
	"minchat-common",
	"minchat-rest",
//	"minchat-cli"
)
