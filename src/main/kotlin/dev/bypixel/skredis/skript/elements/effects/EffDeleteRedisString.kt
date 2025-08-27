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
import dev.bypixel.skredis.lettuce.LettuceRedisClient
import dev.bypixel.skredis.utils.SkRedisCoroutineScope
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.bukkit.event.Event

@Suppress("unused")
@Name("Redis Strings - delete redis string")
@Description("Deletes the given string from Redis.")
@Examples("delete redis string \"myString\"")
@Since("1.0.0")
class EffDeleteRedisString : Effect() {

    companion object{
        init {
            Skript.registerEffect(EffDeleteRedisString::class.java, "delete redis string %string%")
        }
    }

    private var stringName: Expression<String>? = null

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed: Kleenean,
        parser: SkriptParser.ParseResult
    ): Boolean {
        this.stringName = expressions[0] as Expression<String>
        return true
    }

    override fun toString(event: Event?, debug: Boolean): String {
        return "delete redis string ${this.stringName}"
    }

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override fun execute(e: Event?) {
        val plugin = Main.instance

        val stringName = this.stringName?.getSingle(e) ?: return

        SkRedisCoroutineScope.launch(Dispatchers.IO) {
            LettuceRedisClient.commands.del(stringName)
        }
    }
}