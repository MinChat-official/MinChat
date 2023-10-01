package io.minchat.common

import kotlinx.serialization.Serializable

/**
 * The MinChat version this artifact was built for.
 *
 * Each Minchat artifact contains this property,
 * which it can use to check for compatibility with other
 * artifacts.
 */
val MINCHAT_VERSION = BuildVersion(
	major = 0,
	minor = 6,
	patch = 0
)

/**
 * Represents the MinChat version an artifact was built for.
 *
 * This class can be used to check compatibility between two
 * MinChat applications. For example, MinChat clients should
 * request a MinChat build version from the server they connect to
 * before trying to interact with it. If it differs significantly
 * from the build version of the client, it shouldn't try to
 * interact with the server to avoid possible conflicts.
 *
 * @param major The major version of this build. If this number differs between
 * two applications, they're absolutely incompatible.
 * @param minor The minor version. Applications differing in this number can
 * interact with each other in most cases.
 * @param patch The patch version. Differences in this number can be ignored.
 */
@Serializable
data class BuildVersion(
	val major: Int,
	val minor: Int,
	val patch: Int
) : Comparable<BuildVersion> {
	/** Checks if the major versions match. */
	fun isCompatibleWith(other: BuildVersion) =
		major == other.major
	
	/** Checks if the major and minor versions match. */
	fun isInterchangeableWith(other: BuildVersion) =
		isCompatibleWith(other) && minor == other.minor
	
	override fun compareTo(other: BuildVersion) = when {
		major != other.major -> major - other.major
		minor != other.minor -> minor - other.minor
		else -> patch - other.patch
	}

	override fun toString() =
		"$major.$minor.$patch"

	companion object {
		/**
		 * Creates a [BuildVersion] from a string in the form of "major.minor.patch".
		 * Returns null if its invalid.
		 *
		 * A version is considered valid if it contains from 2 to 3 components
		 * separated with dots (e.g. "1.2.3"), all of which are positive integers.
		 */
		fun fromStringOrNull(version: String): BuildVersion? {
			val parts = version.split('.')

			if (parts.size !in 2..3) return null

			val major = parts[0].toIntOrNull() ?: return null
			val minor = parts[1].toIntOrNull() ?: return null
			val patch = parts.getOrNull(2)?.let {
				// Only return null if it's present but invalid
				it.toIntOrNull() ?: return null
			} ?: 0

			if (major < 0 || minor < 0 || patch < 0) return null

			return BuildVersion(major, minor, patch)
		}

		/**
		 * Creates a [BuildVersion] from a string in the form of "major.minor.patch".
		 * @throws IllegalArgumentException if the version is invalid.
		 */
		fun fromString(version: String) =
			fromStringOrNull(version) ?: throw IllegalArgumentException("Invalid version string: $version")
	}
}
