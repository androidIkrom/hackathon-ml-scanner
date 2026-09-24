package com.nungil.core.weather

import com.nungil.contract.Lang

/**
 * Date and time wording for the app bar (under the weather) and Home (above the greeting), in both languages.
 * [month] is 1..12, [dayOfWeek] 1 = Monday .. 7 = Sunday, [hour] 0..23.
 */
object Clock {
    private val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val monthsLong = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val daysShort = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val daysLong = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val daysKo = listOf("월", "화", "수", "목", "금", "토", "일")

    /** "Thu, Sep 25" / "9월 25일 (목)". */
    fun dateLabel(month: Int, day: Int, dayOfWeek: Int, lang: Lang): String = when (lang) {
        Lang.EN -> "${daysShort[dayOfWeek - 1]}, ${monthsShort[month - 1]} $day"
        Lang.KO -> "${month}월 ${day}일 (${daysKo[dayOfWeek - 1]})"
    }

    /** "Thursday, September 25." / "9월 25일 목요일이에요." */
    fun dateSpoken(month: Int, day: Int, dayOfWeek: Int, lang: Lang): String = when (lang) {
        Lang.EN -> "${daysLong[dayOfWeek - 1]}, ${monthsLong[month - 1]} $day."
        Lang.KO -> "${month}월 ${day}일 ${daysKo[dayOfWeek - 1]}요일이에요."
    }

    /** "14:05", or with a 12-hour phone "2:05 PM" / "오후 2:05". */
    fun timeLabel(hour: Int, minute: Int, is24Hour: Boolean, lang: Lang): String {
        val mm = minute.toString().padStart(2, '0')
        if (is24Hour) return "${hour.toString().padStart(2, '0')}:$mm"
        val h = twelve(hour)
        return when (lang) {
            Lang.EN -> "$h:$mm ${if (hour < 12) "AM" else "PM"}"
            Lang.KO -> "${if (hour < 12) "오전" else "오후"} $h:$mm"
        }
    }

    /** "It is 2:05 PM." / "지금 오후 2시 5분이에요." (always 12-hour when spoken, as people say it). */
    fun timeSpoken(hour: Int, minute: Int, lang: Lang): String {
        val h = twelve(hour)
        return when (lang) {
            Lang.EN -> "It is $h:${minute.toString().padStart(2, '0')} ${if (hour < 12) "AM" else "PM"}."
            // 시 ends in a vowel (시예요), 분 in a consonant (분이에요).
            Lang.KO -> "지금 ${if (hour < 12) "오전" else "오후"} " + if (minute == 0) "${h}시예요." else "${h}시 ${minute}분이에요."
        }
    }

    private fun twelve(hour: Int): Int = if (hour % 12 == 0) 12 else hour % 12
}
