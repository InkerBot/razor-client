# Telegram 桥接模块

`application:telegram` 是 Razor Client 的 Telegram 桥接应用。它会登录 Bondage Club 账号并加入指定房间，把 BC 聊天、悄悄话和 beep 转发到 Telegram，同时把 Telegram 消息转回 BC。

## 功能概览

- 自动连接 BC 服务器、登录账号并加入指定房间。
- 普通房间聊天转发到 Telegram 的 General 话题。
- BC whisper 和 beep 转发到独立的 `BEEP-<名字>-<成员编号>` 论坛话题。
- Telegram General 话题中的文本消息转发为 BC 房间聊天。
- Telegram BEEP 话题中的文本消息转发为对应成员的 BC beep。
- 支持 Telegram 图片、贴纸、视频、语音、音频和文件转发为公开媒体链接。
- 支持 BC 聊天中的图片 URL 自动作为 Telegram 图片发送。
- 支持断线重连和应用层 watchdog。
- 支持通过 Telegram 或 BC whisper 执行房间管理命令。

## 运行

从仓库根目录运行：

```bash
./gradlew :application:telegram:bootRun
```

应用启动后会读取当前工作目录下的 `razor-telegram.json`。如果配置缺失，启动会失败并提示需要填写的字段。

日志默认输出到控制台，同时写入：

- `logs/latest.log`
- `logs/debug.log`

## 最小配置

在运行目录创建 `razor-telegram.json`：

```json
{
  "botToken": "123456:telegram-bot-token",
  "chatId": -1001234567890,
  "accountName": "bc-account-name",
  "password": "bc-password",
  "roomName": "RoomName",
  "serverUrl": "https://bondage-club-server.herokuapp.com/",
  "origin": "https://www.bondageprojects.elementfx.com",
  "referer": "https://www.bondageprojects.elementfx.com/R124/BondageClub/"
}
```

`chatId` 通常是 Telegram 群组或超级群的 ID。建议使用启用了 Forum Topics 的超级群，这样每个 beep/whisper 都能分配到独立话题。

## 完整配置示例

```json
{
  "serverUrl": "https://bondage-club-server.herokuapp.com/",
  "origin": "https://www.bondageprojects.elementfx.com",
  "referer": "https://www.bondageprojects.elementfx.com/R124/BondageClub/",
  "proxyHost": "",
  "proxyPort": 0,
  "accountName": "",
  "password": "",
  "roomName": "",
  "room": {
    "description": "",
    "background": "MainHall",
    "limit": 10,
    "admin": [],
    "ban": [],
    "whitelist": [],
    "visibility": [],
    "access": [],
    "blockCategory": [],
    "language": "CN",
    "space": ""
  },
  "botToken": "",
  "chatId": 0,
  "generalTopicId": null,
  "socketIoReconnectionAttempts": 50,
  "socketIoReconnectionDelay": 1000,
  "socketIoReconnectionDelayMax": 30000,
  "watchdogDisconnectTimeoutMs": 120000,
  "watchdogMaxBackoffMs": 300000,
  "livenessTimeoutMs": 180000,
  "livenessCheckIntervalMs": 30000,
  "mediaServerEnabled": false,
  "mediaServerHost": "0.0.0.0",
  "mediaServerPort": 8090,
  "mediaBaseUrl": "http://localhost:8090",
  "mediaStoragePath": "media",
  "topicCachePath": "topic-cache.json"
}
```

## 话题规则

Telegram 到 BC：

- `generalTopicId` 为 `null` 时，没有 `message_thread_id` 的消息会被视为 General 话题消息。
- `generalTopicId` 有值时，只有对应 thread id 的消息会被视为 General 话题消息。
- General 话题文本会转发为 BC 房间聊天，格式为 `[Telegram用户名] 内容`。
- 非 General 话题会通过 `topic-cache.json` 查找对应成员，并转发为 BC beep。

BC 到 Telegram：

- 房间聊天、emote、activity、action 和 server message 会发送到 General 话题。
- whisper 和 beep 会发送到成员专属 BEEP 话题。
- BEEP 话题名格式为 `BEEP-<名字>-<成员编号>`。
- 话题映射会缓存到 `topic-cache.json`。

## 媒体转发

默认关闭媒体转发。启用方式：

```json
{
  "mediaServerEnabled": true,
  "mediaServerHost": "0.0.0.0",
  "mediaServerPort": 8090,
  "mediaBaseUrl": "https://example.com",
  "mediaStoragePath": "media"
}
```

启用后，Telegram 中的非文本消息会下载到 `mediaStoragePath`，并通过内置 Undertow 文件服务器暴露为：

```text
<mediaBaseUrl>/media/<filename>
```

支持的媒体类型包括：

- 图片
- 普通贴纸、动画贴纸和视频贴纸
- GIF/animation
- 视频
- 语音
- 音频
- 视频便签
- 文件

如果部署在公网，请确保 `mediaBaseUrl` 是 Telegram 和 BC 用户都能访问的地址，并注意媒体目录的访问权限和磁盘清理。

## 内置命令

命令前缀是 `代狼`。可以在 Telegram 中发送，也可以在 BC 中通过 whisper 发送给桥接账号。

| 命令 | 说明 |
| --- | --- |
| `代狼 帮助` | 显示帮助。 |
| `代狼 房间信息` | 显示当前房间信息。 |
| `代狼 保存房间` | 把当前房间配置保存到 `razor-telegram.json`。 |
| `代狼 白名单` | 查看白名单。 |
| `代狼 白名单 添加 <编号>` | 添加白名单成员。 |
| `代狼 白名单 移除 <编号>` | 移除白名单成员。 |
| `代狼 管理员` | 查看管理员列表。 |
| `代狼 管理员 添加 <编号>` | 提升成员为管理员。 |
| `代狼 管理员 移除 <编号>` | 撤销成员管理员。 |
| `代狼 封禁` | 查看封禁列表。 |
| `代狼 封禁 添加 <编号>` | 封禁成员。 |
| `代狼 封禁 移除 <编号>` | 解封成员。 |
| `代狼 踢出 <编号>` | 踢出成员。 |

## 重连策略

桥接应用有三层连接保护：

- Socket.IO 内置重连，由 `socketIoReconnectionAttempts`、`socketIoReconnectionDelay`、`socketIoReconnectionDelayMax` 控制。
- 应用层 watchdog，如果断线超过 `watchdogDisconnectTimeoutMs`，会重建客户端并重新登录。
- liveness 检查，如果超过 `livenessTimeoutMs` 未收到 `ServerInfo`，会触发完整重连。

如果服务器返回重复登录或限流类强制断开，watchdog 会抑制自动重连，避免持续重试。

## 注意事项

- Telegram bot 需要有读取消息、发送消息、创建论坛话题的权限。
- 如果使用 Forum Topics，请确认 bot 有管理话题权限。
- `topic-cache.json` 保存成员和 Telegram 话题的对应关系，删除后会重新创建话题。
- `razor-telegram.json` 会被命令更新，例如 `代狼 保存房间` 和名单管理命令。
- 媒体转发会把 Telegram 文件下载到本地并公开访问，生产环境建议加反向代理、访问控制或定期清理。
