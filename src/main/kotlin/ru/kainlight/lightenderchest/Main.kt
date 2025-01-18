package ru.kainlight.lightenderchest

import ru.kainlight.lightenderchest.COMMANDS.EnderCommand
import ru.kainlight.lightenderchest.DATA.Database
import ru.kainlight.lightenderchest.DATA.EnderInventory
import ru.kainlight.lightenderchest.LISTENERS.CustomChestListener
import ru.kainlight.lightenderchest.UTILS.Debug
import ru.kainlight.lightlibrary.LightConfig
import ru.kainlight.lightlibrary.LightPlugin

class Main : LightPlugin() {

    override fun onLoad() {
        this.saveDefaultConfig()
        this.configurationVersion = 1.0
        this.updateConfig()

        LightConfig.saveLanguages(this, "language")
        this.messageConfig.saveDefaultConfig()
        this.messageConfig.configurationVersion = 1.0
        this.messageConfig.updateConfig()
    }

    override fun onEnable() {
        instance = this

        this.enable()

        Debug.updateStatus()

        Database.connect()
        Database.createTables()

        this.registerCommand("lightenderchest", EnderCommand(this))

        //this.registerListener(DefaultChestListener(this)).
        registerListener(CustomChestListener(this))
    }

    override fun onDisable() {
        Database.disconnect()
        this.disable()
        EnderInventory.closeInventoryForAll()
    }

    companion object {
        @JvmStatic
        lateinit var instance: Main
    }
}
