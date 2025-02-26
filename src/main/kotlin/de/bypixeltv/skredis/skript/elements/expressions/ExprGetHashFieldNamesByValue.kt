package de.bypixeltv.skredis.skript.elements.expressions

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.ExpressionType
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.Main
import org.bukkit.event.Event
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since

@Suppress("unused")
@Name("Redis Hashes - get field names by value")
@Description("Returns all field names of a hash stored in Redis that have the given value.")
@Examples("set {_fields::*} to all field names of redis hash \"myHash\" with value \"myValue\"",
    "loop {_fields::*}:",
    "\tbroadcast \"Field: %loop-value%\"")
@Since("1.0.0")
class ExprGetHashFieldNamesByValue : SimpleExpression<String>() {

    companion object{
        init {
            Skript.registerExpression(
                ExprGetHashFieldNamesByValue::class.java, String::class.java,
                ExpressionType.SIMPLE, "name[s] of field with value %string% in redis (hash|value) %string%")
        }
    }

    private var hashKey: Expression<String>? = null
    private var value: Expression<String>? = null

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
        this.value = exprs[1] as Expression<String>? ?: return false
        return true
    }

    override fun get(e: Event?): Array<String>? {
        val plugin = Main.INSTANCE

        val hash: String? = hashKey?.getSingle(e)
        val value: String? = value?.getSingle(e)

        if (hash == null || value == null) {
            return null
        }

        val fieldNames: Array<String?> = plugin.getRC()?.getHashFieldNamesByValue(hash, value)?.toTypedArray() ?: return null

        return if (fieldNames[0] != null) fieldNames.filterNotNull().toTypedArray() else null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "all field names of redis hash ${this.hashKey}"
    }

}