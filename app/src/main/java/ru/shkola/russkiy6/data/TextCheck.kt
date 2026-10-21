package ru.shkola.russkiy6.data

/** Сравнение текста ученика с образцом диктанта (по словам и знакам препинания). */
object TextCheck {

    sealed interface Op {
        data class Same(val token: String) : Op
        /** Ученик написал [written], а нужно [expected]. */
        data class Wrong(val written: String, val expected: String) : Op
        /** Пропущено. */
        data class Missing(val expected: String) : Op
        /** Лишнее. */
        data class Extra(val written: String) : Op
    }

    data class Result(val ops: List<Op>, val errors: Int, val total: Int)

    private val tokenRx = Regex("[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*|[.,!?;:—–«»\"()…-]")

    fun tokens(text: String): List<String> = tokenRx.findAll(text).map { it.value }.toList()

    private fun norm(t: String): String = when (t) {
        "–", "-" -> "—"
        "\"" -> "«"
        "»" -> "«"
        else -> t.replace('ё', 'е').replace('Ё', 'Е')
    }

    fun compare(original: String, written: String): Result {
        val a = tokens(original)
        val b = tokens(written)
        val na = a.map(::norm)
        val nb = b.map(::norm)
        val n = a.size
        val m = b.size
        // LCS
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (na[i] == nb[j]) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val raw = mutableListOf<Op>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                na[i] == nb[j] -> { raw += Op.Same(a[i]); i++; j++ }
                dp[i + 1][j] >= dp[i][j + 1] -> { raw += Op.Missing(a[i]); i++ }
                else -> { raw += Op.Extra(b[j]); j++ }
            }
        }
        while (i < n) raw += Op.Missing(a[i++])
        while (j < m) raw += Op.Extra(b[j++])

        // Склеиваем «пропущено + лишнее» в «ошибка в слове»
        val ops = mutableListOf<Op>()
        var k = 0
        while (k < raw.size) {
            val x = raw[k]
            val y = raw.getOrNull(k + 1)
            if (x is Op.Missing && y is Op.Extra) { ops += Op.Wrong(y.written, x.expected); k += 2 }
            else if (x is Op.Extra && y is Op.Missing) { ops += Op.Wrong(x.written, y.expected); k += 2 }
            else { ops += x; k++ }
        }
        val errors = ops.count { it !is Op.Same }
        return Result(ops, errors, a.size)
    }

    fun isPunct(t: String) = t.length == 1 && !t[0].isLetterOrDigit()
}
