package com.ivangames.slitherbox

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Полный экран
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_menu)
        hideSystemUI()

        // Кнопки
        findViewById<Button>(R.id.btnPlay).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        findViewById<Button>(R.id.btnRecords).setOnClickListener {
            showRecordsDialog()
        }

        findViewById<Button>(R.id.btnExit).setOnClickListener {
            finishAffinity()
        }
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
