package org.bscm.models.enums

enum class MediaHost(val id: Int, val baseUrl: String) {
    DISCORD_AVATAR(0, "https://cdn.discordapp.com/avatars/"),
    DISCORD_ATTACHMENT(1, "https://cdn.discordapp.com/attachments/"),
    ITUNES(2, "https://is1-ssl.mzstatic.com/image/thumb/"),
    DEEZER(3, "https://cdn-images.dzcdn.net/images/cover/");

    companion object {
        fun fromId(id: Int) = entries.first { it.id == id }
    }
}