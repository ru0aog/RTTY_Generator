package com.example.helloworld

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

class ToneGenerator {
    private var audioTrack: AudioTrack? = null
    var onLogMessage: ((String) -> Unit)? = null  // Callback для логов

    // Частоты RTTY (стандарт)
    private val MARK_FREQ = 1170  // «1» (Mark)
    private val SPACE_FREQ = 1000  // «0» (Space)
    private val BAUD_RATE = 45.45  // Скорость передачи, бод
    private val SAMPLE_RATE = 44100  // Частота дискретизации, Гц
    private val AMPLITUDE = 0.2f  // Громкость (0.0–1.0)

    // ITA2/МТК‑2 таблица (упрощённая)
    private val ITA2 = mapOf<Any, List<Int>>(
        'A' to listOf(1, 1, 0, 0, 0),
        'B' to listOf(1, 0, 0, 1, 1),
        'C' to listOf(0, 1, 1, 1, 0),
        'D' to listOf(1, 0, 0, 1, 0),
        'E' to listOf(1, 0, 0, 0, 0),
        'F' to listOf(1, 0, 1, 1, 0),
        'G' to listOf(0, 1, 0, 1, 1),
        'H' to listOf(0, 0, 1, 0, 1),
        'I' to listOf(0, 1, 1, 0, 0),
        'J' to listOf(1, 1, 0, 1, 0),
        'K' to listOf(1, 1, 1, 1, 0),
        'L' to listOf(0, 1, 0, 0, 1),
        'M' to listOf(0, 0, 1, 1, 1),
        'N' to listOf(0, 0, 1, 1, 0),
        'O' to listOf(0, 0, 0, 1, 1),
        'P' to listOf(0, 1, 1, 0, 1),
        'Q' to listOf(1, 1, 1, 0, 1),
        'R' to listOf(0, 1, 0, 1, 0),
        'S' to listOf(1, 0, 1, 0, 0),
        'T' to listOf(0, 0, 0, 0, 1),
        'U' to listOf(1, 1, 1, 0, 0),
        'V' to listOf(0, 1, 1, 1, 1),
        'W' to listOf(1, 1, 0, 0, 1),
        'X' to listOf(1, 0, 1, 1, 1),
        'Y' to listOf(1, 0, 1, 0, 1),
        'Z' to listOf(1, 0, 0, 0, 1),

        '0' to listOf(0, 1, 1, 0, 1),
        '1' to listOf(1, 1, 1, 0, 1),
        '2' to listOf(1, 1, 0, 0, 1),
        '3' to listOf(1, 0, 0, 0, 0),
        '4' to listOf(0, 1, 0, 1, 0),
        '5' to listOf(0, 0, 0, 0, 1),
        '6' to listOf(1, 0, 1, 0, 1),
        '7' to listOf(1, 1, 1, 0, 0),
        '8' to listOf(0, 1, 1, 0, 0),
        '9' to listOf(0, 0, 0, 1, 1),

        '-' to listOf(1, 1, 0, 0, 0),
        '+' to listOf(1, 0, 0, 0, 1),
        '?' to listOf(1, 0, 0, 1, 1),
        ':' to listOf(0, 1, 1, 1, 0),
        '(' to listOf(1, 1, 1, 1, 0),
        ')' to listOf(0, 1, 0, 0, 1),
        '.' to listOf(0, 0, 1, 1, 1),
        ',' to listOf(0, 0, 1, 1, 0),
        '/' to listOf(0, 1, 1, 1, 1),
        ' ' to listOf(0, 0, 1, 0, 0),

        // Русские буквы (МТК‑2)
        'А' to listOf(1, 1, 0, 0, 0),
        'Б' to listOf(1, 0, 0, 1, 1),
        'В' to listOf(1, 1, 0, 0, 1),
        'Г' to listOf(0, 1, 0, 1, 1),
        'Д' to listOf(1, 0, 0, 1, 0),
        'Е' to listOf(1, 0, 0, 0, 0),
        'Ж' to listOf(0, 1, 1, 1, 1),
        'З' to listOf(1, 0, 0, 0, 1),
        'И' to listOf(0, 1, 1, 0, 0),
        'Й' to listOf(1, 1, 0, 1, 0),
        'К' to listOf(1, 1, 1, 1, 0),
        'Л' to listOf(0, 1, 0, 0, 1),
        'М' to listOf(0, 0, 1, 1, 1),
        'Н' to listOf(0, 0, 1, 1, 0),
        'О' to listOf(0, 0, 0, 1, 1),
        'П' to listOf(0, 1, 1, 0, 1),
        'Р' to listOf(0, 1, 0, 1, 0),
        'С' to listOf(1, 0, 1, 0, 0),
        'Т' to listOf(0, 0, 0, 0, 1),
        'У' to listOf(1, 1, 1, 0, 0),
        'Ф' to listOf(1, 0, 1, 1, 0),
        'Х' to listOf(0, 0, 1, 0, 1),
        'Ц' to listOf(0, 1, 1, 1, 0),
        'Ъ' to listOf(1, 0, 1, 1, 1),
        'Ы' to listOf(1, 0, 1, 0, 1),
        'Ь' to listOf(1, 0, 1, 1, 1),
        'Я' to listOf(1, 1, 1, 0, 1),
        'Ё' to listOf(1, 0, 0, 0, 0),

        // Режимы
        "RUS" to listOf(0, 0, 0, 0, 0),
        "FIGS" to listOf(1, 1, 0, 1, 1),
        "LAT" to listOf(1, 1, 1, 1, 1),

        '\r' to listOf(0, 0, 0, 1, 0),
        '\n' to listOf(0, 1, 0, 0, 0)
    )

