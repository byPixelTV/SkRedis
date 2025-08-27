package dev.bypixel.skredis.skript.elements.events

import ch.njol.skript.Skript
import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.registrations.EventValues
import dev.bypixel.skredis.events.CustomRedisMessageEvent
import org.bukkit.event.Event

class EvtCustomRedis : SkriptEvent() {

    companion object {
        init {
            Skript.registerEvent("custom redis message", EvtCustomRedis::class.java, CustomRedisMessageEvent::class.java, "custom redis message").description("Called when any message is received from Redis. The message is a JSON string that can be parsed by SkJson for example.")
                .examples("on custom redis message:", "    broadcast \"%redis message% on channel %redis channel%\"")
                .since("2.0.0")

            EventValues.registerEventValue(CustomRedisMessageEvent::class.java, String::class.java, { event -> event.channelName }, 0)
            EventValues.registerEventValue(CustomRedisMessageEvent::class.java, String::class.java, { event -> event.message }, 0)
        }
    }

    override fun init(literals: Array<Literal<*>?>?, i: Int, parseResult: SkriptParser.ParseResult?): Boolean {
        return true
    }

    override fun check(event: Event?): Boolean {
        return (event is CustomRedisMessageEvent)
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "custom redis message"
    }

}