
import org.gradle.internal.impldep.org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.OutputStream
import java.security.MessageDigest
import java.util.*

plugins {
	kotlin("jvm") version "1.9.20"
	kotlin("plugin.serialization") version "1.9.20"
}

val jarName = "minchat-client"
val mindustryVersion: String by rootProject
val ktorVersion: String by rootProject

val spritesFolder = File("$projectDir/assets/sprites")
val generatedCodeFolder = File("$buildDir/generated/")

sourceSets.main {
	kotlin.srcDir(generatedCodeFolder)
}

dependencies {
	implementation(project(":minchat-common"))
	implementation(project(":minchat-rest"))

	implementation("com.github.mnemotechnician", "mkui", "v1.3.2")

	implementation("io.ktor", "ktor-client-core", ktorVersion)
	implementation("io.ktor", "ktor-client-cio", ktorVersion)
	implementation("io.ktor", "ktor-client-content-negotiation", ktorVersion)
	implementation("io.ktor", "ktor-serialization-kotlinx-json", ktorVersion)

	compileOnly("com.github.Anuken.Arc", "arc-core", "v146")
	compileOnly("com.github.Anuken", "Mindustry", "v146")

	// For the new-console integration
	compileOnly("com.github.mnemotechnician", "new-console", "v1.9.0.2")
}

/** Android-specific stuff. Do not modify unless you're 100% sure you know what you're doing! If you break this task, mobile users won't be able to use your mod!*/
task("jarAndroid") {
	fun hash(data: ByteArray): String =
		MessageDigest.getInstance("MD5")
		.digest(data)
		.let { Base64.getEncoder().encodeToString(it) }
		.replace('/', '_')

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
		val dependencies = 
			(configurations.runtimeClasspath.get().files)
			.map { it.path }
		
		val dexRoot = File("$buildDir/dex/").also { it.mkdirs() }
		val dexCacheRoot = dexRoot.resolve("cache").also { it.mkdirs() }

		// read the dex cache map (path-to-hash)
		val dexCacheHashes = dexRoot.resolve("listing.txt")
			.takeIf { it.exists() }
			?.readText()
			?.lineSequence()
			?.map { it.split(" ") }
			?.filter { it.size == 2 }
			?.associate { it[0] to it[1] }
			.orEmpty()
			.toMutableMap()

		// calculate hashes for all dependencies
		val hashes = dependencies
			.associate {
				it to hash(File(it).readBytes())
			}

		// determime which dependencies can have their cached dex files reused and which can not
		val reusable = ArrayList<String>()
		val needReDex = HashMap<String, String>() // path-to-hash
		hashes.forEach { (path, hash) ->
			if (dexCacheHashes.getOrDefault(path, null) == hash) {
				reusable += path
			} else {
				needReDex[path] = hash
			}
		}

		println("${reusable.size} dependencies are already desugared and can be reused.")
		if (needReDex.isNotEmpty()) println("Desugaring ${needReDex.size} dependencies.")

		// for every non-reusable dependency, invoke d8 and save the new hash
		var index = 1
		needReDex.forEach { (dependency, hash) ->
			println("Processing ${index++}/${needReDex.size} ($dependency)")

			val outputDir = dexCacheRoot.resolve(hash(dependency.toByteArray())).also { it.mkdir() }
			exec {
				errorOutput = OutputStream.nullOutputStream()
				commandLine(
					"d8",
					"--intermediate",
					"--classpath", "${platformRoot.absolutePath}/android.jar",
					"--min-api", "14", 
					"--output", outputDir.absolutePath + "/",
					dependency
				)
			}
			println()
			dexCacheHashes[dependency] = hash
		}

		// write the updated hash map to the file
		dexCacheHashes.asSequence()
			.map { (k, v) -> "$k $v" }
			.joinToString("\n")
			.let { dexRoot.resolve("listing.txt").writeText(it) }

		if (needReDex.isNotEmpty()) println("Done.")
		println("Preparing to desugar the project and merge dex files.")

		val dexPathes = dependencies.map { 
			dexCacheRoot.resolve(hash(it.toByteArray())).also { it.mkdir() }
		}
		// assemble the list of classpath arguments for project dexing
		val dependenciesStr = Array<String>(dependencies.size * 2) {
			if (it % 2 == 0) "--classpath" else dexPathes[it / 2].absolutePath
		}
		
		// now, dex the project
		exec {
			val output = dexCacheRoot.resolve("project").also { it.mkdirs() }
			commandLine(
				"d8", 
				*dependenciesStr,
				"--classpath", "${platformRoot.absolutePath}/android.jar",
				"--min-api", "14",
				"--output", "$output",
				"$buildDir/libs/$jarName.jar"
			)
		}

		// finally, merge all dex files
		exec {
			val depDexes = dexPathes
				.map { it.resolve("classes.dex") }.toTypedArray()
				.filter { it.exists() } // some are empty
				.map { it.absolutePath }
				.toTypedArray()

			commandLine(
				"d8",
				*depDexes,
				dexCacheRoot.resolve("project/classes.dex").absolutePath,
				"--output", "$buildDir/libs/$jarName-android.jar"
			)
		}
	}
}

/** Merges the desktop and android jar files into a multiplatform jar file */
task<Jar>("release") {
	dependsOn("jarAndroid")
	
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("$jarName-any-platform.jar")

	from(
		zipTree("$buildDir/libs/${jarName}.jar"),
		zipTree("$buildDir/libs/${jarName}-android.jar")
	)

	from(*configurations.runtimeClasspath.get().files.map { if (it.isDirectory()) it else zipTree(it) }.toTypedArray())

	doLast {
		delete { delete("$buildDir/libs/${jarName}-android.jar") }
	}
}

val generateResources by tasks.registering {
	val outputFile = generatedCodeFolder.resolve("R.kt")

	inputs.dir(spritesFolder)
	outputs.file(outputFile)

	doLast {
		val sprites = spritesFolder.walkTopDown()
			.filter { it.isFile }
			.filter { it.extension == "png" }
			.map { it.nameWithoutExtension }
			.map { name ->
				// Trasnform kebab-case and snake_case into camelCase
				buildString {
					var isCapital = false
					name.forEach {
						if (it == '-' || it == '_' || it == ' ') {
							isCapital = true
						} else {
							append(if (isCapital) it.uppercase() else it.lowercase())
							isCapital = false
						}
					}
				} to "minchat-$name"
			}

		@Language("kotlin")
		val result = """
			|package io.minchat.client
			|
			|import arc.Core
			|import arc.graphics.g2d.TextureRegion
			|import arc.scene.style.TextureRegionDrawable
			|import arc.scene.ui.layout.Scl
			|
			|object R {
			|	${sprites.joinToString("\n|\t") {
					"val ${it.first} by lazy { Core.atlas.find(\"${it.second}\", \"error\").let(::makeDrawable) }"
				}}
			|
			|       private fun makeDrawable(region: TextureRegion) = object : TextureRegionDrawable(region) {
			|               // With normal ui scaling sprites should be 32px, but I use 48px, hence 32/48
			|               override fun imageSize() = super.imageSize() * Scl.scl(32f / 48f)
			|       }
			|}
		""".trimMargin()

		outputFile.writeText(result)
	}
}

tasks.withType<KotlinCompile> {
	dependsOn(generateResources)
}

tasks.jar {
	duplicatesStrategy = DuplicatesStrategy.EXCLUDE
	archiveFileName.set("${jarName}.jar")

	dependsOn(generateResources)

	from(rootDir) {
		include("mod.hjson")
		include("icon.png")
	}

	from("assets/") {
		include("**")
	}
}
