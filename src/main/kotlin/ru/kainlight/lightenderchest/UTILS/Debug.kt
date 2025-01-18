package ru.kainlight.lightenderchest.UTILS

import ru.kainlight.lightenderchest.Main
import java.util.logging.Level

object Debug {

    private var isEnabled: Boolean = false

    fun log(message: String, level: Level? = Level.INFO) {
        if (this.isEnabled) Main.instance.logger.log(level, message)
    }

    fun updateStatus() {
        this.isEnabled = Main.instance.config.getBoolean("debug")
    }

}