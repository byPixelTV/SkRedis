package de.bypixeltv.skredis.skript.elements.expressions

import ch.njol.skript.Skript
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.ExpressionType
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.util.SimpleExpression
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.Main
import org.bukkit.event.Event

@Suppress("unused")
class ExprGetAllFieldValuesOfHash : SimpleExpression<String>() {

    companion object{
        init {
            Skript.registerExpression(
                ExprGetAllFieldValuesOfHash::class.java, String::class.java,
                ExpressionType.SIMPLE, "all field values of redis hash %string%")
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
            return plugin.getRC()?.getAllHashValues(hashKey)?.toTypedArray()
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