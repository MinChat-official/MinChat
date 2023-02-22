rootProject.name = "minchat"
arrayOf("client", "backend", "common", "rest").forEach {
	include("minchat-$it")
}
