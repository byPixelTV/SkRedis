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
@Name("Redis Lists - delete entry with value from redis list")
@Description("Deletes the entry with the given value from the list stored in Redis.")
@Examples("delete entry with value \"myValue\" from redis list \"myList\"")
@Since("1.0.1")
class EffRemoveValueFromListByValue : Effect() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffRemoveValueFromListByValue::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { EffRemoveValueFromListByValue() }
                .addPattern("delete entry with value %string% from redis (list|array) %string%")
                .build()
        )
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

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(e: Event?) {
        val plugin = SkRedis.instance

        val listVal = listValue!!.getSingle(e)
        if (listVal == null) {
            SkRedisLogger.error("Redis list value was empty. Please check your code.")
            return
        }
        val listKey = listKey!!.getSingle(e)
        if (listKey == null) {
            SkRedisLogger.error("Redis list key was empty. Please check your code.")
            return
        }

        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            SkRedis.instance.lettuceClient.commands.lrem(listKey, 0, listVal)
        }
    }
}