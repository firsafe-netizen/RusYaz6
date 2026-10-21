package ru.shkola.russkiy6.tts

import android.content.Context
import androidx.core.content.edit

/** Настройки диктовки. */
data class DictationSettings(
    /** Скорость речи: 1.0 — обычная. */
    val rate: Float = 0.8f,
    /** Высота голоса (тон): 1.0 — обычная. */
    val pitch: Float = 1.0f,
    /** Темп диктовки: секунд на запись одного слова. */
    val secPerWord: Float = 1.6f,
    /** Сколько раз повторять каждую часть предложения. */
    val repeats: Int = 2,
    /** Максимальная длина части (в словах). */
    val maxWords: Int = 5,
    /** Пауза между предложениями, сек. */
    val sentencePause: Float = 2f,
    val readWholeFirst: Boolean = true,
    val readSentenceBeforeParts: Boolean = true,
    val readWholeLast: Boolean = true,
    /** Имя выбранного голоса или пусто — голос по умолчанию. */
    val voiceName: String = ""
) {
    companion object {
        private const val P = "dictation_settings"

        fun load(c: Context): DictationSettings {
            val p = c.getSharedPreferences(P, Context.MODE_PRIVATE)
            val d = DictationSettings()
            return DictationSettings(
                rate = p.getFloat("rate", d.rate),
                pitch = p.getFloat("pitch", d.pitch),
                secPerWord = p.getFloat("secPerWord", d.secPerWord),
                repeats = p.getInt("repeats", d.repeats),
                maxWords = p.getInt("maxWords", d.maxWords),
                sentencePause = p.getFloat("sentencePause", d.sentencePause),
                readWholeFirst = p.getBoolean("readWholeFirst", d.readWholeFirst),
                readSentenceBeforeParts = p.getBoolean("readSentenceBeforeParts", d.readSentenceBeforeParts),
                readWholeLast = p.getBoolean("readWholeLast", d.readWholeLast),
                voiceName = p.getString("voiceName", d.voiceName) ?: ""
            )
        }

        fun save(c: Context, s: DictationSettings) {
            c.getSharedPreferences(P, Context.MODE_PRIVATE).edit {
                putFloat("rate", s.rate)
                putFloat("pitch", s.pitch)
                putFloat("secPerWord", s.secPerWord)
                putInt("repeats", s.repeats)
                putInt("maxWords", s.maxWords)
                putFloat("sentencePause", s.sentencePause)
                putBoolean("readWholeFirst", s.readWholeFirst)
                putBoolean("readSentenceBeforeParts", s.readSentenceBeforeParts)
                putBoolean("readWholeLast", s.readWholeLast)
                putString("voiceName", s.voiceName)
            }
        }
    }
}

enum class StepKind { WHOLE_FIRST, SENTENCE, PART, WHOLE_LAST }

/**
 * Один шаг диктовки.
 * @param parts фразы, которые произносятся подряд (с короткой паузой)
 * @param pauseAfterMs пауза после шага — время на запись
 */
data class Step(
    val kind: StepKind,
    val parts: List<String>,
    val pauseAfterMs: Long,
    val sentenceIndex: Int,
    val repeatNo: Int = 0
) {
    val label: String
        get() = when (kind) {
            StepKind.WHOLE_FIRST -> "Чтение всего текста"
            StepKind.SENTENCE -> "Предложение ${sentenceIndex + 1} целиком"
            StepKind.PART -> "Предложение ${sentenceIndex + 1}" + if (repeatNo > 0) " (повтор)" else ""
            StepKind.WHOLE_LAST -> "Проверочное чтение"
        }
}

object DictationPlanner {

    private val sentenceSplit = Regex("(?<=[.!?…])[»\"]?\\s+(?=[«\"—–-]?\\s*[А-ЯЁA-Z0-9])")

