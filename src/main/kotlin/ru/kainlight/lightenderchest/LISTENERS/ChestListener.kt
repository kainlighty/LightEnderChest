package ru.kainlight.lightenderchest.LISTENERS

import kotlinx.coroutines.async
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.EnderChest
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import ru.kainlight.lightenderchest.COMMANDS.hasEnderPermission
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightlibrary.UTILS.IOScope

class ChestListener() : Listener {

    @EventHandler
    fun onCreateInventoryEvent(event: PlayerJoinEvent) {
        val username = event.player.name

        Database.Cache.getOrCreateInventory(username)
    }

    @EventHandler
    fun onEnderChestBlockEvent(event: PlayerInteractEvent) {
        if (event.action != Action.RIGHT_CLICK_BLOCK) return
        val clickedBlock = event.clickedBlock ?: return
        if (!clickedBlock.isEnderChest()) return
        val player = event.player

        if(!player.hasEnderPermission("use")) return
        val username = player.name

        event.isCancelled = true

        Database.Cache.getOrCreateInventory(username).let {
            setChest(player)?.open()
            info("Player $username opened the enderchest at the location ${clickedBlock.location}")
            it.openInventory()
        }
        return
    }

    @EventHandler
    fun onOpenEvent(event: InventoryOpenEvent) {
        if (event.inventory.type != InventoryType.ENDER_CHEST) return
        event.isCancelled = true
    }

    @EventHandler
    fun onSaveInventoryEvent(event: PlayerQuitEvent) {
        IOScope.async {
            Database.Cache.getInventory(event.player.name)?.let { inv ->
                Database.updateInventory(inv)
            }
        }
    }

    companion object {
        private val openedChest = mutableMapOf<Player?, EnderChest>()

        private fun setChest(player: Player?): EnderChest? {
            val newChest = getEnderChest(player) ?: return null
            return getChest(player)
                ?.takeIf { it.location == newChest.location }
                ?: newChest.also { openedChest.put(player, it) }
        }

        fun getChest(player: Player?): EnderChest? = this.openedChest.get(player)

        private fun getEnderChest(player: Player?): EnderChest? {
            val block = player?.getTargetBlockExact(6) ?: return null
            if (! block.isEnderChest()) return null
            val state = block.state as? EnderChest ?: return null

            return state
        }
    }

}

fun Block.isEnderChest(): Boolean = this.type == Material.ENDER_CHEST