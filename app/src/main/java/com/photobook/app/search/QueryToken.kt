package com.photobook.app.search

sealed interface QueryToken

data class TemporalToken(val keyword: String) : QueryToken

data class MonthToken(val month: Int) : QueryToken

data class DayOfWeekToken(val day: Int) : QueryToken

data class TimeOfDayToken(val bucket: String) : QueryToken

data class YearToken(val year: Int) : QueryToken

data class RelativeDateToken(val amount: Int, val unit: RelativeUnit) : QueryToken

enum class RelativeUnit {
    DAY,
    WEEK,
    MONTH,
    YEAR,
}

data class FolderToken(val keyword: String) : QueryToken

data class PropertyToken(val keyword: String) : QueryToken

data class LocationToken(val keyword: String) : QueryToken

data class MLTagToken(val keyword: String) : QueryToken

data class TextToken(val keyword: String) : QueryToken

data class UnknownToken(val value: String) : QueryToken
