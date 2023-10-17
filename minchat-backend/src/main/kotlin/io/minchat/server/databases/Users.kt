package io.minchat.server.databases

import io.minchat.common.entity.User
import io.minchat.common.entity.User.RoleBitSet
import io.minchat.server.util.notFound
import org.jetbrains.exposed.sql.*
import org.mindrot.jbcrypt.BCrypt
import java.math.BigInteger
import java.security.MessageDigest
import kotlin.random.Random

object Users : MinchatEntityTable<User>() {
	val username = varchar("name", User.nameLength.endInclusive)
	val nickname = varchar("nickname", User.nameLength.endInclusive).nullable().default(null)

	val passwordHash = varchar("password", 80)
	val token = varchar("token", 64)
	/** The [RoleBitSet] of the user. */
	val role = long("role").default(RoleBitSet.REGULAR_USER.bits)

	val discriminator = integer("discriminator")

	/** If not negative, the user is banned and [banReason] signifies the reason for the ban. */
	val bannedUntil = long("banned-until").default(-1)
	val banReason = varchar("ban-reason", User.Punishment.reasonLength.last).nullable().default(null)

	/** If not negative, the user is muted and [muteReason] signifies the reason for the mute. */
	val mutedUntil = long("muted-until").default(-1)
	val muteReason = varchar("mute-reason", User.Punishment.reasonLength.last).nullable().default(null)

	val isDeleted = bool("deleted").default(false)

	/**
	 * Unused: unreliable and hard to manage.
	 * Will (or will not) be used in the future to prevent users from creating too many accounts.
	 *
	 * If you're reading this source code as a user, no. We do not store your ip. We may store its hash,
	 * but we will never store it in the raw form.
	 */
	val lastIpHash = varchar("last-ip", 256 / 8).nullable().default(null)

	val messageCount = integer("message-count").default(0)
	val lastMessageTimestamp = long("last-sent").default(0L)

	val creationTimestamp = long("created-at")
	val lastLoginTimestamp = long("last-login")

	override fun getRawByIdOrNull(id: Long) =
		super.getRawByIdOrNull(id)?.takeIf { !it[isDeleted] }

	/** Gets a user by id or returns a placeholder value. */
	fun getByIdOrPlaceholder(id: Long) =
		getByIdOrNull(id) ?: User.deletedUser

	override fun createEntity(row: ResultRow) =
		User(
			id = row[id].value,
			username = row[username],
			nickname = row[nickname],
			discriminator = row[discriminator],
			role = RoleBitSet(row[role]),
			isAdmin = RoleBitSet(row[role]).isAdmin,

			ban = if (row[bannedUntil] >= 0) User.Punishment(
				expiresAt = row[bannedUntil],
				reason = row[banReason]
			) else null,

			mute = if (row[mutedUntil] >= 0) User.Punishment(
				expiresAt = row[mutedUntil],
				reason = row[muteReason]
			) else null,

			messageCount = row[messageCount],
			lastMessageTimestamp = row[lastMessageTimestamp],

			creationTimestamp = row[creationTimestamp]
		)


	fun getRawByTokenOrNull(token: String) =
		select { Users.token eq token }.firstOrNull()

	fun getRawByToken(token: String) =
		getRawByTokenOrNull(token) ?: notFound("the provided token is not associated with any user.")

	fun getByTokenOrNull(token: String) =
		getRawByTokenOrNull(token)?.let(::createEntity)

	fun getByToken(token: String) =
		getByTokenOrNull(token) ?: notFound("the provided token is not associated with any user.")

	/** Returns true if the user with the provided token is an admin; false otherwise. */
	fun isAdminToken(token: String) =
		select { Users.token eq token }.firstOrNull()?.get(role)?.let(::RoleBitSet)?.isAdmin ?: false

	/**
	 * Registers a new user.
	 *
	 * This function does not check if the name is already taken.
	 *
	 * @param nickname the nickname of the user, or null if they did not choose one.
	 */
	fun register(
		name: String,
		nickname: String?,
		passwordHash: String,
		role: RoleBitSet
	): ResultRow {
		val salt = BCrypt.gensalt(12)
		val hashedHash = BCrypt.hashpw(passwordHash, salt)

		lateinit var userToken: String

		// generate a token; repeat if the token is already used (this should never happen normally)
		var attempt = 0
		do {
			val input = hashedHash + attempt + name + System.nanoTime() + System.currentTimeMillis()
			userToken = MessageDigest.getInstance("SHA-256")
				.digest(input.toByteArray())
				.let { BigInteger(1, it) }
				.toString(32)
				.padStart(256 / 5 + 1, '0')

			++attempt
		} while (select { Users.token eq userToken }.empty().not())

		// create a new user and get the created row
		val userRow = Users.insert {
			it[username] = name
			it[Users.nickname] = nickname
			it[Users.passwordHash] = hashedHash
			it[token] = userToken

			it[discriminator] = nextDiscriminator(nickname ?: name)
			it[Users.role] = role.value

			it[creationTimestamp] = System.currentTimeMillis()
			it[lastLoginTimestamp] = System.currentTimeMillis()
		}.resultedValues!!.first()

		return userRow
	}

	/** Returns a random unique discriminator for the provided nickname. */
	fun nextDiscriminator(forNickname: String): Int {
		val taken = Users.select { username.lowerCase() eq forNickname.lowercase() }
			.map { it[discriminator] }

		if (taken.size >= 100) {
			error("The nickname $forNickname is too popular.")
		}

		val random = Random(System.currentTimeMillis())
		repeat(100) {
			val discriminator = random.nextInt(1, 10000)
			if (discriminator !in taken) {
				return discriminator
			}
		}
		// Fall back to a simpler approach after too many attempts
		for (i in 1..<10000) {
			if (i !in taken) return i
		}
		error("unreachable")
	}
}
