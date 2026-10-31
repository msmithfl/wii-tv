package com.example.mysmarttv.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"
private const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"

/** Gets the forecast from Open-Meteo, which is free and needs no account or key. */
object WeatherClient {

    suspend fun fetch(location: String): Weather = withContext(Dispatchers.IO) {
        val place = findPlace(location)
        val forecast = getJson(
            "$FORECAST_URL?latitude=${place.latitude}&longitude=${place.longitude}" +
                "&current=temperature_2m,apparent_temperature,weather_code,is_day,wind_speed_10m,wind_direction_10m,relative_humidity_2m" +
                "&hourly=temperature_2m,weather_code,is_day&forecast_hours=12" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                ",wind_speed_10m_max,wind_direction_10m_dominant" +
                "&temperature_unit=fahrenheit&wind_speed_unit=mph&timezone=auto&forecast_days=7"
        )
        Weather(
            place = place.name,
            current = readCurrent(forecast.getJSONObject("current")),
            hourly = readHourly(forecast.getJSONObject("hourly")),
            daily = readDaily(forecast.getJSONObject("daily")),
        )
    }

    private class Place(val name: String, val latitude: Double, val longitude: Double)

    // Takes a city name or ZIP code. For "City, State", the part after the comma picks between
    // places with the same name.
    private fun findPlace(location: String): Place {
        val name = location.substringBefore(",").trim()
        val qualifier = location.substringAfter(",", "").trim()
        val results = getJson("$GEOCODING_URL?name=${URLEncoder.encode(name, "UTF-8")}&count=10")
            .optJSONArray("results") ?: error("No place called $location")
        val matches = List(results.length()) { results.getJSONObject(it) }
        val best = matches.firstOrNull { qualifier.isNotEmpty() && it.optString("admin1").startsWith(qualifier, ignoreCase = true) }
            ?: matches.first()
        val region = best.optString("admin1").ifEmpty { best.optString("country") }
        return Place(
            name = listOf(best.getString("name"), region).filter { it.isNotEmpty() }.joinToString(", "),
            latitude = best.getDouble("latitude"),
            longitude = best.getDouble("longitude"),
        )
    }

    private fun readCurrent(current: JSONObject) = CurrentWeather(
        temperature = current.getDouble("temperature_2m").roundToInt(),
        feelsLike = current.getDouble("apparent_temperature").roundToInt(),
        condition = conditionFor(current.getInt("weather_code"), current.getInt("is_day") == 1),
        windMph = current.getDouble("wind_speed_10m").roundToInt(),
        windDirection = compassPoint(current.getDouble("wind_direction_10m")),
        humidity = current.getInt("relative_humidity_2m"),
        asOf = LocalDateTime.parse(current.getString("time")),
    )

    private fun readHourly(hourly: JSONObject): List<HourlyForecast> {
        val times = hourly.getJSONArray("time")
        val temps = hourly.getJSONArray("temperature_2m")
        val codes = hourly.getJSONArray("weather_code")
        val isDay = hourly.getJSONArray("is_day")
        return List(times.length()) { i ->
            HourlyForecast(
                time = LocalDateTime.parse(times.getString(i)),
                temperature = temps.getDouble(i).roundToInt(),
                condition = conditionFor(codes.getInt(i), isDay.getInt(i) == 1),
            )
        }
    }

    private fun readDaily(daily: JSONObject): List<DailyForecast> {
        val dates = daily.getJSONArray("time")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val codes = daily.getJSONArray("weather_code")
        val rain = daily.getJSONArray("precipitation_probability_max")
        val wind = daily.getJSONArray("wind_speed_10m_max")
        val windDirection = daily.getJSONArray("wind_direction_10m_dominant")
        return List(dates.length()) { i ->
            DailyForecast(
                date = LocalDate.parse(dates.getString(i)),
                high = highs.getDouble(i).roundToInt(),
                low = lows.getDouble(i).roundToInt(),
                condition = conditionFor(codes.getInt(i)),
                rainChance = rain.optIntOrZero(i),
                windMph = wind.getDouble(i).roundToInt(),
                windDirection = compassPoint(windDirection.getDouble(i)),
            )
        }
    }

    // Rain chance can come back empty for days far out.
    private fun JSONArray.optIntOrZero(index: Int): Int = if (isNull(index)) 0 else getInt(index)

    private fun getJson(url: String): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        return try {
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }
}
