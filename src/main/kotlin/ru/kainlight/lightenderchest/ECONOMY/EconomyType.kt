package ru.kainlight.lightenderchest.ECONOMY

import ru.kainlight.lightenderchest.ECONOMY.EconomyType.entries
import ru.kainlight.lightenderchest.Main


enum class EconomyType {
    VAULT,
    PLAYERPOINTS;

    companion object {
        private val configType: String by lazy {
            Main.instance.config.getString("economy")?.uppercase() ?: "VAULT"
        }
        fun getCurrent(): EconomyType = entries.find { it.name == configType } ?: VAULT
    }
}
