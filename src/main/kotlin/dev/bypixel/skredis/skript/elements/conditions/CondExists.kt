package dev.bypixel.skredis.skript.elements. conditions

import ch.njol.skript. doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch. njol.skript.doc. Since
import ch.njol. skript.lang.Condition
import ch.njol.skript.lang.Expression
import ch. njol.skript.lang. SkriptParser
import ch. njol.util. Kleenean
import dev.bypixel.skredis. SkRedis
import org.bukkit.event.Event
import org.skriptlang.skript.docs.Origin
import org.skriptlang.skript.registration.SyntaxInfo
import org.skriptlang.skript.registration.SyntaxRegistry

@Name("Redis Keys - exists")
@Description("Checks if a given key of any type exists in Redis.")
@Examples(
    "on redis message:",
    "\tif \"cool-test-key\" exists:",
    "\t\tbroadcast \"Got %redis message% with existing key!\""
)
@Since("1.1.0")
class CondExists : Condition() {
    private var key: Expression<String>? = null

    fun register() {
        SkRedis.instance.addon.syntaxRegistry().register(
            SyntaxRegistry.CONDITION,
            SyntaxInfo.builder(CondExists::class.java)
                .origin(Origin.of(SkRedis.instance.addon))
                .supplier { CondExists() }
                .addPattern("%string% exists")
                .build()
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun init(
        expressions: Array<Expression<*>>,
        matchedPattern: Int,
        isDelayed:  Kleenean?,
        parseResult: SkriptParser.ParseResult?
    ): Boolean {
        this.key = expressions[0] as Expression<String>?
        isNegated = parseResult?.mark == 1
        return true
    }

    override fun check(e: Event? ): Boolean {
        val key = key?. getSingle(e) ?: return false
        return SkRedis. instance.lettuceClient.withSync { it.exists(key) == 0L != isNegated }
    }

    override fun toString(e: Event?, debug: Boolean): String {
        return "exists $key"
    }
}