package ru.kainlight.lightenderchest.UTILS

import org.bukkit.entity.Player
import ru.kainlight.lightenderchest.ECONOMY.EconomyType
import ru.kainlight.lightenderchest.Main
import ru.kainlight.lightlibrary.API.ECONOMY.LightEconomy
import kotlin.math.pow
import kotlin.math.round

class EconomyManager(val plugin: Main, val economy: EconomyType) {

    init {
        LightEconomy.init(economy.name.lowercase())
    }

    fun withdraw(player: Player, cost: Number, isFree: Boolean = false): Boolean {
        val doubleCost = cost.toDouble()
        val isPurchased = if(isFree) true else when (economy) {
            EconomyType.VAULT -> LightEconomy.VAULT?.withdraw(player, doubleCost) ?: false
            EconomyType.PLAYERPOINTS -> LightEconomy.POINTS?.withdraw(player, doubleCost) ?: false
        }

        return isPurchased
    }

    fun getBalance(player: Player): Double {
        return when (economy) {
            EconomyType.VAULT -> LightEconomy.VAULT?.getBalance(player) ?: -1.0
            EconomyType.PLAYERPOINTS -> LightEconomy.POINTS?.getBalance(player) ?: -1.0
        }
    }

    fun calculateCostForSlot(slotIndex: Int): Number {
        val config = plugin.enderChestConfig.getConfig().getConfigurationSection("blocked-slots")!!
        val prices = config.getConfigurationSection("prices")!!
        val precision = config.getInt("precision", 0) // Количество знаков после запятой
        val priceStep = config.getDouble("price-step", 5.0) // Шаг увеличения цены
        val key = (slotIndex + 1).toString() // Ключ для текущего слота

        // Функция для округления значения до заданного количества знаков
        fun roundToPrecision(value: Double, precision: Int): Double {
            val scale = 10.0.pow(precision)
            return round(value * scale) / scale
        }

        // Если цена для текущего слота задана в конфиге, берём её
        val price = when {
            prices.contains(key) -> prices.getDouble(key)
            slotIndex <= 0 -> 0.0 // Для первого слота или некорректного индекса возвращаем 0.0
            else -> {
                // Берём цену предыдущего слота и добавляем шаг
                val previousPrice = calculateCostForSlot(slotIndex - 1).toDouble()
                roundToPrecision(previousPrice + priceStep, precision)
            }
        }

        // Если precision = 0, преобразуем в Int, иначе возвращаем Double
        return if (precision == 0) price.toInt() else price
    }

}