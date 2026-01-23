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
@Name("Redis Pub/Sub - send custom redis message")
@Description("Sends a custom message to a specific channel in Redis. The message has to be a valid JSON string. If not, the message will not be sent and a warning will be printed in the console.")
@Examples("send custom redis message \"{\"\"foo\"\": \"\"bar\"\"}\" to channel \"cool-channel\"")
@Since("2.0.0")
class EffSendCustomMessage : Effect() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSendCustomMessage::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { EffSendCustomMessage() }
                .addPattern("send custom redis message %string% to [channel] %string%")
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
        return "send custom redis message " + message!!.toString(event, debug) + " to channel " + channel!!.toString(
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
                SkRedis.instance.lettuceClient.sendMessage(JSONObject(message), channel)
            }
        } catch (e: Exception) {
            SkRedisLogger.warn("A message was not sent. Please make sure your message is a valid JSON string.")
            e.printStackTrace()
        }
    }
}