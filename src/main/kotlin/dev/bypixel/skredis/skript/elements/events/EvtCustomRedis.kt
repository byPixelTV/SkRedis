package dev.bypixel.skredis.skript.elements.events

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Literal
import ch.njol.skript.lang.SkriptEvent
import ch.njol.skript.lang.SkriptParser
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.events.CustomRedisMessageEvent
import org.bukkit.event.Event
import org.skriptlang.skript.bukkit.registration.BukkitSyntaxInfos
import org.skriptlang.skript.docs.Origin

@Name("Custom Redis Message - PubSub")
@Description("Called when any message is received from Redis. The message is a JSON string that can be parsed by SkJson for example.")
@Examples("""
on custom redis message:
    broadcast "%redis message% on channel %redis channel%"
""")
@Since("2.0.0")
class EvtCustomRedis : SkriptEvent() {
    fun register() {
        val addon = SkRedis.instance.addon

        addon.syntaxRegistry().register(
            BukkitSyntaxInfos.Event.KEY,
            BukkitSyntaxInfos.Event.builder(EvtCustomRedis::class.java, "custom redis message")
                .origin(Origin.of(addon))
                .supplier { EvtCustomRedis() }
                .addEvent(CustomRedisMessageEvent::class.java)
                .addPattern("custom redis message")
                .build()
        )
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