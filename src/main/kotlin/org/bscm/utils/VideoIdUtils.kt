package org.bscm.utils

/**
 * Normalizes YouTube references to raw video IDs (`catalog_items.preview_video_id`).
 *
 * The API and `bscm.json` manifests store the 11-char video ID only — never a
 * full URL — so old bundles and DB rows stay portable. Dashboard clients send
 * clean IDs, but lenient entry points (Discord `/publish gameplay_url`,
 * website overrides) may hand us full watch / Shorts / embed / `youtu.be` /
 * `music.youtube.com` URLs. This extractor accepts either form and returns the
 * ID, or null when nothing usable can be derived (video is always optional and
 * must never break a publish).
 */
object VideoIdUtils {
    private val watchParam = Regex("[?&]v=([0-9A-Za-z_-]{11})")
    private val shortLink = Regex("youtu\\.be/([0-9A-Za-z_-]{11})")
    private val embedPath = Regex("/(?:embed|shorts|live)/([0-9A-Za-z_-]{11})")
    private val rawId = Regex("^[0-9A-Za-z_-]{11}$")

    fun extractYoutubeId(input: String?): String? {
        val value = input?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        if (rawId.matches(value)) return value
        watchParam.find(value)?.let { return it.groupValues[1] }
        shortLink.find(value)?.let { return it.groupValues[1] }
        embedPath.find(value)?.let { return it.groupValues[1] }
        return null
    }
}
