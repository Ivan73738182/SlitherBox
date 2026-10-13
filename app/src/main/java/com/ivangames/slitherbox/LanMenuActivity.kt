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

    private val net = NetworkManager()
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

        tvHostId = findViewById(R.id.tvHostId)
        tvHostStatus = findViewById(R.id.tvHostStatus)
        etHostIp = findViewById(R.id.etHostIp)
        btnCreateGame = findViewById(R.id.btnCreateGame)
        btnConnect = findViewById(R.id.btnConnect)

        // Кнопка "Начать игру" — создаём программно, если её нет в layout
        btnStartGame = Button(this).apply {
            text = "▶  НАЧАТЬ ИГРУ"
            textSize = 20f
            isEnabled = false
            visibility = View.GONE
        }

        // Настраиваем сетевые колбэки
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
                Toast.makeText(this, "❌ Соединение потеряно", Toast.LENGTH_SHORT).show()
            }
        }

        net.onError = { msg ->
            runOnUiThread {
                tvHostStatus.text = "⚠️ $msg"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }

        net.onMessageReceived = { msg ->
            // Обработка сообщений от сервера
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

        // Создать игру (хост)
        btnCreateGame.setOnClickListener {
            if (isConnected) {
                Toast.makeText(this, "Уже подключено!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            isHost = true
            btnCreateGame.isEnabled = false
            btnConnect.isEnabled = false
            tvHostId.text = "ID: получаю IP..."
            tvHostStatus.text = "Запуск сервера..."
            net.startHost()
        }

        // Подключиться (клиент)
        btnConnect.setOnClickListener {
            val ipPort = etHostIp.text.toString().trim()
            if (ipPort.isEmpty()) {
                Toast.makeText(this, "Введи ID хоста (IP:порт)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Парсим "192.168.1.42:8888"
            val parts = ipPort.split(":")
            val ip = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8888

            isHost = false
            btnCreateGame.isEnabled = false
            btnConnect.isEnabled = false
            tvHostStatus.text = "Подключение к $ip:$port..."
            net.startClient(ip, port)
        }

        // Начать игру
        btnStartGame.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("mode", if (isHost) "HOST" else "CLIENT")
            startActivity(intent)
        }

        // Назад
        findViewById<Button>(R.id.btnBack).setOnClickListener {
            net.stop()
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        net.stop()
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
