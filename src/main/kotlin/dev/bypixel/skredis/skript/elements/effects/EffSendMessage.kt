package dev.bypixel.skredis.skript.elements.effects

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.SkRedisCoroutineScope
import dev.bypixel.skredis.SkRedisLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.Event
import org.json.JSONObject
import org.skriptlang.skript.docs.Origin
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

@Suppress("unused")
@Name("Redis Pub/Sub - send redis message")
@Description("Sends a message to a specific channel in Redis.")
@Examples("send redis message \"Foo\" to channel \"Bar\"")
@Since("1.0.0")
class EffSendMessage : Effect() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSendMessage::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { EffSendMessage() }
                .addPattern("send redis message %string% to [channel] %string%")
                .build()
        )
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

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(event: Event?) {
        val message = message!!.getSingle(event)
        val channel = channel!!.getSingle(event)
        if (message == null) {
            SkRedisLogger.error("Message was empty. Please check your code.")
            return
        }
        if (channel == null) {
            SkRedisLogger.error("Channel was empty. Please check your code.")
            return
        }
        try {
            SkRedisCoroutineScope.launch(Dispatchers.IO) {
                val json = JSONObject().apply {
                    put("message", message)
                    put("action", "skredis-message")
                    put("date", System.currentTimeMillis())
                }
                SkRedis.instance.lettuceClient.sendMessage(json, channel)
            }
        } catch (e: Exception) {
            SkRedisLogger.error("An error occurred while sending the message to the Redis server.")
            e.printStackTrace()
        }
    }
}