package ru.shkola.russkiy6.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.data.Repository
import ru.shkola.russkiy6.data.TheoryCard

@Composable
fun TheoryScreen(sid: String, onBack: () -> Unit) {
    val s = Repository.section(sid)
    val all = s?.theory.orEmpty()
    var listMode by rememberSaveable { mutableStateOf(false) }
    var order by remember(all) { mutableStateOf(all.indices.toList()) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    var shown by rememberSaveable { mutableStateOf(false) }
    var known by rememberSaveable { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<TheoryCard?>(null) }

    ScreenScaffold(
        title = "Вопросы: ${s?.title ?: ""}",
        onBack = onBack,
        actions = {
            if (!listMode) IconButton(onClick = {
                order = all.indices.shuffled(); index = 0; shown = false; known = 0
            }) { Icon(Icons.Default.Shuffle, "Перемешать") }
            IconButton(onClick = { listMode = !listMode }) {
                if (listMode) Icon(Icons.Default.Style, "Карточки")
                else Icon(Icons.AutoMirrored.Filled.ViewList, "Списком")
            }
        }
    ) { padding ->
        if (all.isEmpty()) {
            EmptyNote("В этом разделе пока нет вопросов", padding); return@ScreenScaffold
        }
        if (listMode) {
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp
                )
            ) {
                itemsIndexed(all) { i, c ->
                    Row(Modifier.padding(vertical = 10.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("${i + 1}. ${c.q}", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(c.a, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (c.user) IconButton(onClick = { toDelete = c }) {
                            Icon(Icons.Default.DeleteOutline, "Удалить")
                        }
                    }
                    HorizontalDivider()
                }
            }
            return@ScreenScaffold
        }

        val finished = index >= order.size
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LinearProgressIndicator(
                progress = { (index.coerceAtMost(order.size)).toFloat() / order.size },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))
            if (finished) {
                Text("Готово!", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Вы знали ответ на $known из ${order.size} ${plural(order.size, "вопроса", "вопросов", "вопросов")}.",
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = { index = 0; shown = false; known = 0; order = all.indices.shuffled() }) {
                    Text("Пройти ещё раз")
                }
                return@Column
            }
            val card = all[order[index].coerceIn(all.indices)]
            Text("Вопрос ${index + 1} из ${order.size}", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(12.dp))
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Text(
                    card.q,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(20.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(visible = shown) {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Text(card.a, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(20.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            if (!shown) {
                Text(
                    "Сначала ответьте вслух или про себя, затем проверьте.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = { shown = true }, modifier = Modifier.fillMaxWidth()) { Text("Показать ответ") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            // вопрос вернётся в конец очереди
                            order = order + order[index]; index++; shown = false
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Повторить позже") }
                    Button(
                        onClick = { known++; index++; shown = false },
                        modifier = Modifier.weight(1f)
                    ) { Text("Знаю") }
                }
            }
        }
    }

    toDelete?.let { c ->
        ConfirmDialog(
            title = "Удалить вопрос?",
            text = c.q,
            onConfirm = { Repository.deleteTheory(c) },
            onDismiss = { toDelete = null }
        )
    }
}
