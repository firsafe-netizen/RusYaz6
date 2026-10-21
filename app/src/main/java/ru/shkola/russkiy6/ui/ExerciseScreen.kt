package ru.shkola.russkiy6.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.data.GapParser
import ru.shkola.russkiy6.data.Piece
import ru.shkola.russkiy6.data.Repository

private fun same(a: String, b: String) = a.trim().equals(b.trim(), ignoreCase = true)

private fun shown(opt: String) = if (opt.isEmpty()) "(ничего)" else opt

@Composable
fun ExerciseScreen(sid: String, id: String, onBack: () -> Unit) {
    val ex = Repository.section(sid)?.exercises?.firstOrNull { it.id == id }
    ScreenScaffold(title = ex?.title ?: "Упражнение", onBack = onBack) { padding ->
        if (ex == null) { EmptyNote("Упражнение не найдено", padding); return@ScreenScaffold }

        // Разбор всех строк с единой нумерацией пропусков
        val lines: List<List<Piece>> = remember(ex) {
            var counter = 0
            ex.items.map { line ->
                val pieces = GapParser.parse(line, counter)
                counter += pieces.count { it is Piece.Gap }
                pieces
            }
        }
        val gaps = remember(lines) { lines.flatten().filterIsInstance<Piece.Gap>() }
        val shuffled = remember(gaps) { gaps.associate { it.index to it.options.shuffled() } }
        val answers = remember(ex) { mutableStateMapOf<Int, String>() }
        var checked by remember(ex) { mutableStateOf(false) }

        val correctCount = gaps.count { g -> answers[g.index]?.let { same(it, g.answer) } == true }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            if (ex.instruction.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(ex.instruction, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(16.dp))
            }
            lines.forEachIndexed { li, pieces ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        "${li + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(30.dp).padding(top = 4.dp)
                    )
                    GapLine(
                        pieces = pieces,
                        answers = answers,
                        shuffled = shuffled,
                        checked = checked,
                        onAnswer = { idx, v -> if (!checked) answers[idx] = v }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            if (!checked) {
                Text(
                    "Заполнено ${answers.count { (k, v) -> v.isNotBlank() || gaps.any { it.index == k && it.isChoice } }} из ${gaps.size}",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        checked = true
                        val percent = if (gaps.isEmpty()) 100 else correctCount * 100 / gaps.size
                        Repository.saveResult("ex:${ex.id}", percent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Проверить") }
            } else {
                val percent = if (gaps.isEmpty()) 100 else correctCount * 100 / gaps.size
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (percent >= 80) Marks.goodBg else MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Верно: $correctCount из ${gaps.size} ($percent%)", style = MaterialTheme.typography.titleMedium)
                        Text(grade(percent), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Красным отмечены ошибки, рядом — правильный ответ.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { answers.clear(); checked = false },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Выполнить заново") }
            }
        }
    }
}

fun grade(percent: Int): String = when {
    percent >= 90 -> "Отлично! Оценка «5»."
    percent >= 75 -> "Хорошо. Оценка «4»."
    percent >= 50 -> "Удовлетворительно. Оценка «3». Повторите правило."
    else -> "Нужно повторить тему и попробовать ещё раз."
}

/**
 * Строка с пропусками. Слова переносятся целиком: пропуск внутри слова
 * не отрывается от букв вокруг него.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GapLine(
    pieces: List<Piece>,
    answers: Map<Int, String>,
    shuffled: Map<Int, List<String>>,
    checked: Boolean,
    onAnswer: (Int, String) -> Unit
) {
    // Группируем куски в «слова»
    val units = remember(pieces) {
        val res = mutableListOf<MutableList<Piece>>(mutableListOf())
        for (p in pieces) {
            when (p) {
                is Piece.Gap -> res.last().add(p)
                is Piece.Text -> {
                    val segs = p.text.split(' ')
                    segs.forEachIndexed { i, seg ->
                        if (i > 0) res.add(mutableListOf())
                        if (seg.isNotEmpty()) res.last().add(Piece.Text(seg))
                    }
                }
            }
        }
        res.filter { it.isNotEmpty() }
    }
    val style = MaterialTheme.typography.bodyLarge
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        units.forEach { unit ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                unit.forEach { p ->
                    when (p) {
                        is Piece.Text -> Text(p.text, style = style)
                        is Piece.Gap -> GapField(p, answers[p.index], shuffled[p.index] ?: p.options, checked) {
                            onAnswer(p.index, it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GapField(
    gap: Piece.Gap,
    value: String?,
    options: List<String>,
    checked: Boolean,
    onChange: (String) -> Unit
) {
    val ok = value != null && same(value, gap.answer)
    val border = when {
        !checked -> MaterialTheme.colorScheme.primary
        ok -> Marks.good
        else -> Marks.bad
    }
    val bg = when {
        !checked -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ok -> Marks.goodBg
        else -> Marks.badBg
    }
    val shape = RoundedCornerShape(6.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (gap.isChoice) {
            var open by remember { mutableStateOf(false) }
            Box {
                Text(
                    text = if (value == null) " ? " else shown(value),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = if (checked && !ok) Marks.bad else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(horizontal = 1.dp)
                        .background(bg, shape)
                        .border(1.5.dp, border, shape)
                        .widthIn(min = 30.dp)
                        .clickable(enabled = !checked) { open = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    options.forEach { o ->
                        DropdownMenuItem(
                            text = { Text(shown(o), style = MaterialTheme.typography.titleMedium) },
                            onClick = { onChange(o); open = false }
                        )
                    }
                }
            }
        } else {
            BasicTextField(
                value = value ?: "",
                onValueChange = { if (it.length <= 12) onChange(it) },
                enabled = !checked,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = if (checked && !ok) Marks.bad else MaterialTheme.colorScheme.onSurface
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Next
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .background(bg, shape)
                    .border(1.5.dp, border, shape)
                    .width((26 + 12 * maxOf(gap.answer.length, (value ?: "").length, 1)).dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        if (checked && !ok) {
            Text(
                "(${shown(gap.answer)})",
                color = Marks.good,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}
