package de.bypixeltv.skredis.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class RedisMessageEvent(val channelName: String, val message: String, val date: Long, val isFromProxy: Boolean) : Event(true) {
    companion object {
        @JvmStatic
        val HANDLERS: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }

    override fun getEventName(): String {
        return super.getEventName()
    }

    override fun getHandlers(): HandlerList {
        return HANDLERS
    }
}