package dev.bypixel.skredis.skript.elements.section

import ch.njol.skript.Skript
import ch.njol.skript.config.SectionNode
import ch.njol.skript.doc.Description
import ch.njol.skript.doc.Examples
import ch.njol.skript.doc.Name
import ch.njol.skript.doc.Since
import ch.njol.skript.lang.EffectSection
import ch.njol.skript.lang.Expression
import ch.njol.skript.lang.SkriptParser
import ch.njol.skript.lang.TriggerItem
import ch.njol.skript.variables.Variables
import ch.njol.util.Kleenean
import dev.bypixel.skredis.Main
import org.bukkit.Bukkit
import org.bukkit.event.Event
import java.util.concurrent.atomic.AtomicReference

@Name("Async Operations - run async")
@Description("Runs the following code asynchronously on another thread. Useful for long-running operations.")
@Examples("""
command /test1:
    trigger:
        loop all items:
            set field "%loop-value%" to "test" in redis hash "testHash"

command /test2:
    trigger:
        loop all items:
            run async:
                broadcast value of field "%loop-value%" in redis hash "testHash"
""")
@Since("1.2.0")
class SecRunAsync : EffectSection() {

    private var next: TriggerItem? = null

    override fun init(
        args: Array<out Expression<*>?>?,
        matchedPattern: Int,
        kleenean: Kleenean?,
        parseResult: SkriptParser.ParseResult?,
        node: SectionNode?,
        triggerItems: List<TriggerItem?>?
    ): Boolean {
        next = last
        loadCode(node) // Important: load section content
        return true
    }

    override fun walk(e: Event?): TriggerItem? {
        if (e == null) return null

        val localVars = AtomicReference(Variables.copyLocalVariables(e))

        Bukkit.getScheduler().runTaskAsynchronously(Main.INSTANCE, Runnable {
            Variables.setLocalVariables(e, localVars.get())
            walkSectionWithCancelCheck(e)
            clear(e)
        })

        return next
    }

    private fun walkSectionWithCancelCheck(e: Event) {
        var current: TriggerItem? = first
        while (current != null) {
            if (isCancelled(e)) break

            current = invokeWalk(current, e)
        }
    }


    private fun invokeWalk(item: TriggerItem, event: Event): TriggerItem? {
        val method = TriggerItem::class.java.getDeclaredMethod("walk", Event::class.java)
        method.isAccessible = true
        return method.invoke(item, event) as? TriggerItem
    }

    override fun toString(e: Event?, debug: Boolean): String = "run async"

    companion object {
        private val cancelMap = mutableSetOf<Event>()

        fun cancel(event: Event) {
            cancelMap.add(event)
        }

        fun isCancelled(event: Event): Boolean = event in cancelMap

        fun clear(event: Event) {
            cancelMap.remove(event)
        }

        init {
            Skript.registerSection(
                SecRunAsync::class.java,
                "run [redis] async"
            )
        }
    }

}