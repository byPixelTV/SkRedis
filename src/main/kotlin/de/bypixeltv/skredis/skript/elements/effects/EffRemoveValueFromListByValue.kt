package de.bypixeltv.skredis.skript.elements.effects

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import de.bypixeltv.skredis.Main
import org.bukkit.event.Event

@Suppress("unused")
@Name("Redis Lists - delete entry with value from redis list")
@Description("Deletes the entry with the given value from the list stored in Redis.")
@Examples("delete entry with value \"myValue\" from redis list \"myList\"")
@Since("1.0.1")
class EffRemoveValueFromListByValue : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffRemoveValueFromListByValue::class.java, "delete entry with value %string% from redis (list|array) %string%")
        }
    }

    private var listValue: Expression<String>? = null
    private var listKey: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.listValue = expressions[0] as Expression<String>
        this.listKey = expressions[1] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "delete entry with value ${this.listValue} from redis list ${this.listKey}"
    }

    override fun execute(e: Event?) {
        val plugin = Main.INSTANCE

        val listVal = listValue!!.getSingle(e)
        if (listVal == null) {
            plugin.sendErrorLogs("Redis list value was empty. Please check your code.")
            return
        }
        val listKey = listKey!!.getSingle(e)
        if (listKey == null) {
            plugin.sendErrorLogs("Redis list key was empty. Please check your code.")
            return
        }
        plugin.getRC()!!.removeFromListByValue(listKey, listVal)
    }
}