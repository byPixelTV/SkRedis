package dev.bypixel.skredis.skript.elements.effects

import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.Effect
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.util.Kleenean
import dev.bypixel.skredis.skript.elements.section.SecRunAsync
import org.bukkit.event.Event

@Name("Async Operations - stop async run")
@Description("Stops an async run that was started with `run async`.")
@Examples("""
command /test1:
    trigger:
        loop all items:
            set field "%loop-value%" to "test" in redis hash "testHash"

command /test2:
    trigger:
        set {_n} to 0
        broadcast "Starting loop with initial value of _n: %{_n}%"
        loop all items:
            run async:
                add 1 to {_n}
                broadcast value of field "%loop-value%" in redis hash "testHash"
                broadcast "Current value of _n: %{_n}%"
                if {_n} >= 10:
                    broadcast "Done with %{_n}% iterations"
                    stop run async
""")
@Since("1.2.0")
class EffStopRunAsync : Effect() {

    override fun init(args: Array<out Expression<*>?>?, i: Int, kleenean: Kleenean?, skriptParser: SkriptParser.ParseResult?): Boolean {
        return true
    }

    override fun execute(e: Event) {
        SecRunAsync.cancel(e)
    }

    override fun toString(e: Event?, debug: Boolean): String {
        return "stop run async"
    }

    companion object {
        init {
            ch.njol.skript.Skript.registerEffect(
                EffStopRunAsync::class.java,
                "stop run [redis] async"
            )
        }
    }
}
