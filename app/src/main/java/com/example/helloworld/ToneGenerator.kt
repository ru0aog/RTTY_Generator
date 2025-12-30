package com.example.helloworld

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin
import android.os.Handler
import android.os.Looper
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import kotlin.concurrent.thread


class ToneGenerator {
    private var audioTrack: AudioTrack? = null
    var onLogMessage: ((String) -> Unit)? = null  // Callback для логов

    // Частоты RTTY (стандарт)
    // Изменяемые параметры (по умолчанию)
    var MARK_FREQ: Int = 1170      // «1» (Mark), Гц
    var SPACE_FREQ: Int = 1000     // «0» (Space), Гц
    var BAUD_RATE: Double = 45.45  // Скорость передачи, бод
    private val SAMPLE_RATE = 44100  // Частота дискретизации, Гц (фиксированная)
    var IS_ExpandedLog: Boolean = false    // не выводить подробный лог
    var IS_SavetofileLog: Boolean = false    // не сохранять в файл
    var volume: Float = 0.2f    // Громкость (0.0–1.0)

    fun updateSettings(
        MARK_FREQ: Int,
        SPACE_FREQ: Int,
        BAUD_RATE: Double,
        IS_ExpandedLog: Boolean,
        IS_SavetofileLog: Boolean,
        volume: Float? = null
    ) {
        this.MARK_FREQ = MARK_FREQ
        this.SPACE_FREQ = SPACE_FREQ
        this.BAUD_RATE = BAUD_RATE
        this.IS_ExpandedLog = IS_ExpandedLog
        this.IS_SavetofileLog = IS_SavetofileLog
        if (volume != null) {
            this.volume = volume
        }
        //onLogMessage?.invoke("Настройки обновлены: MARK=$MARK_FREQ Гц, SPACE=$SPACE_FREQ Гц, БОД=$BAUD_RATE, лог=$IS_ExpandedLog, Громкость=$volume")
    }


