package ru.kainlight.lightenderchest.COMMANDS

import kotlinx.coroutines.launch
import org.bukkit.OfflinePlayer
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.configuration.ConfigurationSection
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.MENU.Data.DataInventoryManager
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.getStringWithPrefix
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightenderchest.isNull
import ru.kainlight.lightlibrary.*
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.UTILS.IOScopeLaunch
import ru.kainlight.lightlibrary.UTILS.bukkitThread
import ru.kainlight.lightlibrary.UTILS.bukkitThreadNotNull

class EnderCommand(private val plugin: Main) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (! sender.hasEnderPermission("use")) return true
        info("${sender.name} perform command with arguments: ${args.joinToString(", ")}")

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
                            inventoriesSection.getStringWithPrefix("clear")
                                .sendMessage(sender)

                        } ?: run {
                            sender.message("<red>This command can only be used by a player")
                        }
                        return true
                    }

                    "reload" -> {
                        if (! sender.hasEnderPermission("reload")) return true
                        plugin.reloadConfigurations()
                        plugin.getMessagesConfig().getStringWithPrefix("reload")
                            ?.sendMessage(sender)
                        return true
                    }

                    "test" -> {
                        IOScope.launch {
                            Database.getInventory(sender.name)?.let { inv ->
                                inv.inventory.bukkitThread {
                                    sender.getPlayer()!!.openInventory(it)
                                }
                            }
                        }
                        return true
                    }
                }

                // /lec <username>
                if (! sender.hasEnderPermission("admin.view")) return true
                sender.getPlayer()?.let { player ->
                    val username = action

                    val replacedUsername = arrayOf("#username#" to username)

                    if (plugin.server.getOfflinePlayer(username).isNull()) {
                        plugin.getMessagesConfig().getStringWithPrefix("player-not-found")
                            .sendMessage(player, replace = replacedUsername)
                        return true
                    }

                    Database.Cache.getOrCreateInventory(username).let { inventory ->
                        player.openInventory(inventory.inventory)
                        inventoriesSection.getStringWithPrefix("admin.view")
                            .sendMessage(sender, replace = replacedUsername)
                    }
                    return true
                } ?: run {
                    sender.message("<red>This command can only be used by a player")
                }
                return true
            }

            2 -> { // /lec <action> <username>
                val action = args[0].lowercase()
                val username = args[1].trim()

                return when (action) {
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
        val cacheSection = plugin.getMessagesConfig().getConfigurationSection("cache") !!

        when (arg.lowercase()) {
            "data" -> {
                if (! sender.hasEnderPermission("cache.data")) return true
                sender.getPlayer()?.let { player ->
                    DataInventoryManager(plugin, player).open()
                }
                return true
            }

            "refresh" -> {
                if (! sender.hasEnderPermission("cache.refresh")) return true
                Database.Cache.refreshAsync()
                cacheSection.getStringWithPrefix("refresh")
                    .sendMessage(sender)
                return true
            }

            "purge" -> {
                if (! sender.hasEnderPermission("cache.purge")) return true
                Database.Cache.purge()
                cacheSection.getStringWithPrefix("purge")
                    .sendMessage(sender)
                return true
            }

            "clear" -> {
                if (! sender.hasEnderPermission("cache.clear")) return true
                Database.Cache.clear()
                cacheSection.getStringWithPrefix("clear")
                    .sendMessage(sender)
                return true
            }

            "clear-offline" -> {
                if (! sender.hasEnderPermission("cache.clear-offline")) return true
                Database.Cache.clearOfflines()
                cacheSection.getStringWithPrefix("clear")
                    .sendMessage(sender)
                return true
            }

            "clear-online" -> {
                if (! sender.hasEnderPermission("cache.clear-online")) return true
                Database.Cache.clearOnlines()
                cacheSection.getStringWithPrefix("clear")
                    .sendMessage(sender)
                return true
            }

            else -> return true
        }
    }

    private fun handleAdminCommands(
        username: String,
        sender: CommandSender,
        action: String,
        inventoriesSection: ConfigurationSection
    ): Boolean {
        val offlinePlayer: OfflinePlayer? = plugin.server.getOfflinePlayer(username)
        val replacedUsername = arrayOf("#username#" to username)

        if (offlinePlayer.isNull()) {
            plugin.getMessagesConfig().getStringWithPrefix("player-not-found")
                .sendMessage(sender, replace = replacedUsername)
            return true
        }

        val savedInventory = Database.Cache.getInventory(username)
        if (savedInventory == null) {
            inventoriesSection.getStringWithPrefix("not-found")
                .sendMessage(sender, replace = replacedUsername)
            return true
        }

        val adminSection = inventoriesSection.getConfigurationSection("admin") !!

        when (action) {
            "open" -> {
                if (! sender.hasEnderPermission("admin.open")) return true
                if (! offlinePlayer !!.isOnline) {
                    plugin.getMessagesConfig().getStringWithPrefix("player-not-found")
                        .sendMessage(sender, replace = replacedUsername)
                    return true
                }
                savedInventory.openInventory()
                adminCommandMessage(adminSection, "open", offlinePlayer, sender)
                return true
            }

            "close" -> {
                if (! sender.hasEnderPermission("admin.close")) return true
                if (! offlinePlayer !!.isOnline) {
                    plugin.getMessagesConfig().getStringWithPrefix("player-not-found")
                        .sendMessage(sender, replace = replacedUsername)
                    return true
                }
                savedInventory.closeInventory()
                adminCommandMessage(adminSection, "close", offlinePlayer, sender)
                return true
            }

            "clear" -> {
                if (! sender.hasEnderPermission("admin.clear")) return true
                savedInventory.clearInventory()
                adminCommandMessage(adminSection, "clear", offlinePlayer !!, sender)
                return true
            }

            "reset" -> {
                if (! sender.hasEnderPermission("admin.reset")) return true
                savedInventory.resetInventory().IOScopeLaunch(code = { isRemoved ->
                    if (isRemoved) bukkitThreadNotNull {
                        adminCommandMessage(adminSection, "reset", offlinePlayer !!, sender)
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
    private fun adminCommandMessage(section: ConfigurationSection, category: String, offlinePlayer: OfflinePlayer, sender: CommandSender
    ) {
        if (offlinePlayer.isOnline) section.getStringWithPrefix("$category.owner")
            ?.replace("#username#", sender.name)
            ?.sendMessage(offlinePlayer.player)

        section.getStringWithPrefix("$category.viewer")
            ?.replace("#username#", offlinePlayer.name ?: "")
            .sendMessage(sender)
    }

    private fun sendHelp(sender: CommandSender) {
        if (! sender.hasEnderPermission("help")) return
        plugin.getMessagesConfig().getStringList("help").forEach {
            sender.multiMessage(it)
        }
    }

    class Completer() : TabCompleter {

        override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>
        ): List<String>? {
            when (args.size) {
                1 -> {
                    val result = mutableListOf<String>()

                    if (sender.hasPermission("lightenderchest.help") ||
                        sender.hasPermission("lightenderchest.admin.*") ||
                        sender.hasPermission("lightenderchest.cache.*") ||
                        sender.hasPermission("lightenderchest.*")) {
                        result += "help"
                    }
                    if (sender.hasPermission("lightenderchest.reload")) {
                        result += "reload"
                    }
                    if (sender.hasPermission("lightenderchest.admin.open")) {
                        result += "open"
                    }
                    if (sender.hasPermission("lightenderchest.admin.close")) {
                        result += "close"
                    }
                    if (sender.hasPermission("lightenderchest.clear") || sender.hasPermission("lightenderchest.admin.clear")) {
                        result += "clear"
                    }
                    if (sender.hasPermission("lightenderchest.admin.reset")) {
                        result += "reset"
                    }
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
            Main.instance.getMessagesConfig().getStringWithPrefix("no-permissions")
                ?.replace("#permission#", permissionName)
                ?.sendMessage(this)
        }
    }
    return hasPerm
}