    fun sentences(text: String): List<String> {
        val clean = text.replace(Regex("\\s+"), " ").trim()
        if (clean.isEmpty()) return emptyList()
        // Разбиваем, сохраняя закрывающие кавычки у предыдущего предложения
        val result = mutableListOf<String>()
        var last = 0
        for (m in sentenceSplit.findAll(clean)) {
            val end = m.range.first + m.value.trimEnd().length
            result += clean.substring(last, end).trim()
            last = m.range.last + 1
        }
        if (last < clean.length) result += clean.substring(last).trim()
        return result.filter { it.isNotBlank() }
    }

    private fun wordCount(s: String) = s.split(' ').count { w -> w.any { it.isLetterOrDigit() } }

    /** Делит предложение на части по знакам препинания и по длине. */
    fun parts(sentence: String, maxWords: Int): List<String> {
        // 1. по знакам препинания внутри предложения (знак остаётся в конце части)
        val raw = mutableListOf<String>()
        val sb = StringBuilder()
        val words = sentence.split(' ').filter { it.isNotEmpty() }
        for (w in words) {
            if (sb.isNotEmpty()) sb.append(' ')
            sb.append(w)
            val endsWithBreak = w.last() in ",;:" || w == "—" || w == "–"
            if (endsWithBreak) {
                raw += sb.toString(); sb.clear()
            }
        }
        if (sb.isNotEmpty()) raw += sb.toString()

        // 2. длинные части делим по количеству слов
        val bySize = mutableListOf<String>()
        for (r in raw) {
            val ws = r.split(' ')
            if (wordCount(r) <= maxWords) bySize += r
            else {
                // делим на примерно равные части, не длиннее maxWords
                val k = (ws.size + maxWords - 1) / maxWords
                val size = (ws.size + k - 1) / k
                val chunks = ws.chunked(size).map { it.joinToString(" ") }.toMutableList()
                // не оставляем «хвост» из одного слова
                if (chunks.size > 1 && wordCount(chunks.last()) == 1) {
                    val tail = chunks.removeAt(chunks.lastIndex)
                    chunks[chunks.lastIndex] = chunks.last() + " " + tail
                }
                bySize += chunks
            }
        }

        // 3. слишком короткие части объединяем со следующими
        val merged = mutableListOf<String>()
        for (p in bySize) {
            if (merged.isNotEmpty() && (wordCount(merged.last()) < 2 || wordCount(p) < 2) &&
                wordCount(merged.last()) + wordCount(p) <= maxWords + 1
            ) {
                merged[merged.lastIndex] = merged.last() + " " + p
            } else merged += p
        }
        return merged
    }

    fun build(text: String, s: DictationSettings): List<Step> {
        val sents = sentences(text)
        val steps = mutableListOf<Step>()
        if (s.readWholeFirst && sents.isNotEmpty())
            steps += Step(StepKind.WHOLE_FIRST, sents, 3000, -1)
        sents.forEachIndexed { si, sentence ->
            val ps = parts(sentence, s.maxWords)
            if (s.readSentenceBeforeParts && ps.size > 1)
                steps += Step(StepKind.SENTENCE, listOf(sentence), 1200, si)
            ps.forEachIndexed { pi, p ->
                val writeMs = (wordCount(p) * s.secPerWord * 1000).toLong().coerceAtLeast(1500)
                repeat(s.repeats.coerceAtLeast(1)) { r ->
                    val isLastRepeat = r == s.repeats.coerceAtLeast(1) - 1
                    val pause = when {
                        !isLastRepeat -> 900L
                        pi == ps.lastIndex -> writeMs + (s.sentencePause * 1000).toLong()
                        else -> writeMs
                    }
                    steps += Step(StepKind.PART, listOf(p), pause, si, r)
                }
            }
        }
        if (s.readWholeLast && sents.isNotEmpty())
            steps += Step(StepKind.WHOLE_LAST, sents, 0, -1)
        return steps
    }
}
