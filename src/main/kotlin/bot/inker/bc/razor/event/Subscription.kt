package bot.inker.bc.razor.event

fun interface Subscription : AutoCloseable {
    fun cancel()
    override fun close() = cancel()
}
