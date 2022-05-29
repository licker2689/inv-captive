package com.github.noonmaru.invcaptive.plugin

import com.github.noonmaru.invcaptive.plugin.TimeAdapter
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.shredzone.commons.suncalc.SunTimes
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*



class RealtimePlugin : JavaPlugin(), Runnable {
    private var latitude = 0.0

    private var longitude = 0.0

    private lateinit var timeAdapter: TimeAdapter

    override fun onEnable() {
        saveDefaultConfig()
        this.configFile = File(dataFolder, "config.yml")
        lastModified = configFile!!.lastModified()
        load(config)
        updateTimeAdapter(Instant.now())

        server.scheduler.runTaskTimer(this, this, 0, 1)

        Bukkit.getWorlds().forEach {
            it.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false)
        }
    }

 private fun updateTimeAdapter(now: Instant) {
        val todaySunTimes: SunTimes = SunTimes.compute().on(now).midnight().at(latitude, longitude).execute()

        val todaySunrise = todaySunTimes.rise!!.toInstant()

        if (now.isBefore(todaySunrise)) { //자정 이후 일출 이전
            val yesterday = now.minus(1, ChronoUnit.DAYS)
            val yesterdaySunTimes = SunTimes.compute().on(yesterday).midnight().at(latitude, longitude).execute()
            val yesterdaySunset = yesterdaySunTimes.set!!.toInstant()
            timeAdapter = TimeAdapter(yesterdaySunset, todaySunrise, TimeAdapter.Type.NIGHT)
            logger.info("자정 이후 일출 이전 (새벽) " + Date.from(timeAdapter.from) + " -> " + Date.from(timeAdapter.to))
            return
        }

        val todaySunset = todaySunTimes.set!!.toInstant()

        if (now.isBefore(todaySunset)) { // 일출 이후 일몰 이전
            timeAdapter = TimeAdapter(todaySunrise, todaySunset, TimeAdapter.Type.DAY)
            logger.info("일출 이후 일몰 이전 (낮)" + Date.from(timeAdapter.from) + " -> " + Date.from(timeAdapter.to))
            return
        }

        // 일몰 이후 자정 이전
        val tomorrow = now.plus(1, ChronoUnit.DAYS)
        val tomorrowSunTimes = SunTimes.compute().on(tomorrow).midnight().at(latitude, longitude).execute()
        val tomorrowSunrise = tomorrowSunTimes.rise!!.toInstant()
        timeAdapter = TimeAdapter(todaySunset, tomorrowSunrise, TimeAdapter.Type.NIGHT)
        logger.info("일몰 이후 자정 이전 (밤)" + Date.from(timeAdapter.from) + " -> " + Date.from(timeAdapter.to))
    }
//여기까지 시간 변환부로 추측.
    private var lastTick: Long = 0

    //config reloader
    private var lastModified: Long = 0
    private var configFile: File? = null

    override fun run() {
        //debug
        val now = Instant.now()

        val lastModified = configFile!!.lastModified()
        if (this.lastModified != lastModified) {
            this.lastModified = lastModified
            if (configFile!!.exists()) {
                load(YamlConfiguration.loadConfiguration(configFile!!))
            }
        }
        if (!timeAdapter.isValid(now)) updateTimeAdapter(now)
        val tick: Long = timeAdapter.currentTick(now)

        if (lastTick != tick) {
            lastTick = tick
            for (world in Bukkit.getWorlds()) {
                world.time = tick
            }
        }
    }

    private fun load(config: ConfigurationSection) {
        logger.info("Config reloaded!")
        latitude = config.getDouble("latitude")
        longitude = config.getDouble("longitude")
    }
}