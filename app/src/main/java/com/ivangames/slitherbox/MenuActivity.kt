package com.ivangames.slitherbox

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            setContentView(R.layout.activity_menu)
            hideSystemUI()

            // Играть
            findViewById<Button>(R.id.btnPlay).setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
            }

            // Рекорды
            findViewById<Button>(R.id.btnRecords).setOnClickListener {
                showRecordsDialog()
            }

            // LAN
            findViewById<Button>(R.id.btnLan).setOnClickListener {
                Toast.makeText(this, "🌐 LAN скоро появится!", Toast.LENGTH_SHORT).show()
            }

            // Выход
            findViewById<Button>(R.id.btnExit).setOnClickListener {
                finishAffinity()
            }
        } catch (e: Throwable) {
            showError(e)
        }
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
        val prefs = getSharedPreferences("slitherbox", MODE_PRIVATE)
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
