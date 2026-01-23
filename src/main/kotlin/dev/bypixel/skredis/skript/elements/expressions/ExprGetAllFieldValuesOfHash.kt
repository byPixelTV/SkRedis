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
@Name("Redis Hashes - get all field values of redis hash")
@Description("Returns all field values of a hash stored in Redis.")
@Examples("set {_values::*} to all field values of redis hash \"myHash\"",
    "loop {_values::*}:",
    "\tbroadcast \"Value: %loop-value%\"")
@Since("1.0.0")
class ExprGetAllFieldValuesOfHash : SimpleExpression<String>() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EXPRESSION,
            DefaultSyntaxInfos.Expression.builder(ExprGetAllFieldValuesOfHash::class.java, String::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { ExprGetAllFieldValuesOfHash() }
                .addPattern("all field values of redis hash %string%")
                .build()
        )
    }

    private var hashKey: Expression<String>? = null

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
        this.hashKey = exprs[0] as Expression<String>? ?: return false
        return true
    }

    override fun get(e: Event?): Array<String>? {
        val hashKey: String? = hashKey?.getSingle(e)
        if (hashKey != null) {
            return SkRedis.instance.lettuceClient.withSync {
                it.hvals(hashKey).toTypedArray()
            }
        }
        return null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "all field values of redis hash ${this.hashKey}"
    }

}