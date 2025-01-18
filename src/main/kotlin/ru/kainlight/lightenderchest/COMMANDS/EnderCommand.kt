package ru.kainlight.lightenderchest.COMMANDS

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.kainlight.lightenderchest.DATA.EnderInventory
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightlibrary.multiMessage

class EnderCommand(private val plugin: Main) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        println(args.joinToString())
        when(args.size) {
            1 -> {
                if(sender !is Player) return true
                val username = args[0]

                EnderInventory.getSavedInventory(username).thenAccept {  savedInventory ->
                    if(sender.openEnderchest() == null) {
                        sender.multiMessage("Нет инвентаря")
                        return@thenAccept
                    }
                }
                return true
            }

            2 -> {
                if(sender is Player) {
                    val action = args[0].lowercase()
                    val username = args[1]

                    val player = plugin.server.getPlayer(username)
                    if(player == null) {
                        sender.multiMessage("Нет игрока")
                        return true
                    }

                    EnderInventory.getSavedInventory(username).thenAccept { savedInventory ->
                        if(savedInventory == null) {
                            sender.multiMessage("Нет инвентаря")
                            return@thenAccept
                        }

                        when(action) {
                            "open" -> {
                                player.openEnderchest()
                                sender.multiMessage("Инвентарь открыт для игрока $username")
                                player.multiMessage("Вам открыл инвентарь ${sender.name}")
                            }
                            "close" -> {
                                plugin.runTask { savedInventory.closeInventory() }
                                sender.multiMessage("Инвентарь закрыт для игрока $username")
                                player.multiMessage("Вам закрыл инвентарь ${sender.name}")
                            }
                            "clear" -> {
                                savedInventory.clearInventoryAsync()
                                sender.multiMessage("ОК")
                            }
                            "reset" -> {
                                savedInventory.resetInventoryAsync()
                                sender.multiMessage("ОК")
                            }
                        }
                    }
                    return true
                }
                return true
            }

            else -> {
                if(sender !is Player) return true

                if(sender.openEnderchest() == null) {
                    sender.multiMessage("Нет инвентаря")
                    return true
                }
                return true
            }
        }
    }

    private fun Player.openEnderchest(): EnderInventory.SavedInventory? {
        EnderInventory.getSavedInventory(this.name).thenAccept { savedInventory ->
            if(savedInventory == null) {
                return@thenAccept
            } else {
                plugin.runTask { savedInventory.openInventory() }
            }
        }
        return null
    }
}