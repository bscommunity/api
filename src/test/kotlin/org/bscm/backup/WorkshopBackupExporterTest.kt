package org.bscm.backup

import org.bscm.services.track.clients.jsonClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkshopBackupExporterTest {
    @Test
    fun `writes folder backup structure`() {
        val outputDir = Files.createTempDirectory("workshop-backup-folder")
        try {
            val backupPackage = sampleBackupPackage()

            WorkshopBackupExporter.write(outputDir, backupPackage)

            val manifestPath = outputDir.resolve("manifest.json")
            val bundlePath = outputDir.resolve("messages/123/bundle.zip")
            val coverPath = outputDir.resolve("messages/123/cover.png")
            val infoPath = outputDir.resolve("messages/123/info.json")

            assertTrue(Files.exists(manifestPath))
            assertTrue(Files.exists(bundlePath))
            assertTrue(Files.exists(coverPath))
            assertTrue(Files.exists(infoPath))

            assertEquals("bundle-bytes", Files.readString(bundlePath, StandardCharsets.UTF_8))
            assertContentEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(coverPath))
            assertEquals(
                jsonClient.encodeToString(WorkshopBackupEntryInfo.serializer(), sampleEntry()),
                Files.readString(infoPath, StandardCharsets.UTF_8),
            )
        } finally {
            outputDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun `writes zip backup structure`() {
        val outputZip = Files.createTempFile("workshop-backup-archive", ".zip")
        try {
            val backupPackage = sampleBackupPackage()

            WorkshopBackupExporter.write(outputZip, backupPackage)

            ZipFile(outputZip.toFile()).use { zip ->
                assertTrue(zip.getEntry("manifest.json") != null)
                assertTrue(zip.getEntry("messages/123/bundle.zip") != null)
                assertTrue(zip.getEntry("messages/123/cover.png") != null)
                assertTrue(zip.getEntry("messages/123/info.json") != null)

                val bundleEntry = zip.getEntry("messages/123/bundle.zip")
                val bundleBytes = zip.getInputStream(bundleEntry).readBytes()
                assertEquals("bundle-bytes", String(bundleBytes, StandardCharsets.UTF_8))

                val infoEntry = zip.getEntry("messages/123/info.json")
                assertEquals(
                    jsonClient.encodeToString(WorkshopBackupEntryInfo.serializer(), sampleEntry()),
                    String(zip.getInputStream(infoEntry).readBytes(), StandardCharsets.UTF_8),
                )
            }
        } finally {
            Files.deleteIfExists(outputZip)
        }
    }

    private fun sampleBackupPackage(): WorkshopBackupPackage {
        val manifest = WorkshopBackupManifest(
            channelId = "workshop-channel",
            generatedAt = "2026-05-03T00:00:00Z",
            totalEntries = 1,
            entries = listOf(sampleEntry()),
        )

        return WorkshopBackupPackage(
            files = listOf(
                buildManifestFile(manifest),
                WorkshopBackupFile("messages/123/bundle.zip", "bundle-bytes".toByteArray(StandardCharsets.UTF_8)),
                WorkshopBackupFile("messages/123/cover.png", byteArrayOf(1, 2, 3)),
                WorkshopBackupFile("messages/123/info.json", jsonClient.encodeToString(WorkshopBackupEntryInfo.serializer(), sampleEntry()).toByteArray(StandardCharsets.UTF_8)),
            )
        )
    }

    private fun sampleEntry() = WorkshopBackupEntryInfo(
        messageId = "123",
        versionId = "456",
        bundleUrl = "https://cdn.discordapp.com/attachments/1/bundle.zip",
        coverUrl = "https://cdn.discordapp.com/attachments/1/cover.png",
        audioUrl = null,
        bundlePath = "messages/123/bundle.zip",
        coverPath = "messages/123/cover.png",
        bundleSizeBytes = 12,
        coverSizeBytes = 3,
        downloadedAt = "2026-05-03T00:00:00Z",
    )
}



