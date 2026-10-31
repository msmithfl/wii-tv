package com.example.mysmarttv.weather

import java.time.LocalDate
import java.time.LocalDateTime

data class CurrentWeather(
    val temperature: Int,
    val feelsLike: Int,
    val condition: Condition,
    val windMph: Int,
    val windDirection: String,
    val humidity: Int,
    // When the reading was taken, in the forecast place's local time.
    val asOf: LocalDateTime,
)

data class HourlyForecast(val time: LocalDateTime, val temperature: Int, val condition: Condition)

data class DailyForecast(
    val date: LocalDate,
    val high: Int,
    val low: Int,
    val condition: Condition,
    val rainChance: Int,
    val windMph: Int,
    val windDirection: String,
)

data class Weather(
    val place: String,
    val current: CurrentWeather,
    val hourly: List<HourlyForecast>,
    val daily: List<DailyForecast>,
)

data class Condition(val symbol: String, val description: String)

private val COMPASS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

/** Turns a wind direction in degrees into a compass point, like "NNW". */
fun compassPoint(degrees: Double): String = COMPASS[(((degrees % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]

/** Turns Open-Meteo's weather code (the standard WMO one) into a picture and a few words. */
fun conditionFor(code: Int, isDay: Boolean = true): Condition = when (code) {
    0 -> if (isDay) Condition("☀️", "Sunny") else Condition("🌙", "Clear")
    1 -> if (isDay) Condition("🌤️", "Mostly sunny") else Condition("🌙", "Mostly clear")
    2 -> Condition("⛅", "Partly cloudy")
    3 -> Condition("☁️", "Cloudy")
    45, 48 -> Condition("🌫️", "Fog")
    51, 53, 55, 56, 57 -> Condition("🌦️", "Drizzle")
    61, 63, 65, 66, 67, 80, 81, 82 -> Condition("🌧️", "Rain")
    71, 73, 75, 77, 85, 86 -> Condition("❄️", "Snow")
    95, 96, 99 -> Condition("⛈️", "Thunderstorms")
    else -> Condition("☁️", "Cloudy")
}
