package dev.bypixel.skredis.skript.elements.effects

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import dev.bypixel.skredis.SkRedis
import dev.bypixel.skredis.SkRedisCoroutineScope
import dev.bypixel.skredis.SkRedisLogger
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.Event
import org.skriptlang.skript.docs.Origin
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

@Suppress("unused")
@Name("Redis Lists - set redis list value")
@Description("Sets a specific index in a Redis list to a new value.", "It will overwrite the value at the given index.", "NOTE: If the index does not already exist, it will fail.")
@Examples("set entry with index 2 in redis list \"myList\" to \"myNewValue\"")
@Since("1.0.0")
class EffSetRedisListValue : Effect() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSetRedisListValue::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { EffSetRedisListValue() }
                .addPattern("set entry with index %number% in redis (list|array) %string% to %string%")
                .build()
        )
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

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(e: Event?) {
        val plugin = SkRedis.instance

        val listIndexNumber = listIndex!!.getSingle(e)
        if (listIndexNumber == null) {
            SkRedisLogger.error("Redis list index was empty. Please check your code.")
            return
        }
        val listIndex = listIndexNumber.toInt()
        val listKey = listKey!!.getSingle(e)
        val listValue = listValue!!.getSingle(e)
        if (listKey == null) {
            SkRedisLogger.error("Redis list key was empty. Please check your code.")
            return
        }
        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            SkRedis.instance.lettuceClient.commands.lset(listKey, listIndex.toLong(), listValue ?: "")
        }
    }
}