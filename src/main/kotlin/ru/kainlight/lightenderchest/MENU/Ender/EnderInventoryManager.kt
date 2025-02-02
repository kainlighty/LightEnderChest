package ru.kainlight.lightenderchest.MENU.Ender

import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightenderchest.prefixMessage
import ru.kainlight.lightlibrary.BUILDERS.InventoryBuilder
import ru.kainlight.lightlibrary.BUILDERS.ItemBuilder
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.sound

class EnderInventoryManager(private val player: Player?) {

    fun createInventory(fillBlockedItems: Boolean = true): Inventory {
        val holder = EnderInventoryHolder()

        val title = Main.instance.enderChestConfig.getConfig().getString("inventory.title", InventoryType.ENDER_CHEST.name)
        val size = totalSize
        val inventoryBuilder = InventoryBuilder(Main.instance, title, holder, size, true)

        if(fillBlockedItems) {
            for (i in 0 until size) {
                inventoryBuilder.setItem(i, blockedItems[i])
            }
        }

        val builtInventory = inventoryBuilder.build()

        holder.setInventory(builtInventory)

        inventoryBuilder.clickEvent { event -> this.clickInventoryEvent(event) }
        inventoryBuilder.closeEvent { event -> this.closeInventoryEvent(event) }

        return builtInventory
    }

    private fun clickInventoryEvent(event: InventoryClickEvent) {
        val whoClicked = event.whoClicked as? Player ?: return
        val inventoriesConfig = Main.instance.getMessagesConfig().getConfigurationSection("inventories") !!

        val inventoryOwner = if (player != null) player else {
            inventoriesConfig.getString("offline")?.let {
                whoClicked.prefixMessage(it)
            }
            event.isCancelled = true
            return
        }

        val inventoryOwnerName = inventoryOwner.name
        val hasViewer = whoClicked != inventoryOwner

        val replacedInventoryOwnerName = arrayOf("#username#" to inventoryOwnerName)

        if (hasViewer && !(whoClicked.hasPermission("lightenderchest.admin.edit") || whoClicked.hasPermission("lightenderchest.admin.edit.$inventoryOwnerName"))) {
            inventoriesConfig.getString("admin.no-access.edit")?.let {
                whoClicked.prefixMessage(it, replace = replacedInventoryOwnerName)
            }
            event.isCancelled = true
            return
        }

        val enderInventory = Database.Cache.getInventory(inventoryOwnerName) ?: return
        val inventory = if (enderInventory.inventory.holder is EnderInventoryHolder) enderInventory.inventory else return

        val slot = event.slot
        val currentItem = inventory.getItem(slot)
        val hasBlockedItem = blockedItems[slot].isSimilar(currentItem)

        if (hasViewer && !hasBlockedItem && event.isShiftClick && event.isRightClick) {
            event.isCancelled = true
            if(whoClicked.hasPermission("lightenderchest.admin.refund")) {
                whoClicked.inventorySound("refund")
                whoClicked.moveOrDropItemByCloseSlot(currentItem)
                enderInventory.closeSlot(slot)
            } else {
                Main.instance.getMessagesConfig().getString("no-permissions")?.replace("#permission#", "lightenderchest.admin.refund")?.let {
                    whoClicked.prefixMessage(it)
                }
            }
            return
        }

        if(currentItem == null) return
        if (!hasBlockedItem) return
        if (!player.itemOnCursor.isEmpty || event.isShiftClick || !event.isLeftClick) {
            event.isCancelled = true
            return
        }

        val isFree = hasViewer && whoClicked.hasPermission("lightenderchest.admin.buy")

        if (!isFree && hasViewer) {
            event.isCancelled = true
            whoClicked.inventorySound("failed")
            inventoriesConfig.getString("admin.no-access.buy")?.let {
                whoClicked.prefixMessage(it, replace = replacedInventoryOwnerName)
            }
            return
        }

        val economyManager = Main.instance.economyManager
        val balance = economyManager.getBalance(whoClicked)

        val slotCost = economyManager.calculateCostForSlot(slot)
        val doubleSlotCost = slotCost.toDouble()

        event.isCancelled = true
        if(balance >= doubleSlotCost || (isFree && whoClicked != inventoryOwner)) {
            if (economyManager.withdraw(whoClicked, slot, slotCost, isFree)) {
                if (enderInventory.openSlot(slot)) {
                    whoClicked.inventorySound("successfully")
                    return
                }
            }
            whoClicked.inventorySound("failed")
            return
        } else {
            whoClicked.inventorySound("failed")
            inventoriesConfig.getString("buy.not-enough-money")?.let {
                val calculateNeedMoney = doubleSlotCost - balance
                val isPrecision = Main.instance.enderChestConfig.getConfig().getInt("precision") <= 0
                val needToBuy = if(isPrecision) calculateNeedMoney.toInt() else calculateNeedMoney.toDouble()
                whoClicked.prefixMessage(it.replace("#price#", needToBuy.toString()))
            }
            return
        }
    }

    private fun closeInventoryEvent(event: InventoryCloseEvent) {
        val owner = this.player ?: return
        ChestListener.closeChest(owner)
        val viewer = event.player
        if(owner == viewer) return

        if(!owner.isOnline) {
            IOScope.launch {
                val enderInventory = Database.Cache.getInventory(owner.name)
                if(Database.updateInventory(enderInventory) > 0) {
                    info("Updating inventory for ${owner.name}...")
                } else {
                    info("Error updating inventory for ${owner.name}...")
                }
            }
        }
    }

    private fun Player.moveOrDropItemByCloseSlot(currentItem: ItemStack?) {
        currentItem?.let { itemStack ->
            val leftover = this.inventory.addItem(itemStack)
            if (leftover.isNotEmpty()) {
                leftover.values.forEach { leftoverStack ->
                    this.world.dropItem(this.location, leftoverStack)
                }
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

    companion object {
        val totalSize: Int = Main.instance.enderChestConfig.getConfig().getInt("inventory.size", 54).coerceIn(8, 54)

        val blockedItems: List<ItemStack> by lazy {
            val blockedItemSection = Main.instance.enderChestConfig.getConfig().getConfigurationSection("inventory.blocked-item")!!

            val loreTemplate = blockedItemSection.getStringList("lore")
            val materialName = blockedItemSection.getString("material", "RED_STAINED_GLASS_PANE")!!
            val glowing = blockedItemSection.getBoolean("glow", false)

            return@lazy (0 until totalSize).map { index ->
                // Вычисляем цену для текущего слота
                val price = Main.instance.economyManager.calculateCostForSlot(index)

                val name = blockedItemSection.getString("name", "")!!.replace("#price#", price.toString())
                // Заменяем плейсхолдер #cost# на вычисленную цену
                val replacedLore = loreTemplate.map { it.replace("#cost#", price.toString()) }

                // Создаём предмет для текущего слота
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