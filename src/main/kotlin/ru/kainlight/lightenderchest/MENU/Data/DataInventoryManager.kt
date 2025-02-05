package ru.kainlight.lightenderchest.MENU.Data

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventory
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.prefixMessage
import ru.kainlight.lightlibrary.BUILDERS.InventoryBuilder
import ru.kainlight.lightlibrary.BUILDERS.ItemBuilder
import ru.kainlight.lightlibrary.multiMessage

class DataInventoryManager(val plugin: Main, val player: Player) {

    companion object {
        private val regularSlots = (0..44).toList() // Слоты с игроками
        private val specialSlots = arrayOf(45, 46, 47, 48, 49, 50, 51, 52, 53) // Специальные слоты (функциональные и пустые)
        private val itemsPerPage = regularSlots.size
    }

    fun open(pageIndex: Int = 0) {
        create(pageIndex).let {
            player.openInventory(it)
        }
    }

    private fun create(pageIndex: Int): Inventory {
        val config = plugin.dataChestConfig.getConfig()
        val inventories = Database.Cache.getInventories().toList()

        // Получение страниц
        val totalItems = inventories.size
        val totalPages = maxOf(1, (totalItems + itemsPerPage - 1) / itemsPerPage)
        val adjustedPage = pageIndex.coerceIn(0 until totalPages)
        val currentPage = adjustedPage + 1

        // Расчёт страниц для "стрелок"
        val previousPageValue = if (currentPage > 1) currentPage else 1
        val nextPageValue = if (currentPage < totalPages) currentPage + 1 else 1

        val title = config.getString("title") !!
            .replace("#currentPage#", currentPage.toString())
            .replace("#nextPage#", totalPages.toString())

        val builder = InventoryBuilder(plugin, title, DataInventoryHolder(pageIndex), 54, true)

        // Слот для статистики
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

        // Слот для предыдущей страницы
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

        // Слот для следующей страницы
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

        // Вычисляется начальный индекс для текущей страницы
        val startIndex = adjustedPage * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, totalItems)

        for ((slotIndex, inventoryIndex) in (startIndex until endIndex).withIndex()) {
            builder.setItem(regularSlots[slotIndex], getPlayerHead(inventories[inventoryIndex]))
        }

        builder.clickEvent { event -> clickEvent(event, totalItems) }

        return builder.build()
    }

    private fun clickEvent(event: InventoryClickEvent, totalItems: Int) {
        event.isCancelled = true

        val holder = event.inventory.holder as? DataInventoryHolder ?: return
        val pageIndex = holder.pageIndex
        val slot = event.slot

        when (slot) {
            // Previous Page
            45 -> {
                if (pageIndex > 0) {
                    open(pageIndex - 1)
                }
            }

            // Statistics
            49 -> {
                val stats = Database.Cache.getStats()
                plugin.dataChestConfig.getConfig().getStringList("items.statistics.lore").map {
                    it.replace("#hitCount#", stats.hitCount().toString())
                        .replace("#missCount#", stats.missCount().toString())
                        .replace("#requestCount#", stats.requestCount().toString())
                        .replace("#evictionCount#", stats.evictionCount().toString())
                        .replace("#averageLoadPenalty#", stats.averageLoadPenalty().toLong().toString())
                        .replace("#loadSuccessCount#", stats.loadSuccessCount().toString())
                        .replace("#loadFailureCount#", stats.loadFailureCount().toString())
                }.forEach {
                    player.multiMessage(it)
                }
            }

            // Next Page
            53 -> {
                val maxPage = ((totalItems - 1) / itemsPerPage)
                if (pageIndex < maxPage) {
                    open(pageIndex + 1)
                }
            }

            else -> { // Players
                if (slot in specialSlots) return

                val currentItem = event.currentItem ?: return
                val username = currentItem.getCleanDisplayName()

                when (event.click) {

                    // Open inventory for owner
                    ClickType.LEFT -> {
                        player.performCommand("lightenderchest $username")
                        return
                    }

                    // Teleport to last opened enderchest
                    ClickType.MIDDLE -> {
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

                    // Clear items in player inventory
                    ClickType.RIGHT -> {
                        player.performCommand("lightenderchest clear $username")
                        return
                    }

                    // Delete cached inventory
                    ClickType.DROP -> {
                        Database.Cache.remove(username)
                        plugin.getMessagesConfig().getString("cache.clear")?.let {
                            player.prefixMessage(it)
                            open(pageIndex)
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
        val offlinePlayer = Main.instance.server.getOfflinePlayer(playerName)

        val section = Main.instance.dataChestConfig.getConfig().getConfigurationSection("items.players") !!
        val color = section.getString("name-color")
        println(color)
        val style = Style.style().color(usernameColor(color)).decoration(TextDecoration.ITALIC, false).build()
        println(usernameColor(color))
        println(style)
        val displayName = Component.text(playerName, style)

        val lore = section.getStringList("lore").map {
            it.replace("#openedSlotsCount#", inventory.openedSlots.size.toString())
                .replace("#closedSlotsCount#", inventory.getClosedSlots().size.toString())
                .replace("#itemsCount#", inventory.getValidItemCount().toString())
        }

        val builder = ItemBuilder(Material.PLAYER_HEAD)
            .displayName(displayName)
            .lore(*lore.toTypedArray())
            .skullOwner(offlinePlayer)
            .defaultFlags()
            .build()

        return builder
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

    private fun usernameColor(name: String?): NamedTextColor {
        return when(name?.lowercase()) {
            "black" -> NamedTextColor.BLACK
            "dark_blue" -> NamedTextColor.DARK_BLUE
            "dark_green" -> NamedTextColor.DARK_GREEN
            "dark_aqua" -> NamedTextColor.DARK_AQUA
            "dark_red" -> NamedTextColor.DARK_RED
            "dark_purple" -> NamedTextColor.DARK_PURPLE
            "gold" -> NamedTextColor.GOLD
            "gray" -> NamedTextColor.GRAY
            "dark_gray" -> NamedTextColor.DARK_GRAY
            "blue" -> NamedTextColor.BLUE
            "green" -> NamedTextColor.GREEN
            "aqua" -> NamedTextColor.AQUA
            "red" -> NamedTextColor.RED
            "light_purple" -> NamedTextColor.LIGHT_PURPLE
            "yellow" -> NamedTextColor.YELLOW
            "white" -> NamedTextColor.WHITE
            else -> NamedTextColor.YELLOW
        }
    }
}