    // ITA2/МТК‑2 таблица (упрощённая)
    private val ITA2 = mapOf<Any, List<Int>>(
        // Латинский регистр LAT
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

        // Цифровой регистр FIGS
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

        'Ш' to listOf(0, 1, 0, 1, 1),
        'Щ' to listOf(0, 0, 1, 0, 1),
        'Э' to listOf(1, 0, 1, 1, 0),
        'Ю' to listOf(1, 1, 0, 1, 0),
        'Ч' to listOf(0, 1, 0, 1, 0),

        // Русский регистр RUS (МТК‑2)
        'А' to listOf(1, 1, 0, 0, 0),
        'Б' to listOf(1, 0, 0, 1, 1),
        'В' to listOf(1, 1, 0, 0, 1),
        'Г' to listOf(0, 1, 0, 1, 1),
        'Д' to listOf(1, 0, 0, 1, 0),
        'Е' to listOf(1, 0, 0, 0, 0),
        'Ё' to listOf(1, 0, 0, 0, 0),
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


        // Режимы
        "RUS" to listOf(0, 0, 0, 0, 0),
        "FIGS" to listOf(1, 1, 0, 1, 1),
        "LAT" to listOf(1, 1, 1, 1, 1),

        '\r' to listOf(0, 0, 0, 1, 0),
        '\n' to listOf(0, 1, 0, 0, 0),
        "SPACE" to listOf(0, 0, 0, 0, 0)
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

        // Формируем выделенное сообщение с HTML-разметкой
        val highlightedText = "Передача: '<font color='#39FF14'>${text.toUpperCase()}</font>'"
        log(highlightedText)  // Передаём HTML‑строку
        log("Начинаем кодирование Baudot")

        // Добавляем служебные CR+LF в начало
        addCharToBitArray("LAT")
        addCharToBitArray('\r')
        addCharToBitArray('\n')

        // Кодируем основной текст
        for (char in text.toUpperCase()) {
            if (char !in ITA2) {
                log("<font color='#FF0000'> --> </font> Символ '<font color='#39FF14'>$char</font>' <font color='#FF0000'>НЕ ПОДДЕРЖИВАЕТСЯ</font>")
                log("пропускаем")
                continue
            }

            val targetMode = getCharMode(char)
            // Если символ — пробел, не меняем режим
            if (targetMode == "SPACE") {
                }
            // Если режим изменился (и это не пробел) — переключаем режим и кодируем символ
            else if (targetMode != currentMode) {
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
            ' ' -> "SPACE"  // Специальный режим для пробела
            in 'A'..'Z' -> "LAT"
            in 'А'..'Я', 'Ё' -> {
                if (char in setOf('Ш', 'Щ', 'Э', 'Ю', 'Ч')) {
                    "FIGS"
                } else {
                    "RUS"
                }
            }
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
        val ita2code = ITA2[char]?.joinToString(separator = "")
            ?: "<font color='#FF0000'>НЕ ПОДДЕРЖИВАЕТСЯ</font>"
        val displayedChar = when (char) {
            '\n' -> "LF"
            '\r' -> "CR"
            else -> char.toString()
        }

        // Логируем символ, если включён расширенный режим
        if (IS_ExpandedLog == true) {
            log("Символ: '<font color='#39FF14'>$displayedChar</font>' → 0+$ita2code+1.5")
        }

        //log("<font color='#FF0000'> --> </font> Символ '<font color='#39FF14'>$char</font>' <font color='#FF0000'>НЕ ПОДДЕРЖИВАЕТСЯ</font>")

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
    private fun generateTone(freq: Int, durationSec: Double,
                             phase: Double, volume: Float): FloatArray {

        val numSamples = (SAMPLE_RATE * durationSec).toInt()
        val tone = FloatArray(numSamples)

        // Правильный расчёт угловой частоты
        val omega = 2.0 * PI * freq

        for (i in 0 until numSamples) {
            // Физически корректный расчёт времени
            val t = i.toDouble() / SAMPLE_RATE
            // Полная фаза с учётом начальной фазы
            val totalPhase = omega * t + phase
            tone[i] = sin(totalPhase).toFloat() * volume.coerceIn(0f, 1f) * (Short.MAX_VALUE / 2)
        }

        return tone
    }



    /**
     * Воспроизводит RTTY‑сигнал для заданного текста
     */
    fun playRtty(text: String, volume: Float = 0.3f) {
        if (text.isEmpty()) return

        log("<font color='#B2EC5D'>=== Начало передачи RTTY ===</font>")
        log("Частота MARK: $MARK_FREQ Гц")
        log("Частота SPACE: $SPACE_FREQ Гц")
        log("Скорость: $BAUD_RATE бод")
        log("Громкость: $volume")

        // 1. Кодируем текст в биты
        val bits = encodeToITA2(text)

        // 2. Рассчитываем длительность бита
        val bitDuration = 1.0 / BAUD_RATE

        // 3. Генерируем аудио
        val audioBuffer = mutableListOf<Float>()
        var totalPhase = 0.0

        // Маркерный тон ПЕРЕД передачей — 300 мс
        audioBuffer.addAll(generateTone(MARK_FREQ, 0.3, totalPhase, volume).toList())
        totalPhase = updatePhase(totalPhase, MARK_FREQ, 0.3)

        var displayedbitarray = ""
        for (bit in bits) {
            val freq = when (bit) {
                1.0, 0.5 -> MARK_FREQ
                else -> SPACE_FREQ
            }

            val displayedbit = when (bit) {
                1.0 -> "1"
                0.0 -> "0"
                else -> bit.toString()
            }
            displayedbitarray += displayedbit + ","

            val duration = if (bit == 0.5) bitDuration * 0.5 else bitDuration
            val tone = generateTone(freq, duration, totalPhase, volume)
            audioBuffer.addAll(tone.toList())
            totalPhase = updatePhase(totalPhase, freq, duration)
        }

        // Маркерный тон ПОСЛЕ передачи — 300 мс
        audioBuffer.addAll(generateTone(MARK_FREQ, 0.3, totalPhase, volume).toList())


        // Выводим список битов (если включено)
        if (IS_ExpandedLog) {
            log(displayedbitarray.dropLast(1))
        }

        // Расчёт длительности
        val durationSeconds = audioBuffer.size.toDouble() / SAMPLE_RATE
        log("Длительность аудио: ${"%.1f".format(durationSeconds)} сек")

        // 4. Формируем shortBuffer для воспроизведения и будущей записи
        val shortBuffer = ShortArray(audioBuffer.size) {
            (audioBuffer[it] * 2).toInt().toShort()
        }

        // 5. Создаём AudioTrack
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
            .setBufferSizeInBytes(shortBuffer.size * 2)
            .build()

        // 6. Записываем данные в AudioTrack
        audioTrack?.write(shortBuffer, 0, shortBuffer.size, AudioTrack.WRITE_BLOCKING)


        // 7. Запускаем воспроизведение
        audioTrack?.play()

        // 8. Монитор состояния воспроизведения
        val handler = Handler(Looper.getMainLooper())
        val checkPlayback = object : Runnable {
            override fun run() {
                val position = audioTrack?.getPlaybackHeadPosition() ?: 0

                if (position >= shortBuffer.size) {
                    // Шаг 1: Сообщаем об окончании воспроизведения
                    log("Воспроизведение окончено.")

                    // Шаг 2: Проверяем, нужно ли сохранять файл
                    if (IS_SavetofileLog) {
                        log("Сохранение файла...")

                        // Сохраняем в фоновом потоке
                        thread {
                            val filePath = saveWavFile(shortBuffer, SAMPLE_RATE)

                            // Шаг 3: ПОСЛЕ сохранения — выводим финальное сообщение
                            handler.post {
                                log("<font color='#B2EC5D'>=== Передача завершена ===</font>")
                                // Освобождаем ресурсы
                                audioTrack?.release()
                                audioTrack = null
                                handler.removeCallbacks(this)
                            }
                        }
                    } else {
                        // Шаг 3: Если сохранение не нужно — сразу выводим завершение
                        log("<font color='#B2EC5D'>=== Передача завершена ===</font>")
                        // Освобождаем ресурсы
                        audioTrack?.release()
                        audioTrack = null
                        handler.removeCallbacks(this)
                    }
                } else {
                    handler.postDelayed(this, 100)
                }
            }
        }


        // 9. Уведомляем о начале воспроизведения
        log("Воспроизведение звука...")
        handler.post(checkPlayback)
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


    private fun saveWavFile(shortBuffer: ShortArray, sampleRate: Int) {
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val wavFile = File(downloadsDir, "RTTY.wav")
            val outputStream = FileOutputStream(wavFile)

            try {
                val dataSize = shortBuffer.size * 2
                val fileSize = dataSize + 44 - 8

                outputStream.write("RIFF".toByteArray())
                outputStream.write(intToBytes(fileSize))
                outputStream.write("WAVE".toByteArray())

                outputStream.write("fmt ".toByteArray())
                outputStream.write(intToBytes(16))
                outputStream.write(shortToBytes(1.toShort()))
                outputStream.write(shortToBytes(1.toShort()))
                outputStream.write(intToBytes(sampleRate))
                outputStream.write(intToBytes(sampleRate * 2))
                outputStream.write(shortToBytes(2.toShort()))
                outputStream.write(shortToBytes(16.toShort()))

                outputStream.write("data".toByteArray())
                outputStream.write(intToBytes(dataSize))

                for (sample in shortBuffer) {
                    // Исправленные побитовые операции
                    outputStream.write(sample.toInt().and(0xFF))
                    outputStream.write(sample.toInt().ushr(8).and(0xFF))
                }

                outputStream.flush()
                log("<font color='#90EE90'>Файл сохранён: ${wavFile.absolutePath}</font>")
            } catch (e: Exception) {
                log("<font color='#FF0000'>Ошибка записи файла: ${e.message}</font>")
            } finally {
                outputStream.close()
            }
        } catch (e: Exception) {
            log("<font color='#FF0000'>Ошибка доступа к хранилищу: ${e.message}</font>")
        }
    }



    // Вспомогательная функция: запись заголовка WAV
    private fun writeWavHeader(
        out: FileOutputStream,
        numSamples: Int,
        sampleRate: Int
    ) {
        val byteRate = sampleRate * 2  // 2 байта на образец (16-bit)
        val blockAlign: Short = 2            // 2 байта на канал (моно)

        // RIFF Header
        out.write("RIFF".toByteArray())                     // chunkId
        out.write(intToBytes(36 + numSamples * 2))         // chunkSize (весь файл минус 8 байт)
        out.write("WAVE".toByteArray())                      // format

        // fmt Subchunk
        out.write("fmt ".toByteArray())                    // subchunk1Id
        out.write(intToBytes(16))                          // subchunk1Size
        out.write(shortToBytes(1))                       // audioFormat (PCM = 1)
        out.write(shortToBytes(1))                       // numChannels (моно = 1)
        out.write(intToBytes(sampleRate))               // sampleRate
        out.write(intToBytes(byteRate))                // byteRate
        out.write(shortToBytes(blockAlign))          // blockAlign
        out.write(shortToBytes(16))                  // bitsPerSample (16 bit)

        // data Subchunk
        out.write("data".toByteArray())                   // subchunk2Id
        out.write(intToBytes(numSamples * 2))           // subchunk2Size (число байт данных)
    }

    // Конвертеры чисел в байты (Little Endian)
    private fun intToBytes(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xFF).toByte(),
            (value shr 8 and 0xFF).toByte(),
            (value shr 16 and 0xFF).toByte(),
            (value shr 24 and 0xFF).toByte()
        )
    }

    private fun shortToBytes(value: Short): ByteArray {
        return byteArrayOf(
            (value.toInt() and 0xFF).toByte(),
            ((value.toInt() shr 8) and 0xFF).toByte()
        )
    }



}
