package com.photobook.app.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.WeekFields

object DateUtils {

    data class DateParts(
        val year: Int,
        val month: Int,
        val dayOfMonth: Int,
        val dayOfWeekIso: Int,
        val hourOfDay: Int,
    )

    fun toDateParts(millis: Long, zoneId: ZoneId = ZoneId.systemDefault()): DateParts {
        val dateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId)
        return DateParts(
            year = dateTime.year,
            month = dateTime.monthValue,
            dayOfMonth = dateTime.dayOfMonth,
            dayOfWeekIso = dateTime.dayOfWeek.value,
            hourOfDay = dateTime.hour,
        )
    }

    fun isSameDay(first: Long, second: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        val d1 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(first), zoneId).toLocalDate()
        val d2 = ZonedDateTime.ofInstant(Instant.ofEpochMilli(second), zoneId).toLocalDate()
        return d1 == d2
    }

    fun isInCurrentWeek(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        val weekFields = WeekFields.ISO
        return now.get(weekFields.weekOfWeekBasedYear()) == date.get(weekFields.weekOfWeekBasedYear()) &&
            now.year == date.year
    }

    fun isInLastWeek(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).minusWeeks(1)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        val weekFields = WeekFields.ISO
        return now.get(weekFields.weekOfWeekBasedYear()) == date.get(weekFields.weekOfWeekBasedYear()) &&
            now.year == date.year
    }

    fun isInCurrentMonth(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        return now.year == date.year && now.monthValue == date.monthValue
    }

    fun isInLastMonth(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).minusMonths(1)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        return now.year == date.year && now.monthValue == date.monthValue
    }

    fun isInCurrentYear(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        return now.year == date.year
    }

    fun isInLastYear(timestamp: Long, nowMillis: Long): Boolean {
        val zoneId = ZoneId.systemDefault()
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zoneId).minusYears(1)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(timestamp), zoneId)
        return now.year == date.year
    }
}
