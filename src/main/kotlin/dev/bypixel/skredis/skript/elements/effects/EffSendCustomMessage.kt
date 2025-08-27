package dev.bypixel.skredis.skript.elements.effects

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import dev.bypixel.skredis.Main
import dev.bypixel.skredis.SkRedisLogger
import dev.bypixel.skredis.lettuce.LettuceRedisClient
import dev.bypixel.skredis.utils.SkRedisCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.Event
import org.json.JSONObject

@Suppress("unused")
@Name("Redis Pub/Sub - send custom redis message")
@Description("Sends a custom message to a specific channel in Redis. The message has to be a valid JSON string. If not, the message will not be sent and a warning will be printed in the console.")
@Examples("send custom redis message \"{\"\"foo\"\": \"\"bar\"\"}\" to channel \"cool-channel\"")
@Since("2.0.0")
class EffSendCustomMessage : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffSendCustomMessage::class.java, "send custom redis message %string% to [channel] %string%")
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
        return "send custom redis message " + message!!.toString(event, debug) + " to channel " + channel!!.toString(
            event,
            debug
        )
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(event: Event?) {
        val plugin = Main.instance


        val message = message!!.getSingle(event)
        val channel = channel!!.getSingle(event)
        if (message == null) {
            SkRedisLogger.error(plugin, "Message was empty. Please check your code.")
            return
        }
        if (channel == null) {
            SkRedisLogger.error(plugin, "Channel was empty. Please check your code.")
            return
        }
        try {
            SkRedisCoroutineScope.launch(Dispatchers.IO) {
                LettuceRedisClient.sendCustomMessage(JSONObject(message), channel)
            }
        } catch (e: Exception) {
            SkRedisLogger.warn(plugin, "A message was not sent. Please make sure your message is a valid JSON string.")
            e.printStackTrace()
        }
    }
}