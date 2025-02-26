package de.bypixeltv.skredis.skript.elements.effects

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.Main
import org.bukkit.event.Event

@Suppress("unused")
@Name("Redis Pub/Sub - send redis message")
@Description("Sends a message to a specific channel in Redis.")
@Examples("send redis message \"Foo\" to channel \"Bar\"")
@Since("1.0.0")
class EffSendMessage : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffSendMessage::class.java, "send redis message %string% to [channel] %string%")
        }
    }

    private var message: Expression<String>? = null
    private var channel: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.message = expressions[0] as Expression<String>
        this.channel = expressions[1] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "send redis message " + message!!.toString(event, debug) + " to channel " + channel!!.toString(
            event,
            debug
        )
    }

    override fun execute(event: Event?) {
        val plugin = Main.INSTANCE


        val message = message!!.getSingle(event)
        val channel = channel!!.getSingle(event)
        if (message == null) {
            plugin.sendErrorLogs("Message was empty. Please check your code.")
            return
        }
        if (channel == null) {
            plugin.sendErrorLogs("Channel was empty. Please check your code.")
            return
        }
        try {
            plugin.getRC()?.sendMessage(message.toString(), channel)
        } catch (e: Exception) {
            plugin.sendErrorLogs("An error occurred while sending the message to the Redis server.")
            e.printStackTrace()
        }
    }
}