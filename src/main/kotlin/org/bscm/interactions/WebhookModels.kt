package org.bscm.interactions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
sealed class BaseAttachment

@Serializable
data class Attachment(
    val id: String,
    val filename: String,
    val url: String,
    @SerialName("proxy_url") val proxyUrl: String,
    val size: Int,
    val height: Int? = null,
    val width: Int? = null
) : BaseAttachment()

@Serializable
data class SimpleAttachment(
    val id: String,
    val filename: String,
) : BaseAttachment()

@Serializable
data class WebhookPayload(
    val username: String = "bscm",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val embeds: List<Embed> = emptyList(),
    val attachments: List<BaseAttachment> = emptyList(),
    val components: List<ActionRow> = emptyList(),
    val content: String? = null,
)

@Serializable
data class SimpleWebhookPayload(
    val attachments: List<SimpleAttachment>,
)

@Serializable
data class Embed(
    val title: String,
    val description: String? = null,
    val url: String? = null,
    val color: Int = 0,
    val thumbnail: Thumbnail = Thumbnail(""),
    val image: Image? = null,
    val author: Author? = null,
    val fields: List<EmbedField> = emptyList(),
    val footer: Footer? = null,
    val timestamp: String? = null
)

@Serializable
data class Thumbnail(val url: String)

@Serializable
data class Image(val url: String)

@Serializable
data class Author(val name: String, val url: String? = null)

@Serializable
data class EmbedField(
    val name: String,
    val value: String,
    val inline: Boolean
)

@Serializable
data class Footer(
    val text: String,
    @SerialName("icon_url") val iconUrl: String? = null
)

@Serializable
data class ActionRow(
    val type: Int,
    val components: List<Button>
)

@Serializable
data class Button(
    val type: Int,
    val style: Int,
    val label: String,
    val emoji: Emoji? = null,
    val url: String? = null,
)

@Serializable
data class Emoji(
    val id: String,
    val name: String,
    val animated: Boolean
)

// -------------------- MessageBuilder DSL --------------------
class MessageBuilder {
    private var username: String = "bscm"
    private var avatarUrl: String? = null
    private var content: String? = null
    private val embeds: MutableList<Embed> = mutableListOf()
    private val attachments: MutableList<BaseAttachment> = mutableListOf()
    private val components: MutableList<ActionRow> = mutableListOf()

    fun username(value: String) = apply { this.username = value }
    fun avatar(url: String?) = apply { this.avatarUrl = url }
    fun content(value: String?) = apply { this.content = value }

    fun embed(block: EmbedBuilder.() -> Unit) = apply {
        embeds += EmbedBuilder().apply(block).build()
    }

    fun attachment(att: BaseAttachment) = apply { attachments += att }
    fun attachments(list: Collection<BaseAttachment>) = apply { attachments += list }

    fun actionRow(block: ActionRowBuilder.() -> Unit) = apply {
        components += ActionRowBuilder().apply(block).build()
    }

    fun buttonRow(vararg buttons: Button) = apply {
        components += ActionRow(type = 1, components = buttons.toList())
    }

    fun component(row: ActionRow) = apply { components += row }

    fun build(): WebhookPayload = WebhookPayload(
        username = username,
        avatarUrl = avatarUrl,
        embeds = embeds.toList(),
        attachments = attachments.toList(),
        components = components.toList(),
        content = content,
    )
}

class EmbedBuilder {
    var title: String = ""
    var description: String? = null
    var url: String? = null
    var color: Int = 0
    private var thumbnailUrl: String? = null
    private var imageUrl: String? = null
    private var authorName: String? = null
    private var authorUrl: String? = null
    private val fields: MutableList<EmbedField> = mutableListOf()
    private var footerText: String? = null
    private var footerIcon: String? = null
    private var timestamp: String? = null

    fun thumbnail(url: String?) = apply { thumbnailUrl = url }
    fun image(url: String?) = apply { imageUrl = url }
    fun author(name: String, url: String? = null) = apply { this.authorName = name; this.authorUrl = url }
    fun field(name: String, value: String, inline: Boolean = false) = apply { fields += EmbedField(name, value, inline) }
    fun footer(text: String, iconUrl: String? = null) = apply { footerText = text; footerIcon = iconUrl }
    fun timestamp(iso: String = Instant.now().toString()) = apply { timestamp = iso }

    fun build(): Embed = Embed(
        title = title,
        description = description,
        url = url,
        color = color,
        thumbnail = Thumbnail(thumbnailUrl ?: ""),
        image = imageUrl?.let { Image(it) },
        author = authorName?.let { Author(it, authorUrl) },
        fields = fields.toList(),
        footer = footerText?.let { Footer(it, footerIcon) },
        timestamp = timestamp
    )
}

class ActionRowBuilder {
    private val buttons: MutableList<Button> = mutableListOf()
    fun button(button: Button) = apply { buttons += button }
    fun button(
        label: String,
        style: Int = 5,
        url: String? = null,
        emoji: Emoji? = null,
        type: Int = 2,
    ) = apply { buttons += Button(type = type, style = style, label = label, emoji = emoji, url = url) }

    fun build(): ActionRow = ActionRow(type = 1, components = buttons.toList())
}

fun message(block: MessageBuilder.() -> Unit): WebhookPayload = MessageBuilder().apply(block).build()

