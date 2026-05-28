# TUI 模块

`application:tui` 是 Razor Client 的终端聊天客户端。它基于 Lanterna 构建，提供登录、房间搜索、加入房间、聊天、成员列表、基础管理命令和颜色主题配置。

## 功能概览

- 连接 Bondage Club Socket.IO 服务器。
- 登录 BC 账号。
- 搜索房间并按空间类型筛选。
- 加入房间并查看成员列表。
- 显示普通聊天、whisper、emote、activity、server message 和 beep。
- 发送聊天、whisper 和 emote。
- 支持踢出、封禁、离开房间等基础命令。
- 支持可配置的终端颜色主题。
- 支持 HTTP 请求头和 SOCKS5 代理配置。

## 运行

从仓库根目录运行：

```bash
./gradlew :application:tui:bootRun
```

应用会优先尝试使用当前终端。如果当前环境没有可用 TTY，会尝试创建 Swing 终端窗口。

日志写入：

```text
logs/razor-client.log
```

## 配置文件

TUI 会在当前工作目录读取和写入 `razor-tui.json`。如果文件不存在，会使用默认配置，并在设置或登录时保存。

示例：

```json
{
  "serverUrl": "https://bondage-club-server.herokuapp.com/",
  "origin": "https://www.bondageprojects.elementfx.com",
  "referer": "https://www.bondageprojects.elementfx.com/R124/BondageClub/",
  "proxyHost": "",
  "proxyPort": 0,
  "lastUsername": "",
  "disableShadows": null
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `serverUrl` | BC Socket.IO 服务器地址。 |
| `origin` | 连接时发送的 `Origin` 请求头。 |
| `referer` | 连接时发送的 `Referer` 请求头。 |
| `proxyHost` | SOCKS5 代理地址，留空表示不使用代理。 |
| `proxyPort` | SOCKS5 代理端口，`0` 表示不使用代理。 |
| `lastUsername` | 上次登录的用户名，只保存用户名，不保存密码。 |
| `colorScheme` | 颜色主题配置，由颜色设置界面维护。 |
| `disableShadows` | 是否禁用窗口阴影；`null` 和 `true` 会禁用阴影，`false` 保留阴影。 |

密码不会写入 `razor-tui.json`，每次登录时需要在登录界面输入。

## 使用流程

1. 启动 TUI。
2. 在登录页确认连接状态为 `Connected`。
3. 输入用户名和密码登录。
4. 进入 Lobby 后搜索房间。
5. 选择房间并加入。
6. 在 Chat Room 中输入消息或命令。

## 设置页

登录页的 `Settings` 按钮可以打开设置页。设置页支持修改：

- Server URL
- Origin
- Referer
- Proxy Host
- Proxy Port

保存后会重新连接服务器。

设置页还可以进入 `Color Scheme` 页面，修改终端颜色主题。

## 房间搜索

Lobby 页面支持按关键词搜索房间，并选择空间类型：

| 选项 | Space 值 |
| --- | --- |
| `Classical` | 空字符串 |
| `Mixed` | `X` |
| `Male` | `M` |
| `Asylum` | `Asylum` |

房间列表会显示名称、人数、创建者、语言、锁定状态和描述。选中房间后可以加入。

## 聊天界面

Chat Room 页面包含：

- 左侧成员列表。
- 右侧聊天记录。
- 底部输入框。
- 状态栏。

交互：

- `Enter`：发送当前输入。
- 行尾输入 `\` 后按 `Enter`：插入换行，继续编辑多行消息。
- 鼠标滚轮：滚动聊天记录。
- `Page Up` / `Page Down`：翻页滚动聊天记录。
- `Ctrl+Home` / `Ctrl+End`：跳到聊天记录顶部或底部。
- `Tab`：在成员列表和输入框之间切换焦点。
- 在成员列表中选择成员可以查看详情。

## 聊天命令

未以 `/` 开头的输入会作为普通聊天发送。支持以下命令：

| 命令 | 说明 |
| --- | --- |
| `/w <目标> <消息>` | 向目标发送 whisper。 |
| `/whisper <目标> <消息>` | 向目标发送 whisper。 |
| `/me <文本>` | 发送 emote。 |
| `/emote <文本>` | 发送 emote。 |
| `/leave` | 离开当前房间。 |
| `/rooms` | 离开当前房间并返回 Lobby。 |
| `/kick <目标>` | 踢出目标，需要房间管理员权限。 |
| `/ban <目标>` | 封禁目标，需要房间管理员权限。 |
| `/help` | 在聊天窗口显示命令帮助。 |
| `/quit` | 退出程序。 |
| `/exit` | 退出程序。 |

目标可以是玩家显示名、玩家名称、成员编号，或 `#成员编号`，例如：

```text
/w Alice hello
/w #12345 hello
/kick 12345
```

未知命令或格式错误的命令会按普通聊天发送。

## 颜色主题

`Color Scheme` 页面支持：

- 使用默认主题。
- 使用深色主题。
- 使用浅色主题。
- 分别配置窗口、聊天消息、成员列表和状态栏颜色。

颜色值使用 Lanterna 的 ANSI 颜色名，例如 `DEFAULT`、`WHITE`、`BLACK`、`CYAN`、`MAGENTA`、`YELLOW`、`RED`、`BLUE` 等。

## 注意事项

- TUI 当前只保存用户名，不保存密码。
- `/kick` 和 `/ban` 会直接发送管理请求；是否成功取决于服务器和房间权限。
- `/rooms` 的实际行为是离开当前房间，之后通过 `RoomLeft` 事件回到 Lobby。
- 如果终端不支持鼠标捕获，鼠标滚轮功能可能不可用，但键盘滚动仍可使用。
- 如果无法创建文本终端或 Swing 终端，程序会提示需要交互式终端或图形环境。
