package com.ivangames.slitherbox

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var menuView: MenuView
    private lateinit var tvNick: TextView

    private val skinColors = intArrayOf(
        0xFF80DC78.toInt(), 0xFF64B5F6.toInt(), 0xFFE57373.toInt(), 0xFFFFD54F.toInt(),
        0xFFBA68C8.toInt(), 0xFFFFB74D.toInt(), 0xFFF06292.toInt(), 0xFF4DD0E1.toInt()
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

            menuView = findViewById(R.id.menuView)
            tvNick = findViewById(R.id.tvNick)

            // Скин
            val currentSkin = prefs.getInt("skin_color", skinColors[0])
            menuView.skinColor = currentSkin

            // Ник
            updateNickDisplay()

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

            // Скины (иконка)
            findViewById<TextView>(R.id.btnSkins).setOnClickListener {
                showSkinPicker()
            }

            // Профиль (аватар змейки)
            findViewById<LinearLayout>(R.id.btnProfile).setOnClickListener {
                showProfileDialog()
            }

            // Ник — тоже открывает профиль
            tvNick.setOnClickListener {
                showProfileDialog()
            }

            // Рекорды
            findViewById<LinearLayout>(R.id.btnRecords).setOnClickListener {
                showRecordsDialog()
            }

            // Квесты
            findViewById<LinearLayout>(R.id.btnQuests).setOnClickListener {
                Toast.makeText(this, "📋 Квесты скоро!", Toast.LENGTH_SHORT).show()
            }

            // Ежедневный бонус
            findViewById<LinearLayout>(R.id.btnDailyBonus).setOnClickListener {
                claimDailyBonus()
            }

        } catch (e: Throwable) {
            showError(e)
        }
    }

    private fun updateNickDisplay() {
        val nick = prefs.getString("player_nick", "IVAN") ?: "IVAN"
        tvNick.text = nick
    }

    private fun showProfileDialog() {
        val currentNick = prefs.getString("player_nick", "IVAN") ?: "IVAN"

        val input = EditText(this).apply {
            setText(currentNick)
            filters = arrayOf(InputFilter.LengthFilter(12))
            hint = "Твой ник"
            setPadding(30, 30, 30, 30)
        }

        AlertDialog.Builder(this)
            .setTitle("👤 Профиль")
            .setMessage("Измени свой ник")
            .setView(input)
            .setPositiveButton("Сохранить") { _, _ ->
                val newNick = input.text.toString().trim()
                if (newNick.length < 2) {
                    Toast.makeText(this, "Ник слишком короткий", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                prefs.edit().putString("player_nick", newNick).apply()
                updateNickDisplay()
                Toast.makeText(this, "Ник: $newNick", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showSkinPicker() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("🐍 Выберите скин")

        builder.setItems(skinNames) { _, which ->
            prefs.edit().putInt("skin_color", skinColors[which]).apply()
            menuView.skinColor = skinColors[which]
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
        val coins = prefs.getInt("coins", 0)

        AlertDialog.Builder(this)
            .setTitle("🏆 Рекорды")
            .setMessage("Лучший результат: $bestScore\nМонет: $coins")
            .setPositiveButton("OK", null)
            .show()
    }

    private fun claimDailyBonus() {
        val now = System.currentTimeMillis()
        val lastBonus = prefs.getLong("last_bonus_time", 0L)
        val cooldown = 24 * 60 * 60 * 1000L

        if (now - lastBonus >= cooldown) {
            val currentCoins = prefs.getInt("coins", 0)
            val newCoins = currentCoins + 3
            prefs.edit()
                .putInt("coins", newCoins)
                .putLong("last_bonus_time", now)
                .apply()

            findViewById<TextView>(R.id.tvCoins).text = newCoins.toString()
            Toast.makeText(this, "🪙 +3 монеты! Приходи завтра!", Toast.LENGTH_LONG).show()
        } else {
            val remaining = cooldown - (now - lastBonus)
            val hours = remaining / (60 * 60 * 1000)
            val minutes = (remaining / (60 * 1000)) % 60
            Toast.makeText(this, "⏳ Следующий бонус через ${hours}ч ${minutes}м", Toast.LENGTH_LONG).show()
        }
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
