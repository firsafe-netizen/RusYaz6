package ru.shkola.russkiy6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.shkola.russkiy6.data.Dictation
import ru.shkola.russkiy6.data.Exercise
import ru.shkola.russkiy6.data.GapParser
import ru.shkola.russkiy6.data.Piece
import ru.shkola.russkiy6.data.Repository
import ru.shkola.russkiy6.data.Test
import ru.shkola.russkiy6.data.TestQuestion
import ru.shkola.russkiy6.data.TheoryCard

private val types = listOf("dictation" to "Диктант", "test" to "Тест", "exercise" to "Упражнение", "theory" to "Вопрос")

/** Редактируемый вопрос теста. */
private class QDraft {
    var q by mutableStateOf("")
    val options = mutableStateListOf("", "", "")
    var correct by mutableStateOf(0)
    var explain by mutableStateOf("")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(initialSection: String, initialType: String, onBack: () -> Unit) {
    val choices = remember { Repository.sectionChoices() }
    var sectionId by rememberSaveable {
        mutableStateOf(initialSection.ifEmpty { Repository.MY_SECTION_ID }.let { id ->
            if (choices.any { it.first == id }) id else Repository.MY_SECTION_ID
        })
    }
    var type by rememberSaveable { mutableStateOf(initialType.takeIf { t -> types.any { it.first == t } } ?: "dictation") }
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }      // текст диктанта / строки упражнения / ответ
    var extra by rememberSaveable { mutableStateOf("") }     // примечание / задание / вопрос
    val questions = remember { mutableStateListOf(QDraft()) }
    var sectionMenu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun fail(msg: String) { scope.launch { snackbar.showSnackbar(msg) } }

    fun save() {
        when (type) {
            "theory" -> {
                if (extra.isBlank() || body.isBlank()) return fail("Заполните вопрос и ответ")
                Repository.addTheory(sectionId, TheoryCard(extra.trim(), body.trim()))
            }
            "exercise" -> {
                val lines = body.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) return fail("Добавьте хотя бы одну строку")
                if (lines.none { GapParser.parse(it).any { p -> p is Piece.Gap } })
                    return fail("Отметьте пропуски фигурными скобками, например: к{о}рова")
                Repository.addExercise(
                    sectionId,
                    Exercise(Repository.newId("ex"), title.ifBlank { "Моё упражнение" }, extra.trim(), lines)
                )
            }
            "test" -> {
                val qs = questions.filter { it.q.isNotBlank() }.map { d ->
                    val opts = d.options.map { it.trim() }
                    val nonEmpty = opts.withIndex().filter { it.value.isNotEmpty() }
                    val correctNew = nonEmpty.indexOfFirst { it.index == d.correct }
                    TestQuestion(d.q.trim(), nonEmpty.map { it.value }, correctNew, d.explain.trim())
                }
                if (qs.isEmpty()) return fail("Добавьте хотя бы один вопрос")
                if (qs.any { it.options.size < 2 || it.correct < 0 })
                    return fail("В каждом вопросе нужно минимум 2 варианта и отмеченный правильный ответ")
                Repository.addTest(sectionId, Test(Repository.newId("test"), title.ifBlank { "Мой тест" }, qs))
            }
            else -> {
                if (body.isBlank()) return fail("Введите текст диктанта")
                Repository.addDictation(
                    sectionId,
                    Dictation(Repository.newId("dict"), title.ifBlank { "Мой диктант" }, body.trim(), extra.trim())
                )
            }
        }
        onBack()
    }

    ScreenScaffold(
        title = "Добавить материал",
        onBack = onBack,
        snackbar = snackbar,
        actions = { TextButton(onClick = { save() }) { Text("Сохранить") } }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            Text("Раздел", style = MaterialTheme.typography.labelLarge)
            Box {
                OutlinedButton(onClick = { sectionMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(choices.firstOrNull { it.first == sectionId }?.second ?: "Мои материалы", Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = sectionMenu, onDismissRequest = { sectionMenu = false }) {
                    choices.forEach { (id, name) ->
                        DropdownMenuItem(text = { Text(name) }, onClick = { sectionId = id; sectionMenu = false })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                types.forEachIndexed { i, (key, name) ->
                    SegmentedButton(
                        selected = type == key,
                        onClick = { type = key },
                        shape = SegmentedButtonDefaults.itemShape(i, types.size)
                    ) { Text(name, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
                }
            }
            Spacer(Modifier.height(16.dp))

            when (type) {
                "theory" -> {
                    OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text("Вопрос") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp),
                        label = { Text("Ответ / правило") }
                    )
                }
                "exercise" -> {
                    Hint(
                        "Каждая строка — отдельное предложение или слово. Пропуск запишите в фигурных скобках:\n" +
                            "• {о} — ученик сам вписывает букву;\n" +
                            "• {о|а} — выбор из вариантов, первый — правильный;\n" +
                            "• {|ь} — правильно «ничего не писать» (вариант «(ничего)»).\n" +
                            "Пример: Зимой р{а|о}стения отдыхают. Мы уч{и|е}мся пис{а}ть."
                    )
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text("Задание (что нужно сделать)") })
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        label = { Text("Строки с пропусками") }
                    )
                    val gaps = body.lines().sumOf { l -> GapParser.parse(l).count { it is Piece.Gap } }
                    Text("Найдено пропусков: $gaps", style = MaterialTheme.typography.labelMedium)
                }
                "test" -> {
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название теста") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    questions.forEachIndexed { qi, d ->
                        QuestionEditor(qi, d, canDelete = questions.size > 1) { questions.removeAt(qi) }
                        Spacer(Modifier.height(10.dp))
                    }
                    OutlinedButton(onClick = { questions.add(QDraft()) }) {
                        Icon(Icons.Default.Add, null); Text("Добавить вопрос")
                    }
                }
                else -> {
                    Hint(
                        "Вставьте или наберите текст диктанта. Приложение само разделит его на предложения " +
                            "и части и продиктует с паузами. Скорость и темп настраиваются при прослушивании."
                    )
                    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("Название") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp),
                        label = { Text("Текст диктанта") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(extra, { extra = it }, Modifier.fillMaxWidth(), label = { Text("Примечание (необязательно)") })
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Text(text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuestionEditor(index: Int, d: QDraft, canDelete: Boolean, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Вопрос ${index + 1}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (canDelete) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Удалить вопрос") }
            }
            OutlinedTextField(d.q, { d.q = it }, Modifier.fillMaxWidth(), label = { Text("Текст вопроса") })
            Spacer(Modifier.height(6.dp))
            Text("Варианты (отметьте правильный):", style = MaterialTheme.typography.labelMedium)
            d.options.forEachIndexed { oi, o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = d.correct == oi, onClick = { d.correct = oi })
                    OutlinedTextField(
                        o, { d.options[oi] = it }, Modifier.weight(1f),
                        singleLine = true, placeholder = { Text("Вариант ${oi + 1}") }
                    )
                    if (d.options.size > 2) IconButton(onClick = {
                        d.options.removeAt(oi)
                        if (d.correct >= d.options.size) d.correct = 0
                    }) { Icon(Icons.Default.Close, "Убрать вариант") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (d.options.size < 6) TextButton(onClick = { d.options.add("") }) { Text("+ вариант") }
            }
            OutlinedTextField(
                d.explain, { d.explain = it }, Modifier.fillMaxWidth(),
                label = { Text("Пояснение (необязательно)") }
            )
        }
    }
}
