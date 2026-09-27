package com.airconnection.app.network

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.airconnection.app.AppConfig

object SupabaseManager {
    private const val TAG = "SupabaseManager"
    
    var supabaseUrl: String = AppConfig.SUPABASE_URL
    var supabaseKey: String = AppConfig.SUPABASE_KEY

    lateinit var client: SupabaseClient

    fun init() {
        if (supabaseUrl.contains("your-project-id") || supabaseKey.contains("your-anon-key")) {
            Log.e(TAG, "Supabase URL or Key is not configured properly.")
            return
        }
        
        client = createSupabaseClient(
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseKey
        ) {
            install(Realtime)
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                client.realtime.connect()
                Log.d(TAG, "Connected to Supabase Realtime!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to Supabase: ${e.message}")
            }
        }
    }
}
