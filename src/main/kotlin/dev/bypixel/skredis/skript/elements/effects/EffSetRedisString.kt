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
@Name("Redis Strings - set redis string")
@Description("Sets a string that is stored in Redis to a specific value.")
@Examples("set redis string \"myString\" to \"Hello World!\"")
@Since("1.0.0")
class EffSetRedisString : Effect() {
    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.EFFECT,
            SyntaxInfo.builder(EffSetRedisString::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { EffSetRedisString() }
                .addPattern("set redis string %string% to %string%")
                .build()
        )
    }

    private var stringName: Expression<String>? = null
    private var stringValue: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.stringName = expressions[0] as Expression<String>
        this.stringValue = expressions[1] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "set redis string ${this.stringName} to ${this.stringValue}"
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(e: Event?) {
        val plugin = SkRedis.instance

        val name = stringName!!.getSingle(e)
        if (name == null) {
            SkRedisLogger.error("Redis string name was empty. Please check your code.")
            return
        }
        val value = stringValue!!.getSingle(e)
        if (value == null) {
            SkRedisLogger.error("Redis string value was empty. Please check your code.")
            return
        }
        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            SkRedis.instance.lettuceClient.commands.set(name, value)
        }
    }
}