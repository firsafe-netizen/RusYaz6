package ru.shkola.russkiy6.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.data.Repository
import ru.shkola.russkiy6.data.TextCheck
import ru.shkola.russkiy6.tts.DictationPlayer
import ru.shkola.russkiy6.tts.StepKind
import ru.shkola.russkiy6.tts.TtsState
import kotlin.math.roundToInt

@Composable
fun DictationScreen(sid: String, id: String, onBack: () -> Unit) {
    val d = Repository.section(sid)?.dictations?.firstOrNull { it.id == id }
    val context = LocalContext.current
    val player = remember { DictationPlayer(context) }
    DisposableEffect(Unit) { onDispose { player.release() } }
    LaunchedEffect(d?.text) { if (d != null) player.load(d.text) }

    // экран не гаснет, пока идёт диктовка
    val view = LocalView.current
    DisposableEffect(player.playing) {
        view.keepScreenOn = player.playing
        onDispose { view.keepScreenOn = false }
    }

    var showSettings by remember { mutableStateOf(false) }
    var showPhrase by rememberSaveable { mutableStateOf(false) }
    var showText by rememberSaveable { mutableStateOf(false) }
    var written by rememberSaveable { mutableStateOf("") }
    var result by remember { mutableStateOf<TextCheck.Result?>(null) }

    ScreenScaffold(
        title = d?.title ?: "Диктант",
        onBack = { player.pause(); onBack() },
        actions = {
            IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Tune, "Настройки озвучки") }
        }
    ) { padding ->
        if (d == null) { EmptyNote("Диктант не найден", padding); return@ScreenScaffold }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            TtsBanner(player.state) { intent ->
                try { context.startActivity(intent) } catch (e: ActivityNotFoundException) { e.printStackTrace() }
            }
            if (d.note.isNotEmpty()) {
                Text(d.note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
            }

            /* ---------- Плеер ---------- */
            val steps = player.steps
            val cur = player.current
            val step = steps.getOrNull(cur)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        when {
                            steps.isEmpty() -> "Нет текста"
                            step == null -> "Диктант окончен. Проверьте написанное."
                            player.playing -> step.label
                            cur == 0 -> "Нажмите ▶, чтобы начать диктовку"
                            else -> "Пауза · ${step.label}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Шаг ${(cur + 1).coerceAtMost(steps.size)} из ${steps.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    if (showPhrase && step != null && step.kind != StepKind.WHOLE_FIRST && step.kind != StepKind.WHOLE_LAST) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            step.parts.joinToString(" "),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (steps.isNotEmpty()) {
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = drag ?: cur.coerceAtMost(steps.size - 1).toFloat(),
                            onValueChange = { drag = it },
                            onValueChangeFinished = {
                                drag?.let { player.jump(it.roundToInt()) }
                                drag = null
                            },
                            valueRange = 0f..(steps.size - 1).coerceAtLeast(1).toFloat()
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalIconButton(onClick = { player.previous() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Предыдущая часть")
                        }
                        FilledTonalIconButton(onClick = { player.repeatCurrent() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.Replay, "Повторить часть")
                        }
                        FilledIconButton(
                            onClick = { player.toggle() },
                            modifier = Modifier.size(72.dp),
                            colors = IconButtonDefaults.filledIconButtonColors()
                        ) {
                            Icon(
                                if (player.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                if (player.playing) "Пауза" else "Слушать",
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        FilledTonalIconButton(onClick = { player.next() }, modifier = Modifier.size(52.dp)) {
                            Icon(Icons.Default.SkipNext, "Следующая часть")
                        }
                        FilledTonalIconButton(onClick = { showPhrase = !showPhrase }, modifier = Modifier.size(52.dp)) {
                            Icon(
                                if (showPhrase) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                if (showPhrase) "Скрыть фразу" else "Показать фразу"
                            )
                        }
                    }
                }
            }

            /* ---------- Быстрые настройки ---------- */
            Spacer(Modifier.height(12.dp))
            val st = player.settings
            SettingSlider(
                label = "Скорость речи",
                value = st.rate, range = 0.3f..1.6f,
                format = { "×" + "%.2f".format(it).trimEnd('0').trimEnd(',', '.') + speedWord(it) },
                onCommit = { player.updateSettings(player.settings.copy(rate = it)) }
            )
            SettingSlider(
                label = "Темп диктовки (время на запись слова)",
                value = st.secPerWord, range = 0.5f..4f,
                format = { "%.1f с".format(it) },
                onCommit = { player.updateSettings(player.settings.copy(secPerWord = it)) }
            )
            TextButton(onClick = { showSettings = true }) { Text("Все настройки: тон, голос, повторы, длина фраз…") }

            /* ---------- Проверка ---------- */
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Проверь себя", style = MaterialTheme.typography.titleMedium)
            Text(
                "Пишите диктант в тетради. Чтобы проверить себя, перепишите сюда написанный текст " +
                    "(или наберите его во время диктовки) и нажмите «Проверить».",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = written,
                onValueChange = { written = it; result = null },
                modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                label = { Text("Ваш текст") }
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val r = TextCheck.compare(d.text, written)
                        result = r
                        val percent = if (r.total == 0) 0 else (100 - r.errors * 100 / r.total).coerceAtLeast(0)
                        Repository.saveResult("dict:${d.id}", percent)
                    },
                    enabled = written.isNotBlank()
                ) { Text("Проверить") }
                OutlinedButton(onClick = { showText = !showText }) {
                    Text(if (showText) "Скрыть текст" else "Показать текст")
                }
            }
            result?.let { r ->
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            if (r.errors == 0) "Ошибок нет — отлично!"
                            else "${r.errors} ${plural(r.errors, "ошибка", "ошибки", "ошибок")} · ${dictationGrade(r.errors)}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "Оценка примерная: программа считает и орфографические, и пунктуационные ошибки, а также пропущенные слова.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(diffText(r.ops), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(legend(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (showText) {
                Spacer(Modifier.height(12.dp))
                Card(Modifier.fillMaxWidth()) {
                    Text(d.text, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showSettings) DictationSettingsSheet(player) { showSettings = false }
}

private fun speedWord(v: Float) = when {
    v < 0.6f -> " (очень медленно)"
    v < 0.9f -> " (медленно)"
    v < 1.15f -> " (обычно)"
    else -> " (быстро)"
}

/** Примерная оценка по числу ошибок. */
fun dictationGrade(errors: Int) = when {
    errors <= 1 -> "оценка «5»"
    errors <= 4 -> "оценка «4»"
    errors <= 8 -> "оценка «3»"
    else -> "оценка «2»"
}

@Composable
private fun TtsBanner(state: TtsState, open: (Intent) -> Unit) {
    if (state == TtsState.READY || state == TtsState.INITIALIZING) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (state == TtsState.NO_RUSSIAN) {
                Text("Русский голос не установлен", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Установите русский язык в синтезаторе речи (например, «Распознавание и синтез речи от Google»), " +
                        "чтобы диктант озвучивался без интернета.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { open(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }) {
                        Text("Установить голос")
                    }
                    TextButton(onClick = { open(Intent("com.android.settings.TTS_SETTINGS")) }) {
                        Text("Настройки речи")
                    }
                }
            } else {
                Text("Синтезатор речи не найден", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Установите приложение «Распознавание и синтез речи от Google» из Google Play.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = {
                    open(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.google.android.tts")))
                }) { Text("Открыть Google Play") }
            }
        }
    }
}

/** Слайдер, который применяет значение после отпускания. */
@Composable
fun SettingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    format: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(format(local), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps
        )
    }
}

private val noSpaceBefore = setOf(",", ".", "!", "?", ";", ":", "…", "»", ")")
private val noSpaceAfter = setOf("«", "(")

private fun diffText(ops: List<TextCheck.Op>): AnnotatedString = buildAnnotatedString {
    var prev = ""
    fun sep(t: String) {
        if (length > 0 && t !in noSpaceBefore && prev !in noSpaceAfter) append(' ')
        prev = t
    }
    val bad = SpanStyle(color = Marks.bad, textDecoration = TextDecoration.LineThrough)
    val good = SpanStyle(color = Marks.good, fontWeight = FontWeight.Bold)
    for (op in ops) when (op) {
        is TextCheck.Op.Same -> { sep(op.token); append(op.token) }
        is TextCheck.Op.Wrong -> {
            sep(op.written); withStyle(bad) { append(op.written) }
            append(' '); withStyle(good) { append(op.expected) }
            prev = op.expected
        }
        is TextCheck.Op.Missing -> {
            sep(op.expected)
            withStyle(good.copy(textDecoration = TextDecoration.Underline)) { append("[${op.expected}]") }
        }
        is TextCheck.Op.Extra -> { sep(op.written); withStyle(bad) { append(op.written) } }
    }
}

private fun legend(): AnnotatedString = buildAnnotatedString {
    withStyle(SpanStyle(color = Marks.bad, textDecoration = TextDecoration.LineThrough)) { append("зачёркнуто") }
    append(" — ошибка или лишнее; ")
    withStyle(SpanStyle(color = Marks.good, fontWeight = FontWeight.Bold)) { append("зелёным") }
    append(" — как правильно; ")
    withStyle(SpanStyle(color = Marks.good, textDecoration = TextDecoration.Underline)) { append("[в скобках]") }
    append(" — пропущено.")
}
