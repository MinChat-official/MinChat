rootProject.name = "minchat"

pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

include(
	"minchat-client",
	"minchat-backend",
	"minchat-common",
	"minchat-rest",
	"minchat-cli"
)
