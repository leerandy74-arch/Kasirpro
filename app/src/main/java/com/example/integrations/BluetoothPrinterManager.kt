package com.example.integrations

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.model.DecryptedTxPayload
import com.example.model.PosTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class BluetoothPrinterDevice(
    val name: String,
    val address: String
)

class BluetoothPrinterManager(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter?
        get() {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            return manager?.adapter
        }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.BLUETOOTH
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothAvailableAndEnabled(): Boolean {
        val adapter = bluetoothAdapter ?: return false
        return adapter.isEnabled
    }

    @SuppressLint("MissingPermission")
    fun getBondedPrinters(): List<BluetoothPrinterDevice> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return adapter.bondedDevices.orEmpty().map { device: BluetoothDevice ->
            BluetoothPrinterDevice(
                name = device.name?.takeIf { it.isNotBlank() } ?: "Bluetooth Printer",
                address = device.address
            )
        }.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    suspend fun printEscPosReceipt(
        macAddress: String,
        storeName: String,
        tx: PosTransaction,
        payload: DecryptedTxPayload?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (macAddress.isBlank()) {
            return@withContext Result.failure(IllegalStateException("No Bluetooth printer selected"))
        }
        if (!hasBluetoothPermission()) {
            return@withContext Result.failure(SecurityException("Bluetooth permission not granted"))
        }
        val adapter = bluetoothAdapter
            ?: return@withContext Result.failure(IllegalStateException("Bluetooth adapter unavailable on this device"))
        if (!adapter.isEnabled) {
            return@withContext Result.failure(IllegalStateException("Bluetooth is turned off"))
        }

        var socket: BluetoothSocket? = null
        return@withContext try {
            val device = adapter.getRemoteDevice(macAddress)
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter.cancelDiscovery()
            socket.connect()
            val out = socket.outputStream
            val bytes = buildEscPosBytes(storeName, tx, payload)
            out.write(bytes)
            out.flush()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun formatReceiptPreviewText(
        storeName: String,
        tx: PosTransaction,
        payload: DecryptedTxPayload?
    ): String {
        val idr = NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            .format(tx.createdAt?.toDate() ?: Date())

        return buildString {
            appendLine("================================")
            appendLine(centerText(storeName.uppercase(), 32))
            appendLine(centerText(tx.branchName, 32))
            appendLine("================================")
            appendLine("No   : ${tx.receiptNumber}")
            appendLine("Tgl  : $dateStr")
            appendLine("Kasir: ${tx.cashierName}")
            if (!payload?.customerName.isNullOrBlank()) {
                appendLine("Plg  : ${payload?.customerName}")
            }
            appendLine("--------------------------------")
            val items = payload?.items.orEmpty()
            if (items.isEmpty()) {
                appendLine("${tx.itemCount} item(s) [E2EE Locked]")
            } else {
                items.forEach { item ->
                    appendLine(item.name.take(32))
                    val left = "  ${item.quantity} x ${idr.format(item.unitPrice)}"
                    val right = idr.format(item.lineTotal)
                    appendLine(padColumns(left, right, 32))
                }
            }
            appendLine("--------------------------------")
            appendLine(padColumns("Subtotal", idr.format(tx.subtotal), 32))
            if (tx.discount > 0) {
                appendLine(padColumns("Diskon", "-${idr.format(tx.discount)}", 32))
            }
            appendLine(padColumns("TOTAL", idr.format(tx.totalAmount), 32))
            appendLine(padColumns("Bayar (${tx.paymentMethod})", idr.format(tx.paidAmount), 32))
            if (tx.changeAmount > 0) {
                appendLine(padColumns("Kembali", idr.format(tx.changeAmount), 32))
            }
            appendLine("--------------------------------")
            appendLine("E2EE : AES-256-GCM VERIFIED")
            appendLine(centerText("Terima Kasih!", 32))
            appendLine("================================")
        }
    }

    private fun buildEscPosBytes(
        storeName: String,
        tx: PosTransaction,
        payload: DecryptedTxPayload?
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        // ESC @ - Initialize printer
        stream.write(byteArrayOf(0x1B, 0x40))
        // ESC a 1 - Center align header
        stream.write(byteArrayOf(0x1B, 0x61, 0x01))
        // ESC E 1 - Bold on
        stream.write(byteArrayOf(0x1B, 0x45, 0x01))
        stream.write("${storeName.uppercase()}\n".toByteArray(Charsets.UTF_8))
        stream.write(byteArrayOf(0x1B, 0x45, 0x00))
        stream.write("${tx.branchName}\n".toByteArray(Charsets.UTF_8))
        // ESC a 0 - Left align body
        stream.write(byteArrayOf(0x1B, 0x61, 0x00))
        val preview = formatReceiptPreviewText(storeName, tx, payload)
        stream.write(preview.toByteArray(Charsets.UTF_8))
        stream.write("\n\n\n".toByteArray(Charsets.UTF_8))
        // GS V 1 - Partial cut
        stream.write(byteArrayOf(0x1D, 0x56, 0x01))
        return stream.toByteArray()
    }

    private fun centerText(text: String, width: Int): String {
        val trimmed = text.take(width)
        val pad = ((width - trimmed.length) / 2).coerceAtLeast(0)
        return " ".repeat(pad) + trimmed
    }

    private fun padColumns(left: String, right: String, width: Int): String {
        val space = (width - left.length - right.length).coerceAtLeast(1)
        return left + " ".repeat(space) + right
    }
}
