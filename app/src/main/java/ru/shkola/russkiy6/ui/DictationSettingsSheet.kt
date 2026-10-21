package ru.shkola.russkiy6.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.shkola.russkiy6.tts.DictationPlayer
import ru.shkola.russkiy6.tts.DictationSettings
import kotlin.math.roundToInt

private data class Preset(val name: String, val rate: Float, val secPerWord: Float, val repeats: Int, val maxWords: Int)

private val presets = listOf(
    Preset("Медленно", 0.6f, 2.5f, 2, 4),
    Preset("Обычно", 0.8f, 1.6f, 2, 5),
    Preset("Быстро", 1.0f, 1.0f, 1, 7),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictationSettingsSheet(player: DictationPlayer, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val s = player.settings
    fun set(new: DictationSettings) = player.updateSettings(new)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Настройки диктовки", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            Text("Готовые режимы", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    val selected = s.rate == p.rate && s.secPerWord == p.secPerWord &&
                        s.repeats == p.repeats && s.maxWords == p.maxWords
                    FilterChip(
                        selected = selected,
                        onClick = {
                            set(player.settings.copy(rate = p.rate, secPerWord = p.secPerWord, repeats = p.repeats, maxWords = p.maxWords))
                        },
                        label = { Text(p.name) }
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Голос", style = MaterialTheme.typography.titleSmall)
            SettingSlider(
                label = "Скорость речи",
                value = s.rate, range = 0.3f..1.6f, steps = 12,
                format = { "×" + "%.1f".format(it) },
                onCommit = { set(player.settings.copy(rate = it)) }
            )
            SettingSlider(
                label = "Высота голоса (тон)",
                value = s.pitch, range = 0.5f..1.5f, steps = 9,
                format = { "%.1f".format(it) },
                onCommit = { set(player.settings.copy(pitch = it)) }
            )
            OutlinedButton(onClick = {
                player.sayOnce("Наступила золотая осень. Листья на деревьях пожелтели.")
            }) { Text("Прослушать пример") }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Темп диктовки", style = MaterialTheme.typography.titleSmall)
            SettingSlider(
                label = "Время на запись одного слова",
                value = s.secPerWord, range = 0.5f..4f, steps = 34,
                format = { "%.1f с".format(it) },
                onCommit = { set(player.settings.copy(secPerWord = it)) }
            )
            SettingSlider(
                label = "Пауза между предложениями",
                value = s.sentencePause, range = 0f..6f, steps = 11,
                format = { "%.1f с".format(it) },
                onCommit = { set(player.settings.copy(sentencePause = it)) }
            )
            SettingSlider(
                label = "Длина диктуемой части",
                value = s.maxWords.toFloat(), range = 2f..10f, steps = 7,
                format = { "${it.roundToInt()} ${plural(it.roundToInt(), "слово", "слова", "слов")}" },
                onCommit = { set(player.settings.copy(maxWords = it.roundToInt())) }
            )
            Text("Сколько раз повторять каждую часть", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                (1..3).forEach { n ->
                    SegmentedButton(
                        selected = s.repeats == n,
                        onClick = { set(player.settings.copy(repeats = n)) },
                        shape = SegmentedButtonDefaults.itemShape(index = n - 1, count = 3)
                    ) { Text("$n") }
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Порядок чтения", style = MaterialTheme.typography.titleSmall)
            SwitchRow("Прочитать весь текст перед диктовкой", s.readWholeFirst) {
                set(player.settings.copy(readWholeFirst = it))
            }
            SwitchRow("Читать предложение целиком перед диктовкой по частям", s.readSentenceBeforeParts) {
                set(player.settings.copy(readSentenceBeforeParts = it))
            }
            SwitchRow("Проверочное чтение в конце", s.readWholeLast) {
                set(player.settings.copy(readWholeLast = it))
            }

            if (player.russianVoices.isNotEmpty()) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("Выбор голоса", style = MaterialTheme.typography.titleSmall)
                VoiceRow("По умолчанию", s.voiceName.isEmpty()) { set(player.settings.copy(voiceName = "")) }
                player.russianVoices.forEachIndexed { i, v ->
                    val title = "Голос ${i + 1}" + if (v.isNetworkConnectionRequired) " (нужен интернет)" else ""
                    VoiceRow(title, s.voiceName == v.name, v.name) { set(player.settings.copy(voiceName = v.name)) }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun VoiceRow(title: String, selected: Boolean, subtitle: String = "", onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle.isNotEmpty()) Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
