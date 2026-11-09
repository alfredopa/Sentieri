package com.apstudio.sentieri.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.apstudio.sentieri.BuildConfig

@Suppress("DEPRECATION")
object SecureTokenManager {
    private const val TAG = "SecureTokenManager"
    private const val PREFS_NAME = "secure_sentieri_prefs"
    private const val KEY_NAS_TOKEN = "nas_auth_token"

    private fun getEncryptedSharedPreferences(context: Context): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Errore nella creazione di EncryptedSharedPreferences", e)
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Recupera il token di autenticazione NAS crittografato.
     * Se non presente, tenta la migrazione da SharedPreferences standard o usa BuildConfig.NAS_AUTH_TOKEN come fallback.
     */
    fun getToken(context: Context): String {
        val encryptedPrefs = getEncryptedSharedPreferences(context)
        val savedToken = encryptedPrefs.getString(KEY_NAS_TOKEN, null)?.trim()
        if (!savedToken.isNullOrEmpty()) {
            return savedToken
        }

        // Migrazione da SharedPreferences non crittografato se esisteva in precedenza
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        val legacyToken = defaultPrefs.getString(KEY_NAS_TOKEN, null)?.trim()
            ?: defaultPrefs.getString("NAS_AUTH_TOKEN", null)?.trim()

        if (!legacyToken.isNullOrEmpty()) {
            saveToken(context, legacyToken)
            defaultPrefs.edit {
                remove(KEY_NAS_TOKEN)
                remove("NAS_AUTH_TOKEN")
            }
            Log.d(TAG, "Token migrato con successo in EncryptedSharedPreferences")
            return legacyToken
        }

        return BuildConfig.NAS_AUTH_TOKEN.trim()
    }

    /**
     * Salva il token in modo crittografato tramite EncryptedSharedPreferences.
     */
    fun saveToken(context: Context, token: String?) {
        val encryptedPrefs = getEncryptedSharedPreferences(context)
        val cleanToken = token?.trim().orEmpty()
        encryptedPrefs.edit {
            putString(KEY_NAS_TOKEN, cleanToken)
        }

        // Rimuove eventuali copie in chiaro rimaste nelle preferenze standard
        val defaultPrefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (defaultPrefs.contains(KEY_NAS_TOKEN) || defaultPrefs.contains("NAS_AUTH_TOKEN")) {
            defaultPrefs.edit {
                remove(KEY_NAS_TOKEN)
                remove("NAS_AUTH_TOKEN")
            }
        }
    }
}
