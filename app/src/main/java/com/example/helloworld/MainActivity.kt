package com.example.helloworld

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher


class MainActivity : AppCompatActivity() {

    private lateinit var editText: EditText
    private lateinit var buttonStart: Button
    private lateinit var buttonStop: Button
    private lateinit var logContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private val toneGenerator = ToneGenerator()

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

        // Настройка ToneGenerator: передача логов в UI
        toneGenerator.onLogMessage = { message ->
            runOnUiThread { addLogMessage(message) }
        }

        // Загрузка сохранённых данных
        editText.setText(sharedPreferences.getString(EDIT_TEXT_KEY, "") ?: "")
        loadLogFromPreferences()

        // Сохранение текста при изменении
        editText.addTextChangedListener(object : TextWatcherAdapter() {
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
            addLogMessage("Передача остановлена.")
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
            val dateFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            val timeString = dateFormat.format(java.util.Date())
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

    override fun onDestroy() {
        super.onDestroy()
        toneGenerator.stopTone() // Гарантируем остановку аудио
    }
}

// Упрощённый адаптер для TextWatcher
abstract class TextWatcherAdapter : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
}
