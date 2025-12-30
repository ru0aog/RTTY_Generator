package com.example.helloworld

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.text.TextWatcher
import android.text.Editable
import android.os.Handler
import android.os.Looper
import java.util.*
import java.text.SimpleDateFormat
import android.widget.CheckBox
import android.widget.SeekBar
import android.text.Html
import android.os.Environment
import java.io.File


class MainActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var buttonSettings: Button
    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var tvLocalTime: TextView
    private lateinit var tvUtcTime: TextView

    private val toneGenerator = ToneGenerator()
    private val handler = Handler(Looper.getMainLooper())
    private val timeUpdater = object : Runnable {
        override fun run() {
            updateTime()
            handler.postDelayed(this, 1000)
        }
    }

    // Поля для SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences
    private companion object {
        private const val PREF_NAME = "app_preferences"
        private const val EDIT_TEXT_KEY = "edit_text_content"
        private const val LOG_KEY = "log_content"
    }
    private lateinit var cbSaveToFile: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Инициализация виджетов
        editText = findViewById(R.id.editText)
        buttonStart = findViewById(R.id.button1)
        buttonStop = findViewById(R.id.button2)
        buttonSettings = findViewById(R.id.btnSettings)
        logContainer = findViewById(R.id.logContainer)
        scrollView = findViewById(R.id.scrollView)

        // Добавляем часы
        tvLocalTime = findViewById(R.id.tvLocalTime)
        tvUtcTime = findViewById(R.id.tvUtcTime)
        // Настройка ToneGenerator: передача логов в UI
        toneGenerator.onLogMessage = { message ->
            runOnUiThread { addLogMessage(message) }
        }

        // Загрузка сохранённых данных
        editText.setText(sharedPreferences.getString(EDIT_TEXT_KEY, "") ?: "")
        loadLogFromPreferences()

        // Сохранение текста при изменении
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable?) {
                sharedPreferences.edit()
                    .putString(EDIT_TEXT_KEY, editable.toString())
                    .apply()
            }
        })

        // Обработчик двойного тапа на ScrollView
        var lastClickTime: Long = 0
        scrollView.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    showClearDialog()
                }
                lastClickTime = currentTime
            }
            false
        }

        // Обработчик кнопки "СТАРТ"
        buttonStart.setOnClickListener {
            // Сначала обновляем настройки (даже если они не менялись)
            applySettings()

            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                val volumePercent = sharedPreferences.getInt("volume_percent", 20)
                val volumeFloat = volumePercent / 100f  // 20% → 0.2f
                toneGenerator.playRtty(text, volumeFloat)
            } else {
                addLogMessage("Ошибка: текст не введён.")
            }
        }
        // Обработчик кнопки "СТОП"
        buttonStop.setOnClickListener {
            toneGenerator.stopTone()
            addLogMessage("=== Передача остановлена ===\n")
        }
        // Обработчик кнопки "ПАРАМЕТРЫ"
        buttonSettings.setOnClickListener {
            showSettingsDialog()
        }

        // ЗАПУСКАЕМ ОБНОВЛЕНИЕ ЧАСОВ СРАЗУ ПРИ СТАРТЕ
        handler.post(timeUpdater)


    }

    private fun loadLogFromPreferences() {
        val savedLog = sharedPreferences.getString(LOG_KEY, "")
        savedLog?.takeIf { it.isNotEmpty() }?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?.forEach { addLogMessage(it, skipTimestamp = true) }
    }

    private fun addLogMessage(message: String, skipTimestamp: Boolean = false) {
        val textView = TextView(this).apply {
            val formattedText = if (skipTimestamp) {
                // Если метка уже есть — оставляем как есть (без изменений)
                message
            } else {
                // Формируем метку времени с HTML-разметкой
                val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val timeString = dateFormat.format(Date())
                val htmlTimestamp = "<font color='#A52A2A'>[$timeString]</font>"  // Коричневый цвет
                "$htmlTimestamp $message"
            }
            // Преобразуем HTML в отформатированный текст
            text = Html.fromHtml(formattedText, Html.FROM_HTML_MODE_COMPACT)
            textSize = 14f
            setTextColor(0xFFFFD700.toInt()) // Основной текст — золотистый
            setBackgroundColor(0xFF000000.toInt()) // Чёрный фон
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 0) }
        }

        logContainer.addView(textView)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }

        if (!skipTimestamp) {
            saveLogToPreferences()
        }
    }


    private fun saveLogToPreferences() {
        val logMessages = sequence {
            for (i in 0 until logContainer.childCount) {
                (logContainer.getChildAt(i) as? TextView)?.text?.toString()?.let {
                    yield(it)
                }
            }
        }.toList()

        sharedPreferences.edit()
            .putString(LOG_KEY, logMessages.joinToString("\n"))
            .apply()
    }

    private fun showClearDialog() {
        AlertDialog.Builder(this)
            .setTitle("Очистить лог?")
            .setMessage("Вы уверены, что хотите удалить все записи?")
            .setPositiveButton("Да") { dialog, _ ->
                logContainer.removeAllViews()
                saveLogToPreferences()
                dialog.dismiss()
            }
            .setNegativeButton("Нет") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun updateTime() {
        val localFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        tvLocalTime.text = "LOCAL: ${localFormat.format(Date())}"

        val utcFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        tvUtcTime.text = "UTC: ${utcFormat.format(Date())}"
    }

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.stopTone()
        handler.removeCallbacks(timeUpdater) // Остановка часов
        saveLogToFile() // запись логов в TXT-файл
    }

    // обработчик кнопки "УСТАНОВКИ"
    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        // Находим элементы диалога
        val etBaudRate = view.findViewById<EditText>(R.id.etBaudRate)
        val etMarkFreq = view.findViewById<EditText>(R.id.etMarkFreq)
        val etSpaceFreq = view.findViewById<EditText>(R.id.etSpaceFreq)
        val etTimeDiff = view.findViewById<EditText>(R.id.etTimeDiff)
        val cbExpandedLog = view.findViewById<CheckBox>(R.id.cbExpandedLog)
        val cbSaveToFile = view.findViewById<CheckBox>(R.id.cbSaveToFile)
        val seekBarVolume = view.findViewById<SeekBar>(R.id.seekBarVolume)
        val etVolumeValue = view.findViewById<EditText>(R.id.etVolumeValue)

        // Загружаем текущие настройки из SharedPreferences
        etBaudRate.setText(sharedPreferences.getString("baud_rate", "45.45") ?: "45.45")
        etMarkFreq.setText(sharedPreferences.getString("mark_freq", "1170") ?: "1170")
        etSpaceFreq.setText(sharedPreferences.getString("space_freq", "1000") ?: "1000")
        etTimeDiff.setText(sharedPreferences.getString("time_diff", "7") ?: "7")
        cbExpandedLog.isChecked = sharedPreferences.getBoolean("expanded_log", false)
        cbSaveToFile.isChecked = sharedPreferences.getBoolean("savetofile_log", false)

        // Загрузка громкости
        val savedVolume = sharedPreferences.getInt("volume_percent", 20)
        seekBarVolume.progress = savedVolume
        etVolumeValue.setText(savedVolume.toString())

        // Синхронизация слайдера → поле ввода
        seekBarVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {  // Только если изменил пользователь
                    etVolumeValue.setText(progress.toString())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Синхронизация поля ввода → слайдер
        etVolumeValue.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(editable: Editable?) {
                val text = editable.toString()
                if (text.isNotEmpty()) {
                    val value = text.toIntOrNull() ?: 0
                    if (value in 0..100) {  // Проверка диапазона
                        seekBarVolume.progress = value
                    } else {
                        // Если вне диапазона — корректируем
                        val clamped = value.coerceIn(0, 100)
                        etVolumeValue.setText(clamped.toString())
                        seekBarVolume.progress = clamped
                    }
                } else {
                    etVolumeValue.setText("0")  // Если пусто — ставим 0
                }
            }
        })

        // Рассчитываем разницу во времени LOC и UTC
        val utcOffset = TimeZone.getTimeZone("UTC").getOffset(Date().time)
        val localOffset = TimeZone.getDefault().getOffset(Date().time)
        val diffHours = (localOffset - utcOffset) / (1000 * 60 * 60)
        //etTimeDiff.text = when {
        //    diffHours > 0 -> "Разница: +${diffHours} ч"
        //    diffHours < 0 -> "Разница: ${diffHours} ч"
        //    else -> "Разница: 0 ч"
        //}

        // Создаём диалог
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setView(view)
            .setPositiveButton("Сохранить") { dialog, _ ->
                // Сохраняем настройки
                // Сохраняем громкость из EditText
                val volumeText = etVolumeValue.text.toString()
                val volumePercent = volumeText.toIntOrNull() ?: 20  // По умолчанию 20
                // Валидация скорости БОД: должна быть положительным числом
                val baudText = etBaudRate.text.toString()
                if (baudText.isNotEmpty() && baudText.toDoubleOrNull() != null && baudText.toDouble() > 0) {
                    // Сохраняем как строку (сохраняет точку и дроби)
                    sharedPreferences.edit()
                        .putString("baud_rate", baudText)
                        .putString("mark_freq", etMarkFreq.text.toString())
                        .putString("space_freq", etSpaceFreq.text.toString())
                        .putString("time_diff", etTimeDiff.text.toString())
                        .putBoolean("expanded_log", cbExpandedLog.isChecked)
                        .putBoolean("savetofile_log", cbSaveToFile.isChecked)
                        .putInt("volume_percent", volumePercent.coerceIn(0, 100))  // Ограничиваем 0–100
                        .apply()
                } else {
                    // Если ввод некорректен — показываем ошибку
                    etBaudRate.error = "Введите положительное число (например, 45.45)"
                    return@setPositiveButton // Не закрываем диалог
                }
                // ПЕРЕДАЁМ НАСТРОЙКИ В ОСНОВНУЮ ПРОГРАММУ
                (this as MainActivity).applySettings()
                saveLogToFile() // запись логов в TXT-файл
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .show()
    }
    fun applySettings() {
        val baudRate = sharedPreferences.getString("baud_rate", "45.45")?.toDoubleOrNull() ?: 45.45
        val markFreq = sharedPreferences.getString("mark_freq", "1170")?.toIntOrNull() ?: 1170
        val spaceFreq = sharedPreferences.getString("space_freq", "1000")?.toIntOrNull() ?: 1000
        val timeDiff = sharedPreferences.getString("time_diff", "7")?.toIntOrNull() ?: 7
        val isExpandedLog = sharedPreferences.getBoolean("expanded_log", false)
        val isSavetofileLog = sharedPreferences.getBoolean("savetofile_log", false)
        // Получаем громкость в процентах
        val volumePercent = sharedPreferences.getInt("volume_percent", 20)
        val volumeFloat = volumePercent / 100f  // 20% → 0.2f

        // Передаём настройки в ToneGenerator
        toneGenerator.updateSettings(
            MARK_FREQ = markFreq,
            SPACE_FREQ = spaceFreq,
            BAUD_RATE = baudRate,
            IS_ExpandedLog = isExpandedLog,
            IS_SavetofileLog = isSavetofileLog,
            volume = volumeFloat
        )
    }

    private fun getLogText(): String {
        val builder = StringBuilder()
        for (i in 0 until logContainer.childCount) {
            val child = logContainer.getChildAt(i)
            if (child is TextView) {
                builder.append(child.text).append("\n")
            }
        }
        return builder.toString().trim()
    }

    private fun saveLogToFile() {
        try {
            // Получаем каталог «Загрузки»
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val file = File(downloadsDir, "RTTY.txt")

            // Записываем текст (перезаписывает файл, если существует)
            file.writeText(getLogText())

            // Логируем успех (если есть onLogMessage)
            toneGenerator.onLogMessage?.invoke("<font color='#90EE90'>Лог сохранён: ${file.absolutePath}</font>")
        } catch (e: Exception) {
            toneGenerator.onLogMessage?.invoke("<font color='#FF0000'>Ошибка сохранения лога: ${e.message}</font>")
        }
    }

}
