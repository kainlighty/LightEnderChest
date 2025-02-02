package ru.kainlight.lightenderchest.LISTENERS

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightlibrary.startsWithIgnoreCase

class CommandAliasListener : Listener {

    private val aliases = Main.instance.config.getStringList("command-aliases").map { it.lowercase() }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        if(event.isCancelled) return
        val message = event.message
        if (!message.startsWith("/")) return
        if(message.startsWithIgnoreCase("/lightenderchest") || message.startsWithIgnoreCase("/lec")) return
        val withoutSlash = message.removePrefix("/")
        val parts = withoutSlash.split(" ")

        val commandAlias = parts.firstOrNull()?.lowercase() ?: return
        if (aliases.contains(commandAlias)) {
            // Убирается alias + собираю остальную часть
            val args = parts.drop(1).joinToString(" ")
            // Замена команды
            event.message = "/lightenderchest $args"
        }
    }

}