package ru.kainlight.lightenderchest.LISTENERS

import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.UTILS.InternalConfig

class DefaultChestListener(private val plugin: Main) : Listener {

    private val openedSlots = mutableSetOf<Int>()

    @EventHandler
    fun onOpenEvent(event: InventoryOpenEvent) {
        val inv = event.inventory
        if(inv.type != InventoryType.ENDER_CHEST) return
        val slot = inv.size

        for(i in 0 until slot) {
            if(openedSlots.contains(i)) {
                continue
            }
            inv.setItem(i, InternalConfig.blockedItem)
        }
    }

    @EventHandler
    fun onClickEvent(event: InventoryClickEvent) {
        val inv = event.clickedInventory
        if(inv == null) return
        if(inv.type != InventoryType.ENDER_CHEST) return
        val slot = event.slot
        val currentItem = inv.getItem(slot)
        if(currentItem == null) return
        if(currentItem != InternalConfig.blockedItem) return

        inv.setItem(slot, ItemStack(Material.AIR))
        openedSlots.add(slot)
    }

}