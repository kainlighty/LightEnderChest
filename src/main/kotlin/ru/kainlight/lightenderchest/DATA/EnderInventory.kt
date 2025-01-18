package ru.kainlight.lightenderchest.DATA

import net.kyori.adventure.sound.Sound
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.UTILS.InternalConfig
import ru.kainlight.lightlibrary.BUILDERS.InventoryBuilder
import ru.kainlight.lightlibrary.ECONOMY.LightEconomy
import ru.kainlight.lightlibrary.multiMessage
import java.util.concurrent.CompletableFuture

class EnderInventory(private val plugin: Main, private val player: Player) {

    /**
     * Открывает игроку инвентарь (если есть в БД) или создаёт новый.
     * Возвращает будущий инвентарь через CompletableFuture.
     * Когда всё загрузится, ты можешь сделать .thenAccept { inv -> player.openInventory(inv) }
     */
    fun open() {
        getOrCreateSavedInventory().thenApply { saved ->
            // На момент thenApply у нас уже есть готовый SavedInventory (из базы или новый)
            // Возвращаем сам инвентарь
            plugin.runTask { player.openInventory(saved.inventory) }
        }
    }

    /**
     * Возвращает инвентарь (из базы или создаёт новый).
     * Теперь это асинхронная операция, так как нам надо сходить в БД.
     */
    fun getInventory(): CompletableFuture<Inventory> {
        return getOrCreateSavedInventory().thenApply { saved ->
            saved.inventory
        }
    }

    /**
     * Асинхронно пытается загрузить SavedInventory из базы (по нику).
     * Если в базе нет — создаёт новый (54 слота) и вставляет в БД.
     * Возвращает `CompletableFuture<SavedInventory>` — ты можешь thenAccept/thenApply и т.д.
     */
    fun getOrCreateSavedInventory(): CompletableFuture<SavedInventory> {
        val username = player.name

        // Сходить в базу
        return Database.getInventoryAsync(username).thenCompose { maybeSaved ->
            if (maybeSaved == null) {
                // Нет записи - создаём

                val newInventory = createInventory(player)  // 54 слота, все заблокированы
                val newSaved = SavedInventory(username, mutableSetOf(), mutableListOf(), newInventory)

                // Так как это "первое" создание, один раз вызываем fillBlockedItems
                // (но мы уже делаем это внутри createInventory – см. код)
                // Если хочешь, можешь перенести fillBlockedItems сюда.

                // Вставляем в базу
                Database.insertInventoryAsync(newSaved).thenApply {
                    newSaved // thenApply вернёт newSaved
                }
            } else {
                // Запись есть - возвращаем сразу
                // Важно: мы НЕ вызываем fillBlockedItems(), ведь всё уже хранится в БД
                CompletableFuture.completedFuture(maybeSaved)
            }
        }
    }

    /**
     * Обработка клика по заблокированному слоту:
     * 1) Проверяем деньги
     * 2) Убираем BLOCKED_ITEM
     * 3) Асинхронно обновляем в базе (добавляем слот в openedSlots)
     */
    private fun clickInventoryEvent(event: InventoryClickEvent) {
        val slot = event.slot
        println(slot)
        val currentItem = event.currentItem ?: return
        if (currentItem.type == Material.AIR) return
        if (currentItem != InternalConfig.blockedItem) return

        if (event.isShiftClick) {
            event.isCancelled = true
            return
        }

        val clicker = event.whoClicked
        if (clicker !is Player) return

        val balance = LightEconomy.POINTS?.getBalance(clicker) ?: return
        if (balance < 300.0) {
            event.isCancelled = true
            clicker.multiMessage("Недостаточно средств")
            val sound = Sound.sound(org.bukkit.Sound.ENTITY_VILLAGER_HURT.key, Sound.Source.MASTER, 1f, 1f)
            clicker.playSound(sound)
            return
        }

        // Асинхронно загружаем SavedInventory, добавляем слот, сохраняем
        getOrCreateSavedInventory().thenAccept { saved ->
            // Устанавливаем открытый слот в памяти
            saved.openSlotAsync(slot).thenAccept {
                plugin.runTask {
                    Runnable {
                        event.isCancelled = true
                        event.currentItem = ItemStack(Material.AIR) // убираем блок
                        val sound = Sound.sound(org.bukkit.Sound.ENTITY_PLAYER_LEVELUP.key, Sound.Source.MASTER, 1f, 1f)
                        clicker.playSound(sound)
                        clicker.multiMessage("Слот $slot куплен")
                    }
                }
            }
        }
    }

    /**
     * Событие закрытия: сохраняем текущее содержимое в БД (асинхронно).
     */
    private fun closeInventoryEvent(event: InventoryCloseEvent) {
        val p = event.player
        if (p !is Player) return

        // Забираем инвентарь и сохраняем содержимое
        // Асинхронный апдейт в БД
        getOrCreateSavedInventory().thenAccept { saved ->
            saved.inventory.contents = event.inventory.contents
            Database.updateInventoryAsync(saved)
        }
    }

