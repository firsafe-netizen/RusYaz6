package ru.shkola.russkiy6.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.data.Repository

@Composable
fun TestScreen(sid: String, id: String, onBack: () -> Unit) {
    val test = Repository.section(sid)?.tests?.firstOrNull { it.id == id }
    ScreenScaffold(title = test?.title ?: "Тест", onBack = onBack) { padding ->
        if (test == null || test.questions.isEmpty()) {
            EmptyNote("Тест не найден или пуст", padding); return@ScreenScaffold
        }
        var attempt by remember { mutableIntStateOf(0) }
        // порядок вариантов перемешивается при каждой попытке
        val orders = remember(test, attempt) { test.questions.map { it.options.indices.shuffled() } }
        var index by remember(test, attempt) { mutableIntStateOf(0) }
        var chosen by remember(test, attempt, index) { mutableStateOf<Int?>(null) }
        val results = remember(test, attempt) { mutableStateListOf<Pair<Int, Int>>() } // вопрос -> выбранный вариант

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            val total = test.questions.size
            LinearProgressIndicator(
                progress = { index.toFloat() / total },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            if (index >= total) {
                val right = results.count { (qi, oi) -> test.questions[qi].correct == oi }
                val percent = right * 100 / total
                Text("Результат: $right из $total", style = MaterialTheme.typography.headlineSmall)
                Text("$percent% · ${grade(percent)}", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(16.dp))
                val wrong = results.filter { (qi, oi) -> test.questions[qi].correct != oi }
                if (wrong.isNotEmpty()) {
                    Text("Работа над ошибками", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    wrong.forEach { (qi, oi) ->
                        val q = test.questions[qi]
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text("${qi + 1}. ${q.q}", fontWeight = FontWeight.SemiBold)
                            Text("Ваш ответ: ${q.options.getOrElse(oi) { "—" }}", color = Marks.bad)
                            Text("Правильно: ${q.options.getOrElse(q.correct) { "—" }}", color = Marks.good)
                            if (q.explain.isNotEmpty()) Text(q.explain, style = MaterialTheme.typography.bodySmall)
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Button(onClick = { attempt++ }, modifier = Modifier.fillMaxWidth()) { Text("Пройти ещё раз") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("К разделу") }
                return@Column
            }

            val q = test.questions[index]
            Text("Вопрос ${index + 1} из $total", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(q.q, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            orders[index].forEach { oi ->
                val isRight = oi == q.correct
                val answered = chosen != null
                val container = when {
                    !answered -> MaterialTheme.colorScheme.surface
                    isRight -> Marks.goodBg
                    chosen == oi -> Marks.badBg
                    else -> MaterialTheme.colorScheme.surface
                }
                val borderColor = when {
                    !answered -> MaterialTheme.colorScheme.outline
                    isRight -> Marks.good
                    chosen == oi -> Marks.bad
                    else -> MaterialTheme.colorScheme.outlineVariant
                }
                OutlinedCard(
                    onClick = { if (!answered) { chosen = oi; results.add(index to oi) } },
                    colors = CardDefaults.outlinedCardColors(containerColor = container),
                    border = BorderStroke(1.5.dp, borderColor),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(q.options[oi], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (answered && isRight) Icon(Icons.Default.CheckCircle, null, tint = Marks.good, modifier = Modifier.size(22.dp))
                        if (answered && chosen == oi && !isRight) Icon(Icons.Default.Cancel, null, tint = Marks.bad, modifier = Modifier.size(22.dp))
                    }
                }
            }
            if (chosen != null) {
                Spacer(Modifier.height(12.dp))
                if (q.explain.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(q.explain, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        index++
                        if (index >= total) {
                            val right = results.count { (qi, oi) -> test.questions[qi].correct == oi }
                            Repository.saveResult("test:${test.id}", right * 100 / total)
                        }
                    }) { Text(if (index == total - 1) "Завершить" else "Далее") }
                }
            }
        }
    }
}
