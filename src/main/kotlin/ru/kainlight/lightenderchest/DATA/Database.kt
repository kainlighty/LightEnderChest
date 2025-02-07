package ru.kainlight.lightenderchest.DATA

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.stats.CacheStats
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventory
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventoryHolder
import ru.kainlight.lightenderchest.MENU.Ender.EnderInventoryManager
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.info
import ru.kainlight.lightenderchest.serve
import ru.kainlight.lightenderchest.warning
import ru.kainlight.lightlibrary.UTILS.IODispatcher
import ru.kainlight.lightlibrary.UTILS.bukkitThread
import ru.kainlight.lightlibrary.UTILS.useCatching
import ru.kainlight.lightlibrary.equalsIgnoreCase
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@Suppress("UNUSED")
object Database {

    private val baseType: String = Main.instance.config.getString("database.connection.type") ?: "SQLite"

    private var hikariDataSource: HikariDataSource? = null
    private val hikariConfig: HikariConfig = HikariConfig()

    private const val tableName = "inventories"

    private val inventoryCache: LoadingCache<String, EnderInventory> = Caffeine.newBuilder().recordStats()
        .maximumSize(Main.instance.config.getLong("database.cache.maximum-size", 1000))
        .expireAfterWrite(Main.instance.config.getLong("database.cache.expire", 4), TimeUnit.HOURS)
        .removalListener { username: String?, inventory: EnderInventory?, cause: RemovalCause ->
            if (!isConnected()) return@removalListener
            if (inventory == null) return@removalListener

            info("Cache: Removal listener returned cause: $cause")
            when (cause) {
                RemovalCause.EXPLICIT, RemovalCause.REPLACED -> {
                    inventory.closeInventoryForViewers()
                    updateInventorySync(inventory)
                }
                RemovalCause.SIZE, RemovalCause.EXPIRED, RemovalCause.COLLECTED -> updateInventorySync(inventory)
            }
        }.build { username ->
            // Загрузка инвентаря из базы данных или создание нового
            fetchInventoryFromDatabase(username) ?: createAndInsertInventory(username, true)
        }

    //----------------------------------
    // Configuration
    // ----------------------------------

    private fun configureDataSource(driverClassName: String, jdbcUrl: String, sqlite: Boolean = false) {
        val connection: ConfigurationSection = Main.instance.config.getConfigurationSection("database.connection") !!
        val settings: ConfigurationSection? = Main.instance.config.getConfigurationSection("database.settings")

        hikariConfig.apply {
            this.driverClassName = driverClassName
            this.jdbcUrl = if (sqlite) "jdbc:sqlite://$jdbcUrl" else {
                val host: String = connection.getString("host", "localhost") !!
                val port: Int = connection.getInt("port", 3306)
                val base: String = connection.getString("base", "lightenderchest") !!
                "$jdbcUrl$host:$port/$base"
            }

            username = connection.getString("user", "root") !!
            password = connection.getString("password", "") !!
            poolName = "LightEnterChest-Pool"

            settings?.let {
                it.getInt("pool-size").takeIf { value -> value > 0 }?.let { value ->
                    maximumPoolSize = value
                }
                it.getLong("connection-timeout").takeIf { value -> value > 0 }?.let { value ->
                    connectionTimeout = value
                }
                it.getLong("idle-timeout").takeIf { value -> value > 0 }?.let { value ->
                    idleTimeout = value
                }
                it.getLong("max-lifetime").takeIf { value -> value > 0 }?.let { value ->
                    maxLifetime = value
                }
                it.getInt("minimum-idle").takeIf { value -> value >= 0 }?.let { value ->
                    minimumIdle = value
                }
            }
        }

        hikariDataSource = HikariDataSource(hikariConfig)
    }

    // ----------------------------------
    // Connection and creation
    // ----------------------------------

    fun init() {
        connect()
        createTables()
    }

