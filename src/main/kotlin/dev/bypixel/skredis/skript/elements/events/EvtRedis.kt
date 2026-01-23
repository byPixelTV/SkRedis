package dev.bypixel.skredis.skript.elements.events

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.registrations.EventValues
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.events.RedisMessageEvent
import org.bukkit.event.Event
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos
import org.skriptlang.skript.docs.Origin

@Name("Custom Redis Message - PubSub")
@Description("Called when any message is received from Redis. This event only fires when the message is sent by SkRedis.")
@Examples("""
on redis message:
    broadcast "%redis message%"
""")
@Since("1.0.0")
class EvtRedis : SkriptEvent() {
    fun register() {
        val addon = SkRedis.instance.addon

        addon.syntaxRegistry().register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(EvtRedis::class.java, "redis message")
                .origin(Origin.of(addon))
                .supplier { EvtRedis() }
                .addEvent(RedisMessageEvent::class.java)
                .addPattern("redis message")
                .build()
        )
        EventValues.registerEventValue(
            RedisMessageEvent::class.java,
            String::class.java
        ) { event -> event.message }
        EventValues.registerEventValue(
            RedisMessageEvent::class.java,
            String::class.java,
        ) { event -> event.channelName }
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