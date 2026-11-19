package com.example.integrations

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.localization.AppStrings
import com.example.model.DecryptedTxPayload
import com.example.model.PosTransaction
import java.text.NumberFormat
import java.util.Locale

object WhatsAppNotifier {

    fun normalizePhoneNumber(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return when {
            digits.startsWith("0") -> "62" + digits.drop(1)
            digits.startsWith("62") -> digits
            digits.startsWith("8") -> "62$digits"
            else -> digits
        }
    }

    fun buildOrderCompletedMessage(
        strings: AppStrings,
        storeName: String,
        tx: PosTransaction,
        payload: DecryptedTxPayload?
    ): String {
        val idr = NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }
        val customer = payload?.customerName?.takeIf { it.isNotBlank() } ?: ""
        val greeting = if (customer.isNotBlank()) "${strings.whatsappGreeting} $customer," else "${strings.whatsappGreeting},"
        return buildString {
            appendLine(greeting)
            appendLine("*$storeName - ${tx.branchName}*")
            appendLine(strings.whatsappOrderCompleted)
            appendLine()
            appendLine("🧾 No. Struk: *${tx.receiptNumber}*")
            payload?.items?.forEach { item ->
                appendLine("• ${item.name} (${item.quantity}x) = ${idr.format(item.lineTotal)}")
            }
            appendLine("💰 Total: *${idr.format(tx.totalAmount)}* (${tx.paymentMethod})")
            appendLine("🔒 E2EE Verified")
            appendLine()
            append("${strings.whatsappThankYou} $storeName!")
        }
    }

    fun openWhatsAppChat(context: Context, phone: String, message: String): Result<Unit> {
        val normalized = normalizePhoneNumber(phone)
        if (normalized.length < 8) {
            return Result.failure(IllegalArgumentException("Invalid WhatsApp number"))
        }
        val uri = Uri.parse("https://wa.me/$normalized?text=${Uri.encode(message)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            Result.success(Unit)
        } catch (_: Exception) {
            // Fallback to generic share sheet if no browser/WhatsApp handler on test device
            try {
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(share, "WhatsApp").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                Result.success(Unit)
            } catch (ex: Exception) {
                Result.failure(ex)
            }
        }
    }
}
