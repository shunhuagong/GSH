package com.gsh.app.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object DateUtils {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): String = LocalDate.now().format(formatter)

    fun format(date: LocalDate): String = date.format(formatter)

    fun parse(date: String): LocalDate = LocalDate.parse(date, formatter)

    /** 日期字符串 → DatePicker 使用的 UTC 毫秒值 */
    fun toEpochMillis(date: String): Long =
        parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** DatePicker 的 UTC 毫秒值 → 日期字符串 */
    fun fromEpochMillis(millis: Long): String =
        format(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())

    /** 中文显示，如 10月8日 周四 */
    fun display(date: String): String {
        val d = parse(date)
        val week = when (d.dayOfWeek.value) {
            1 -> "一"; 2 -> "二"; 3 -> "三"; 4 -> "四"; 5 -> "五"; 6 -> "六"; else -> "日"
        }
        return "${d.monthValue}月${d.dayOfMonth}日 周$week"
    }
}
