package org.bscm.backup

import io.ktor.util.logging.*
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.bscm.services.track.clients.jsonClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

private val log = KtorSimpleLogger("WorkshopBackupExporter")

object WorkshopBackupExporter {
    fun write(outputPath: Path, backupPackage: WorkshopBackupPackage) {
        if (outputPath.toString().endsWith(".zip", ignoreCase = true)) {
            writeZip(outputPath, backupPackage)
        } else {
            writeFolder(outputPath, backupPackage)
        }
    }

    private fun writeFolder(outputPath: Path, backupPackage: WorkshopBackupPackage) {
        Files.createDirectories(outputPath)

        backupPackage.files.forEach { file ->
            val target = outputPath.resolve(file.relativePath)
            val parent = target.parent
            if (parent != null) {
                Files.createDirectories(parent)
            }
            Files.write(target, file.bytes)
        }

        log.info("Workshop backup written to folder: $outputPath")
    }

    private fun writeZip(outputPath: Path, backupPackage: WorkshopBackupPackage) {
        val parent = outputPath.parent ?: outputPath.toAbsolutePath().parent
        if (parent != null) {
            Files.createDirectories(parent)
        }

        Files.newOutputStream(outputPath).use { outputStream ->
            ZipArchiveOutputStream(outputStream).use { zipOut ->
                backupPackage.files.forEach { file ->
                    val entryName = file.relativePath.replace('\\', '/')
                    val entry = ZipArchiveEntry(entryName)
                    entry.size = file.bytes.size.toLong()
                    zipOut.putArchiveEntry(entry)
                    zipOut.write(file.bytes)
                    zipOut.closeArchiveEntry()
                }
            }
        }

        log.info("Workshop backup written to zip: $outputPath")
    }
}

fun buildManifestFile(manifest: WorkshopBackupManifest): WorkshopBackupFile =
    WorkshopBackupFile(
        relativePath = "manifest.json",
        bytes = jsonClient.encodeToString(WorkshopBackupManifest.serializer(), manifest).toByteArray(StandardCharsets.UTF_8),
    )


