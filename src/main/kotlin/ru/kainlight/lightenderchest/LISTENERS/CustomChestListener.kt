package ru.kainlight.lightenderchest.LISTENERS

import net.kyori.adventure.sound.Sound
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.DATA.EnderHolder
import ru.kainlight.lightenderchest.DATA.EnderInventory
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.UTILS.InternalConfig
import ru.kainlight.lightlibrary.ECONOMY.LightEconomy
import ru.kainlight.lightlibrary.multiMessage

class CustomChestListener(private val plugin: Main) : Listener {

    @EventHandler
    fun onCreateInventoryEvent(event: PlayerJoinEvent) {
        EnderInventory(plugin, event.player).getOrCreateSavedInventory().thenAccept { saved ->
            plugin.logger.info("Создали/загрузили инвентарь для ${event.player.name}, открыто слотов: ${saved.openedSlots.size}")
        }.exceptionally { ex ->
            ex.printStackTrace()
            null
        }
    }

    @EventHandler
    fun onOpenEvent(event: InventoryOpenEvent) {
        val player = event.player
        if(player !is Player) return
        val inventory = event.inventory
        if (inventory.type != InventoryType.ENDER_CHEST) return

        if (inventory.holder is EnderHolder) return

        event.isCancelled = true

        EnderInventory(plugin, player).open()
    }

    @EventHandler
    fun clickInventoryEvent(event: InventoryClickEvent) {
        val slot = event.slot
        val inventory = event.clickedInventory ?: return
        println("gg")
        if(inventory.holder !is EnderHolder) return
        println(slot)
        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return
        if (currentItem != InternalConfig.blockedItem) return

        if (event.isShiftClick) {
            event.isCancelled = true
            return
        }

        val player = event.whoClicked
        if (player !is Player) return

        val balance = LightEconomy.POINTS?.getBalance(player) ?: return
        if (balance < 300.0) {
            event.isCancelled = true
            player.multiMessage("Недостаточно средств")
            val sound = Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_HURT.key, Sound.Source.MASTER, 1f, 1f)
            player.playSound(sound)
            return
        }

        // Асинхронно загружаем SavedInventory, добавляем слот, сохраняем
        Database.getInventoryAsync(player.name).thenAccept { saved ->
            if(saved == null) return@thenAccept
            println("sdgsg")
            // Устанавливаем открытый слот в памяти
            saved.openSlotAsync(slot).thenAccept {
                plugin.runTask {
                    Runnable {
                        event.isCancelled = true
                        event.currentItem = ItemStack(Material.AIR) // убираем блок
                        val sound = Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key, Sound.Source.MASTER, 1f, 1f)
                        player.playSound(sound)
                        player.multiMessage("Слот $slot куплен")
                    }
                }
            }
        }
    }

    @EventHandler
    fun closeInventoryEvent(event: InventoryCloseEvent) {
        val player = event.player
        if (player !is Player) return
        val holder = event.inventory.holder ?: return
        if(holder !is EnderHolder) return

        // Забираем инвентарь и сохраняем содержимое
        // Асинхронный апдейт в БД
        Database.getInventoryAsync(player.name).thenAccept { saved ->
            if(saved == null) return@thenAccept
            //saved.inventory.contents = event.inventory.contents
            Database.updateInventoryAsync(saved)
        }
    }

}