package dev.bypixel.skredis.skript.elements.events

import ch.njol.skript.Skript
import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.registrations.EventValues
import dev.bypixel.skredis.events.RedisMessageEvent
import org.bukkit.event.Event
import java.util.*

class EvtRedis : SkriptEvent() {

    companion object {
        init {
            Skript.registerEvent("redis message", EvtRedis::class.java, RedisMessageEvent::class.java, "redis message").description("Called when a message is received from Redis. This event only fires when the message is sent by SkRedis.")
                .examples("on redis message:", "    broadcast \"%redis message%\"")
                .since("1.0.0")

            EventValues.registerEventValue(RedisMessageEvent::class.java, String::class.java, { event -> event.channelName }, 0)
            EventValues.registerEventValue(RedisMessageEvent::class.java, String::class.java, { event -> event.message }, 0)
            EventValues.registerEventValue(RedisMessageEvent::class.java, Date::class.java, { event -> Date(event.date) }, 0)
        }
    }

    override fun init(literals: Array<Literal<*>?>?, i: Int, parseResult: SkriptParser.ParseResult?): Boolean {
        return true
    }

    override fun check(event: Event?): Boolean {
        return (event is RedisMessageEvent)
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "redis message"
    }

}