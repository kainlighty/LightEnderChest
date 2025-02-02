package ru.kainlight.lightenderchest.MENU.Data

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.SkullMeta
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventory
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.prefixMessage
import ru.kainlight.lightlibrary.BUILDERS.InventoryBuilder
import ru.kainlight.lightlibrary.BUILDERS.ItemBuilder
import ru.kainlight.lightlibrary.UTILS.IODispatcher
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.UTILS.bukkitThread
import ru.kainlight.lightlibrary.multiMessage

class DataInventoryManager(val plugin: Main, val player: Player) {

    companion object {
        private val regularSlots = (0..44).toList() // Только обычные слоты
        private val specialSlots = arrayOf(45, 46, 47, 48, 49, 50, 51, 52, 53)
        private val itemsPerPage = regularSlots.size
    }

    suspend fun open(pageIndex: Int = 0) = withContext(IODispatcher) {
        val inv = create(pageIndex)
        inv.bukkitThread {
            player.openInventory(it)
        }
    }

    /*fun open(pageIndex: Int = 0) {
        player.openInventory(create(pageIndex))
    }*/

    private fun create(pageIndex: Int): Inventory {
        val config = plugin.dataChestConfig.getConfig()

        val inventories = Database.Cache.getInventories().toList()
        val totalItems = inventories.size

        val totalPages = maxOf(1, (totalItems + itemsPerPage - 1) / itemsPerPage)
        val adjustedPage = pageIndex.coerceIn(0 until totalPages)
        val currentPage = adjustedPage + 1

        // Для стрелок
        val previousPageValue = if (currentPage > 1) currentPage else 1
        val nextPageValue = if (currentPage < totalPages) currentPage + 1 else 1

        val title = config.getString("title") !!
            .replace("#currentPage#", currentPage.toString())
            .replace("#nextPage#", totalPages.toString())

        val builder = InventoryBuilder(plugin, title, DataInventoryHolder(pageIndex), 54, true)

        // $ Слоты для стрелок, компас ---
        config.getConfigurationSection("items.statistics") !!.let { section ->
            val displayName = section.getString("name")
            val material = section.getString("material") ?: "COMPASS"
            val isGlowing = section.getBoolean("glow", false)

            val stats = Database.Cache.getStats()
            val lore = section.getStringList("lore").map {
                it.replace("#hitCount#", stats.hitCount().toString())
                    .replace("#missCount#", stats.missCount().toString())
                    .replace("#requestCount#", stats.requestCount().toString())
                    .replace("#evictionCount#", stats.evictionCount().toString())
                    .replace("#averageLoadPenalty#", stats.averageLoadPenalty().toLong().toString())
                    .replace("#loadSuccessCount#", stats.loadSuccessCount().toString())
                    .replace("#loadFailureCount#", stats.loadFailureCount().toString())
            }

            val statisticItem = ItemBuilder(material)
                .displayName(displayName)
                .lore(*lore.toTypedArray())
                .glow(isGlowing)
                .defaultFlags()
                .build()
            builder.setItem(49, statisticItem)
        }

        config.getConfigurationSection("items.previous-page") !!.let { section ->
            val displayName = section.getString("name")
            val material = section.getString("material") ?: "ARROW"
            val isGlowing = section.getBoolean("glow", false)
            val lore = section.getStringList("lore")

            val previousPageItem = ItemBuilder(material)
                .amount(previousPageValue)
                .displayName(displayName)
                .lore(*lore.toTypedArray())
                .glow(isGlowing)
                .defaultFlags()
                .build()
            builder.setItem(45, previousPageItem)
        }

        config.getConfigurationSection("items.next-page") !!.let { section ->
            val displayName = section.getString("name")
            val material = section.getString("material") ?: "ARROW"
            val isGlowing = section.getBoolean("glow", false)
            val lore = section.getStringList("lore")

            val nextPageItem = ItemBuilder(material)
                .amount(nextPageValue)
                .displayName(displayName)
                .lore(*lore.toTypedArray())
                .glow(isGlowing)
                .defaultFlags()
                .build()
            builder.setItem(53, nextPageItem)
        }
        // $ ---

        // Вычисляем начальный индекс для текущей страницы
        val startIndex = adjustedPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, totalItems)

