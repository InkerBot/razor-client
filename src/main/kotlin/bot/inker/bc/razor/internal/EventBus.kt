package bot.inker.bc.razor.internal

import bot.inker.bc.razor.event.RazorEvent
import bot.inker.bc.razor.event.Subscription
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.reflect.KClass

@PublishedApi
internal class EventBus {
    private val logger = Logger.getLogger(EventBus::class.java.name)
    private val handlers = ConcurrentHashMap<KClass<*>, CopyOnWriteArrayList<EventHandler<*>>>()
    private val globalHandlers = CopyOnWriteArrayList<EventHandler<RazorEvent>>()

    fun <T : RazorEvent> subscribe(eventType: KClass<T>, handler: (T) -> Unit): Subscription {
        val entry = EventHandler(handler)
        handlers.getOrPut(eventType) { CopyOnWriteArrayList() }.add(entry)
        return Subscription {
            handlers[eventType]?.remove(entry)
        }
    }

    inline fun <reified T : RazorEvent> on(noinline handler: (T) -> Unit): Subscription {
        return subscribe(T::class, handler)
    }

    fun onAny(handler: (RazorEvent) -> Unit): Subscription {
        val entry = EventHandler(handler)
        globalHandlers.add(entry)
        return Subscription {
            globalHandlers.remove(entry)
        }
    }

    fun dispatch(event: RazorEvent) {
        val typed = handlers[event::class]
        if (typed != null) {
            for (entry in typed) {
                try {
                    @Suppress("UNCHECKED_CAST")
                    (entry as EventHandler<RazorEvent>).handler(event)
                } catch (e: Exception) {
                    logger.log(Level.WARNING, "Error in event handler", e)
                }
            }
        }
        for (entry in globalHandlers) {
            try {
                entry.handler(event)
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Error in event handler", e)
            }
        }
    }

    fun clear() {
        handlers.clear()
        globalHandlers.clear()
    }

    private class EventHandler<T>(val handler: (T) -> Unit)
}
