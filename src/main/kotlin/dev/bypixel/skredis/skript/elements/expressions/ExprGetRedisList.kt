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
@Name("Redis Lists - get redis list")
@Description("Returns all values of a list stored in Redis.")
@Examples("set {_list::*} to redis list \"myList\"",
    "loop {_list::*}:",
    "\tbroadcast \"Value: %loop-value%\"")
@Since("1.0.0")
class ExprGetRedisList : SimpleExpression<String>() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprGetRedisList::class.java, String::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { ExprGetRedisList() }
                .addPattern("redis (array|list) %string%")
                .build()
        )
    }

    private var listKey: Expression<String>? = null

    override fun isSingle(): Boolean {
        return false
    }

    @Suppress("UNCHECKED_CAST")
    override fun init(
        exprs: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        this.listKey = exprs[0] as Expression<String>? ?: return false
        return true
    }

    override fun get(e: Event?): Array<String>? {
        val redisListName: String? = listKey?.getSingle(e)
        if (redisListName != null) {
            return SkRedis.instance.lettuceClient.withSync {
                it.lrange(redisListName, 0, -1).toTypedArray()
            }
        }
        return null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "redis list ${this.listKey}"
    }

}