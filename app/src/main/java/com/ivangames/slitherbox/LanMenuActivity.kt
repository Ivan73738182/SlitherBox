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
    private lateinit var btnCreateGame: Button
    private lateinit var btnConnect: Button
    private lateinit var btnStartGame: Button

    private lateinit var net: NetworkManager
    private var isHost = false
    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_lan_menu)
        hideSystemUI()

        // Используем общий NetworkManager из NetworkHolder
        if (NetworkHolder.manager == null) {
            NetworkHolder.manager = NetworkManager()
        }
        net = NetworkHolder.manager!!

        tvHostId = findViewById(R.id.tvHostId)
        tvHostStatus = findViewById(R.id.tvHostStatus)
        etHostIp = findViewById(R.id.etHostIp)
        btnCreateGame = findViewById(R.id.btnCreateGame)
        btnConnect = findViewById(R.id.btnConnect)
        btnStartGame = findViewById(R.id.btnStartGame)
        btnStartGame.isEnabled = false
        btnStartGame.visibility = View.GONE

        // Колбэки
        net.onConnected = {
            isConnected = true
            runOnUiThread {
                tvHostStatus.text = "✅ Подключено!"
                btnStartGame.isEnabled = true
                btnStartGame.visibility = View.VISIBLE
                Toast.makeText(this, "✅ Подключено!", Toast.LENGTH_SHORT).show()
            }
        }

        net.onDisconnected = {
            isConnected = false
            runOnUiThread {
                tvHostStatus.text = "❌ Отключено"
                btnStartGame.isEnabled = false
                btnStartGame.visibility = View.GONE
            }
        }

        net.onError = { msg ->
            runOnUiThread {
                tvHostStatus.text = "⚠️ $msg"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        net.onMessageReceived = { msg ->
            if (msg.startsWith("HOST_READY")) {
                val parts = msg.split("|")
                val ip = parts.getOrNull(1) ?: "?"
                val port = parts.getOrNull(2) ?: "8888"
                runOnUiThread {
                    tvHostId.text = "ID: $ip:$port"
                    tvHostStatus.text = "Ожидание подключения..."
                }
            } else if (msg == "HELLO") {
                runOnUiThread {
                    tvHostStatus.text = "✅ Игрок подключился!"
                }
            }
        }

        // Создать игру
        btnCreateGame.setOnClickListener {
            if (isConnected) return@setOnClickListener
            isHost = true
            NetworkHolder.mode = "HOST"
            btnCreateGame.isEnabled = false
            btnConnect.isEnabled = false
            tvHostId.text = "ID: получаю IP..."
            tvHostStatus.text = "Запуск сервера..."
            net.startHost()
        }

        // Подключиться
        btnConnect.setOnClickListener {
            val ipPort = etHostIp.text.toString().trim()
            if (ipPort.isEmpty()) {
                Toast.makeText(this, "Введи ID хоста", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val parts = ipPort.split(":")
            val ip = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8888

            isHost = false
            NetworkHolder.mode = "CLIENT"
            btnCreateGame.isEnabled = false
            btnConnect.isEnabled = false
            tvHostStatus.text = "Подключение к $ip:$port..."
            net.startClient(ip, port)
        }

        // Начать игру
        btnStartGame.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // Назад
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            net.stop()
            NetworkHolder.manager = null
            NetworkHolder.mode = "OFFLINE"
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // НЕ закрываем сеть — она нужна в MainActivity
        // net.stop() — только по кнопке "Назад"
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
