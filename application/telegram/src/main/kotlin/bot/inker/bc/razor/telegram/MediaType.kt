package bot.inker.bc.razor.telegram

sealed class MediaType(val label: String, val defaultExtension: String) {
    data object Photo : MediaType("Image", "jpg")
    data object Sticker : MediaType("Sticker", "webp")
    data object AnimatedSticker : MediaType("Sticker", "tgs")
    data object VideoSticker : MediaType("Sticker", "webm")
    data object Animation : MediaType("GIF", "mp4")
    data object Document : MediaType("File", "bin")
    data object Video : MediaType("Video", "mp4")
    data object Voice : MediaType("Voice", "ogg")
    data object Audio : MediaType("Audio", "mp3")
    data object VideoNote : MediaType("VideoNote", "mp4")
}
