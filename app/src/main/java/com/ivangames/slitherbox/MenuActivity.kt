package com.ivangames.slitherbox

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences

    // Цвета скинов (8 штук)
    private val skinColors = intArrayOf(
        0xFF80DC78.toInt(),  // зелёный (по умолчанию)
        0xFF64B5F6.toInt(),  // голубой
        0xFFE57373.toInt(),  // красный
        0xFFFFD54F.toInt(),  // жёлтый
        0xFFBA68C8.toInt(),  // фиолетовый
        0xFFFFB74D.toInt(),  // оранжевый
        0xFFF06292.toInt(),  // розовый
        0xFF4DD0E1.toInt()   // бирюзовый
    )

    private val skinNames = arrayOf(
        "Зелёный", "Голубой", "Красный", "Жёлтый",
        "Фиолетовый", "Оранжевый", "Розовый", "Бирюзовый"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            prefs = getSharedPreferences("slitherbox", MODE_PRIVATE)

            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            setContentView(R.layout.activity_menu)
            hideSystemUI()

            // Рекорд
            val bestScore = prefs.getInt("best_score", 0)
            findViewById<TextView>(R.id.tvBestScore).text = bestScore.toString()

            // Монеты
            val coins = prefs.getInt("coins", 0)
            findViewById<TextView>(R.id.tvCoins).text = coins.toString()

            // Играть
            findViewById<Button>(R.id.btnPlay).setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
            }

            // Настройки
            findViewById<TextView>(R.id.btnSettings).setOnClickListener {
                Toast.makeText(this, "⚙️ Настройки скоро!", Toast.LENGTH_SHORT).show()
            }

// LAN
findViewById<TextView>(R.id.btnLan).setOnClickListener {
    startActivity(Intent(this, LanMenuActivity::class.java))
}
            // Скины (верхняя иконка)
            findViewById<TextView>(R.id.btnSkins).setOnClickListener {
                showSkinPicker()
            }

            // Рекорды
            findViewById<LinearLayout>(R.id.btnRecords).setOnClickListener {
                showRecordsDialog()
            }

            // Квесты
            findViewById<LinearLayout>(R.id.btnQuests).setOnClickListener {
                Toast.makeText(this, "📋 Квесты скоро!", Toast.LENGTH_SHORT).show()
            }

            // Редактирование змеи
            findViewById<LinearLayout>(R.id.btnSnakeSkin).setOnClickListener {
                showSkinPicker()
            }

        } catch (e: Throwable) {
            showError(e)
        }
    }

    private fun showSkinPicker() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🐍 Выберите скин")

        // Строим список из цветов
        val items = skinNames
        builder.setItems(items) { _, which ->
            prefs.edit().putInt("skin_color", skinColors[which]).apply()
            Toast.makeText(this, "Скин: ${skinNames[which]}", Toast.LENGTH_SHORT).show()
        }
        builder.setPositiveButton("Отмена", null)
        builder.show()
    }

    private fun showError(e: Throwable) {
        val tv = TextView(this)
        tv.text = "ОШИБКА:\n\n${e.message}\n\n${e.stackTraceToString().take(2000)}"
        tv.setTextColor(Color.WHITE)
        tv.textSize = 12f
        tv.setPadding(20, 20, 20, 20)
        tv.setBackgroundColor(Color.BLACK)
        setContentView(tv)
    }

    private fun showRecordsDialog() {
        val bestScore = prefs.getInt("best_score", 0)

        AlertDialog.Builder(this)
            .setTitle("🏆 Рекорды")
            .setMessage("Лучший результат: $bestScore")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun hideSystemUI() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }
}
