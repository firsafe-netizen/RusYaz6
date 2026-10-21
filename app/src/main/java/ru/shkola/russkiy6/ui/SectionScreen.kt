package ru.shkola.russkiy6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.data.Repository

@Composable
fun SectionScreen(
    sid: String,
    onBack: () -> Unit,
    openTheory: () -> Unit,
    openExercise: (String) -> Unit,
    openTest: (String) -> Unit,
    openDictation: (String) -> Unit,
    openEditor: (String) -> Unit
) {
    val s = Repository.section(sid)
    var toDelete by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    ScreenScaffold(title = s?.title ?: "Раздел", onBack = onBack) { padding ->
        if (s == null) {
            EmptyNote("Раздел не найден", padding); return@ScreenScaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (s.description.isNotEmpty()) item {
                Text(
                    s.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            header("Обучающие вопросы", Icons.Default.School) { openEditor("theory") }
            if (s.theory.isEmpty()) emptyLine()
            else item {
                Entry(
                    title = "Карточки «вопрос — ответ»",
                    subtitle = "${s.theory.size} ${plural(s.theory.size, "вопрос", "вопроса", "вопросов")}",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    onClick = openTheory
                )
            }

            header("Упражнения", Icons.Default.Edit) { openEditor("exercise") }
            if (s.exercises.isEmpty()) emptyLine()
            items(s.exercises, key = { "e" + it.id }) { e ->
                Entry(
                    title = e.title,
                    subtitle = resultText("ex:${e.id}", "${e.items.size} ${plural(e.items.size, "строка", "строки", "строк")}"),
                    icon = Icons.Default.Edit,
                    done = Repository.progress.containsKey("ex:${e.id}"),
                    onClick = { openExercise(e.id) },
                    onDelete = if (e.user) ({ toDelete = e.title to { Repository.deleteExercise(e.id) } }) else null
                )
            }

            header("Тесты", Icons.Default.Quiz) { openEditor("test") }
            if (s.tests.isEmpty()) emptyLine()
            items(s.tests, key = { "t" + it.id }) { t ->
                Entry(
                    title = t.title,
                    subtitle = resultText("test:${t.id}", "${t.questions.size} ${plural(t.questions.size, "вопрос", "вопроса", "вопросов")}"),
                    icon = Icons.Default.Quiz,
                    done = Repository.progress.containsKey("test:${t.id}"),
                    onClick = { openTest(t.id) },
                    onDelete = if (t.user) ({ toDelete = t.title to { Repository.deleteTest(t.id) } }) else null
                )
            }

            header("Диктанты", Icons.Default.RecordVoiceOver) { openEditor("dictation") }
            if (s.dictations.isEmpty()) emptyLine()
            items(s.dictations, key = { "d" + it.id }) { d ->
                val words = d.text.split(Regex("\\s+")).count { w -> w.any { it.isLetter() } }
                Entry(
                    title = d.title,
                    subtitle = resultText("dict:${d.id}", "$words ${plural(words, "слово", "слова", "слов")}"),
                    icon = Icons.Default.RecordVoiceOver,
                    done = Repository.progress.containsKey("dict:${d.id}"),
                    onClick = { openDictation(d.id) },
                    onDelete = if (d.user) ({ toDelete = d.title to { Repository.deleteDictation(d.id) } }) else null
                )
            }
        }
    }

    toDelete?.let { (name, action) ->
        ConfirmDialog(
            title = "Удалить?",
            text = "«$name» будет удалено из ваших материалов.",
            onConfirm = action,
            onDismiss = { toDelete = null }
        )
    }
}

private fun resultText(key: String, base: String): String {
    val p = Repository.progress[key] ?: return base
    return "$base · лучший результат $p%"
}

private fun LazyListScope.header(title: String, icon: ImageVector, onAdd: () -> Unit) {
    item {
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            TextButton(onClick = onAdd) {
                Icon(Icons.Default.Add, null)
                Text("Добавить")
            }
        }
    }
}

private fun LazyListScope.emptyLine() {
    item {
        Text(
            "Пока нет. Нажмите «Добавить», чтобы внести своё задание.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 32.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun Entry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    done: Boolean = false,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = {
                if (done) Icon(Icons.Default.CheckCircle, "Выполнено", tint = Marks.good)
                else Icon(icon, null)
            },
            trailingContent = if (onDelete != null) {
                { IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Удалить") } }
            } else null
        )
    }
}