        for ((slotIndex, inventoryIndex) in (startIndex until endIndex).withIndex()) {
            builder.setItem(regularSlots[slotIndex], getPlayerHead(inventories[inventoryIndex]))
        }

        // Регистрируем событие клика
        builder.clickEvent { event -> clickEvent(event, totalItems) }

        return builder.build()
    }

    private fun clickEvent(event: InventoryClickEvent, totalItems: Int) {
        event.isCancelled = true

        val holder = event.inventory.holder as? DataInventoryHolder ?: return
        val pageIndex = holder.pageIndex
        val slot = event.slot

        when (slot) {
            45 -> { // Previous Page
                if (pageIndex > 0) {
                    IOScope.launch {
                        open(pageIndex - 1)
                    }
                }
            }

            49 -> { // Statistics
                plugin.dataChestConfig.getConfig().getStringList("items.players.lore").forEach {
                    player.multiMessage(it)
                }
            }

            53 -> { // Next Page
                val maxPage = ((totalItems - 1) / itemsPerPage)
                if (pageIndex < maxPage) {
                    IOScope.launch {
                        open(pageIndex + 1)
                    }
                }
            }

            else -> { // Players
                if (slot in specialSlots) return

                val currentItem = event.currentItem ?: return
                val username = currentItem.getCleanDisplayName()

                when (event.click) {
                    ClickType.LEFT -> { // Open inventory for owner
                        player.performCommand("lightenderchest $username")
                        return
                    }

                    ClickType.MIDDLE -> { // teleport to last enderchest
                        val player = plugin.server.getPlayerExact(username)
                        val chest = ChestListener.getChest(player)
                        if (chest == null) {
                            plugin.getMessagesConfig().getString("cache.chest-not-found")?.let {
                                this.player.prefixMessage(it.replace("#username#", username))
                            }
                            return
                        } else {
                            this.player.teleport(chest.location)
                        }
                    }

                    ClickType.RIGHT -> { // clear items in owner inventory
                        player.performCommand("lightenderchest clear $username")
                        return
                    }

                    ClickType.DROP -> { // delete cached inventory
                        Database.Cache.remove(username)
                        plugin.getMessagesConfig().getString("cache.clear")?.let {
                            player.prefixMessage(it)
                            IOScope.launch {
                                open(pageIndex)
                            }
                        }
                        return
                    }

                    else -> return
                }
            }
        }
    }

    // ----------------
    // Доп.
    // ----------------

    private fun getPlayerHead(inventory: EnderInventory): ItemStack {
        val playerName = inventory.username

        val skull = ItemStack(Material.PLAYER_HEAD)
        val skullMeta: SkullMeta = skull.itemMeta as SkullMeta
        skullMeta.owningPlayer = Main.instance.server.getOfflinePlayer(playerName)
        skull.setItemMeta(skullMeta)

        val section = Main.instance.dataChestConfig.getConfig().getConfigurationSection("items.players") !!
        val displayName = section.getString("name-color") + playerName

        val lore = section.getStringList("lore").map {
            it.replace("#openedSlotsCount#", inventory.openedSlots.size.toString())
                .replace("#closedSlotsCount#", inventory.getClosedSlots().size.toString())
                .replace("#itemsCount#", inventory.getValidItemCount().toString())
        }

        val builder = ItemBuilder(skull)
            .displayName(displayName)
            .lore(*lore.toTypedArray())
            .defaultFlags()

        return builder.build()
    }

    fun ItemStack.getCleanDisplayName(): String {
        val itemMeta: ItemMeta? = this.itemMeta
        if (itemMeta?.hasDisplayName() == true) {
            val displayNameComponent: Component? = itemMeta.displayName()
            if (displayNameComponent != null) {
                return PlainTextComponentSerializer.plainText().serialize(displayNameComponent)
            }
        }
        return ""
    }
}