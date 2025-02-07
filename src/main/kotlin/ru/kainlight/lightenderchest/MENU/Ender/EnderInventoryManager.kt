package ru.kainlight.lightenderchest.MENU.Ender

import kotlinx.coroutines.launch
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.getStringWithPrefix
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightenderchest.isNull
import ru.kainlight.lightlibrary.BUILDERS.InventoryBuilder
import ru.kainlight.lightlibrary.BUILDERS.ItemBuilder
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.UTILS.Parser
import ru.kainlight.lightlibrary.sendMessage
import ru.kainlight.lightlibrary.sound

class EnderInventoryManager(private val username: String) {

    fun createInventory(fillBlockedItems: Boolean = true): Inventory {
        val holder = EnderInventoryHolder()

        val title = Main.instance.enderChestConfig.getConfig().getString("inventory.title", InventoryType.ENDER_CHEST.name)!!
        val inventoryBuilder = InventoryBuilder(Main.instance, Parser.mini(title), holder, totalSize, true)

        if(fillBlockedItems) {
            for (i in 0 until totalSize) {
                inventoryBuilder.setItem(i, blockedItems[i])
            }
        }

        val builtInventory = inventoryBuilder.build()
        holder.setInventory(builtInventory)

        inventoryBuilder
            .clickEvent { event -> this.clickInventoryEvent(event) }
            .closeEvent { event -> this.closeInventoryEvent(event) }

        return builtInventory
    }

    private fun clickInventoryEvent(event: InventoryClickEvent) {
        val viewer = event.whoClicked as? Player ?: return
        val inventoriesConfig = Main.instance.getMessagesConfig().getConfigurationSection("inventories") ?: return

        val inventoryOwnerName= username
        val hasViewer = viewer.name != inventoryOwnerName

        val replacedInventoryOwnerName = arrayOf("#username#" to inventoryOwnerName)

        if (hasViewer && !(viewer.hasPermission("lightenderchest.admin.edit") || viewer.hasPermission("lightenderchest.admin.edit.$inventoryOwnerName"))) {
            inventoriesConfig.getStringWithPrefix("admin.no-access.edit")
                .sendMessage(viewer, replace = replacedInventoryOwnerName)
            event.isCancelled = true
            return
        }

        val enderInventory = Database.Cache.getInventory(inventoryOwnerName, false) ?: return
        val inventory = if (enderInventory.inventory.holder is EnderInventoryHolder) enderInventory.inventory else return

        val slot = event.slot
        val currentItem = inventory.getItem(slot)
        val hasBlockedItem = blockedItems[slot].isSimilar(currentItem)

        if (hasViewer && !hasBlockedItem && event.isShiftClick && event.isRightClick) {
            event.isCancelled = true
            if(viewer.hasPermission("lightenderchest.admin.refund")) {
                viewer.inventorySound("refund")
                viewer.moveOrDropItemByCloseSlot(currentItem)
                enderInventory.closeSlot(slot)
            } else {
                Main.instance.getMessagesConfig().getStringWithPrefix("no-permissions")
                    ?.replace("#permission#", "lightenderchest.admin.refund")
                    ?.sendMessage(viewer)
            }
            return
        }

        if(currentItem == null) return
        if (!hasBlockedItem) return
        if (viewer.itemOnCursor.isNotEmpty() || event.isShiftClick || !event.isLeftClick) {
            event.isCancelled = true
            return
        }

        val isFree = hasViewer && viewer.hasPermission("lightenderchest.admin.buy")

        if (!isFree && hasViewer) {
            event.isCancelled = true
            viewer.inventorySound("failed")
            inventoriesConfig.getStringWithPrefix("admin.no-access.buy")
                .sendMessage(viewer, replace = replacedInventoryOwnerName)
            return
        }

        val economyManager = Main.instance.economyManager
        val balance = economyManager.getBalance(viewer)

        val slotCost = economyManager.calculateCostForSlot(slot)
        val doubleSlotCost = slotCost.toDouble()

        event.isCancelled = true
        if(balance >= doubleSlotCost || isFree) {
            if (economyManager.withdraw(viewer, slotCost, isFree)) {
                if (enderInventory.openSlot(slot)) {
                    val slot = slot + 1
                    inventoriesConfig.getStringWithPrefix("buy.purchased")!!
                        .replace("#slot#", slot.toString())
                        .replace("#price#", slotCost.toString())
                        .sendMessage(viewer)

                    viewer.inventorySound("successfully")
                    return
                }
            }
            viewer.inventorySound("failed")
            return
        } else {
            val calculateNeedMoney = doubleSlotCost - balance
            val isPrecision = Main.instance.enderChestConfig.getConfig().getInt("precision") <= 0
            val needToBuy = if(isPrecision) calculateNeedMoney.toInt() else calculateNeedMoney.toDouble()

            viewer.inventorySound("failed")
            inventoriesConfig.getStringWithPrefix("buy.not-enough-money")
                ?.replace("#price#", needToBuy.toString())
                .sendMessage(viewer)
            return
        }
    }

    private fun closeInventoryEvent(event: InventoryCloseEvent) {
        val owner: OfflinePlayer? = Main.instance.server.getOfflinePlayer(username)
        if(owner.isNull()) return
        if(owner!!.isOnline) ChestListener.getChest(owner.player)?.close()
        val viewer = event.player
        if(owner == viewer) return

        if(!owner.isOnline) {
            IOScope.launch {
                Database.Cache.getInventory(owner.name!!).let {
                    Database.updateInventory(it)
                }
            }
        }
    }

    private fun Player.moveOrDropItemByCloseSlot(currentItem: ItemStack?) {
        if(currentItem == null) return

        val leftover = this.inventory.addItem(currentItem)
        if (leftover.isNotEmpty()) {
            leftover.values.forEach { leftoverStack ->
                this.world.dropItem(this.location, leftoverStack)
            }
        }
    }

    private fun Player.inventorySound(soundName: String) {
        val soundSection = Main.instance.enderChestConfig.getConfig().getConfigurationSection("inventory.sound") !!
        val soundVolume = soundSection.getDouble("volume").toFloat()
        val soundPitch = soundSection.getDouble("pitch").toFloat()

        soundSection.getString(soundName).takeIf { !it.isNullOrBlank() }.let {
            info("Played sound $soundPitch for ${this.name}")
            this.sound(it, soundVolume, soundPitch)
        }
    }

    private fun ItemStack.isNotEmpty(): Boolean = !(type.isAir || amount <= 0)

    companion object {
        val totalSize: Int = Main.instance.enderChestConfig.getConfig().getInt("inventory.size", 54).coerceIn(8, 54)

        val blockedItems: List<ItemStack> by lazy {
            val blockedItemSection = Main.instance.enderChestConfig.getConfig().getConfigurationSection("inventory.blocked-item")!!

            val loreTemplate = blockedItemSection.getStringList("lore")
            val materialName = blockedItemSection.getString("material", "RED_STAINED_GLASS_PANE")!!
            val glowing = blockedItemSection.getBoolean("glow", false)

            return@lazy (0 until totalSize).map { index ->
                val price = Main.instance.economyManager.calculateCostForSlot(index)
                val name = blockedItemSection.getString("name", "")!!.replace("#price#", price.toString())
                val replacedLore = loreTemplate.map { it.replace("#cost#", price.toString()) }

                ItemBuilder(materialName)
                    .displayName(name)
                    .lore(*replacedLore.toTypedArray())
                    .glow(glowing)
                    .defaultFlags()
                    .build()
            }
        }
    }
}