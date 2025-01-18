package ru.kainlight.lightenderchest.DATA

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import ru.kainlight.lightenderchest.DATA.EnderInventory.SavedInventory
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightenderchest.UTILS.Debug
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level

@Suppress("UNUSED")
object Database {

    private val dbConfig: ConfigurationSection = Main.instance.config.getConfigurationSection("database")!!
    private val host: String = dbConfig.getString("host", "localhost")!!
    private val port: Int = dbConfig.getInt("port", 3306)
    private val base: String = dbConfig.getString("base", "lightcutter")!!

    private var dataSource: HikariDataSource? = null
    private val config: HikariConfig = HikariConfig()

    private val tableName = "players"

    private val cache: ConcurrentHashMap<String, SavedInventory> = ConcurrentHashMap()
    private val caching: Boolean = dbConfig.getBoolean("caching", false)

    // ----------------------------------
    // Подключение / отключение
    // ----------------------------------

    private fun configureDataSource(driverClassName: String, jdbcUrl: String, sqlite: Boolean = false) {
        config.driverClassName = driverClassName
        config.jdbcUrl = if (sqlite) {
            "jdbc:sqlite://$jdbcUrl"
        } else {
            "$jdbcUrl$host:$port/$base"
        }
        config.username = dbConfig.getString("user", "root")!!
        config.password = dbConfig.getString("password", "")!!
        config.maximumPoolSize = dbConfig.getInt("pool-size", 2)
        config.poolName = "LightEnterChest-Pool"

        dataSource = HikariDataSource(config)
    }

    fun connect() {
        when (dbConfig.getString("storage", "sqlite")!!.lowercase()) {
            "mysql" -> configureDataSource("com.mysql.cj.jdbc.Driver", "jdbc:mysql://")
            "mariadb" -> configureDataSource("org.mariadb.jdbc.Driver", "jdbc:mariadb://")
            "sqlite" -> {
                val dbFile = File(Main.instance.dataFolder, "$base.db")
                if (!dbFile.exists()) {
                    try {
                        dbFile.createNewFile()
                    } catch (e: IOException) {
                        Debug.log(e.message.toString(), Level.SEVERE)
                    }
                }
                configureDataSource("org.sqlite.JDBC", dbFile.absolutePath, true)
            }
        }
    }

    fun disconnect() {
        try {
            if (isConnected()) {
                dataSource?.close()
            }
        } catch (e: Exception) {
            Debug.log(e.message.toString(), Level.SEVERE)
        }
    }

    private fun isConnected(): Boolean =
        dataSource != null && !(dataSource?.isClosed ?: true)

    fun createTables(): Int {
        return executeUpdate(
            """
            CREATE TABLE IF NOT EXISTS $tableName (
                username VARCHAR(64) PRIMARY KEY,
                opened_slots TEXT,
                closed_slots TEXT,
                inventory TEXT
            )
            """.trimIndent()
        )
    }

    // ----------------------------------
    // Асинхронные методы (публичные)
    // ----------------------------------

