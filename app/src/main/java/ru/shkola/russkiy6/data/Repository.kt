package ru.shkola.russkiy6.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * Хранилище учебных материалов.
 *  - встроенные материалы: assets/content.json (только чтение);
 *  - материалы пользователя: files/user_content.json (добавляются в редакторе или импортом).
 */
object Repository {

    const val MY_SECTION_ID = "my"
    private const val USER_FILE = "user_content.json"

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    private var base: List<Section> = emptyList()
    private var userSections: List<Section> = emptyList()

    /** Объединённый список разделов для интерфейса. */
    var sections by mutableStateOf<List<Section>>(emptyList())
        private set

    /** Лучшие результаты: ключ → процент. */
    val progress = mutableStateMapOf<String, Int>()

    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        prefs = appContext.getSharedPreferences("progress", Context.MODE_PRIVATE)
        base = runCatching {
            val text = appContext.assets.open("content.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            ContentJson.parse(text, user = false)
        }.getOrElse { emptyList() }
        userSections = runCatching {
            val f = File(appContext.filesDir, USER_FILE)
            if (f.exists()) ContentJson.parse(f.readText(Charsets.UTF_8), user = true) else emptyList()
        }.getOrElse { emptyList() }
        prefs.all.forEach { (k, v) -> if (v is Int) progress[k] = v }
        rebuild()
    }

    private fun rebuild() {
        val userById = userSections.associateBy { it.id }
        val merged = base.map { b ->
            val u = userById[b.id]
            if (u == null) b else b.copy(
                theory = b.theory + u.theory,
                exercises = b.exercises + u.exercises,
                tests = b.tests + u.tests,
                dictations = b.dictations + u.dictations
            )
        }
        val baseIds = base.map { it.id }.toSet()
        val extra = userSections.filter { it.id !in baseIds && !it.isEmpty }
        sections = merged + extra
    }

    fun section(id: String): Section? = sections.firstOrNull { it.id == id }

    /* ------------------------- Прогресс ------------------------- */

    fun saveResult(key: String, percent: Int) {
        val old = progress[key] ?: -1
        if (percent > old) {
            progress[key] = percent
            prefs.edit().putInt(key, percent).apply()
        }
    }

    fun resetProgress() {
        progress.clear()
        prefs.edit().clear().apply()
    }

    /* ------------------- Материалы пользователя ------------------- */

    private fun persistUser() {
        File(appContext.filesDir, USER_FILE).writeText(ContentJson.write(userSections), Charsets.UTF_8)
        rebuild()
    }

    private fun userSectionFor(sectionId: String): Section {
        userSections.firstOrNull { it.id == sectionId }?.let { return it }
        val baseSec = base.firstOrNull { it.id == sectionId }
        return Section(
            id = sectionId,
            title = baseSec?.title ?: "Мои материалы",
            description = baseSec?.description ?: "Задания и диктанты, добавленные вами",
            theory = emptyList(), exercises = emptyList(), tests = emptyList(), dictations = emptyList()
        )
    }

    private fun updateUserSection(sectionId: String, change: (Section) -> Section) {
        val updated = change(userSectionFor(sectionId))
        userSections = if (userSections.any { it.id == sectionId })
            userSections.map { if (it.id == sectionId) updated else it }
        else userSections + updated
        persistUser()
    }

    fun newId(prefix: String) = "$prefix-u${System.currentTimeMillis()}"

    fun addTheory(sectionId: String, card: TheoryCard) =
        updateUserSection(sectionId) { it.copy(theory = it.theory + card.copy(user = true)) }

    fun addExercise(sectionId: String, ex: Exercise) =
        updateUserSection(sectionId) { it.copy(exercises = it.exercises + ex.copy(user = true)) }

    fun addTest(sectionId: String, test: Test) =
        updateUserSection(sectionId) { it.copy(tests = it.tests + test.copy(user = true)) }

    fun addDictation(sectionId: String, d: Dictation) =
        updateUserSection(sectionId) { it.copy(dictations = it.dictations + d.copy(user = true)) }

    fun deleteTheory(card: TheoryCard) {
        userSections = userSections.map { s -> s.copy(theory = s.theory.filterNot { it.q == card.q && it.a == card.a }) }
        persistUser()
    }

    fun deleteExercise(id: String) {
        userSections = userSections.map { s -> s.copy(exercises = s.exercises.filterNot { it.id == id }) }
        persistUser()
    }

    fun deleteTest(id: String) {
        userSections = userSections.map { s -> s.copy(tests = s.tests.filterNot { it.id == id }) }
        persistUser()
    }

    fun deleteDictation(id: String) {
        userSections = userSections.map { s -> s.copy(dictations = s.dictations.filterNot { it.id == id }) }
        persistUser()
    }

    /* ------------------------ Импорт / экспорт ------------------------ */

    /**
     * Импортирует файл JSON того же формата, что и assets/content.json.
     * Материалы с совпадающим id раздела добавляются в этот раздел.
     * Возвращает количество добавленных элементов.
     */
    fun importFrom(uri: Uri): Int {
        val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: error("Не удалось открыть файл")
        val incoming = ContentJson.parse(text, user = true)
        var count = 0
        var result = userSections
        for (inc in incoming) {
            // делаем id уникальными, чтобы повторный импорт не ломал прогресс и удаление
            val stamp = System.nanoTime()
            val fixed = inc.copy(
                exercises = inc.exercises.mapIndexed { i, e -> e.copy(id = "${e.id}-i$stamp$i") },
                tests = inc.tests.mapIndexed { i, t -> t.copy(id = "${t.id}-i$stamp$i") },
                dictations = inc.dictations.mapIndexed { i, d -> d.copy(id = "${d.id}-i$stamp$i") }
            )
            count += fixed.theory.size + fixed.exercises.size + fixed.tests.size + fixed.dictations.size
            val existing = result.firstOrNull { it.id == fixed.id }
            result = if (existing == null) result + fixed else result.map {
                if (it.id == fixed.id) it.copy(
                    theory = it.theory + fixed.theory,
                    exercises = it.exercises + fixed.exercises,
                    tests = it.tests + fixed.tests,
                    dictations = it.dictations + fixed.dictations
                ) else it
            }
        }
        userSections = result
        persistUser()
        return count
    }

    fun exportTo(uri: Uri) {
        appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(ContentJson.write(userSections))
        } ?: error("Не удалось записать файл")
    }

    fun userItemsCount(): Int =
        userSections.sumOf { it.theory.size + it.exercises.size + it.tests.size + it.dictations.size }

    /** Список разделов для выбора в редакторе: встроенные + «Мои материалы». */
    fun sectionChoices(): List<Pair<String, String>> {
        val list = base.map { it.id to it.title }.toMutableList()
        userSections.filter { s -> list.none { it.first == s.id } }.forEach { list += it.id to it.title }
        if (list.none { it.first == MY_SECTION_ID }) list += MY_SECTION_ID to "Мои материалы"
        return list
    }
}
