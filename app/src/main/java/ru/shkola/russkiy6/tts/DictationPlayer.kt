package ru.shkola.russkiy6.tts

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class TtsState { INITIALIZING, READY, NO_RUSSIAN, NO_ENGINE }

/**
 * Озвучивание диктанта встроенным синтезатором речи Android (работает без интернета,
 * если установлен русский голос, например «Синтезатор речи Google»).
 */
class DictationPlayer(context: Context) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val tts = TextToSpeech(appContext, this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var job: Job? = null

    var state by mutableStateOf(TtsState.INITIALIZING)
        private set
    var russianVoices by mutableStateOf<List<Voice>>(emptyList())
        private set
    var settings by mutableStateOf(DictationSettings.load(appContext))
        private set
    var steps by mutableStateOf<List<Step>>(emptyList())
        private set
    /** Индекс текущего шага; steps.size — диктант окончен. */
    var current by mutableIntStateOf(0)
        private set
    var playing by mutableStateOf(false)
        private set

    private var text: String = ""

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            state = TtsState.NO_ENGINE
            return
        }
        val ru = Locale.forLanguageTag("ru-RU")
        val res = tts.setLanguage(ru)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) = finish(utteranceId)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = finish(utteranceId)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
        russianVoices = runCatching {
            tts.voices.orEmpty()
                .filter { it.locale.language == "ru" }
                .sortedWith(compareBy<Voice> { it.isNetworkConnectionRequired }.thenBy { it.name })
        }.getOrElse { emptyList() }
        applyVoice()
        state = if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED)
            TtsState.NO_RUSSIAN else TtsState.READY
    }

    private fun finish(id: String?) {
        if (id != null) pending.remove(id)?.complete(Unit)
    }

    private fun applyVoice() {
        val name = settings.voiceName
        if (name.isNotEmpty()) {
            russianVoices.firstOrNull { it.name == name }?.let { tts.setVoice(it) }
        }
    }

    fun load(dictationText: String) {
        text = dictationText
        steps = DictationPlanner.build(text, settings)
        current = 0
    }

    fun updateSettings(new: DictationSettings) {
        val replan = new.maxWords != settings.maxWords || new.repeats != settings.repeats ||
            new.readWholeFirst != settings.readWholeFirst || new.readWholeLast != settings.readWholeLast ||
            new.readSentenceBeforeParts != settings.readSentenceBeforeParts ||
            new.secPerWord != settings.secPerWord || new.sentencePause != settings.sentencePause
        val voiceChanged = new.voiceName != settings.voiceName
        settings = new
        DictationSettings.save(appContext, new)
        if (voiceChanged) {
            if (new.voiceName.isEmpty()) tts.setLanguage(Locale.forLanguageTag("ru-RU")) else applyVoice()
        }
        if (replan && text.isNotEmpty()) {
            // стараемся остаться в том же предложении
            val sentence = steps.getOrNull(current)?.sentenceIndex ?: -1
            val wasPlaying = playing
            pause()
            steps = DictationPlanner.build(text, settings)
            current = if (sentence < 0) current.coerceIn(0, steps.size)
            else steps.indexOfFirst { it.sentenceIndex == sentence }.coerceAtLeast(0)
            if (wasPlaying) play()
        }
    }

    /** Произнести одну фразу и дождаться окончания. */
    private suspend fun speak(phrase: String) {
        tts.setSpeechRate(settings.rate)
        tts.setPitch(settings.pitch)
        val id = UUID.randomUUID().toString()
        val done = CompletableDeferred<Unit>()
        pending[id] = done
        val params = Bundle()
        val r = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, params, id)
        if (r != TextToSpeech.SUCCESS) {
            pending.remove(id)
            delay(500)
            return
        }
        try {
            done.await()
        } finally {
            pending.remove(id)
        }
    }

    fun play(from: Int = current) {
        if (state != TtsState.READY && state != TtsState.NO_RUSSIAN) return
        job?.cancel()
        tts.stop()
        val start = if (from >= steps.size) 0 else from.coerceAtLeast(0)
        playing = true
        job = scope.launch {
            val me = currentCoroutineContext()[Job]
            try {
                for (i in start until steps.size) {
                    current = i
                    val step = steps[i]
                    step.parts.forEachIndexed { k, p ->
                        speak(p)
                        if (k < step.parts.lastIndex) delay(450)
                    }
                    delay(step.pauseAfterMs)
                }
                current = steps.size
            } finally {
                if (job === me) playing = false
            }
        }
    }

    fun pause() {
        job?.cancel()
        job = null
        tts.stop()
        playing = false
    }

    fun toggle() = if (playing) pause() else play()

    /** Повторить текущую часть. */
    fun repeatCurrent() = play(current.coerceAtMost(steps.lastIndex))

    /** Перейти к следующей части (пропуская повторы той же части). */
    fun next() {
        if (steps.isEmpty()) return
        val cur = steps.getOrNull(current)
        var i = current + 1
        while (i < steps.size && cur != null && steps[i].kind == cur.kind &&
            steps[i].parts == cur.parts && steps[i].sentenceIndex == cur.sentenceIndex
        ) i++
        jump(i.coerceAtMost(steps.size - 1))
    }

    fun previous() {
        if (steps.isEmpty()) return
        var i = (current - 1).coerceAtLeast(0)
        // к первому повтору предыдущей части
        while (i > 0 && steps[i].repeatNo > 0) i--
        jump(i)
    }

    fun jump(index: Int) {
        val wasPlaying = playing
        pause()
        current = index.coerceIn(0, steps.size)
        if (wasPlaying) play(current)
    }

    /** Прослушать фразу (например, при проверке). */
    fun sayOnce(phrase: String) {
        pause()
        playing = true
        job = scope.launch {
            val me = currentCoroutineContext()[Job]
            try { speak(phrase) } finally { if (job === me) playing = false }
        }
    }

    fun release() {
        pause()
        scope.cancel()
        tts.shutdown()
    }
}
