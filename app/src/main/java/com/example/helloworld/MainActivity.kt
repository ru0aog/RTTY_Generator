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

class MainActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Инициализация SharedPreferences
        sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Инициализация виджетов
        editText = findViewById(R.id.editText)
        buttonStart = findViewById(R.id.button1)
        buttonStop = findViewById(R.id.button2)
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

        // Обработчики кнопок
        buttonStart.setOnClickListener {
            val text = editText.text.toString().trim()
            if (text.isNotEmpty()) {
                toneGenerator.playRtty(text, volume = 0.5f)
            } else {
                addLogMessage("Ошибка: текст не введён.")
            }
        }

        buttonStop.setOnClickListener {
            toneGenerator.stopTone()
            addLogMessage("=== Передача остановлена ===\n")
        }

        // ЗАПУСКАЕМ ОБНОВЛЕНИЕ ЧАСОВ СРАЗУ ПРИ СТАРТЕ
        handler.post(timeUpdater)

        // Находим кнопку настроек
        val btnSettings = findViewById<Button>(R.id.btnSettings)

        // Обработчик нажатия
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun loadLogFromPreferences() {
        val savedLog = sharedPreferences.getString(LOG_KEY, "")
        savedLog?.takeIf { it.isNotEmpty() }?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?.forEach { addLogMessage(it) }
    }

    private fun addLogMessage(message: String) {
        val textView = TextView(this).apply {
            val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            val timeString = dateFormat.format(Date())
            text = "[$timeString] $message"
            textSize = 14f
            setTextColor(0xFFFFD700.toInt()) // золотистый
            setBackgroundColor(0xFF000000.toInt()) // чёрный
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 0) }
        }
        logContainer.addView(textView)
        scrollView.post { scrollView.fullScroll(ScrollView.FOCUS_DOWN) }
        saveLogToPreferences() // Сохраняем после каждого добавления
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
    }

    private fun showSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_settings, null)

        // Находим элементы диалога
        val etBaudRate = view.findViewById<EditText>(R.id.etBaudRate)
        val etMarkFreq = view.findViewById<EditText>(R.id.etMarkFreq)
        val etSpaceFreq = view.findViewById<EditText>(R.id.etSpaceFreq)
        val tvTimeDiff = view.findViewById<TextView>(R.id.tvTimeDiff)
        val cbExpandedLog = view.findViewById<CheckBox>(R.id.cbExpandedLog)

        // Загружаем текущие настройки из SharedPreferences
        etBaudRate.setText(sharedPreferences.getString("baud_rate", "50") ?: "50")
        etMarkFreq.setText(sharedPreferences.getString("mark_freq", "1300") ?: "1300")
        etSpaceFreq.setText(sharedPreferences.getString("space_freq", "2100") ?: "2100")
        cbExpandedLog.isChecked = sharedPreferences.getBoolean("expanded_log", false)

        // Рассчитываем разницу во времени LOC и UTC
        val utcOffset = TimeZone.getTimeZone("UTC").getOffset(Date().time)
        val localOffset = TimeZone.getDefault().getOffset(Date().time)
        val diffHours = (localOffset - utcOffset) / (1000 * 60 * 60)
        tvTimeDiff.text = when {
            diffHours > 0 -> "Разница: +${diffHours} ч"
            diffHours < 0 -> "Разница: ${diffHours} ч"
            else -> "Разница: 0 ч"
        }

        // Создаём диалог
        AlertDialog.Builder(this)
            .setTitle("Настройки")
            .setView(view)
            .setPositiveButton("Сохранить") { dialog, _ ->
                // Сохраняем настройки
                sharedPreferences.edit()
                    .putString("baud_rate", etBaudRate.text.toString())
                    .putString("mark_freq", etMarkFreq.text.toString())
                    .putString("space_freq", etSpaceFreq.text.toString())
                    .putBoolean("expanded_log", cbExpandedLog.isChecked)
                    .apply()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}
