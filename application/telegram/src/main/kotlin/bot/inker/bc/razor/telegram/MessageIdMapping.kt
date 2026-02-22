package bot.inker.bc.razor.telegram

class MessageIdMapping(private val maxSize: Int = 10000) {
    private val bcToTg = LinkedHashMap<String, Int>(maxSize, 0.75f, true)
    private val tgToBc = LinkedHashMap<Int, String>(maxSize, 0.75f, true)

    @Synchronized
    fun put(bcMsgId: String, tgMsgId: Int) {
        bcToTg[bcMsgId] = tgMsgId
        tgToBc[tgMsgId] = bcMsgId
        while (bcToTg.size > maxSize) {
            val eldest = bcToTg.entries.iterator().next()
            bcToTg.remove(eldest.key)
            tgToBc.remove(eldest.value)
        }
    }

    @Synchronized
    fun getTgMsgId(bcMsgId: String): Int? = bcToTg[bcMsgId]

    @Synchronized
    fun getBcMsgId(tgMsgId: Int): String? = tgToBc[tgMsgId]
}
