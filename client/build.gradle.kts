plugins {
	kotlin("jvm") version "1.8.0"
}

val jarName = "minchat"
val mindustryVersion: String by rootProject
val ktorVersion: String by rootProject

dependencies {
	implementation(project("common"))

	implementation("com.github.mnemotechnician", "mkui", "master-SNAPSHOT")

	implementation("io.ktor", "ktor-client-core", ktorVersion)

	compileOnly("com.github.Anuken", "mindustryjitpack", mindustryVersion)
	compileOnly("com.github.Anuken.Arc", "arc-core", mindustryVersion)
}

/** Android-specific stuff. Do not modify unless you're 100% sure you know what you're doing! If you break this task, mobile users won't be able to use your mod!*/
task("jarAndroid") {
	dependsOn("jar")
	
	doLast {
		val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
		
		if(sdkRoot == null || sdkRoot.isEmpty() || !File(sdkRoot).exists()) {
			throw GradleException("""
				No valid Android SDK found. Ensure that ANDROID_HOME is set to your Android SDK directory.
				Note: if the gradle daemon has been started before ANDROID_HOME env variable was defined, it won't be able to read this variable.
				In this case you have to run "./gradlew --stop" and try again
			""".trimIndent());
		}
		
		println("searching for an android sdk... ")
		val platformRoot = File("$sdkRoot/platforms/").listFiles().filter { 
			val fi = File(it, "android.jar")
			val valid = fi.exists() && it.name.startsWith("android-")
			
			if (valid) {
				print(it)
				println(" — OK.")
			}
			return@filter valid
		}.maxByOrNull {
			it.name.substring("android-".length).toIntOrNull() ?: -1
		}
		
		if (platformRoot == null) {
			throw GradleException("No android.jar found. Ensure that you have an Android platform installed. (platformRoot = $platformRoot)")
		} else {
			println("using ${platformRoot.absolutePath}")
		}
		
		
		//collect dependencies needed to translate java 8 bytecode code to android-compatible bytecode (yeah, android's dvm and art do be sucking)
		val dependencies = (configurations.compileClasspath.get().files + configurations.runtimeClasspath.files + File(platformRoot, "android.jar")).map { it.path }
		val dependenciesStr = Array<String>(dependencies.size * 2) {
			if (it % 2 == 0) "--classpath" else dependencies.elementAt(it / 2)
		}
		
		//dexing. As a result of this process, a .dex file will be added to the jar file. This requires d8 tool in your $PATH
		exec {
			workingDir("$buildDir/libs")
			commandLine("d8", *dependenciesStr, "--min-api", "14", "--output", "${jarName}-android.jar", "${jarName}-desktop.jar")
		}
	}
}

/** Merges the dektop and android jar files into a multiplatform jar file */
task<Jar>("release") {
	dependsOn("jarAndroid")
	
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("$jarName-any-platform.jar")

	from(
		zipTree("$buildDir/libs/${jarName}-desktop.jar"),
		zipTree("$buildDir/libs/${jarName}-android.jar")
	)

	doLast {
		delete { delete("$buildDir/libs/${jarName}-desktop.jar") }
		delete { delete("$buildDir/libs/${jarName}-android.jar") }
	}
}


tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("${jarName}-desktop.jar")

	from(*configurations.runtimeClasspath.files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())

	from(rootDir) {
		include("mod.hjson")
		include("icon.png")
	}

	from("assets/") {
		include("**")
	}
}
