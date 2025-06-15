package dev.bypixel.skredis.skript.elements.expressions

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.ExpressionType
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import dev.bypixel.skredis.Main
import org.bukkit.event.Event

@Suppress("unused")
@Name("Redis Hashes - get all field names of redis hash")
@Description("Returns all field names of a hash stored in Redis.")
@Examples("set {_fields::*} to all field names of redis hash \"myHash\"",
    "loop {_fields::*}:",
    "\tbroadcast \"Field: %loop-value%\"")
@Since("1.0.0")
class ExprGetAllFieldNamesOfHash : SimpleExpression<String>() {

    companion object{
        init {
            Skript.registerExpression(
                ExprGetAllFieldNamesOfHash::class.java, String::class.java,
                ExpressionType.SIMPLE, "all field names of redis hash %string%")
        }
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
        val plugin = Main.INSTANCE

        val hashKey: String? = hashKey?.getSingle(e)
        if (hashKey != null) {
            return plugin.getRC()?.getAllHashFields(hashKey)?.toTypedArray()
        }
        return null
    }

    override fun getReturnType(): Class<out String> {
        return String::class.java
    }

    override fun toString(event: Event?, b: Boolean): String {
        return "all field names of redis hash ${this.hashKey}"
    }

}