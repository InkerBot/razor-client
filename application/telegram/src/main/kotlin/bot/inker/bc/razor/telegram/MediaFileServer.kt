package bot.inker.bc.razor.telegram

import io.undertow.Undertow
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.RoutingHandler
import io.undertow.server.handlers.resource.PathResourceManager
import io.undertow.server.handlers.resource.ResourceHandler
import io.undertow.server.handlers.ResponseCodeHandler
import io.undertow.util.Headers
import io.undertow.util.HttpString
import io.undertow.util.Methods
import io.undertow.util.MimeMappings
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

class MediaFileServer(
    private val config: TelegramConfig,
) : AutoCloseable {
    private val logger = LoggerFactory.getLogger(MediaFileServer::class.java)
    private val storagePath: Path = Path.of(config.mediaStoragePath).toAbsolutePath()
    private var server: Undertow? = null

    fun start() {
        Files.createDirectories(storagePath)

        val mimeMappings = MimeMappings.builder()
            .addMapping("tgs", "application/gzip")
            .addMapping("webp", "image/webp")
            .addMapping("webm", "video/webm")
            .addMapping("mp4", "video/mp4")
            .addMapping("ogg", "audio/ogg")
            .addMapping("mp3", "audio/mpeg")
            .addMapping("jpg", "image/jpeg")
            .addMapping("jpeg", "image/jpeg")
            .addMapping("png", "image/png")
            .addMapping("gif", "image/gif")
            .build()

        val removeMediaPrefixHandler = object : HttpHandler {
            private val resourceHandler = ResourceHandler(
                PathResourceManager.builder()
                    .setBase(storagePath)
                    .build()
            ).setMimeMappings(mimeMappings).setDirectoryListingEnabled(false)

            override fun handleRequest(exchange: HttpServerExchange) {
                exchange.requestPath = exchange.requestPath.substring("/media".length)
                exchange.relativePath = exchange.relativePath.substring("/media".length)
                exchange.requestURI = exchange.requestURI.substring("/media".length)
                resourceHandler.handleRequest(exchange)
            }
        }

        val routingHandler = HttpHandler { exchange ->
            if (exchange.requestPath.startsWith("/media/")) {
                if (exchange.requestMethod == Methods.HEAD || exchange.requestMethod == Methods.GET) {
                    removeMediaPrefixHandler.handleRequest(exchange)
                } else {
                    ResponseCodeHandler.HANDLE_405.handleRequest(exchange)
                }
            } else {
                ResponseCodeHandler.HANDLE_404.handleRequest(exchange)
            }
        }

        val corsHandler = HttpHandler { exchange ->
            exchange.responseHeaders.put(HttpString.tryFromString("Access-Control-Allow-Origin"), "*")
            exchange.responseHeaders.put(HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, HEAD, OPTIONS")
            exchange.responseHeaders.put(HttpString.tryFromString("Access-Control-Allow-Headers"), "Content-Type")

            if (exchange.requestMethod == Methods.OPTIONS) {
                exchange.statusCode = 200
                exchange.endExchange()
            } else {
                routingHandler.handleRequest(exchange)
            }
        }

        server = Undertow.builder()
            .addHttpListener(config.mediaServerPort, config.mediaServerHost)
            .setHandler(corsHandler)
            .build()

        server!!.start()
        logger.info("Media file server started on {}:{}", config.mediaServerHost, config.mediaServerPort)
    }

    fun getPublicUrl(filename: String): String {
        val base = config.mediaBaseUrl.trimEnd('/')
        return "$base/media/$filename"
    }

    override fun close() {
        server?.stop()
        server = null
        logger.info("Media file server stopped")
    }
}
