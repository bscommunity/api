package org.bscm.services

import io.github.deficuet.unitykt.ImportContext
import io.github.deficuet.unitykt.UnityAssetManager
import io.github.deficuet.unitykt.classes.TextAsset
import io.github.deficuet.unitykt.firstObjectOf
import org.apache.commons.compress.archivers.zip.ZipFile
class DecodingService {
    companion object {
        fun extractBundleBytesFromZip(zipBytes: ByteArray, targetBundleName: String): ByteArray? {
            // Use Commons Compress to read from byte array
            ZipFile.Builder().setByteArray(zipBytes).get().use { zip ->
                val entries = zip.entries
                for (entry in entries) {
                    if (!entry.isDirectory && entry.name.equals(targetBundleName, ignoreCase = true)) {
                        zip.getInputStream(entry).use { input ->
                            return input.readBytes()
                        }
                    }
                }
            }
            return null
        }

        /**
         * Extract the text content of the `.bytes` file inside `chart.bundle` from the uploaded zip bytes.
         *
         * @param zipBytes The raw bytes of the uploaded .zip archive.
         * @param targetBundleName The name of the bundle inside zip (e.g., "chart.bundle").
         * @return The raw bytes of the extracted .bytes file, or null if not found.
         */
        fun extractChartFileFromBundle(
            zipBytes: ByteArray,
            targetBundleName: String = "chart.bundle",
        ): ByteArray? {
            // 1. Unzip in memory and locate the bundle bytes
            val bundleBytes = extractBundleBytesFromZip(zipBytes, targetBundleName)
                ?: return null  // Bundle not found

            println("Bundle '$targetBundleName' extracted, size=${bundleBytes.size} bytes.")

            // 2. Load the bundle via UnityKt
            UnityAssetManager.new().use { manager ->
                // Load the ByteArray bundle
                val context: ImportContext = manager.loadFromByteArray(bundleBytes, targetBundleName)

                // If there is no TextAsset object, an IndexOutOfBoundsException will be thrown.
                // Use the function firstOfOrNull<>() if the existence of the object is not guaranteed.
                // The data of this TextAsset object has not been read yet.
                val tex: TextAsset = context.objectMap.values.firstObjectOf<TextAsset>()
                println("TextAsset '${tex.mName}' found")

                // Write the .bytes data to Desktop for debugging
                val desktopPath = "C:\\Users\\Eduardo" + "\\Desktop\\" + tex.mName + ".bytes"
                java.io.File(desktopPath).writeBytes(tex.mScript)

                // 3. Read the .bytes data from the TextAsset
                return tex.mScript
            }
        }
    }
}