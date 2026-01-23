package dev.bypixel.skredis.skript.elements.expressions

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import dev.bypixel.skredis.SkRedis
import org.bukkit.event.Event
import org.skriptlang.skript.docs.Origin
import org.skriptlang.skript.registration.DefaultSyntaxInfos
import org.skriptlang.skript.registration.SyntaxRegistry

@Suppress("unused")
@Name("Redis Strings - get redis string")
@Description("Returns the string stored in Redis.")
@Examples("set {_string} to redis string \"myString\"",
    "broadcast \"Value: %{_string}%\"")
@Since("1.0.0")
class ExprGetRedisString : SimpleExpression<String>() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprGetRedisString::class.java, String::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { ExprGetRedisString() }
                .addPattern("redis string %string%")
                .build()
        )
    }

    private var stringKey: Expression<String>? = null

    override fun isSingle(): Boolean {
        return true
    }

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        this.stringKey = exprs[0] as Expression<String>? ?: return false
        return true
    }

    override fun get(e: Event?): Array<String>? {
        val stringKey: String? = stringKey?.getSingle(e)
        if (stringKey != null) {
            return SkRedis.instance.lettuceClient.sync.get(stringKey)?.let { arrayOf(it) }
        }
        return null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "redis string ${this.stringKey}"
    }

}