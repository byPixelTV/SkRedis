package dev.bypixel.skredis.skript.elements.expressions

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.skript.log.ErrorQuality
import ch.njol.util.Kleenean
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.events.CustomRedisMessageEvent
import dev.bypixel.skredis.events.RedisMessageEvent
import org.bukkit.event.Event
import org.skriptlang.skript.docs.Origin
import org.skriptlang.skript.registration.DefaultSyntaxInfos
import org.skriptlang.skript.registration.SyntaxRegistry

@Suppress("unused")
@Name("Redis Message Event - get redis channel")
@Description("Returns the channel name of the Redis message event.", "This can only be used in the Redis Message Event.")
@Examples("on redis message:",
    "\tset {_channel} to redis channel",
    "\tbroadcast \"Got message from channel %{_channel}%\"")
@Since("1.0.0")
class ExprChannel : SimpleExpression<String>() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprChannel::class.java, String::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { ExprChannel() }
                .addPattern("redis channel")
                .build()
        )
    }

    override fun isSingle(): Boolean = true

    @Suppress("DEPRECATION")
    override fun init(
        exprs: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        if (!parser.isCurrentEvent(RedisMessageEvent::class.java) &&
            !parser.isCurrentEvent(CustomRedisMessageEvent::class.java)) {
            Skript.error("Cannot use 'redis channel' outside of a redis message event", ErrorQuality.SEMANTIC_ERROR)
            return false
        }
        return true
    }

    override fun get(e: Event?): Array<String>? {
        return when (e) {
            is RedisMessageEvent -> arrayOf(e.channelName)
            is CustomRedisMessageEvent -> arrayOf(e.channelName)
            else -> null
        }
    }

    override fun getReturnType(): Class<out String> = String::class.java

    override fun toString(event: Event?, b: Boolean): String = "redis channel"
}