package bot.inker.bc.razor.telegram

import org.slf4j.LoggerFactory
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.GetFile
import org.telegram.telegrambots.meta.api.objects.message.Message
import java.io.InputStream
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

data class DownloadedMedia(
    val localPath: Path,
    val publicUrl: String,
    val mediaType: MediaType,
    val caption: String?,
    val originalFilename: String?,
)

class TelegramMediaDownloader(
    private val config: TelegramConfig,
    private val telegramClient: OkHttpTelegramClient,
    private val mediaFileServer: MediaFileServer,
) {
    private val logger = LoggerFactory.getLogger(TelegramMediaDownloader::class.java)
    private val storagePath: Path = Path.of(config.mediaStoragePath).toAbsolutePath()

    fun extractAndDownload(message: Message): DownloadedMedia? {
        val (fileId, mediaType, originalFilename) = extractMediaInfo(message) ?: return null

        return try {
            val telegramFile = telegramClient.execute(GetFile(fileId))
            val filePath = telegramFile.filePath ?: return null

            val extension = originalFilename?.substringAfterLast('.', "")
                ?.takeIf { it.isNotEmpty() && it.length <= 10 }
                ?: mediaType.defaultExtension

            val localFilename = "${UUID.randomUUID()}.$extension"
            val localPath = storagePath.resolve(localFilename)

            Files.createDirectories(storagePath)
            downloadFile(filePath, localPath)

            val publicUrl = mediaFileServer.getPublicUrl(localFilename)
            val caption = message.caption

            DownloadedMedia(
                localPath = localPath,
                publicUrl = publicUrl,
                mediaType = mediaType,
                caption = caption,
                originalFilename = originalFilename,
            )
        } catch (e: Exception) {
            logger.error("Failed to download media (type={})", mediaType.label, e)
            null
        }
    }

    private data class MediaInfo(
        val fileId: String,
        val mediaType: MediaType,
        val originalFilename: String?,
    )

    private fun extractMediaInfo(message: Message): MediaInfo? {
        return when {
            message.hasPhoto() -> {
                val photo = message.photo.maxByOrNull { it.fileSize ?: 0 } ?: return null
                MediaInfo(photo.fileId, MediaType.Photo, null)
            }

            message.hasSticker() -> {
                val sticker = message.sticker
                val type = when {
                    sticker.isAnimated -> MediaType.AnimatedSticker
                    sticker.isVideo -> MediaType.VideoSticker
                    else -> MediaType.Sticker
                }
                MediaInfo(sticker.fileId, type, null)
            }

            message.hasAnimation() -> {
                val animation = message.animation
                MediaInfo(animation.fileId, MediaType.Animation, animation.fileName)
            }

            message.hasVideo() -> {
                val video = message.video
                MediaInfo(video.fileId, MediaType.Video, video.fileName)
            }

            message.hasVoice() -> {
                MediaInfo(message.voice.fileId, MediaType.Voice, null)
            }

            message.hasAudio() -> {
                val audio = message.audio
                MediaInfo(audio.fileId, MediaType.Audio, audio.fileName)
            }

            message.hasVideoNote() -> {
                MediaInfo(message.videoNote.fileId, MediaType.VideoNote, null)
            }

            message.hasDocument() -> {
                val document = message.document
                MediaInfo(document.fileId, MediaType.Document, document.fileName)
            }

            else -> null
        }
    }

    private fun downloadFile(filePath: String, destination: Path) {
        val url = "https://api.telegram.org/file/bot${config.botToken}/$filePath"
        URI(url).toURL().openStream().use { input: InputStream ->
            Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