    companion object {

        /**
         * Создаёт инвентарь на 54 слота и заполняет заблокированными слотами.
         * Вешает слушатели на клики и закрытие.
         */
        fun createInventory(player: Player?): Inventory {
            val plugin = Main.instance

            val size = 54
            val inventoryBuilder = InventoryBuilder(plugin, InternalConfig.INVENTORY_TITLE, player ?: "", size, true)

            for (i in 0 until size) {
                inventoryBuilder.setItem(i, InternalConfig.blockedItem)
            }

            /*plugin.runTask {
                inventoryBuilder.clickEvent { event -> clickInventoryEvent(event) }
                inventoryBuilder.closeEvent { event -> closeInventoryEvent(event) }
            }*/

            val builtInventory = inventoryBuilder.build()

            EnderHolder().setInventory(builtInventory)

            return builtInventory
        }

        /**
         * Раньше тут мог быть inventories: MutableMap.
         * Теперь ничего не храним локально, всё в БД.
         */

        /**
         * Если нужно получить SavedInventory игрока по нику извне,
         * можно обратиться напрямую к базе. Или тут сделать shortcut:
         */
        fun getSavedInventory(username: String): CompletableFuture<SavedInventory?> {
            return Database.getInventoryAsync(username)
        }

        /**
         * Закрыть всем "Эндер-сундуки":
         * Можно обойти онлайн-игроков и закрыть, если их инвентарь
         * совпадает с титулом INVENTORY_TITLE.
         */
        fun closeInventoryForAll() {
            Bukkit.getOnlinePlayers().forEach { pl ->
                val topInv = pl.openInventory.topInventory
                if (topInv.holder is EnderHolder) {
                    pl.closeInventory()
                }
            }
        }
    }


    data class SavedInventory(
        val username: String,
        val openedSlots: MutableSet<Int>,
        val closedSlots: MutableList<Int>,
        val inventory: Inventory
    ) {

        /**
         * Открыть слот (убрать блокировку в самом инвентаре),
         * затем асинхронно обновить в БД.
         */
        fun openSlotAsync(slot: Int): CompletableFuture<Void> {
            val future = CompletableFuture<Void>()
            // Все операции с инвентарём — строго в главном потоке
            Main.instance.runTask {
                // 1) Меняем локальные коллекции
                openedSlots.add(slot)
                closedSlots.remove(slot)
                // 2) Убираем блокирующий предмет (если хочешь это делать физически)
                inventory.setItem(slot, ItemStack(Material.AIR))

                // 3) Асинхронно обновляем в базе
                Database.updateInventoryAsync(this).thenRun {
                    // Когда база обновилась, завершаем future
                    future.complete(null)
                }.exceptionally { ex ->
                    future.completeExceptionally(ex)
                    null
                }
            }
            return future
        }

        /**
         * Закрыть слот (вернуть блокировку),
         * затем асинхронно обновить в БД.
         */
        fun closeSlotAsync(slot: Int): CompletableFuture<Void> {
            val future = CompletableFuture<Void>()
            Main.instance.runTask {
                openedSlots.remove(slot)
                if (! closedSlots.contains(slot)) {
                    closedSlots.add(slot)
                }
                // Вернуть в инвентарь блокировку (например стекло)
                inventory.setItem(slot, InternalConfig.blockedItem)

                Database.updateInventoryAsync(this).thenRun {
                    future.complete(null)
                }.exceptionally { ex ->
                    future.completeExceptionally(ex)
                    null
                }
            }
            return future
        }

        fun openInventory(player: Player? = null): Player? {
            val holder = inventory.holder !!
            if (holder is EnderHolder) {
                if(player == null) {
                    inventory.viewers.find { it == player }?.openInventory(inventory)
                } else player.closeInventory()

                return player
            }
            return null
        }

        fun closeInventory(player: Player? = null): Player? {
            val holder = inventory.holder !!
            if (holder is EnderHolder) {
                if(player == null) {
                    inventory.viewers.find { it == player }?.closeInventory()
                } else player.closeInventory()

                return player
            }
            return null
        }

        /**
         * Очистить открытые слоты (или вообще весь инвентарь),
         * затем обновить в базе.
         */
        fun clearInventoryAsync(): CompletableFuture<Void> {
            val future = CompletableFuture<Void>()
            Main.instance.runTask {
                openedSlots.forEach { slot ->
                    inventory.setItem(slot, InternalConfig.emptyItem)
                }
                Database.updateInventoryAsync(this).thenRun {
                    future.complete(null)
                }.exceptionally { ex ->
                    future.completeExceptionally(ex)
                    null
                }
            }
            return future
        }

        /**
         * «Сбросить» инвентарь: закрыть всем зрителям и т.д.
         */
        fun resetInventoryAsync() {
            Database.removeInventoryAsync(username).thenRun {

                Main.instance.runTask {
                    val viewersSnapshot = inventory.viewers.toList()
                    viewersSnapshot.forEach { viewer ->
                        if (viewer is Player) {
                            viewer.closeInventory()
                        }
                    }
                }

            }.exceptionally { ex ->
                ex.printStackTrace()
                null
            }
        }
    }

}