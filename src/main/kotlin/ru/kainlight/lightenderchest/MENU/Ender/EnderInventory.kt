package ru.kainlight.lightenderchest.MENU.Ender

import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightlibrary.UTILS.IOScope

data class EnderInventory(
    val username: String,
    val inventory: Inventory,
    val openedSlots: MutableSet<Int> = mutableSetOf()
) {

    companion object {

        /**
         * Список закрытых слотов.
         * Высчитывается исходя из открытых слотов
         */
        fun getClosedSlots(openedSlots: MutableSet<Int>): List<Int> {
            return (0 until EnderInventoryManager.totalSize).filterNot { it in openedSlots }
        }

        fun closeInventoryForViewers(inventory: Inventory) {
            inventory.viewers.toList().mapNotNull { it as? Player }.forEach {
                ChestListener.getChest(it)?.close()
                it.closeInventory()
            }
        }
    }

    fun openSlot(slot: Int): Boolean {
        this.openedSlots.add(slot).let {
            inventory.setItem(slot, null)
            info("Player $username has been opened slot $slot")
            return it
        }
    }

    fun closeSlot(slot: Int): Boolean {
        this.openedSlots.remove(slot).let {
            this.inventory.setItem(slot, EnderInventoryManager.blockedItems[slot])
            info("Slot $slot has been closed for $username")
            return it
        }
    }

    fun getClosedSlots(): List<Int> {
        return getClosedSlots(openedSlots)
    }

    /**
     * Получение списка предметов в инвентаре
     */
    fun getValidItems(): List<ItemStack> {
        return inventory.contents
            .filterNotNull()
            .filter { item ->
                ! item.type.isAir &&
                        ! EnderInventoryManager.blockedItems.contains(item)
            }
    }

    /**
     * Получения количества предметов
     */
    fun getValidItemCount(): Int {
        return inventory.contents
            .filterNotNull()
            .count { item ->
                ! item.type.isAir &&
                        ! EnderInventoryManager.blockedItems.contains(item)
            }
    }

    fun openInventory(player: Player? = Main.instance.server.getPlayer(username)): Player? {
        if (inventory.holder !is EnderInventoryHolder) return null
        player?.let {
            it.openInventory(inventory)
            info("Player $username opened the enderchest")
            return it
        }
        return null
    }

    fun closeInventory(): Player? {
        if (inventory.holder !is EnderInventoryHolder) return null
        Main.instance.server.getPlayer(username)?.let {
            it.closeInventory()
            info("Player $username closed the enderchest")
            return it
        }
        return null
    }

    fun closeInventoryForViewers() {
        closeInventoryForViewers(inventory)
        info("The $username inventory has been closed to everyone")
    }

    fun clearInventory() {
        info("Inventory has been cleared for $username")
        openedSlots.toList().forEach { inventory.setItem(it, null) }
        IOScope.launch {
            Database.updateInventory(this@EnderInventory)
        }
    }

    fun resetInventory() = IOScope.async {
        info("Inventory has been reset for $username")
        return@async Database.removeInventory(username) > 0
    }

    fun simpleString(): String {
        return "EnderInventory(username='$username', openedSlots=$openedSlots, closedSlots=${getClosedSlots()})"
    }

}