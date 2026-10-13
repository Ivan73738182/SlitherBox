package com.ivangames.slitherbox

import android.os.Handler
import android.os.Looper
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket

/**
 * Сетевой менеджер для LAN-игры.
 *
 * Роли:
 *  - ХОСТ: открывает ServerSocket, ждёт клиента, оба обмениваются сообщениями.
 *  - КЛИЕНТ: подключается к ServerSocket хоста.
 *
 * Сообщения — строки, разделитель параметров '|'.
 */
class NetworkManager {

    companion object {
        const val PORT = 8888
    }

    enum class Mode { HOST, CLIENT, OFFLINE }

    // Слушатели событий
    var onConnected: (() -> Unit)? = null
    var onDisconnected: (() -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private var running = false
    var mode: Mode = Mode.OFFLINE
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Получить IP-адрес устройства в локальной сети (Wi-Fi).
     * Возвращает "192.168.x.x" или "не найден".
     */
    fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "?"
                    }
                }
            }
        } catch (e: Exception) {
            return "?"
        }
        return "?"
    }

    /**
     * Запуск в режиме ХОСТА. Ждёт подключения клиента.
     */
    fun startHost() {
        mode = Mode.HOST
        running = true

        Thread {
            try {
                serverSocket = ServerSocket(PORT)
                val ip = getLocalIpAddress()

                mainHandler.post {
                    onMessageReceived?.invoke("HOST_READY|$ip|$PORT")
                }

                // Ждём клиента
                val client = serverSocket?.accept()
                if (client == null) {
                    mainHandler.post { onError?.invoke("Не удалось принять клиента") }
                    return@Thread
                }

                socket = client
                setupStreams(client)

                mainHandler.post {
                    onConnected?.invoke()
                }

                listenLoop()

            } catch (e: Exception) {
                mainHandler.post {
                    if (running) onError?.invoke("Ошибка хоста: ${e.message}")
                }
            }
        }.start()
    }

    /**
     * Запуск в режиме КЛИЕНТА. Подключается к ip:port.
     */
    fun startClient(hostIp: String, port: Int = PORT) {
        mode = Mode.CLIENT
        running = true

        Thread {
            try {
                val s = Socket(hostIp, port)
                socket = s
                setupStreams(s)

                mainHandler.post {
                    onConnected?.invoke()
                }

                // Отправляем приветствие
                send("HELLO")

                listenLoop()

            } catch (e: Exception) {
                mainHandler.post {
                    if (running) onError?.invoke("Ошибка клиента: ${e.message}")
                }
            }
        }.start()
    }

    private fun setupStreams(s: Socket) {
        writer = PrintWriter(s.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(s.getInputStream()))
    }

    /**
     * Цикл приёма сообщений.
     */
    private fun listenLoop() {
        try {
            while (running) {
                val line = reader?.readLine() ?: break
                mainHandler.post {
                    onMessageReceived?.invoke(line)
                }
            }
        } catch (e: Exception) {
            // Тихо — отключились
        } finally {
            mainHandler.post {
                if (running) {
                    running = false
                    onDisconnected?.invoke()
                }
            }
        }
    }

    /**
     * Отправка строки. Возвращает true, если отправлено.
     */
    fun send(message: String): Boolean {
        return try {
            writer?.println(message)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Остановить сеть, закрыть сокеты.
     */
    fun stop() {
        running = false
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        try { serverSocket?.close() } catch (_: Exception) {}
        writer = null
        reader = null
        socket = null
        serverSocket = null
    }
}
