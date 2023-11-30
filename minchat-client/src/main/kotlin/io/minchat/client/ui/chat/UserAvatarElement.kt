package io.minchat.client.ui.chat

import arc.Core
import arc.scene.style.TextureRegionDrawable
import com.github.mnemotechnician.mkui.extensions.elements.updateLast
import io.minchat.client.Minchat
import io.minchat.client.ui.AsyncImage
import io.minchat.common.entity.User
import kotlinx.coroutines.CoroutineScope

class UserAvatarElement(
	userId: Long?,
	avatar: User.Avatar?,
	val full: Boolean,
	parentScope: CoroutineScope
) : AsyncImage(parentScope) {
	var userId = userId
		set(value) {
			field = value
			updateAvatar()
		}
	var avatar = avatar
		set(value) {
			field = value
			updateAvatar()
		}

	init {
		updateAvatar()
	}

	fun updateAvatar() {
		val avatar = avatar ?: User.Avatar.defaultAvatar

		when (avatar) {
			is User.Avatar.IconAvatar -> {
				setDrawableAsync(avatar.iconName) {
					val fullIconName = avatar.iconName.removeSuffix("-ui") + "-full"
					val region = Core.atlas.find(fullIconName).takeIf { Core.atlas.isFound(it) }
					             ?: Core.atlas.find(avatar.iconName).takeIf { Core.atlas.isFound(it) }
					             ?: error("No region found for icon avatar ${avatar.iconName}!")

					TextureRegionDrawable(region)
				}
			}

			is User.Avatar.ImageAvatar, is User.Avatar.LocalAvatar -> {
				setFileAsync {
					val userId = userId ?: error("No user ID set!")

					Minchat.client.getCacheableAvatar(userId, avatar, full) {}
						?: error("No avatar found for user ${userId} and avatar ${avatar}")
				}
			}
		}
	}
}

fun CoroutineScope.UserAvatarElement(
	userId: Long,
	avatar: User.Avatar?,
	full: Boolean
) = UserAvatarElement(userId, avatar, full, this)

fun CoroutineScope.UserAvatarElement(
	userId: () -> Long?,
	avatar: () -> User.Avatar?,
	full: Boolean
) = UserAvatarElement(userId(), avatar(), false, this).also {
	it.updateLast {
		val newId = userId()
		val newAvatar = avatar()

		if (newId != it.userId || newAvatar != it.avatar) {
			it.userId = newId
			it.avatar = newAvatar
		}
	}
}
