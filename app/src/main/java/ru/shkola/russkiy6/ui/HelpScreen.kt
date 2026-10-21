package ru.shkola.russkiy6.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private val sample = """
{
  "version": 1,
  "sections": [
    {
      "id": "leksika",
      "title": "Лексика",
      "theory": [ { "q": "Вопрос", "a": "Ответ" } ],
      "exercises": [
        { "id": "upr-12", "title": "Упр. 12",
          "instruction": "Вставьте буквы.",
          "items": [ "Р{а|о}сток пробился.", "Ш{е}пот листьев." ] }
      ],
      "tests": [
        { "id": "t1", "title": "Тест",
          "questions": [ { "q": "Вопрос?", "options": ["А","Б","В"],
                           "correct": 0, "explain": "Почему" } ] }
      ],
      "dictations": [
        { "id": "d1", "title": "Диктант", "text": "Текст диктанта." }
      ]
    }
  ]
}
""".trimIndent()

@Composable
fun HelpScreen(onBack: () -> Unit) {
    ScreenScaffold(title = "Справка", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            H("Что есть в приложении")
            P(
                "Материалы разбиты по темам курса русского языка 6 класса. В каждой теме — обучающие вопросы " +
                    "(карточки), упражнения с пропусками, тесты и диктанты. Встроенные задания составлены " +
                    "самостоятельно по программе 6 класса; задания из своего учебника или сборника " +
                    "(например, сборника упражнений Т. В. Шкляровой) вы можете добавить сами."
            )
            H("Диктанты")
            P(
                "Диктант читается голосом так же, как на уроке: сначала весь текст, затем каждое предложение " +
                    "по частям с повтором и паузой на запись, в конце — проверочное чтение. " +
                    "Кнопкой ⚙ (настройки) меняются скорость речи, тон голоса, время на запись слова, " +
                    "длина частей, число повторов и сам голос."
            )
            P(
                "Для озвучки нужен синтезатор речи с русским языком. Обычно это «Распознавание и синтез речи от Google». " +
                    "Проверьте: Настройки → Специальные возможности → Синтез речи → Русский. Скачанный голос " +
                    "работает без интернета."
            )
            H("Как добавить задание из учебника")
            P(
                "Кнопка «Добавить» на главном экране или в разделе. Для диктанта достаточно вставить текст. " +
                    "В упражнении пропущенные буквы отмечаются фигурными скобками: к{о}рова — ученик впишет букву; " +
                    "р{а|о}стение — выбор из вариантов, первый вариант правильный."
            )
            H("Импорт и экспорт")
            P(
                "Через меню ⋮ можно загрузить файл .json с заданиями (например, подготовленный учителем) " +
                    "и сохранить свои материалы в файл, чтобы перенести их на другой телефон. Формат файла:"
            )
            Text(sample, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            P(
                "Если id раздела совпадает со встроенным (например, \"leksika\", \"sushch\", \"glagol\"), " +
                    "задания добавятся в этот раздел, иначе появится новый раздел."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun H(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun P(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 6.dp))
}