    private fun connect() {
        when (baseType.lowercase()) {
            "mysql" -> configureDataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://")
            "mariadb" -> configureDataSource("org.mariadb.jdbc.Driver", "jdbc:mariadb://")
            "sqlite" -> {
                val fileName: String = Main.instance.config.getString("database.connection.base", "lightenderchest") !!
                val dbFile = File(Main.instance.dataFolder, "$fileName.db")
                if (! dbFile.exists()) {
                    runCatching {
                        dbFile.createNewFile()
                    }.onFailure {
                        it.printStackTrace()
                    }
                }
                configureDataSource("org.sqlite.JDBC", dbFile.absolutePath, true)
            }
        }
    }

    private fun reconnect() {
        if (this.disconnect()) {
            this.connect()
        }
    }

    private fun isConnected(): Boolean = hikariDataSource != null && ! (hikariDataSource?.isClosed ?: true)

    fun disconnect(): Boolean {
        try {
            if (isConnected()) {
                hikariDataSource?.close()
                return hikariDataSource?.isClosed == true
            } else return false
        } catch (e: Exception) {
            serve(e.message.toString())
            return false
        }
    }

    private fun createTables(): Int {
        return executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS $tableName (
                username VARCHAR(32) PRIMARY KEY,
                opened_slots TEXT,
                inventory TEXT
            )
            """.trimIndent()
        )
    }

    private fun createAndInsertInventory(username: String, skipCache: Boolean = false): EnderInventory {
        val inventory = EnderInventoryManager(username).createInventory(fillBlockedItems = true)
        val newInventory = EnderInventory(username, inventory)

        insertInventorySync(newInventory, skipCache)
        return newInventory
    }

    // ----------------------------------
    //? Asynchronously functions
    // ----------------------------------

    suspend fun insertInventory(inventory: EnderInventory?, skipCache: Boolean = false): Int =
        withContext(IODispatcher) {
            runCatching {
                insertInventorySync(inventory, skipCache)
            }.onFailure { e ->
                serve("Couldn't insert player inventory for ${inventory?.username}\n${e.message}")
            }.getOrDefault(0)
        }

    suspend fun removeInventory(username: String): Int = withContext(IODispatcher) {
        runCatching {
            removeInventorySync(username)
        }.onFailure { e ->
            serve("Couldn't remove player inventory for $username\n${e.message}")
        }.getOrDefault(0)
    }

    suspend fun updateInventory(inventory: EnderInventory?, skipCache: Boolean = false): Int =
        withContext(IODispatcher) {
            runCatching {
                val updating = updateInventorySync(inventory, skipCache)
                if(updating > 0) info("Updating inventory for ${inventory?.username}...")
                updating
            }.onFailure { e ->
                serve("Couldn't update player inventory for ${inventory?.username}\n${e.message}")
            }.getOrDefault(0)
        }

    suspend fun insertOrUpdateInventory(inventory: EnderInventory?, skipCache: Boolean = false): Int =
        withContext(IODispatcher) {
            runCatching {
                insertOrUpdateInventorySync(inventory, skipCache)
            }.onFailure { e ->
                serve("Couldn't upsert player inventory for ${inventory?.username}\n${e.message}")
            }.getOrDefault(0)
        }

    suspend fun hasInventory(username: String): Boolean = withContext(IODispatcher) {
        runCatching {
            hasInventorySync(username)
        }.onFailure { e ->
            e.printStackTrace()
            serve("Couldn't get player inventory for $username\n${e.message}")
        }.getOrDefault(false)
    }

    suspend fun getInventory(username: String): EnderInventory? = withContext(IODispatcher) {
        runCatching {
            getInventorySync(username)
        }.onFailure { e ->
            serve("Couldn't get player inventory for $username\n${e.message}")
        }.getOrNull()
    }

    suspend fun getInventories(): List<EnderInventory> = withContext(IODispatcher) {
        runCatching {
            getInventoriesSync()
        }.onFailure { e ->
            e.printStackTrace()
        }.getOrDefault(emptyList())
    }

    // ----------------------------------
    // ! Synchronized functions (don't use)
    // ----------------------------------

    private fun insertOrUpdateInventorySync(inventory: EnderInventory?, skipCache: Boolean = false): Int {
        if (inventory == null) {
            serve("Inventory is null")
            return 0
        }

        val username = inventory.username
        val serializeInventory = serializeInventory(inventory.inventory)

        if(serializeInventory == null) {
            serve("Failed to serialize $username inventory")
            return 0
        }

        val sql = if (baseType.equalsIgnoreCase("sqlite")) {
            """
            INSERT INTO $tableName (username, unique_id, opened_slots, inventory)
            VALUES (?, ?, ?)
            ON CONFLICT(username) DO UPDATE SET 
                opened_slots = excluded.opened_slots,
                inventory = excluded.inventory
            """.trimIndent()
        } else {
            """
            INSERT INTO $tableName (username, unique_id, opened_slots, inventory)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE 
                opened_slots = VALUES(opened_slots), 
                inventory = VALUES(inventory)
            """.trimIndent()
        }

        val result = executeUpdate(sql) {
            it.setString(1, username)
            it.setString(2, inventory.openedSlots.joinToString(","))
            it.setString(3, serializeInventory)
        }

        info("Inventory created or updated for $username. Cached: ${! skipCache}")

        if (! skipCache) Cache.set(username, inventory)
        return result
    }

    private fun insertInventorySync(inventory: EnderInventory?, skipCache: Boolean = false): Int {
        if (inventory == null) return 0
        val username = inventory.username
        val serializeInventory = serializeInventory(inventory.inventory)

        if(serializeInventory == null) {
            serve("Failed to serialize $username inventory")
            return 0
        }

        val sql = """
                INSERT INTO $tableName (username, opened_slots, inventory)
                SELECT ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM $tableName WHERE username = ?
                )
            """.trimIndent()
        val result = executeUpdate(sql) {
            it.setString(1, username)
            it.setString(2, inventory.openedSlots.joinToString(","))
            it.setString(3, serializeInventory)
            it.setString(4, username)
        }
        if (! skipCache) {
            Cache.set(username, inventory)
        }
        return result
    }

    private fun removeInventorySync(username: String): Int {
        Main.instance.runTask {
            Main.instance.server.getPlayer(username)?.let { player ->
                val topInventory = player.openInventory.topInventory
                val holder = topInventory.holder
                if (holder is EnderInventoryHolder) {
                    player.closeInventory()
                    topInventory.viewers.forEach { it.closeInventory() }
                }
            }
        }

        val sql = "DELETE FROM $tableName WHERE username = ?"
        val result = executeUpdate(sql) {
            it.setString(1, username)
        }
        Cache.remove(username)
        return result
    }

    private fun updateInventorySync(inventory: EnderInventory?, skipCache: Boolean = true): Int {
        if (inventory == null) return 0
        val username = inventory.username

        val serializeInventory = serializeInventory(inventory.inventory)

        if(serializeInventory == null) {
            serve("Failed to serialize $username inventory")
            return 0
        }

        val sql = """
            UPDATE $tableName 
            SET opened_slots = ?, inventory = ?
            WHERE username = ?
        """.trimIndent()

        val result = executeUpdate(sql) {
            it.setString(1, inventory.openedSlots.joinToString(","))
            it.setString(2, serializeInventory)
            it.setString(3, username)
        }
        if (! skipCache) Cache.set(username, inventory)
        return result
    }

    private fun hasInventorySync(username: String): Boolean {
        val sql = "SELECT 1 FROM $tableName WHERE username = ?"
        return executeQuery(sql, { it.setString(1, username) }) { true } != null
    }

    private fun getInventorySync(username: String): EnderInventory? {
        return fetchInventoryFromDatabase(username)
    }

    private fun getInventoriesSync(): List<EnderInventory> {
        return fetchAllInventoriesFromDatabase()
    }

    // ----------------------------------
    // Fetching
    // ----------------------------------

    private fun fetchInventoryFromDatabase(username: String): EnderInventory? {
        return executeQuery("SELECT * FROM $tableName WHERE username = ?", {
            it.setString(1, username)
        }) { rs ->
            // Курсор уже установлен на строку результата, если она есть
            val dbUsername = rs.getString("username") ?: username
            val openedSlotsStr = rs.getString("opened_slots") ?: ""
            val inventoryData = rs.getString("inventory")

            val openedSlots = openedSlotsStr.split(",")
                .mapNotNull { it.toIntOrNull() }
                .toMutableSet()

            val inventory = if (! inventoryData.isNullOrBlank()) {
                deserializeInventory(dbUsername, inventoryData, openedSlots)
            } else {
                serve("Inventory for $username not found")
                null
            }

            if (inventory == null) {
                serve("Failed to deserialize inventory for player $username")
                null
            } else {
                info("Request for fetching $username inventory. Result: OK")
                EnderInventory(
                    username = dbUsername,
                    openedSlots = openedSlots,
                    inventory = inventory
                )
            }
        }
    }

    private fun fetchAllInventoriesFromDatabase(): List<EnderInventory> {
        return executeQuery("SELECT * FROM $tableName") { rs ->
            val list = mutableListOf<EnderInventory>()

            do {
                val username = rs.getString("username")
                val openedSlots = rs.getString("opened_slots").split(",")
                    .mapNotNull { it.toIntOrNull() }
                    .toMutableSet()
                val inventory = deserializeInventory(username, rs.getString("inventory"), openedSlots)

                list += EnderInventory(username = username,
                    openedSlots = openedSlots,
                    inventory = inventory!!
                )
            } while (rs.next())
            info("Request for fetching all inventories. Result: ${list.size} values")
            list
        } ?: emptyList()
    }

    // ----------------------------------
    // ! Serialization and deserialization
    // ----------------------------------

    private fun serializeInventory(inventory: Inventory?): String? {
        if (inventory == null) return null

        // Пустой слот или из blockedItems → null
        // По итогу не записываем в базу blockedItems (при десереализоации заполняются в createInventory)
        val items = inventory.contents.map { item ->
            when {
                item == null -> null
                EnderInventoryManager.blockedItems.any { blocked -> item.isSimilar(blocked) } -> null
                else -> item
            }
        }.toTypedArray()

        return ByteArrayOutputStream().useCatching { byteOut ->
            BukkitObjectOutputStream(byteOut).useCatching { dataOut ->
                dataOut.writeInt(items.size)
                for (item in items) {
                    dataOut.writeObject(item)
                }
            }
            Base64.getEncoder().encodeToString(byteOut.toByteArray())
        }
    }


    private fun deserializeInventory(username: String, data: String?, openedSlots: MutableSet<Int> = mutableSetOf<Int>()): Inventory? {
        if (data.isNullOrBlank()) return null

        val bytes = Base64.getDecoder().decode(data)
        ByteArrayInputStream(bytes).useCatching { byteIn ->
            BukkitObjectInputStream(byteIn).useCatching { dataIn ->
                val size = dataIn.readInt()
                val items = arrayOfNulls<ItemStack>(size)

                for (i in 0 until size) {
                    items[i] = dataIn.readObject() as ItemStack?
                }

                val inventory = EnderInventoryManager(username).createInventory(fillBlockedItems = true)

                for (slot in openedSlots) {
                    if (slot in 0 until size) {
                        inventory.setItem(slot, items[slot])
                    }
                }

                info("Deserialize inventory for $username is successfully")

                return inventory
            }
        }
        return null
    }

    // ----------------------------------
    // $ Кеш
    // ----------------------------------

    object Cache {
        fun set(username: String, inventory: EnderInventory) {
            inventoryCache.put(username, inventory)
            info("Cache: Inventory for $username has been cached")
        }

        fun remove(username: String) {
            inventoryCache.invalidate(username)
            info("Cache: Inventory for $username has been uncached")
        }

        fun purge() {
            inventoryCache.cleanUp()
            info("Cache: Old inventories has been purged")
        }

        fun clear() {
            inventoryCache.invalidateAll()
            info("Cache: All inventories has been uncached")
        }

        fun clearOfflines() {
            val map: List<String> = Main.instance.server.offlinePlayers
                .filter { !it.isOnline }
                .mapNotNull { it.name }
            inventoryCache.invalidateAll(map)

            info("Cache: Offline inventories has been uncached")
        }

        fun clearOnlines() {
            val map: List<String> = Main.instance.server.onlinePlayers.mapNotNull { it.name }
            inventoryCache.invalidateAll(map)
            info("Cache: Online inventories has been uncached")
        }

        fun getOrCreateInventory(username: String): EnderInventory {
            info("Cache: Create or get inventory for $username")
            return inventoryCache.get(username)
        }

        fun getInventory(username: String, withInfo: Boolean = true): EnderInventory? {
            if(withInfo) info("Cache: Getting inventory for $username")
            return inventoryCache.getIfPresent(username)
        }

        fun hasInventory(username: String): Boolean {
            info("Cache: Request to verify the existence of an inventory for $username")
            return inventoryCache.getIfPresent(username) != null
        }

        fun getInventories(): MutableCollection<EnderInventory> {
            info("Cache: Getting all inventories")
            return inventoryCache.asMap().values
        }

        fun getStats(): CacheStats {
            return inventoryCache.stats()
        }

        fun getOnlineInventories(): MutableCollection<EnderInventory> {
            val map = Main.instance.server.onlinePlayers.mapNotNull { it.name }
            return inventoryCache.getAllPresent(map).values
        }

        fun refreshAsync(username: String): CompletableFuture<EnderInventory> {
            info("Cache: Asynchronously request for refreshed for $username")
            return inventoryCache.refresh(username).exceptionally {
                it.printStackTrace()
                null
            }
        }

        fun refreshAsync(): CompletableFuture<Map<String, EnderInventory>> {
            info("Cache: Asynchronously request for refreshed all inventories")
            val map = Main.instance.server.onlinePlayers.mapNotNull { it.name }
            return inventoryCache.refreshAll(map).exceptionally {
                it.printStackTrace()
                null
            }
        }

        fun refreshAllAsync(): CompletableFuture<Map<String, EnderInventory>> {
            info("Cache: Asynchronously request for refreshed all inventories")
            val map = Main.instance.server.onlinePlayers.mapNotNull { it.name }
                .zip(Main.instance.server.offlinePlayers.mapNotNull { it.name }).toMap().values
            return inventoryCache.refreshAll(map).exceptionally {
                it.printStackTrace()
                null
            }
        }

        fun refreshOnlineInventoriesAsync(): CompletableFuture<Map<String, EnderInventory>> {
            info("Cache: Asynchronously request for refreshed online inventories")
            val map = Main.instance.server.onlinePlayers.mapNotNull { it.name }
            return inventoryCache.refreshAll(map).exceptionally {
                it.printStackTrace()
                null
            }
        }

        /**
         ** При выключении сервера оставить флаг `closeInventories` в `false`, иначе всё ляжет нахрен
         **/
        suspend fun saveToDatabase(closeInventories: Boolean = false): List<Int> = supervisorScope {
            val inventories = getInventories()

            if (inventories.isEmpty()) {
                warning("Cache: Asynchronously request for saving all inventories in database: inventories is empty")
                return@supervisorScope emptyList<Int>()
            }

            info("Cache: Asynchronously request for saving all inventories in database")
            if (closeInventories) {
                this.bukkitThread(inventories) {
                    warning("Cache: Synchronously request for closing all inventories")
                    it.forEach {
                        it.closeInventoryForViewers()
                    }
                }
            }

            val jobs = inventories.map { inventory ->
                async(IODispatcher) {
                    info("The data from the cache has been saved to the database")
                    insertInventory(inventory, true)
                }
            }

            jobs.awaitAll()
        }
    }

    // ----------------------------------
    // ? Запросы
    // ----------------------------------

    // SELECT
    private fun <T> executeQuery(sql: String, setter: (PreparedStatement) -> Unit = {}, mapper: (ResultSet) -> T?): T? {
        hikariDataSource?.connection?.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                setter(statement)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) mapper(resultSet) else null
                }
            }
        }
        return null
    }

    // INSERT/UPDATE/DELETE
    private fun executeUpdate(sql: String, setter: (PreparedStatement) -> Unit = {}): Int {
        hikariDataSource?.connection?.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                setter(statement)
                return statement.executeUpdate()
            }
        }
        return 0
    }
}



