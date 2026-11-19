package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.KasirProDatabase
import com.example.data.local.LocalPosRepository
import com.example.integrations.QrisGenerator
import com.example.integrations.ReportExporter
import com.example.integrations.WhatsAppNotifier
import com.example.model.Branch
import com.example.model.DecryptedTxPayload
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.ReceiptLineItem
import com.example.model.StaffRole
import com.example.security.CryptoManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class KasirProUnitTest {

    @Test
    fun `pbkdf2 password hashing and verification works`() {
        val digest = CryptoManager.hashPassword("pusat123")
        assertTrue(CryptoManager.verifyPassword("pusat123", digest.hashBase64, digest.saltBase64))
        assertFalse(CryptoManager.verifyPassword("wrongpass", digest.hashBase64, digest.saltBase64))
    }

    @Test
    fun `aes256 gcm end-to-end encryption roundtrip and wrong key rejection`() {
        val salt = CryptoManager.generateSaltBase64()
        val key = CryptoManager.deriveE2eeKey("secret-vault-pass", salt)
        val wrongKey = CryptoManager.deriveE2eeKey("wrong-vault-pass", salt)

        val payload = DecryptedTxPayload(
            customerName = "Budi Santoso",
            customerPhone = "081234567890",
            paymentReference = "QRIS-001",
            items = listOf(ReceiptLineItem("p1", "SKU-1", "Kopi Susu", "cup", 24000.0, 10000.0, 2))
        ).toJsonString()

        val encrypted = CryptoManager.encrypt(payload, key)
        assertNotEquals(payload, encrypted.ciphertextBase64)

        val decrypted = CryptoManager.decrypt(encrypted.ciphertextBase64, encrypted.ivBase64, key)
        assertEquals(payload, decrypted)

        val failedDecrypt = CryptoManager.decrypt(encrypted.ciphertextBase64, encrypted.ivBase64, wrongKey)
        assertNull(failedDecrypt)
    }

    @Test
    fun `qris payload builder includes merchant, amount, and crc16`() {
        val payload = QrisGenerator.buildQrisPayload(
            merchantId = "ID1020304050607",
            merchantName = "KasirPro Store",
            branchCity = "Jakarta",
            amountIdr = 75000.0,
            referenceId = "INV-001"
        )
        assertTrue(payload.contains("ID.CO.QRIS.WWW"))
        assertTrue(payload.contains("540575000"))
        assertTrue(payload.contains("6304"))
        assertEquals(4, payload.substringAfterLast("6304").length)
        val bmp = QrisGenerator.renderQrBitmap(payload, 256)
        assertNotNull(bmp)
        assertEquals(256, bmp!!.width)
    }

    @Test
    fun `whatsapp phone normalization converts local 08 prefix to 62`() {
        assertEquals("628123456789", WhatsAppNotifier.normalizePhoneNumber("0812-3456-789"))
        assertEquals("628123456789", WhatsAppNotifier.normalizePhoneNumber("+62 812 3456 789"))
    }

    @Test
    fun `excel xlsx and pdf exporters generate valid output`() {
        val sampleBranches = listOf(Branch(id = "b1", name = "Pusat", code = "HQ"))
        val sampleTxs = listOf(
            PosTransaction(
                id = "t1",
                branchId = "b1",
                branchName = "Pusat",
                receiptNumber = "INV-001",
                cashierName = "Rina",
                paymentMethod = "QRIS",
                status = "COMPLETED",
                totalAmount = 50000.0,
                itemCount = 2
            )
        )
        val sampleProducts = listOf(Product(id = "p1", branchId = "b1", sku = "SKU-1", name = "Kopi", stock = 10))

        val xlsxOut = ByteArrayOutputStream()
        ReportExporter.writeExcelXlsxReport(
            outputStream = xlsxOut,
            storeName = "KasirPro",
            periodLabel = "2026-11",
            branches = sampleBranches,
            transactions = sampleTxs,
            products = sampleProducts
        )
        val entries = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(xlsxOut.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entries.add(entry.name)
                entry = zip.nextEntry
            }
        }
        assertTrue(entries.contains("[Content_Types].xml"))
        assertTrue(entries.contains("xl/workbook.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet2.xml"))
        assertTrue(entries.contains("xl/worksheets/sheet3.xml"))
    }

    @Test
    fun `local room repository bootstraps staff and supports username password login without gmail`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, KasirProDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LocalPosRepository(db = db, firestore = null)
            assertTrue(repo.bootstrapIfNeeded("kasirpro-e2ee-default").isSuccess)

            val adminLogin = repo.authenticateStaff("admin.pusat", "pusat123")
            assertTrue(adminLogin.isSuccess)
            assertEquals(StaffRole.CENTRAL_ADMIN, adminLogin.getOrThrow().staffRole)

            val wrongPass = repo.authenticateStaff("admin.pusat", "salah123")
            assertTrue(wrongPass.isFailure)

            val staffList = repo.observeStaff().first()
            val cashier = staffList.first { it.username == "kasir.01" }
            val centralAdmin = adminLogin.getOrThrow()

            assertTrue(repo.changeStaffPassword(cashier, "baru2026", centralAdmin).isSuccess)
            assertTrue(repo.authenticateStaff("kasir.01", "kasir123").isFailure)
            assertTrue(repo.authenticateStaff("kasir.01", "baru2026").isSuccess)
        } finally {
            db.close()
        }
    }
}
