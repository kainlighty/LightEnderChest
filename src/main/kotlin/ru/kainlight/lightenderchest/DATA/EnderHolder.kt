package ru.kainlight.lightenderchest.DATA

import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class EnderHolder : InventoryHolder {
    private var inv: Inventory? = null

    fun setInventory(inventory: Inventory) {
        this.inv = inventory
    }

    override fun getInventory(): Inventory {
        return inv !!
    }

    override fun toString(): String {
        return "EnderHolder(inv=$inv)"
    }


}