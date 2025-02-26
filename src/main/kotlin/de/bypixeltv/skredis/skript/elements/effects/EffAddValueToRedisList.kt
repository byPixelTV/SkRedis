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
@Name("Redis Lists - add value to redis list")
@Description("Adds a value to a specific list stored in Redis")
@Examples("add \"Hello\" to redis list \"myList\"")
@Since("1.0.0")
class EffAddValueToRedisList : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffAddValueToRedisList::class.java, "add %strings% to redis (list|array) %string%")
        }
    }

    private var addValues: Expression<String>? = null
    private var listKey: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.addValues = expressions[0] as Expression<String>
        this.listKey = expressions[1] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "add ${this.addValues} to redis list ${this.listKey}"
    }

    override fun execute(e: Event?) {
        val plugin = Main.INSTANCE

        var addValues = addValues!!.getAll(e)
        if (addValues[0] == null) {
            addValues = arrayOf<String?>(" ")
        }
        val listKey = listKey!!.getSingle(e)
        if (listKey == null) {
            plugin.sendErrorLogs("Redis list key was empty. Please check your code.")
            return
        }
        plugin.getRC()?.addToList(listKey, addValues)
    }
}