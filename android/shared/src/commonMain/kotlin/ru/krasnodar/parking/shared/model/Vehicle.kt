package ru.krasnodar.parking.shared.model

import kotlinx.serialization.Serializable

/**
 * Автомобиль гаража. Хранится только на устройстве. Госномер не пишем в логи:
 * для отладки есть [Plate.masked].
 */
@Serializable
data class Vehicle(
    val id: String,
    val plate: String,
    /** Марка и модель, если пользователь их указал. */
    val title: String = "",
    /** Пользовательское прозвище: «Моя», «Папина». */
    val nickname: String = "",
)

/** Нормализация и проверка российского госномера. */
object Plate {
    // Буквы, разрешённые в номерах: у них есть латинские двойники.
    private const val LETTERS = "АВЕКМНОРСТУХ"
    private val latinToCyr = mapOf(
        'A' to 'А', 'B' to 'В', 'E' to 'Е', 'K' to 'К', 'M' to 'М', 'H' to 'Н',
        'O' to 'О', 'P' to 'Р', 'C' to 'С', 'T' to 'Т', 'Y' to 'У', 'X' to 'Х',
    )
    private val standard = Regex("^[$LETTERS]\\d{3}[$LETTERS]{2}\\d{2,3}$")

    /** «а 123 вс 93» → «А123ВС93». Латиница заменяется кириллицей. */
    fun normalize(input: String): String = buildString {
        for (ch in input.uppercase()) {
            when {
                ch.isWhitespace() || ch == '-' -> Unit
                ch == 'Ё' -> append('Е')
                else -> append(latinToCyr[ch] ?: ch)
            }
        }
    }

    /** Стандартный номер легкового автомобиля (тип 1). Другие форматы вводятся вручную без проверки. */
    fun isStandard(normalized: String): Boolean = standard.matches(normalized)

    /** «А123ВС93» → «А123ВС 93» для показа. */
    fun display(normalized: String): String =
        if (isStandard(normalized)) normalized.substring(0, 6) + " " + normalized.substring(6) else normalized

    /** Для логов и отладки: «А***ВС**». */
    fun masked(normalized: String): String =
        normalized.mapIndexed { i, c -> if (c.isDigit() || i > 5) '*' else c }.joinToString("")
}
