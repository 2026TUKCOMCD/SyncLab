package com.tukorea.synclab_mobile.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * 네트워크 연결 상태를 모니터링하는 유틸리티 클래스
 */
class NetworkMonitor(context: Context) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // 현재 네트워크가 Wi-Fi인지 여부를 실시간 Flow로 제공
    val isWifiConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                launch { send(checkIsWifi()) }
            }

            override fun onLost(network: Network) {
                launch { send(checkIsWifi()) }
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                launch { send(capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) }
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // 초기 상태 전송
        trySend(checkIsWifi())

        // Flow가 닫힐 때 콜백 해제 (메모리 누수 방지)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
        .distinctUntilChanged() // 상태가 실제로 바뀔 때만 방출

    // 단순 현재 상태 확인용 함수
    private fun checkIsWifi(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}