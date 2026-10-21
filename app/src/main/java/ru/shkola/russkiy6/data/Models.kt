package ru.shkola.russkiy6.data

import org.json.JSONArray
import org.json.JSONObject

/** Обучающий вопрос (карточка «вопрос — ответ»). */
data class TheoryCard(val q: String, val a: String, val user: Boolean = false)

/**
 * Упражнение с пропусками.
 * В строке задания пропуск записывается в фигурных скобках:
 *   {о}        — ученик вписывает букву сам;
 *   {о|а}      — выбор из вариантов, ПЕРВЫЙ вариант правильный (на экране варианты перемешиваются);
 *   {|ь}       — пустой правильный вариант (показывается как «(ничего)»).
 */
data class Exercise(
    val id: String,
    val title: String,
    val instruction: String,
    val items: List<String>,
    val user: Boolean = false
)

data class TestQuestion(
    val q: String,
    val options: List<String>,
    val correct: Int,
    val explain: String = ""
)

data class Test(
    val id: String,
    val title: String,
    val questions: List<TestQuestion>,
    val user: Boolean = false
)

data class Dictation(
    val id: String,
    val title: String,
    val text: String,
    val note: String = "",
    val user: Boolean = false
)

data class Section(
    val id: String,
    val title: String,
    val description: String,
    val theory: List<TheoryCard>,
    val exercises: List<Exercise>,
    val tests: List<Test>,
    val dictations: List<Dictation>
) {
    val isEmpty: Boolean
        get() = theory.isEmpty() && exercises.isEmpty() && tests.isEmpty() && dictations.isEmpty()
}

/* ---------------------------- Разбор JSON ---------------------------- */

object ContentJson {

    fun parse(text: String, user: Boolean): List<Section> {
        val root = JSONObject(text)
        val arr = root.optJSONArray("sections") ?: JSONArray()
        return (0 until arr.length()).map { parseSection(arr.getJSONObject(it), user) }
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).map { a.getString(it) }

    private fun parseSection(o: JSONObject, user: Boolean): Section {
        val theory = o.optJSONArray("theory").objects().map {
            TheoryCard(it.getString("q"), it.getString("a"), user)
        }
        val exercises = o.optJSONArray("exercises").objects().map {
            Exercise(
                id = it.getString("id"),
                title = it.optString("title", "Упражнение"),
                instruction = it.optString("instruction", ""),
                items = strings(it.optJSONArray("items")),
                user = user
            )
        }
        val tests = o.optJSONArray("tests").objects().map { t ->
            Test(
                id = t.getString("id"),
                title = t.optString("title", "Тест"),
                questions = t.optJSONArray("questions").objects().map { q ->
                    TestQuestion(
                        q = q.getString("q"),
                        options = strings(q.optJSONArray("options")),
                        correct = q.optInt("correct", 0),
                        explain = q.optString("explain", "")
                    )
                },
                user = user
            )
        }
        val dictations = o.optJSONArray("dictations").objects().map {
            Dictation(
                id = it.getString("id"),
                title = it.optString("title", "Диктант"),
                text = it.getString("text"),
                note = it.optString("note", ""),
                user = user
            )
        }
        return Section(
            id = o.getString("id"),
            title = o.optString("title", "Раздел"),
            description = o.optString("description", ""),
            theory = theory,
            exercises = exercises,
            tests = tests,
            dictations = dictations
        )
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    /* ---------------------------- Запись ---------------------------- */

    fun write(sections: List<Section>): String {
        val arr = JSONArray()
        sections.filter { !it.isEmpty }.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("title", s.title)
            o.put("description", s.description)
            o.put("theory", JSONArray().apply {
                s.theory.forEach { put(JSONObject().put("q", it.q).put("a", it.a)) }
            })
            o.put("exercises", JSONArray().apply {
                s.exercises.forEach { e ->
                    put(
                        JSONObject().put("id", e.id).put("title", e.title)
                            .put("instruction", e.instruction)
                            .put("items", JSONArray(e.items))
                    )
                }
            })
            o.put("tests", JSONArray().apply {
                s.tests.forEach { t ->
                    put(JSONObject().put("id", t.id).put("title", t.title).put("questions",
                        JSONArray().apply {
                            t.questions.forEach { q ->
                                put(
                                    JSONObject().put("q", q.q)
                                        .put("options", JSONArray(q.options))
                                        .put("correct", q.correct)
                                        .put("explain", q.explain)
                                )
                            }
                        }))
                }
            })
            o.put("dictations", JSONArray().apply {
                s.dictations.forEach { d ->
                    put(
                        JSONObject().put("id", d.id).put("title", d.title)
                            .put("text", d.text).put("note", d.note)
                    )
                }
            })
            arr.put(o)
        }
        return JSONObject().put("version", 1).put("sections", arr).toString(2)
    }
}

/* ------------------------ Разбор пропусков ------------------------ */

sealed interface Piece {
    data class Text(val text: String) : Piece
    /** options[0] — правильный ответ. */
    data class Gap(val index: Int, val options: List<String>) : Piece {
        val answer: String get() = options.first()
        val isChoice: Boolean get() = options.size > 1
    }
}

object GapParser {
    private val regex = Regex("\\{([^{}]*)\\}")

    fun parse(line: String, startIndex: Int = 0): List<Piece> {
        val result = mutableListOf<Piece>()
        var last = 0
        var idx = startIndex
        for (m in regex.findAll(line)) {
            if (m.range.first > last) result += Piece.Text(line.substring(last, m.range.first))
            val opts = m.groupValues[1].split('|').map { it.trim() }
            result += Piece.Gap(idx++, opts)
            last = m.range.last + 1
        }
        if (last < line.length) result += Piece.Text(line.substring(last))
        return result
    }

    /** Строка с подставленными правильными ответами. */
    fun solved(line: String): String = regex.replace(line) { it.groupValues[1].split('|').first().trim() }
}
