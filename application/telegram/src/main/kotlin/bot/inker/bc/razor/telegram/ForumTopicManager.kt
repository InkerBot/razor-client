package bot.inker.bc.razor.telegram

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.slf4j.LoggerFactory
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient
import org.telegram.telegrambots.meta.api.methods.forum.CreateForumTopic
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

class ForumTopicManager(
    private val config: TelegramConfig,
    private val telegramClient: OkHttpTelegramClient,
) {
    private val logger = LoggerFactory.getLogger(ForumTopicManager::class.java)
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val cachePath: Path = Path.of(config.topicCachePath)

    // member → threadId
    private val memberToTopic = ConcurrentHashMap<MemberIdentity, Int>()

    // threadId → member (reverse mapping)
    private val topicToMember = ConcurrentHashMap<Int, MemberIdentity>()

    fun loadCache() {
        if (!Files.exists(cachePath)) return
        try {
            val json = Files.readString(cachePath)
            val type = object : TypeToken<List<TopicCacheEntry>>() {}.type
            val entries: List<TopicCacheEntry> = gson.fromJson(json, type) ?: return
            for (entry in entries) {
                val identity = MemberIdentity(entry.memberName, entry.memberNumber)
                memberToTopic[identity] = entry.threadId
                topicToMember[entry.threadId] = identity
            }
            logger.info("Loaded {} topic cache entries", entries.size)
        } catch (e: Exception) {
            logger.warn("Failed to load topic cache", e)
        }
    }

    private fun saveCache() {
        try {
            val entries = memberToTopic.map { (identity, threadId) ->
                TopicCacheEntry(identity.memberName, identity.memberNumber, threadId)
            }
            Files.writeString(cachePath, gson.toJson(entries))
        } catch (e: Exception) {
            logger.warn("Failed to save topic cache", e)
        }
    }

    fun getGeneralTopicId(): Int? {
        return config.generalTopicId
    }

    fun getOrCreateTopic(member: MemberIdentity): Int {
        memberToTopic[member]?.let { return it }

        synchronized(this) {
            // Double-check after acquiring lock
            memberToTopic[member]?.let { return it }

            val topicName = buildTopicName(member)
            logger.info("Creating forum topic: {}", topicName)

            val createTopic = CreateForumTopic.builder()
                .chatId(config.chatId)
                .name(topicName)
                .build()

            val result = telegramClient.execute(createTopic)
            val threadId = result.messageThreadId

            memberToTopic[member] = threadId
            topicToMember[threadId] = member
            saveCache()

            logger.info("Created forum topic '{}' with threadId={}", topicName, threadId)
            return threadId
        }
    }

    fun lookupMember(threadId: Int): MemberIdentity? {
        return topicToMember[threadId]
    }

    fun isGeneralTopic(threadId: Int?): Boolean {
        val generalId = config.generalTopicId
        if (generalId == null) {
            // null generalTopicId means messages in the default "General" topic have no thread_id
            return threadId == null
        }
        return threadId == generalId
    }

    private fun buildTopicName(member: MemberIdentity): String {
        val name = sanitizeName(member.memberName)
        val full = "BEEP-$name-${member.memberNumber}"
        return if (full.length > 128) full.take(128) else full
    }

    private fun sanitizeName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private data class TopicCacheEntry(
        val memberName: String,
        val memberNumber: Int,
        val threadId: Int,
    )
}
