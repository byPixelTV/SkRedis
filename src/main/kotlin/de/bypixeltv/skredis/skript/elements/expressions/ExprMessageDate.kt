package de.bypixeltv.skredis.skript.elements.expressions

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.ExpressionType
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.skript.log.ErrorQuality
import ch.njol.skript.util.Date
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.events.RedisMessageEvent
import org.bukkit.event.Event

@Suppress("unused")
class ExprMessageDate : SimpleExpression<String>() {

    companion object{
        init {
            Skript.registerExpression(
                ExprMessageDate::class.java, String::class.java,
                ExpressionType.SIMPLE, "redis message date")
        }
    }

    override fun isSingle(): Boolean {
        return true
    }

    @Suppress("DEPRECATION")
    override fun init(
        exprs: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        if (!parser.isCurrentEvent(RedisMessageEvent::class.java)) {
            Skript.error("Cannot use 'redis message date' outside of a redis message event", ErrorQuality.SEMANTIC_ERROR)
            return false
        }
        return true
    }

    override fun get(e: Event?): Array<String>? {
        if (e is RedisMessageEvent) {
            val date: Long = e.date
            return arrayOf(Date(date)).map { it.toString() }.toTypedArray()
        }
        return null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "redis message date"
    }

}