package de.bypixeltv.skredis.skript.elements.effects

import ch.njol.skript.Skript
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.Main
import org.bukkit.event.Event

@Suppress("unused")
class EffSetRedisListValue : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffSetRedisListValue::class.java, "set entry with index %number% in redis (list|array) %string% to %string%")
        }
    }

    private var listIndex: Expression<Number>? = null
    private var listKey: Expression<String>? = null
    private var listValue: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.listIndex = expressions[0] as Expression<Number>
        this.listKey = expressions[1] as Expression<String>
        this.listValue = expressions[2] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "set entry with index ${this.listIndex} in redis list ${this.listKey} to ${this.listValue}"
    }

    override fun execute(e: Event?) {
        val plugin = Main.INSTANCE

        val listIndexNumber = listIndex!!.getSingle(e)
        if (listIndexNumber == null) {
            plugin.sendErrorLogs("Redis list index was empty. Please check your code.")
            return
        }
        val listIndex = listIndexNumber.toInt()
        val listKey = listKey!!.getSingle(e)
        val listValue = listValue!!.getSingle(e)
        if (listKey == null) {
            plugin.sendErrorLogs("Redis list key was empty. Please check your code.")
            return
        }
        plugin.getRC()!!.setListValue(listKey, listIndex, listValue!!)
    }
}