package com.ivangames.slitherbox

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class LanMenuActivity : AppCompatActivity() {

    private lateinit var tvHostId: TextView
    private lateinit var tvHostStatus: TextView
    private lateinit var etHostIp: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_lan_menu)
        hideSystemUI()

        tvHostId = findViewById(R.id.tvHostId)
        tvHostStatus = findViewById(R.id.tvHostStatus)
        etHostIp = findViewById(R.id.etHostIp)

        // Создать игру (хост)
        findViewById<Button>(R.id.btnCreateGame).setOnClickListener {
            // Пока заглушка — Часть 2 сделает сокет
            tvHostId.text = "ID: 192.168.1.42:8888"
            tvHostStatus.text = "Ожидание подключения... (скоро)"
            Toast.makeText(this, "🖥 Хост-режим (скоро соединение)", Toast.LENGTH_SHORT).show()
        }

        // Подключиться (клиент)
        findViewById<Button>(R.id.btnConnect).setOnClickListener {
            val ip = etHostIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(this, "Введи ID хоста", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Пока заглушка — Часть 2 сделает сокет
            Toast.makeText(this, "📱 Подключение к $ip (скоро)", Toast.LENGTH_SHORT).show()
        }

        // Назад
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            finish()
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
