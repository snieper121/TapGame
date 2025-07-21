package com.example.tapgame

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.example.tapgame.server.IMyPermissionServer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Класс для интеграции с нативным сервером разрешений
 * Обеспечивает получение и сохранение разрешений после отладки по WiFi
 */
class ServerIntegration private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "ServerIntegration"
        
        @Volatile
        private var INSTANCE: ServerIntegration? = null
        
        fun getInstance(context: Context): ServerIntegration {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ServerIntegration(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var permissionServer: IMyPermissionServer? = null
    private var isConnected = false
    
    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()
    
    private val _permissionState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val permissionState: StateFlow<Map<String, Boolean>> = _permissionState.asStateFlow()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Connected to permission server")
            permissionServer = IMyPermissionServer.Stub.asInterface(service)
            isConnected = true
            _connectionState.value = true
            
            // Загружаем сохраненные разрешения
            loadSavedPermissions()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Disconnected from permission server")
            permissionServer = null
            isConnected = false
            _connectionState.value = false
        }
    }
    
    /**
     * Подключение к серверу разрешений
     */
    fun connectToServer(): Boolean {
        return try {
            val intent = Intent().apply {
                component = ComponentName(
                    "com.example.tapgame",
                    "com.example.tapgame.server.PermissionServerService"
                )
            }
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to server", e)
            false
        }
    }
    
    /**
     * Отключение от сервера разрешений
     */
    fun disconnectFromServer() {
        try {
            if (isConnected) {
                context.unbindService(serviceConnection)
                isConnected = false
                _connectionState.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disconnect from server", e)
        }
    }
    
    /**
     * Сохранение разрешения в сервере
     */
    fun savePermission(permission: String, granted: Boolean): Boolean {
        return try {
            permissionServer?.savePermission(permission, granted) ?: false.also {
                Log.w(TAG, "Server not connected, cannot save permission: $permission")
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to save permission: $permission", e)
            false
        }
    }
    
    /**
     * Получение состояния разрешения из сервера
     */
    fun getPermission(permission: String): Boolean {
        return try {
            permissionServer?.getPermission(permission) ?: false.also {
                Log.w(TAG, "Server not connected, cannot get permission: $permission")
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get permission: $permission", e)
            false
        }
    }
    
    /**
     * Получение всех сохраненных разрешений
     */
    fun getAllPermissions(): Map<String, Boolean> {
        return try {
            val permissions = mutableMapOf<String, Boolean>()
            permissionServer?.getAllPermissions()?.let { bundle ->
                for (key in bundle.keySet()) {
                    permissions[key] = bundle.getBoolean(key, false)
                }
            }
            permissions
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to get all permissions", e)
            emptyMap()
        }
    }
    
    /**
     * Очистка всех сохраненных разрешений
     */
    fun clearAllPermissions(): Boolean {
        return try {
            permissionServer?.clearAllPermissions() ?: false.also {
                Log.w(TAG, "Server not connected, cannot clear permissions")
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to clear all permissions", e)
            false
        }
    }
    
    /**
     * Проверка активности сервера
     */
    fun isServerActive(): Boolean {
        return try {
            permissionServer?.isActive() ?: false
        } catch (e: RemoteException) {
            Log.e(TAG, "Failed to check server status", e)
            false
        }
    }
    
    /**
     * Загрузка сохраненных разрешений при подключении
     */
    private fun loadSavedPermissions() {
        try {
            val permissions = getAllPermissions()
            _permissionState.value = permissions
            Log.d(TAG, "Loaded ${permissions.size} saved permissions")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved permissions", e)
        }
    }
    
    /**
     * Обновление состояния разрешения в локальном кэше
     */
    fun updatePermissionState(permission: String, granted: Boolean) {
        val currentState = _permissionState.value.toMutableMap()
        currentState[permission] = granted
        _permissionState.value = currentState
        
        // Сохраняем в сервере
        savePermission(permission, granted)
    }
    
    /**
     * Массовое обновление разрешений
     */
    fun updateMultiplePermissions(permissions: Map<String, Boolean>) {
        val currentState = _permissionState.value.toMutableMap()
        permissions.forEach { (permission, granted) ->
            currentState[permission] = granted
            savePermission(permission, granted)
        }
        _permissionState.value = currentState
    }
}

