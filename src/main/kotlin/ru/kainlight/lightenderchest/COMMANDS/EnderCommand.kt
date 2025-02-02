package ru.kainlight.lightenderchest.COMMANDS

import kotlinx.coroutines.launch
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.MENU.Data.DataInventoryManager
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventory
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventoryManager
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightenderchest.prefixMessage
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.UTILS.IOScopeLaunch
import ru.kainlight.lightlibrary.UTILS.bukkitThread
import ru.kainlight.lightlibrary.UTILS.bukkitThreadNotNull
import ru.kainlight.lightlibrary.equalsIgnoreCase
import ru.kainlight.lightlibrary.getPlayer
import ru.kainlight.lightlibrary.message
import ru.kainlight.lightlibrary.multiMessage

class EnderCommand(private val plugin: Main) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if(!sender.hasEnderPermission("use")) return true
        info("${sender.name} perform command with arguments: ${args.joinToString(",")}")

        val inventoriesSection = plugin.getMessagesConfig().getConfigurationSection("inventories") !!

        when (args.size) {
            0 -> {
                sender.getPlayer()?.let { player ->
                    Database.Cache.getOrCreateInventory(player.name).openInventory()
                } ?: run {
                    sender.message("<red>This command can only be used by the player")
                }
                return true
            }

            1 -> {
                val action = args[0].trim()

                when (action.lowercase()) {
                    "help" -> {
                        this.sendHelp(sender)
                        return true
                    }

                    "clear" -> {
                        if (! sender.hasEnderPermission("clear")) return true
                        sender.getPlayer()?.let { player ->
                            Database.Cache.getInventory(player.name)?.clearInventory()
                            inventoriesSection.getString("clear").let {
                                sender.prefixMessage(it)
                            }
                        } ?: run {
                            sender.message("<red>This command can only be used by a player")
                        }
                        return true
                    }

                    "reload" -> {
                        if (! sender.hasEnderPermission("reload")) return true
                        plugin.reloadConfigurations()
                        plugin.getMessagesConfig().getString("reload").let {
                            sender.prefixMessage(it)
                        }
                        return true
                    }
                }

                // $ /lec <playerName> (смотреть чужой)
                if (! sender.hasEnderPermission("admin.view")) return true
                sender.getPlayer()?.let { player ->
                    val username = action
                    val replacedUsername = arrayOf("#username#" to username)

                    Database.Cache.getInventory(username)?.let { inventory ->
                        player.openInventory(inventory.inventory)
                        inventoriesSection.getString("admin.view").let {
                            sender.prefixMessage(it, replace = replacedUsername)
                        }
                    } ?: run {
                        IOScope.launch {
                            Database.getInventory(username)?.bukkitThread { inventory ->
                                player.openInventory(inventory.inventory)
                                inventoriesSection.getString("admin.view").let {
                                    sender.prefixMessage(it, replace = replacedUsername)
                                }
                            } ?: run {
                                inventoriesSection.getString("not-found").let {
                                    sender.prefixMessage(it, replace = replacedUsername)
                                }
                            }
                        }
                    }
                } ?: run {
                    sender.message("<red>This command can only be used by a player")
                }
                return true
            }

            2 -> { // /lec <action> <username>
                val action = args[0].lowercase()
                val username = args[1].trim()

                return when(action) {
                    "cache" -> handleCacheCommands(sender, username)
                    else -> handleAdminCommands(username, sender, action, inventoriesSection)
                }
            }

            else -> { // Если много аргументов
                this.sendHelp(sender)
                return true
            }
        }
    }

    /**
     * Обработка субкоманд /lec cache <subcommand>
     */
    private fun handleCacheCommands(sender: CommandSender, arg: String): Boolean {
        val cacheSection = plugin.getMessagesConfig().getConfigurationSection("cache")!!

        when (arg.lowercase()) {
            "data" -> {
                if (! sender.hasEnderPermission("cache.data")) return true
                sender.getPlayer()?.let { player ->
                    DataInventoryManager(plugin, player).let {
                        IOScope.launch {
                            it.open()
                        }
                    }
                }
                return true
            }

            "refresh" -> {
                if (! sender.hasEnderPermission("cache.refresh")) return true
                Database.Cache.refreshAllAsync()
                cacheSection.getString("refresh").let {
                    sender.prefixMessage(it)
                }
                return true
            }

            "purge" -> {
                if (! sender.hasEnderPermission("cache.purge")) return true
                Database.Cache.purge()
                cacheSection.getString("purge").let {
                    sender.prefixMessage(it)
                }
                return true
            }

            "clear" -> {
                if (! sender.hasEnderPermission("cache.clear")) return true
                Database.Cache.clear()
                cacheSection.getString("clear").let {
                    sender.prefixMessage(it)
                }
                return true
            }

            "clear-offline" -> {
                if (! sender.hasEnderPermission("cache.clear-offline")) return true
                Database.Cache.clearOfflines()
                cacheSection.getString("clear").let {
                    sender.prefixMessage(it)
                }
                return true
            }

            "clear-online" -> {
                if (! sender.hasEnderPermission("cache.clear-online")) return true
                Database.Cache.clearOnlines()
                cacheSection.getString("clear").let {
                    sender.prefixMessage(it)
                }
                return true
            }

            "data-copy" -> {
                if(sender !is Player) return true
                var i = 1
                repeat(74) {
                    i++
                    val name = "test$i"
                    Database.Cache.set(name, EnderInventory(name, EnderInventoryManager(sender).createInventory()))
                }
                return true
            }
            else -> return true
        }
    }

    private fun handleAdminCommands(username: String, sender: CommandSender, action: String, inventoriesSection: ConfigurationSection): Boolean {
        val player = plugin.server.getPlayerExact(username)
        val replacedUsername = arrayOf("#username#" to username)

        if (player == null) {
            plugin.getMessagesConfig().getString("player-not-found").let {
                sender.prefixMessage(it, replace = replacedUsername)
            }
            return true
        }

        val savedInventory = Database.Cache.getInventory(username)
        if (savedInventory == null) {
            inventoriesSection.getString("not-found").let {
                sender.prefixMessage(it, replace = replacedUsername)
            }
            return true
        }

        val adminSection = inventoriesSection.getConfigurationSection("admin")!!

        when (action) {
            "open" -> {
                if (! sender.hasEnderPermission("admin.open")) return true
                savedInventory.openInventory()
                adminCommandMessage(adminSection, "open", player, sender)
                return true
            }

            "close" -> {
                if (! sender.hasEnderPermission("admin.close")) return true
                savedInventory.closeInventory()
                adminCommandMessage(adminSection, "close", player, sender)
                return true
            }

            "clear" -> {
                if (! sender.hasEnderPermission("admin.clear")) return true
                savedInventory.clearInventory()
                adminCommandMessage(adminSection, "clear", player, sender)
                return true
            }

            "reset" -> {
                if (! sender.hasEnderPermission("admin.reset")) return true
                savedInventory.resetInventory().IOScopeLaunch(code = {
                    if(it > 0) it.bukkitThreadNotNull {
                        adminCommandMessage(adminSection, "reset", player, sender)
                    }
                })

                return true
            }
            else -> return true
        }
    }

    /**
     * Сообщения при операциях "open", "close", "clear", "reset" с другим игроком
     */
    private fun adminCommandMessage(section: ConfigurationSection, category: String, player: Player, sender: CommandSender) {
        section.getString("$category.owner")?.let {
            player.prefixMessage(it.replace("#username#", sender.name))
        }
        section.getString("$category.viewer")?.let {
            sender.prefixMessage(it.replace("#username#", player.name))
        }
    }

    private fun sendHelp(sender: CommandSender) {
        plugin.getMessagesConfig().getStringList("help").forEach {
            sender.multiMessage(it)
        }
    }

    class Completer() : TabCompleter {

        override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String>? {
            when (args.size) {
                1 -> {
                    val result = mutableListOf<String>()

                    if(sender.hasPermission("lightenderchest.admin.*") ||
                        sender.hasPermission("lightenderchest.cache.*") ||
                        sender.hasPermission("lightenderchest.*")) {
                        result += "help"
                    }

                    // Если у sender есть право на reload
                    if (sender.hasPermission("lightenderchest.reload")) {
                        result += "reload"
                    }
                    // Если может открывать чужие (admin.open) или хотя бы своё (lightenderchest.use)
                    if (sender.hasPermission("lightenderchest.admin.open")) {
                        result += "open"
                    }
                    // Если может закрывать чужие (admin.close)
                    if (sender.hasPermission("lightenderchest.admin.close")) {
                        result += "close"
                    }
                    // Если может чистить — либо своё (lightenderchest.clear), либо чужое (admin.clear)
                    if (sender.hasPermission("lightenderchest.clear") || sender.hasPermission("lightenderchest.admin.clear")) {
                        result += "clear"
                    }
                    // Если может ресетить чужое
                    if (sender.hasPermission("lightenderchest.admin.reset")) {
                        result += "reset"
                    }
                    // Если есть право на работу с cache
                    if (sender.hasPermission("lightenderchest.cache.refresh") ||
                        sender.hasPermission("lightenderchest.cache.purge") ||
                        sender.hasPermission("lightenderchest.cache.clear") ||
                        sender.hasPermission("lightenderchest.cache.clear-offline") ||
                        sender.hasPermission("lightenderchest.cache.clear-online") ||
                        sender.hasPermission("lightenderchest.cache.data")) {
                        result += "cache"
                    }

                    if (sender.hasPermission("lightenderchest.admin.view") ||
                        sender.hasPermission("lightenderchest.admin.open") ||
                        sender.hasPermission("lightenderchest.admin.close") ||
                        sender.hasPermission("lightenderchest.admin.clear") ||
                        sender.hasPermission("lightenderchest.admin.reset")) {
                        val players = Main.instance.server.onlinePlayers.map { it.name }
                        result += players
                    }

                    // Фильтруем по текущему аргументу (если уже набирается часть слова)
                    return result.filter { it.startsWith(args[0], ignoreCase = true) }
                }

                2 -> {
                    if (args[0].equalsIgnoreCase("cache")) {
                        val subList = mutableListOf<String>()

                        // Проверяем каждую подкоманду cache
                        if (sender.hasPermission("lightenderchest.cache.refresh")) {
                            subList += "refresh"
                        }
                        if (sender.hasPermission("lightenderchest.cache.purge")) {
                            subList += "purge"
                        }
                        if (sender.hasPermission("lightenderchest.cache.clear-offline")) {
                            subList += "clear-offline"
                        }
                        if (sender.hasPermission("lightenderchest.cache.clear-online")) {
                            subList += "clear-online"
                        }
                        if (sender.hasPermission("lightenderchest.cache.clear")) {
                            subList += "clear"
                        }
                        if (sender.hasPermission("lightenderchest.cache.data")) {
                            subList += "data"
                        }

                        return subList.filter { it.startsWith(args[1], ignoreCase = true) }
                    }
                }
            }

            return null
        }
    }

}

// ! ---------------------------------------------------------------------------------------------------------------

fun CommandSender.hasEnderPermission(permission: String, message: Boolean = true): Boolean {
    val permissionName = "lightenderchest.$permission"
    val hasPerm = this.hasPermission(permissionName)

    info("Permission $permission has $hasPerm for executor ${this.name}")

    if (! hasPerm) {
        if (message) {
            Main.instance.getMessagesConfig().getString("no-permissions")?.replace("#permission#", permissionName)?.let {
                this.prefixMessage(it)
            }
        }
    }
    return hasPerm
}