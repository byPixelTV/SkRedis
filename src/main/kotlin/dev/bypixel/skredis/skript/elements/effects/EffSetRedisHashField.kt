package dev.bypixel.skredis.skript.elements.effects

import ch.njol.skript.Skript
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import dev.bypixel.skredis.Main
import dev.bypixel.skredis.SkRedisLogger
import dev.bypixel.skredis.lettuce.LettuceRedisClient
import dev.bypixel.skredis.utils.SkRedisCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.Event

@Suppress("unused")
@Name("Redis Hashes - set redis hash field")
@Description("Sets the given hash field to a value in a hash stored in Redis.")
@Examples("set field \"Foo\" to \"Bar\" in redis hash \"Foobar\"")
@Since("1.0.0")
class EffSetRedisHashField : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffSetRedisHashField::class.java, "set [hash] field %string% to %string% in [redis] (hash|value) %string%")
        }
    }

    private var hashName: Expression<String>? = null
    private var fieldName: Expression<String>? = null
    private var fieldValue: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.fieldName = expressions[0] as Expression<String>
        this.fieldValue = expressions[1] as Expression<String>
        this.hashName = expressions[2] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "set redis hash ${this.hashName}"
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(e: Event?) {
        val plugin = Main.instance

        val hashName = hashName?.getSingle(e)
        val fieldName = fieldName?.getSingle(e)
        val fieldValue = fieldValue?.getSingle(e)

        if (hashName == null) {
            SkRedisLogger.error(plugin, "HashName was empty. Please check your code.")
            return
        }
        if (fieldName == null) {
            SkRedisLogger.error(plugin, "FieldName was empty. Please check your code.")
            return
        }
        if (fieldValue == null) {
            SkRedisLogger.error(plugin, "FieldValue was empty. Please check your code.")
            return
        }

        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            LettuceRedisClient.commands.hset(hashName, fieldName, fieldValue)
        }
    }
}