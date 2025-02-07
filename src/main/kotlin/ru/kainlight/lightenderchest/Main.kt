package ru.kainlight.lightenderchest

import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.OfflinePlayer
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import ru.kainlight.lightenderchest.COMMANDS.EnderCommand
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.ECONOMY.EconomyType
import ru.kainlight.lightenderchest.LISTENERS.ChestListener
import ru.kainlight.lightenderchest.LISTENERS.CommandAliasListener
import ru.kainlight.lightenderchest.UTILS.EconomyManager
import ru.kainlight.lightlibrary.LightConfig
import ru.kainlight.lightlibrary.LightPlugin
import ru.kainlight.lightlibrary.UTILS.IOScope
import ru.kainlight.lightlibrary.UTILS.Init
import ru.kainlight.lightlibrary.UTILS.Parser
import ru.kainlight.lightlibrary.UTILS.bukkitThread
import ru.kainlight.lightlibrary.equalsIgnoreCase

class Main : LightPlugin() {

    lateinit var economyManager: EconomyManager
    lateinit var enderChestConfig: LightConfig
    lateinit var dataChestConfig: LightConfig

    internal var debugIsEnabled: Boolean = false
    internal var MESSAGE_PREFIX: String = ""

    override fun onLoad() {
        this.saveDefaultConfig()
        this.configurationVersion = 1.0
        this.updateConfig()

        this.enderChestConfig = LightConfig(this, "gui", "ender_chest.yml")
        this.enderChestConfig.saveDefaultConfig()
        this.enderChestConfig.configurationVersion = 1.0
        this.enderChestConfig.updateConfig()

        this.dataChestConfig = LightConfig(this, "gui", "data_chest.yml")
        this.dataChestConfig.saveDefaultConfig()
        this.dataChestConfig.configurationVersion = 1.0
        this.dataChestConfig.updateConfig()

        LightConfig.saveLanguages(this, "language")
        this.messageConfig.saveDefaultConfig()
        this.messageConfig.configurationVersion = 1.0
        this.messageConfig.updateConfig()
    }

    override fun onEnable() {
        instance = this
        this.enable()

        this.reloadConfigurations()

        IOScope.launch {
            info("IO Coroutine started")
            this.bukkitThread { Database.init() }
        }

        this.economyManager = EconomyManager(this, EconomyType.getCurrent())

        this.registerCommand("lightenderchest", EnderCommand(this), EnderCommand.Completer())
        this.registerListener(ChestListener())
            .registerListener(CommandAliasListener())

        Init.start(this, true)
    }

    override fun onDisable() {
        Database.Cache.getInventories().forEach { it.closeInventoryForViewers() }
        runBlocking { Database.Cache.saveToDatabase() }
        Database.disconnect()

        IOScope.cancel("IO Coroutine cancelled")

        this.disable()
    }

    fun reloadConfigurations() {
        this.saveDefaultConfig()
        this.reloadConfig()

        this.messageConfig.saveDefaultConfig()
        this.messageConfig.reloadConfig()
        info("config.yml has been reloaded")

        this.debugIsEnabled = this.config.getBoolean("debug")
        Parser.parseMode = this.config.getString("parse_mode", "MINIMESSAGE")!!
        MESSAGE_PREFIX = this.getMessagesConfig().getString("prefix", "")!!

        this.enderChestConfig.saveDefaultConfig()
        this.enderChestConfig.reloadConfig()
        info("ender_chest.yml has been reloaded")

        this.dataChestConfig.saveDefaultConfig()
        this.dataChestConfig.reloadConfig()
        info("data_chest.yml has been reloaded")
    }

    companion object {
        @JvmStatic lateinit var instance: Main
    }
}

// --------------------------------------- //

fun FileConfiguration.getStringWithPrefix(path: String): String? {
    var text = this.getString(path)?.replace("#prefix#", Main.instance.MESSAGE_PREFIX)
    text = if(Parser.parseMode.equalsIgnoreCase("MINIMESSSAGE")) text.plus("<reset>") else text
    return text
}

fun ConfigurationSection.getStringWithPrefix(path: String): String? {
    var text = this.getString(path)?.replace("#prefix#", Main.instance.MESSAGE_PREFIX)
    text = if(Parser.parseMode.equalsIgnoreCase("MINIMESSSAGE")) text.plus("<reset>") else text
    return text
}

fun OfflinePlayer?.isNull(): Boolean {
    return if(this == null || !this.hasPlayedBefore()) true else false
}

fun info(text: Any) {
    if (Main.instance.debugIsEnabled) {
        Main.instance.logger.info(text.toString())
    }
}
fun serve(text: Any) {
    if(Main.instance.debugIsEnabled) {
        Main.instance.logger.severe(text.toString())
    }
}
fun warning(text: Any) {
    if(Main.instance.debugIsEnabled) {
        Main.instance.logger.warning(text.toString())
    }
}