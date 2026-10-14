package com.ivangames.slitherbox

/**
 * Глобальный держатель NetworkManager между Activity.
 * Нужен, чтобы при переходе LanMenu → MainActivity соединение не рвалось.
 */
object NetworkHolder {
    var manager: NetworkManager? = null
    var mode: String = "OFFLINE"   // OFFLINE / HOST / CLIENT
}
