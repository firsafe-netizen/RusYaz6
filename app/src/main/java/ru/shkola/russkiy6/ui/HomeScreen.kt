package ru.shkola.russkiy6.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.shkola.russkiy6.data.Repository
import ru.shkola.russkiy6.data.Section

/** Доля выполненных заданий раздела (тесты, упражнения, диктанты). */
fun sectionProgress(s: Section): Pair<Int, Int> {
    val keys = s.tests.map { "test:${it.id}" } + s.exercises.map { "ex:${it.id}" } + s.dictations.map { "dict:${it.id}" }
    val done = keys.count { Repository.progress.containsKey(it) }
    return done to keys.size
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(openSection: (String) -> Unit, openEditor: () -> Unit, openHelp: () -> Unit) {
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { Repository.importFrom(uri) }.fold(
                onSuccess = { "Добавлено материалов: $it" },
                onFailure = { "Ошибка импорта: ${it.message ?: "неверный формат файла"}" }
            )
            snackbar.showSnackbar(msg)
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val msg = runCatching { Repository.exportTo(uri) }.fold(
                onSuccess = { "Мои материалы сохранены в файл" },
                onFailure = { "Ошибка сохранения: ${it.message}" }
            )
            snackbar.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Русский язык · 6 класс", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Упражнения, тесты, диктанты",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Меню") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Импорт материалов (JSON)") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, null) },
                            onClick = { menu = false; importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                        )
                        DropdownMenuItem(
                            text = { Text("Экспорт моих материалов") },
                            leadingIcon = { Icon(Icons.Default.FileUpload, null) },
                            onClick = {
                                menu = false
                                if (Repository.userItemsCount() == 0)
                                    scope.launch { snackbar.showSnackbar("Вы ещё не добавили своих материалов") }
                                else exporter.launch("russkiy6_materialy.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Сбросить результаты") },
                            leadingIcon = { Icon(Icons.Default.RestartAlt, null) },
                            onClick = { menu = false; askReset = true }
                        )
                        DropdownMenuItem(
                            text = { Text("Справка") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.HelpOutline, null) },
                            onClick = { menu = false; openHelp() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = openEditor,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Добавить") }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        val sections = Repository.sections
        if (sections.isEmpty()) {
            EmptyNote("Материалы не найдены. Добавьте их через меню «Импорт» или кнопку «Добавить».", padding)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(sections, key = { _, s -> s.id }) { i, s ->
                    SectionCard(i + 1, s) { openSection(s.id) }
                }
            }
        }
    }

    if (askReset) ConfirmDialog(
        title = "Сбросить результаты?",
        text = "Будут удалены все сохранённые баллы за тесты, упражнения и диктанты.",
        onConfirm = { Repository.resetProgress() },
        onDismiss = { askReset = false }
    )
}

@Composable
private fun SectionCard(number: Int, s: Section, onClick: () -> Unit) {
    val (done, total) = sectionProgress(s)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (s.id == Repository.MY_SECTION_ID) "★" else number.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                if (s.description.isNotEmpty()) Text(
                    s.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                val parts = buildList {
                    if (s.theory.isNotEmpty()) add("вопросов ${s.theory.size}")
                    if (s.exercises.isNotEmpty()) add("упр. ${s.exercises.size}")
                    if (s.tests.isNotEmpty()) add("тестов ${s.tests.size}")
                    if (s.dictations.isNotEmpty()) add("диктантов ${s.dictations.size}")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelMedium)
                if (total > 0) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
