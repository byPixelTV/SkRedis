package de.bypixeltv.skredis.skript.elements.conditions

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Condition
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.log.ErrorQuality
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.events.RedisMessageEvent
import org.bukkit.event.Event

@Name("Redis Message Event - message is from proxy")
@Description("Checks if the message is from the proxy.", "This will only return true if the message is from RediVelocity if you have the integration enabled.",
"This can only be used in the Redis Message Event.")
@Examples("on redis message:",
    "\tif message is from proxy:",
    "\t\tbroadcast \"Got %redis message% from proxy!\"")
@Since("1.1.0")
class CondIsFromProxy : Condition() {

    companion object{
        init {
            Skript.registerCondition(
                CondIsFromProxy::class.java, "message (1¦is|2¦is(n't| not)) from (proxy|redivelocity|rv)")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        if (!parser.isCurrentEvent(RedisMessageEvent::class.java)) {
            Skript.error("Cannot use 'message from proxy' outside of a redis message event", ErrorQuality.SEMANTIC_ERROR)
            return false
        }
        isNegated = parseResult?.mark == 1
        return true
    }

    override fun check(e: Event?): Boolean {
        if (e is RedisMessageEvent) {
            return e.isFromProxy != !isNegated
        }
        return false
    }

    override fun toString(e: Event?, debug: Boolean): String {
        return "message is from proxy"
    }

}