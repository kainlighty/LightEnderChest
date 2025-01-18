package ru.kainlight.lightenderchest.UTILS

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightlibrary.BUILDERS.ItemBuilder

object InternalConfig {
    const val INVENTORY_TITLE = "Эндер-сундук"

    val blockedItem by lazy {
        val blockedMat = Main.instance.config.getString("inventory.blocked-item", "RED_STAINED_GLASS_PANE")!!

        val item = ItemStack(Material.valueOf(blockedMat))

        item
    }

    val emptyItem by lazy {
        ItemStack(Material.AIR)
    }
}
