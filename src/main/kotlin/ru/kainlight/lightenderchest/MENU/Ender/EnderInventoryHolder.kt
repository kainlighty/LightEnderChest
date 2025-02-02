package ru.kainlight.lightenderchest.MENU.Ender

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class EnderInventoryHolder() : InventoryHolder {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EnderInventoryHolder

        return inv == other.inv
    }

    override fun hashCode(): Int {
        return inv?.hashCode() ?: 0
    }


}