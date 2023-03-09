rootProject.name = "minchat"
arrayOf("client", "backend", "common", "rest", "cli").forEach {
	include("minchat-$it")
}

include("remote")
