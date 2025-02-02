package ru.kainlight.lightenderchest.MENU.Data

import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class DataInventoryHolder(val pageIndex: Int) : InventoryHolder {

    override fun getInventory(): Inventory {
        return Bukkit.createInventory(null, 54)
    }
}