    fun insertInventoryAsync(saved: SavedInventory): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            insertInventorySync(saved)
        }
    }

    fun removeInventoryAsync(username: String): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            removeInventorySync(username)
        }
    }

    fun updateInventoryAsync(saved: SavedInventory): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            updateInventorySync(saved)
        }
    }

    fun hasInventoryAsync(username: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            hasInventorySync(username)
        }
    }

    fun getInventoryAsync(username: String): CompletableFuture<SavedInventory?> {
        return CompletableFuture.supplyAsync {
            getInventorySync(username)
        }
    }

    fun getInventoriesAsync(): CompletableFuture<List<SavedInventory>> {
        return CompletableFuture.supplyAsync {
            getInventoriesSync()
        }
    }

    // ----------------------------------
    // Синхронные методы (приватные)
    // ----------------------------------

    private fun insertInventorySync(saved: SavedInventory): Int {
        val rowsAffected = executeUpdate(
            """
            INSERT INTO $tableName (username, opened_slots, closed_slots, inventory)
            SELECT ?, ?, ?, ?
            WHERE NOT EXISTS (
                SELECT 1 FROM $tableName WHERE username = ?
            )
            """.trimIndent()
        ) {
            it.setString(1, saved.username)
            it.setString(2, saved.openedSlots.joinToString(","))
            it.setString(3, saved.closedSlots.joinToString(","))
            it.setString(4, serializeInventory(saved.inventory))
            it.setString(5, saved.username)
        }

        if (caching && rowsAffected > 0) {
            cache[saved.username] = saved
        }
        return rowsAffected
    }

    private fun removeInventorySync(username: String): Int {
        val rowsAffected = executeUpdate(
            "DELETE FROM $tableName WHERE username = ?"
        ) {
            it.setString(1, username)
        }
        if (caching && rowsAffected > 0) {
            cache.remove(username)
        }
        return rowsAffected
    }

    private fun updateInventorySync(saved: SavedInventory): Int {
        val rowsAffected = executeUpdate(
            """
            UPDATE $tableName 
            SET opened_slots = ?, closed_slots = ?, inventory = ?
            WHERE username = ?
            """.trimIndent()
        ) {
            it.setString(1, saved.openedSlots.joinToString(","))
            it.setString(2, saved.closedSlots.joinToString(","))
            it.setString(3, serializeInventory(saved.inventory))
            it.setString(4, saved.username)
        }

        if (caching && rowsAffected > 0) {
            cache[saved.username] = saved
        }
        return rowsAffected
    }

    private fun hasInventorySync(username: String): Boolean {
        return if (caching) {
            cache.containsKey(username) || getInventorySync(username) != null
        } else {
            executeQuery(
                "SELECT 1 FROM $tableName WHERE username = ?",
                { it.setString(1, username) }
            ) { true } != null
        }
    }

    private fun getInventorySync(username: String): SavedInventory? {
        return if (caching) {
            cache[username] ?: fetchInventoryFromDatabase(username)?.also {
                cache[username] = it
            }
        } else {
            fetchInventoryFromDatabase(username)
        }
    }

    private fun getInventoriesSync(): List<SavedInventory> {
        return if (caching) {
            cache.values.toList()
        } else {
            fetchAllInventoriesFromDatabase()
        }
    }

    // ----------------------------------
    // Вспомогательные методы
    // ----------------------------------

    private fun fetchInventoryFromDatabase(username: String?): SavedInventory? {
        return executeQuery(
            "SELECT * FROM $tableName WHERE username = ?",
            { it.setString(1, username) }
        ) { rs ->
            SavedInventory(
                username = rs.getString("username"),
                openedSlots = rs.getString("opened_slots")?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toMutableSet() ?: mutableSetOf(),
                closedSlots = rs.getString("closed_slots")?.split(",")
                    ?.mapNotNull { it.toIntOrNull() }
                    ?.toMutableList() ?: mutableListOf(),
                inventory = deserializeInventory(username, rs.getString("inventory"))!!
            )
        }
    }

    private fun fetchAllInventoriesFromDatabase(): List<SavedInventory> {
        return executeQuery(
            "SELECT * FROM $tableName"
        ) { rs ->
            val list = mutableListOf<SavedInventory>()

            do {
                list += SavedInventory(
                    username = rs.getString("username"),
                    openedSlots = rs.getString("opened_slots")?.split(",")
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.toMutableSet() ?: mutableSetOf(),
                    closedSlots = rs.getString("closed_slots")?.split(",")
                        ?.mapNotNull { it.toIntOrNull() }
                        ?.toMutableList() ?: mutableListOf(),
                    inventory = deserializeInventory(null, rs.getString("inventory"))!!
                )
            } while (rs.next())
            list
        } ?: emptyList()
    }

    // Универсальный метод для SELECT
    private fun <T> executeQuery(
        sql: String,
        setter: (PreparedStatement) -> Unit = {},
        mapper: (ResultSet) -> T?
    ): T? {
        dataSource?.connection.use { connection ->
            connection?.prepareStatement(sql).use { statement ->
                setter(statement!!)
                statement.executeQuery().use { resultSet ->
                    return if (resultSet.next()) mapper(resultSet) else null
                }
            }
        }
        return null
    }

    // Универсальный метод для INSERT/UPDATE/DELETE
    private fun executeUpdate(
        sql: String,
        setter: (PreparedStatement) -> Unit = {}
    ): Int {
        dataSource?.connection.use { connection ->
            connection?.prepareStatement(sql).use { statement ->
                setter(statement!!)
                return statement.executeUpdate()
            }
        }
        return 0
    }

    // ----------------------------------
    // Сериализация
    // ----------------------------------
    private fun serializeInventory(inventory: Inventory?): String {
        if (inventory == null) return ""

        val items = inventory.contents
        ByteArrayOutputStream().use { byteOut ->
            BukkitObjectOutputStream(byteOut).use { dataOut ->
                dataOut.writeInt(items.size)
                for (item in items) {
                    dataOut.writeObject(item)
                }
            }
            return Base64.getEncoder().encodeToString(byteOut.toByteArray())
        }
    }

    private fun deserializeInventory(username: String?, data: String?): Inventory? {
        if (data.isNullOrBlank()) return null

        val bytes = Base64.getDecoder().decode(data)
        ByteArrayInputStream(bytes).use { byteIn ->
            BukkitObjectInputStream(byteIn).use { dataIn ->
                val size = dataIn.readInt()
                val items = arrayOfNulls<ItemStack>(size)
                for (i in 0 until size) {
                    items[i] = dataIn.readObject() as ItemStack?
                }
                // Если у тебя всегда 54 слота
                // val inventory = Bukkit.createInventory(null, 54, "Ender-Menu")
                // иначе используем size
                val inventory = EnderInventory.createInventory(Main.instance.server.getPlayer(username!!))
                inventory.contents = items
                return inventory
            }
        }
    }
}



