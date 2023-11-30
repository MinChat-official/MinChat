package io.minchat.server.modules

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import io.ktor.utils.io.*
import io.minchat.common.*
import io.minchat.common.Route
import io.minchat.common.entity.User
import io.minchat.common.event.UserModifyEvent
import io.minchat.common.request.*
import io.minchat.server.ServerContext
import io.minchat.server.databases.Users
import io.minchat.server.util.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.io.encoding.*
import kotlin.math.min

class UserModule : AbstractMinchatServerModule() {
	lateinit var authModule: AuthModule

	val avatarDir by lazy { server.dataDir.resolve("avatars").also { it.mkdir() } }

	override fun Application.onLoad() {
		routing {
			get(Route.User.fetch) {
				val id = call.parameters.getOrFail<Long>("id")

				newSuspendedTransaction {
					val user = Users.getByIdOrPlaceholder(id)
						.checkAndUpdateUserPunishments(checkMute = false, checkBan = false)

					call.respond(user)
				}
			}

			post(Route.User.edit) {
				val id = call.parameters.getOrFail<Long>("id")
				val data = call.receive<UserModifyRequest>()
				val token = call.token()

				val newNickname = data.newNickname?.nameConvention()

				newSuspendedTransaction {
					val requestedBy = Users.getByToken(token)
					requestedBy.checkAndUpdateUserPunishments(checkMute = false)
					authModule.validate(null, newNickname)
					val oldUser = Users.getById(id)

					require(requestedBy.canEditUser(oldUser)) {
						"You are not allowed to edit this user."
					}

					Users.update({ Users.id eq id }) { row ->
						newNickname?.let {
							row[Users.nickname] = it
							// generate a new unique discriminator on name change
							row[discriminator] = Users.nextDiscriminator(it)
						}
					}.throwIfNotFound { "user with the provided id-token pair does not exist." }

					val newUser = Users.getById(id)

					call.respond(newUser)
					logger.info { "${oldUser.loggable()} was edited by ${requestedBy.loggable()}. Now: ${newUser.loggable()}" }
					server.sendEvent(UserModifyEvent(newUser))
				}
			}

			post(Route.User.delete) {
				val id = call.parameters.getOrFail<Long>("id")
				call.receive<UserDeleteRequest>() // unused
				val token = call.token()

				transaction {
					val invokingUser = Users.getByToken(token)
					val oldUser = Users.getById(id)

					require(invokingUser.canDeleteUser(oldUser)) {
						"You are not allowed to delete this user."
					}

					Users.update({ Users.id eq id }) {
						it[username] = Constants.deletedAccountName
						it[Users.token] = "" // token() will fail if an empty string is provided
						it[passwordHash] = Constants.deletedAccountPasswordHash

						it[discriminator] = 0
						it[Users.isDeleted] = true
					}.throwIfNotFound { "user with the provide id-token pair does not exist." }

					logger.info { "${oldUser.loggable()} was deleted by ${invokingUser.loggable()}" }
				}
				call.response.statusOk()

				// getById would throw an exception since the user is already deleted
				server.sendEvent(UserModifyEvent(
					Users.select { Users.id eq id }.single().let(Users::createEntity)
				))
			}

			post(Route.User.modifyPunishments) {
				val id = call.parameters.getOrFail<Long>("id")
				val request = call.receive<UserPunishmentsModifyRequest>()
				val token = call.token()

				newSuspendedTransaction {
					val caller = Users.getByToken(token)
					val user = Users.getById(id)

					require(caller.canModifyUserPunishments(user)) {
						"You are not allowed to modify this user's punishments."
					}

					Users.update({ Users.id eq id }) {
						it[bannedUntil] = request.newBan?.expiresAt ?: -1
						it[banReason] = request.newBan?.reason
						it[mutedUntil] = request.newMute?.expiresAt ?: -1
						it[muteReason] = request.newMute?.reason
					} // hopefully no need to validate

					logger.info { "${user.loggable()}'s punishments were modified by ${caller.loggable()}" }

					val newUser = Users.getById(id)
					call.respond(newUser)
					server.sendEvent(UserModifyEvent(newUser))
				}
			}

			get(Route.User.getImageAvatar) {
				val userId = call.parameters.getOrFail<Long>("id")
				val full = call.request.queryParameters.get("full")?.toBoolean() ?: false

				val targetFile = getUserAvatarFile(userId, full).takeIf { it.exists() && it.isFile } ?: run {
					notFound("Provided user either has no avatar or has an icon avatar.")
				}

				if (targetFile.length() > User.Avatar.maxUploadSize) {
					call.response.status(HttpStatusCode(500, "User avatar too large (did file size limit decrease?)"))
				}

				call.respondFile(targetFile)
			}

			post(Route.User.setIconAvatar) {
				val userId = call.parameters.getOrFail<Long>("id")
				val data = call.receive<IconAvatarSetRequest>()

				val iconName = data.iconName
				if (iconName != null) {
					if (iconName.length !in 3..127) {
						illegalInput("Malformed icon name.")
					} else if (iconName.endsWith("-full")) {
						illegalInput("Cannot use full-scale icons as avatars. Use a -ui variant instead.")
					}
				}

				lateinit var invokingUser: User
				lateinit var targetUser: User
				newSuspendedTransaction {
					invokingUser = Users.getByToken(call.token())
					invokingUser.checkAndUpdateUserPunishments(checkMute = false)

					if (invokingUser.id == userId) {
						targetUser = invokingUser
					} else {
						targetUser = Users.getById(userId)
						if (!invokingUser.canEditUser(targetUser)) {
							accessDenied("You are not allowed to edit this user.")
						}
					}

					Users.update({ Users.id eq targetUser.id }) {
						it[avatarIconName] = data.iconName
						it[avatarType] = data.iconName?.let { User.Avatar.Type.ICON } // null for null icons
						it[avatarHash] = null
						it[avatarWidth] = 0
						it[avatarHeight] = 0
					}

					val updatedUser = Users.getById(targetUser.id)
					call.respond(updatedUser)
					server.sendEvent(UserModifyEvent(updatedUser))

					logger.info { "${targetUser.loggable()}'s icon avatar was set to ${data.iconName} by ${invokingUser.loggable()}" }
				}

			}

			post(Route.User.uploadImageAvatar) {
				val userId = call.parameters.getOrFail<Long>("id")
				val contentLength = call.request.contentLength()
				                    ?: illegalInput("Must specify content length.")

				if (contentLength > User.Avatar.maxUploadSize) {
					illegalInput("User avatar too large. Limit: ${User.Avatar.maxUploadSize / 1024} kb.")
				}

				// Permission validation
				lateinit var invokingUser: User
				lateinit var targetUser: User
				transaction {
					invokingUser = Users.getByToken(call.token())
					invokingUser.checkAndUpdateUserPunishments(checkMute = false)

					if (invokingUser.id == userId) {
						targetUser = invokingUser
					} else {
						targetUser = Users.getById(userId)
						if (!invokingUser.canEditUser(targetUser)) {
							accessDenied("You are not allowed to edit this user.")
						}
					}
				}

				// Upload
				val readChannel = call.receive<ByteReadChannel>()
				val buffer = ByteArray(contentLength.toInt())

				logger.lifecycle { "Avatar upload began for user ${targetUser.loggable()}." }

				withContext(Dispatchers.IO) {
					readChannel.readFully(buffer)
					getUserAvatarFile(userId, true).writeBytes(buffer)
				}

				// Scaling
				val image = ImageIO.read(ByteArrayInputStream(buffer))
				val width = image.width
				val height = image.height

				val scaledImage = if (width > User.Avatar.maxWidth || height > User.Avatar.maxHeight) {
					val scaleX = User.Avatar.maxWidth / width.toFloat()
					val scaleY = User.Avatar.maxHeight / height.toFloat()
					val scale = min(scaleX, scaleY)

					val scaled = BufferedImage((width * scale).toInt(), (height * scale).toInt(), BufferedImage.TYPE_INT_ARGB)
					val graphics = scaled.createGraphics()

					graphics.drawImage(image.getScaledInstance(
						(width * scale).toInt(),
						(height * scale).toInt(),
						BufferedImage.SCALE_SMOOTH
					), 0, 0, null)

					scaled
				} else {
					image // no scaling
				}

				withContext(Dispatchers.IO) {
					ImageIO.write(scaledImage, "png", getUserAvatarFile(userId, false))
				}

				@OptIn(ExperimentalEncodingApi::class)
				val hash = MessageDigest.getInstance("sha-256")
					.digest(buffer)
					.let { Base64.encode(it) }

				transaction {
					Users.update({ Users.id eq userId }) {
						it[avatarHash] = hash
						it[avatarWidth] = width
						it[avatarHeight] = height
						it[avatarType] = User.Avatar.Type.IMAGE
					}
				}

				logger.info { "Avatar update was performed for user ${targetUser.loggable()} by ${invokingUser.loggable()}." }

				val updatedUser = Users.getById(userId)
				call.respond(updatedUser)
				server.sendEvent(UserModifyEvent(updatedUser))
			}
		}
	}

	override suspend fun ServerContext.afterLoad() {
		authModule = module<AuthModule>() ?: error("User module requires auth module to be loaded.")
	}

	fun getUserAvatarFile(id: Long, fullSize: Boolean = false) = run {
		val suffix = when {
			fullSize -> "full"
			else -> "scaled"
		}

		avatarDir.resolve("avatar-$id-$suffix.png")
			.also {
				if (it.isDirectory) it.deleteRecursively()
			}
	}
}
