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
@Name("Redis Lists - delete entry with index from redis list")
@Description("Deletes the entry with the given index from the list stored in Redis.")
@Examples("delete entry with index 2 from redis list \"myList\"")
@Since("1.0.0")
class EffRemoveValueFromListByIndex : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffRemoveValueFromListByIndex::class.java, "delete entry with index %number% from redis (list|array) %string%")
        }
    }

    private var removeIndex: Expression<Number>? = null
    private var listKey: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.removeIndex = expressions[0] as Expression<Number>
        this.listKey = expressions[1] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "delete entry with index ${this.removeIndex} from redis list ${this.listKey}"
    }

    override fun execute(e: Event?) {
        val plugin = Main.INSTANCE

        val removeIndexNumber = removeIndex!!.getSingle(e)
        if (removeIndexNumber == null) {
            plugin.sendErrorLogs("Redis list index was empty. Please check your code.")
            return
        }
        val removeIndex = removeIndexNumber.toInt()
        val listKey = listKey!!.getSingle(e)
        if (listKey == null) {
            plugin.sendErrorLogs("Redis list key was empty. Please check your code.")
            return
        }
        plugin.getRC()!!.removeFromList(listKey, removeIndex)
    }
}