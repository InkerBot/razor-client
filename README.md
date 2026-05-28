# Razor Client

Razor Client 是一个用于连接 Bondage Club Socket.IO 游戏服务器的 Kotlin/JVM 客户端库，同时包含若干可直接运行的应用。它在 JVM 上重新实现了浏览器客户端的网络层，支持登录、房间操作、聊天、角色同步、账号更新和本地化资源。

## 构建与测试

运行全部测试：

```bash
./gradlew test
```

构建全部模块：

```bash
./gradlew build
```

i18n 资源生成插件会在生成资源前更新本地的 Bondage Club 参考代码仓库。如果你只想进行本地构建，不希望执行更新任务，可以使用：

```bash
./gradlew build -x updateBondageClub
./gradlew test -x updateBondageClub
```

## 模块

| 模块 | 说明 |
| --- | --- |
| `:` | 核心 Kotlin 客户端库，以及协议和状态模型。 |
| `:application:tui` | 终端 UI 应用。 |
| `:application:telegram` | Telegram 桥接应用。 |

## 客户端库用法

使用默认 Socket.IO transport 创建客户端：

```kotlin
import bot.inker.bc.razor.RazorClient
import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.protocol.auth.LoginResult

val client = RazorClient.builder()
    .serverUrl("https://bondage-club-server.herokuapp.com/")
    .transport()
    .header("Origin", "https://www.bondageprojects.elementfx.com")
    .header("Referer", "https://www.bondageprojects.elementfx.com/R124/BondageClub/")
    .buildTransport()
    .on<RazorEvent.ChatMessage> { event ->
        println("${event.message.sender}: ${event.message.content}")
    }
    .build()

client.connect()

client.login("account-name", "password").thenAccept { result ->
    when (result) {
        is LoginResult.Success -> client.room.join("RoomName")
        is LoginResult.Error -> println("Login failed: ${result.message}")
    }
}
```

客户端不再使用时，请关闭连接并释放资源：

```kotlin
client.close()
```

## Transport 配置

Socket.IO transport 支持通过 builder 配置连接参数：

```kotlin
val client = RazorClient.builder()
    .transport()
    .proxy("127.0.0.1", 1080)
    .proxyAuth("username", "password")
    .reconnection(true)
    .reconnectionAttempts(50)
    .timeout(30_000)
    .buildTransport()
    .build()
```

支持的配置项包括：

- `header.<Name>`：自定义 HTTP 请求头。
- `proxy.host`、`proxy.port`、`proxy.username`、`proxy.password`：SOCKS5 代理配置。
- `reconnection`、`forceNew`、`reconnectionAttempts`、`reconnectionDelay`、`reconnectionDelayMax`：重连相关配置。
- `timeout`、`path`、`query`：连接超时、Socket.IO 路径和查询参数。

## 终端 UI

运行终端 UI：

```bash
./gradlew :application:tui:bootRun
```

TUI 会在当前工作目录读取和写入 `razor-tui.json`。常用字段示例：

```json
{
  "serverUrl": "https://bondage-club-server.herokuapp.com/",
  "origin": "https://www.bondageprojects.elementfx.com",
  "referer": "https://www.bondageprojects.elementfx.com/R124/BondageClub/",
  "proxyHost": "",
  "proxyPort": 0,
  "lastUsername": ""
}
```

## Telegram 桥接

运行 Telegram 桥接应用：

```bash
./gradlew :application:telegram:bootRun
```

桥接应用会在当前工作目录读取和写入 `razor-telegram.json`。最少需要配置以下字段：

```json
{
  "botToken": "",
  "chatId": 0,
  "accountName": "",
  "password": "",
  "roomName": "",
  "serverUrl": "https://bondage-club-server.herokuapp.com/"
}
```

如需启用媒体转发，可以配置：

```json
{
  "mediaServerEnabled": true,
  "mediaServerHost": "0.0.0.0",
  "mediaServerPort": 8090,
  "mediaBaseUrl": "http://localhost:8090",
  "mediaStoragePath": "media"
}
```

请妥善保管 `razor-telegram.json`，不要提交到公开仓库。该文件包含 Telegram bot token 和 Bondage Club 账号密码。

## 本地化资源

`bc-i18n` Gradle 插件会从本地 Bondage Club 参考代码仓库，以及 `docs/` 下可选的 mod 目录中生成 JSON 本地化资源。

常用任务：

```bash
./gradlew updateBondageClub
./gradlew generateI18n
```

默认情况下，生成的资源会写入 `build/generated/resources/i18n`，并加入主资源集。

## 开发说明

- 核心客户端事件通过 `RazorEvent` 和 `client.on<T>()` 暴露。
- 请求/响应类 API 返回 `CompletableFuture`。
- 账号更新会先进行防抖和批处理，再发送到服务器。
- Socket.IO transport 通过 `ServiceLoader` 按名称加载；使用默认 transport 时，运行时需要包含 `:transport:socketio`。