    // Текущий режим кодирования
    private var currentMode = "LAT"
    private val bitArray = mutableListOf<Double>()  // Битовый массив (0, 1, 0.5)


    /**
     * Преобразует текст в битовый массив по ITA2/МТК‑2
     */
    private fun encodeToITA2(text: String): List<Double> {
        bitArray.clear()
        currentMode = "LAT"

        log("Передача: '$text'")
        log("Начинаем кодирование Baudot")

        // Добавляем служебные CR+LF в начало
        addCharToBitArray("LAT")
        addCharToBitArray('\r')
        addCharToBitArray('\n')

        // Кодируем основной текст
        for (char in text.toUpperCase()) {
            if (char !in ITA2) {
                log("Символ '$char' не поддерживается, пропускаем")
                continue
            }

            val targetMode = getCharMode(char)
            if (targetMode != currentMode) {
                addCharToBitArray(targetMode)
                currentMode = targetMode
            }
            addCharToBitArray(char)
        }

        // Добавляем служебные CR+LF в конец
        addCharToBitArray('\r')
        addCharToBitArray('\n')
        log("Кодирование завершено.")
        log("Всего битов: ${bitArray.size}")

        return bitArray
    }

    /**
     * Определяет режим для символа
     */
    private fun getCharMode(char: Char): String {
        return when (char) {
            in 'A'..'Z' -> "LAT"
            in 'А'..'Я', 'Ё' -> "RUS"
            else -> "FIGS"
        }
    }

    /**
     * Добавляет символ в битовый массив с старт/стоп‑битами
     */
    private fun addCharToBitArray(char: Any) {
        val code = when (char) {
            is Char -> ITA2[char] ?: return
            is String -> ITA2[char] ?: return
            else -> return
        }
        // отобразить вывод
        val ita2code = ITA2[char]?.joinToString(separator = "") ?: "НЕ ПОДДЕРЖИВАЕТСЯ"
        val displayedChar = when (char) {
            '\n' -> "LF"
            '\r' -> "CR"
            else -> char.toString()
        }
        log("Символ: '$displayedChar' → 0+"+"$ita2code"+"+1.5")

        // Старт‑бит (0)
        bitArray.add(0.0)
        // 5 бит кода
        code.forEach { bit ->
            bitArray.add(bit.toDouble())
        }
        // Стоп‑бит (1)
        bitArray.add(1.0)
        // Полустоп‑бит (0.5)
        bitArray.add(0.5)
    }

