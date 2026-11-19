package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.kasirDataStore: DataStore<Preferences> by preferencesDataStore(name = "kasirpro_prefs")

data class LocalAppPreferences(
    val language: AppLanguage = AppLanguage.INDONESIAN,
    val activeBranchId: String = "",
    val printerMac: String = "",
    val printerName: String = "",
    val loggedInStaffId: String = "",
    val cachedVaultPassphrase: String? = null
)

class PreferencesManager(private val context: Context) {
    private val keyLanguage = stringPreferencesKey("language_code")
    private val keyActiveBranch = stringPreferencesKey("active_branch_id")
    private val keyPrinterMac = stringPreferencesKey("printer_mac")
    private val keyPrinterName = stringPreferencesKey("printer_name")
    private val keyLoggedInStaffId = stringPreferencesKey("logged_in_staff_id")
    private val keyVaultCipher = stringPreferencesKey("vault_cipher")
    private val keyVaultIv = stringPreferencesKey("vault_iv")

    val preferencesFlow: Flow<LocalAppPreferences> = context.kasirDataStore.data.map { prefs ->
        val cipher = prefs[keyVaultCipher]
        val iv = prefs[keyVaultIv]
        val decryptedPass = if (!cipher.isNullOrBlank() && !iv.isNullOrBlank()) {
            CryptoManager.decryptWithDeviceKeystore(cipher, iv)
        } else {
            null
        }
        LocalAppPreferences(
            language = AppLanguage.fromCode(prefs[keyLanguage]),
            activeBranchId = prefs[keyActiveBranch].orEmpty(),
            printerMac = prefs[keyPrinterMac].orEmpty(),
            printerName = prefs[keyPrinterName].orEmpty(),
            loggedInStaffId = prefs[keyLoggedInStaffId].orEmpty(),
            cachedVaultPassphrase = decryptedPass
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.kasirDataStore.edit { it[keyLanguage] = language.code }
    }

    suspend fun setActiveBranchId(branchId: String) {
        context.kasirDataStore.edit { it[keyActiveBranch] = branchId }
    }

    suspend fun setPrinter(mac: String, name: String) {
        context.kasirDataStore.edit {
            it[keyPrinterMac] = mac
            it[keyPrinterName] = name
        }
    }

    suspend fun setLoggedInStaffId(staffId: String?) {
        context.kasirDataStore.edit {
            if (staffId.isNullOrBlank()) {
                it.remove(keyLoggedInStaffId)
            } else {
                it[keyLoggedInStaffId] = staffId
            }
        }
    }

    suspend fun saveVaultPassphrase(passphrase: String) {
        val blob = CryptoManager.encryptWithDeviceKeystore(passphrase) ?: return
        context.kasirDataStore.edit {
            it[keyVaultCipher] = blob.ciphertextBase64
            it[keyVaultIv] = blob.ivBase64
        }
    }

    suspend fun clearVaultPassphrase() {
        context.kasirDataStore.edit {
            it.remove(keyVaultCipher)
            it.remove(keyVaultIv)
        }
    }
}
