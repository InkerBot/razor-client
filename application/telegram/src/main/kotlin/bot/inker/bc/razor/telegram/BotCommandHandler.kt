package bot.inker.bc.razor.telegram

import bot.inker.bc.razor.protocol.room.ChatRoomData
import bot.inker.bc.razor.state.RoomState
import org.slf4j.LoggerFactory

class BotCommandHandler(
    private val app: TelegramApplication,
    private val messageSender: TelegramMessageSender,
) {
    private val logger = LoggerFactory.getLogger(BotCommandHandler::class.java)

    companion object {
        const val PREFIX = "代狼"
    }

    fun isCommand(text: String): Boolean {
        return text.trimStart().startsWith(PREFIX)
    }

    /**
     * Handle a command. Returns true if it was processed.
     * [replyToGeneral] sends the reply to the General topic.
     */
    fun handleCommand(text: String) {
        val body = text.trimStart().removePrefix(PREFIX).trim()
        val parts = body.split(Regex("\\s+"), limit = 3)
        val cmd = parts.getOrNull(0) ?: ""

        try {
            when (cmd) {
                "保存房间" -> cmdSaveRoom()
                "白名单" -> cmdWhitelist(parts)
                "管理员" -> cmdAdmin(parts)
                "封禁" -> cmdBan(parts)
                "踢出" -> cmdKick(parts)
                "房间信息" -> cmdRoomInfo()
                "帮助" -> cmdHelp()
                else -> reply("未知指令: $cmd\n发送「代狼 帮助」查看可用指令")
            }
        } catch (e: Exception) {
            logger.error("Error handling command: {}", text, e)
            reply("指令执行失败: ${e.message}")
        }
    }

    private fun cmdSaveRoom() {
        val client = app.getClient()
        val room = client?.room?.state
        if (room == null) {
            reply("当前不在房间中，无法保存")
            return
        }

        val newRoomConfig = roomStateToConfig(room)
        app.updateConfig(app.config.copy(room = newRoomConfig))
        reply("房间配置已保存到 razor-telegram.json\n" + formatRoomConfig(newRoomConfig))
    }

    private fun cmdWhitelist(parts: List<String>) {
        val action = parts.getOrNull(1) ?: run {
            reply("用法: 代狼 白名单 添加/移除 <编号>\n当前白名单: ${app.config.room.whitelist.joinToString(", ").ifEmpty { "空" }}")
            return
        }
        val memberNumber = parts.getOrNull(2)?.toIntOrNull() ?: run {
            reply("请提供有效的成员编号，如: 代狼 白名单 添加 12345")
            return
        }

        when (action) {
            "添加" -> {
                val current = app.config.room.whitelist
                if (memberNumber in current) {
                    reply("#$memberNumber 已在白名单中")
                    return
                }
                val newList = current + memberNumber
                updateRoomList(whitelist = newList)
                reply("已添加 #$memberNumber 到白名单")
            }

            "移除" -> {
                val newList = app.config.room.whitelist - memberNumber
                updateRoomList(whitelist = newList)
                reply("已从白名单移除 #$memberNumber")
            }

            else -> reply("用法: 代狼 白名单 添加/移除 <编号>")
        }
    }

    private fun cmdAdmin(parts: List<String>) {
        val action = parts.getOrNull(1) ?: run {
            reply("用法: 代狼 管理员 添加/移除 <编号>\n当前管理员: ${app.config.room.admin.joinToString(", ").ifEmpty { "空" }}")
            return
        }
        val memberNumber = parts.getOrNull(2)?.toIntOrNull() ?: run {
            reply("请提供有效的成员编号，如: 代狼 管理员 添加 12345")
            return
        }

        val client = app.getClient() ?: run {
            reply("BC 未连接")
            return
        }

        when (action) {
            "添加" -> {
                val current = app.config.room.admin
                if (memberNumber in current) {
                    reply("#$memberNumber 已是管理员")
                    return
                }
                client.room.promote(memberNumber)
                val newList = current + memberNumber
                updateRoomList(admin = newList)
                reply("已提升 #$memberNumber 为管理员")
            }

            "移除" -> {
                client.room.demote(memberNumber)
                val newList = app.config.room.admin - memberNumber
                updateRoomList(admin = newList)
                reply("已撤销 #$memberNumber 的管理员")
            }

            else -> reply("用法: 代狼 管理员 添加/移除 <编号>")
        }
    }

    private fun cmdBan(parts: List<String>) {
        val action = parts.getOrNull(1) ?: run {
            reply("用法: 代狼 封禁 添加/移除 <编号>\n当前封禁: ${app.config.room.ban.joinToString(", ").ifEmpty { "空" }}")
            return
        }
        val memberNumber = parts.getOrNull(2)?.toIntOrNull() ?: run {
            reply("请提供有效的成员编号，如: 代狼 封禁 添加 12345")
            return
        }

        val client = app.getClient() ?: run {
            reply("BC 未连接")
            return
        }

        when (action) {
            "添加" -> {
                client.room.ban(memberNumber)
                val current = app.config.room.ban
                if (memberNumber !in current) {
                    updateRoomList(ban = current + memberNumber)
                }
                reply("已封禁 #$memberNumber")
            }

            "移除" -> {
                client.room.unban(memberNumber)
                val newList = app.config.room.ban - memberNumber
                updateRoomList(ban = newList)
                reply("已解封 #$memberNumber")
            }

            else -> reply("用法: 代狼 封禁 添加/移除 <编号>")
        }
    }

    private fun cmdKick(parts: List<String>) {
        val memberNumber = parts.getOrNull(1)?.toIntOrNull() ?: run {
            reply("用法: 代狼 踢出 <编号>")
            return
        }

        val client = app.getClient() ?: run {
            reply("BC 未连接")
            return
        }

        client.room.kick(memberNumber)
        reply("已踢出 #$memberNumber")
    }

    private fun cmdRoomInfo() {
        val client = app.getClient()
        val room = client?.room?.state
        if (room == null) {
            reply("当前不在房间中")
            return
        }

        val members = room.characters.joinToString("\n") { "  ${it.displayName} (#${it.memberNumber})" }
        val info = buildString {
            appendLine("房间: ${room.name}")
            appendLine("描述: ${room.description}")
            appendLine("背景: ${room.background}")
            appendLine("人数: ${room.characters.size}/${room.limit}")
            appendLine("管理员: ${room.admin.joinToString(", ")}")
            appendLine("封禁: ${room.ban.joinToString(", ").ifEmpty { "无" }}")
            appendLine("白名单: ${room.whitelist.joinToString(", ").ifEmpty { "无" }}")
            appendLine("成员:")
            append(members)
        }
        reply(info)
    }

    private fun cmdHelp() {
        reply(
            """
            代狼 帮助 - 显示此帮助
            代狼 房间信息 - 显示当前房间信息
            代狼 保存房间 - 保存当前房间配置到配置文件
            代狼 白名单 - 查看白名单
            代狼 白名单 添加 <编号> - 添加白名单
            代狼 白名单 移除 <编号> - 移除白名单
            代狼 管理员 - 查看管理员
            代狼 管理员 添加 <编号> - 添加管理员
            代狼 管理员 移除 <编号> - 移除管理员
            代狼 封禁 - 查看封禁名单
            代狼 封禁 添加 <编号> - 封禁成员
            代狼 封禁 移除 <编号> - 解封成员
            代狼 踢出 <编号> - 踢出成员
            """.trimIndent()
        )
    }

    private fun updateRoomList(
        admin: List<Int>? = null,
        ban: List<Int>? = null,
        whitelist: List<Int>? = null,
    ) {
        val rc = app.config.room
        val newRoomConfig = rc.copy(
            admin = admin ?: rc.admin,
            ban = ban ?: rc.ban,
            whitelist = whitelist ?: rc.whitelist,
        )
        app.updateConfig(app.config.copy(room = newRoomConfig))

        // Also update the live room if connected
        val client = app.getClient()
        val currentRoom = client?.room?.state ?: return
        val roomData = ChatRoomData(
            name = currentRoom.name,
            admin = admin ?: currentRoom.admin,
            ban = ban ?: currentRoom.ban,
            whitelist = whitelist ?: currentRoom.whitelist,
        )
        client.room.updateSettings(roomData)
    }

    private fun roomStateToConfig(room: RoomState): RoomConfig {
        return RoomConfig(
            description = room.description,
            background = room.background,
            limit = room.limit,
            admin = room.admin,
            ban = room.ban,
            whitelist = room.whitelist,
            visibility = room.visibility.map { it.wireValue },
            access = room.access.map { it.wireValue },
            blockCategory = room.blockCategory.map { it.wireValue },
            language = room.language.wireValue,
            space = room.space.wireValue,
        )
    }

    private fun formatRoomConfig(rc: RoomConfig): String {
        return buildString {
            appendLine("背景: ${rc.background}, 人数上限: ${rc.limit}")
            if (rc.admin.isNotEmpty()) appendLine("管理员: ${rc.admin.joinToString(", ")}")
            if (rc.ban.isNotEmpty()) appendLine("封禁: ${rc.ban.joinToString(", ")}")
            if (rc.whitelist.isNotEmpty()) appendLine("白名单: ${rc.whitelist.joinToString(", ")}")
        }.trimEnd()
    }

    private fun reply(text: String) {
        messageSender.sendToGeneral(MessageFormatter.escapeHtml(text))
    }
}