    /**
     * Генерирует синусоидальный тон заданной частоты и длительности
     */
    private fun generateTone(
        freq: Int,
        durationSec: Double,
        phase: Double,
        volume: Float
    ): FloatArray {
        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val tone = FloatArray(numSamples)

        // Правильный расчёт угловой частоты
        val omega = 2.0 * PI * freq

        for (i in 0 until numSamples) {
            // Физически корректный расчёт времени
            val t = i.toDouble() / SAMPLE_RATE
            // Полная фаза с учётом начальной фазы
            val totalPhase = omega * t + phase
            tone[i] = (sin(totalPhase) * (volume * Short.MAX_VALUE)).toFloat()
        }

        return tone
    }



    /**
     * Воспроизводит RTTY‑сигнал для заданного текста
     */
    fun playRtty(text: String, volume: Float = 0.3f) {  // Добавляем параметр volume с дефолтным значением 1.0
        if (text.isEmpty()) return

        log("=== Начало передачи RTTY ===")
        log("Частота MARK: $MARK_FREQ Гц")
        log("Частота SPACE: $SPACE_FREQ Гц")
        log("Скорость: $BAUD_RATE бод")
        log("Громкость: $volume")

        // 1. Кодируем текст в биты
        val bits = encodeToITA2(text)

        // 2. Рассчитываем длительность бита
        val bitDuration = 1.0 / BAUD_RATE  // сек

// 3. Генерируем аудио
        val audioBuffer = mutableListOf<Float>()
        var totalPhase = 0.0  // начальная фаза

// Маркерный тон ПЕРЕД передачей - 300 мс
        audioBuffer.addAll(generateTone(MARK_FREQ, 0.3, totalPhase, volume).toList())
        totalPhase = updatePhase(totalPhase, MARK_FREQ, 0.3)  // обновляем фазу

        var displayedbitarray = ""
        for (bit in bits) {
            val freq = when (bit) {
                1.0, 0.5 -> MARK_FREQ  // Mark (1 или полустоп)
                else -> SPACE_FREQ       // Space (0)
            }

            val displayedbit = when (bit) {
                1.0 -> "1"
                0.0 -> "0"
                else -> bit.toString()
            }
            displayedbitarray += displayedbit + ","

            // Длительность: для полустопа — половина бита
            val duration = if (bit == 0.5) bitDuration * 0.5 else bitDuration


            // Генерируем тон с учётом текущей фазы
            val tone = generateTone(freq, duration, totalPhase, volume)
            audioBuffer.addAll(tone.toList())

            // Корректно обновляем фазу для следующего тона
            totalPhase = updatePhase(totalPhase, freq, duration)
        }

        // Маркерный тон ПОСЛЕ передачи - 300 мс
        audioBuffer.addAll(generateTone(MARK_FREQ, 0.3, totalPhase, volume).toList())

        //вывести список передаваемых битов
        log(displayedbitarray.dropLast(1))


        // 4. Создаём AudioTrack
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(audioBuffer.size * 2)
            .build()

        // 5. Записываем и играем
        val shortBuffer = ShortArray(audioBuffer.size) {
            // Вариант 1: округление до ближайшего целого (рекомендуется для аудио)
            (audioBuffer[it] * 2).toInt().toShort()
        }
        audioTrack?.write(shortBuffer, 0, shortBuffer.size, AudioTrack.WRITE_BLOCKING)
        audioTrack?.play()

        log("=== Передача завершена ===\n")

    }


    private fun updatePhase(currentPhase: Double, freq: Int, durationSec: Double): Double {
        // Количество циклов за время durationSec
        val cycles = freq * durationSec
        // Сдвиг фазы в радианах
        val phaseShift = 2.0 * PI * cycles
        // Новая фаза с учётом модуля 2π
        return (currentPhase + phaseShift) % (2.0 * PI)
    }


    private fun log(message: String) {
        onLogMessage?.invoke("$message")
    }

    fun stopTone() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun isPlaying(): Boolean {
        return audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING
    }
}
