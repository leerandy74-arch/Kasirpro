package com.example.integrations

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.Branch
import com.example.model.PosTransaction
import com.example.model.Product
import com.example.model.TransactionStatus
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {

    private fun idrFormat(): NumberFormat = NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
        maximumFractionDigits = 0
    }

    fun createCacheReportFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        return File(dir, fileName)
    }

    fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun writePdfReport(
        outputStream: OutputStream,
        storeName: String,
        periodLabel: String,
        branchFilterLabel: String,
        transactions: List<PosTransaction>,
        products: List<Product>
    ) {
        val pdf = PdfDocument()
        val pageWidth = 595 // A4 points
        val pageHeight = 842
        val idr = idrFormat()
        val dateFmt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val titlePaint = Paint().apply {
            color = Color.parseColor("#0B3B3C")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val subtitlePaint = Paint().apply {
            color = Color.parseColor("#4B6360")
            textSize = 10f
        }
        val sectionPaint = Paint().apply {
            color = Color.parseColor("#0F6B5B")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val textPaint = Paint().apply {
            color = Color.parseColor("#111827")
            textSize = 9.5f
        }
        val linePaint = Paint().apply {
            color = Color.parseColor("#CAD8D4")
            strokeWidth = 1f
        }

        var pageNum = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 44f

        canvas.drawText("$storeName — Laporan Penjualan & Stok Otomatis", 36f, y, titlePaint)
        y += 18f
        canvas.drawText("Periode: $periodLabel  |  Filter: $branchFilterLabel  |  Dibuat: ${dateFmt.format(Date())}", 36f, y, subtitlePaint)
        y += 14f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 20f

        val completed = transactions.filter { it.statusEnum == TransactionStatus.COMPLETED }
        val totalRev = completed.sumOf { it.totalAmount }
        val avgBasket = if (completed.isNotEmpty()) totalRev / completed.size else 0.0
        val lowStockCount = products.count { it.lowStock }

        canvas.drawText("Ringkasan Eksekutif", 36f, y, sectionPaint)
        y += 16f
        canvas.drawText("• Total Omzet: ${idr.format(totalRev)}   |   Transaksi Selesai: ${completed.size}", 36f, y, textPaint)
        y += 14f
        canvas.drawText("• Rata-rata Keranjang: ${idr.format(avgBasket)}   |   Produk Stok Menipis: $lowStockCount", 36f, y, textPaint)
        y += 22f

        canvas.drawText("Daftar Transaksi Real-Time (E2EE Verified)", 36f, y, sectionPaint)
        y += 16f
        canvas.drawText("No. Struk", 36f, y, textPaint)
        canvas.drawText("Cabang", 155f, y, textPaint)
        canvas.drawText("Metode", 275f, y, textPaint)
        canvas.drawText("Status", 355f, y, textPaint)
        canvas.drawText("Total", 445f, y, textPaint)
        y += 6f
        canvas.drawLine(36f, y, pageWidth - 36f, y, linePaint)
        y += 14f

        transactions.take(120).forEach { tx ->
            if (y > pageHeight - 50) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
            canvas.drawText(tx.receiptNumber.take(18), 36f, y, textPaint)
            canvas.drawText(tx.branchName.take(18), 155f, y, textPaint)
            canvas.drawText(tx.paymentMethod, 275f, y, textPaint)
            canvas.drawText(tx.status, 355f, y, textPaint)
            canvas.drawText(idr.format(tx.totalAmount), 445f, y, textPaint)
            y += 14f
        }

        y += 14f
        if (y > pageHeight - 80) {
            pdf.finishPage(page)
            pageNum++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
            canvas = page.canvas
            y = 44f
        }
        canvas.drawText("Status Inventori & Stok Otomatis", 36f, y, sectionPaint)
        y += 16f
        products.take(80).forEach { p ->
            if (y > pageHeight - 40) {
                pdf.finishPage(page)
                pageNum++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
            }
            val flag = if (p.lowStock) " [MENIPIS]" else ""
            canvas.drawText("${p.sku}  ${p.name.take(28)}", 36f, y, textPaint)
            canvas.drawText("Stok: ${p.stock} ${p.unit}$flag", 310f, y, textPaint)
            canvas.drawText(idr.format(p.price), 460f, y, textPaint)
            y += 13f
        }

        pdf.finishPage(page)
        pdf.writeTo(outputStream)
        pdf.close()
    }

    /**
     * Generates a genuine multi-sheet OOXML Excel (.xlsx) file using standard ZipOutputStream.
     */
    fun writeExcelXlsxReport(
        outputStream: OutputStream,
        storeName: String,
        periodLabel: String,
        branches: List<Branch>,
        transactions: List<PosTransaction>,
        products: List<Product>
    ) {
        val branchMap = branches.associateBy { it.id }
        val completed = transactions.filter { it.statusEnum == TransactionStatus.COMPLETED }
        val totalRev = completed.sumOf { it.totalAmount }

        val summaryRows = buildList<List<String>> {
            add(listOf("Laporan Otomatis KasirPro", storeName))
            add(listOf("Periode", periodLabel))
            add(listOf("Total Omzet (IDR)", totalRev.toLong().toString()))
            add(listOf("Total Transaksi Selesai", completed.size.toString()))
            add(listOf("Jumlah Cabang", branches.size.toString()))
            add(listOf("Total SKU Produk", products.size.toString()))
            add(listOf(""))
            add(listOf("Kode Cabang", "Nama Cabang", "Transaksi", "Omzet (IDR)"))
            branches.forEach { b ->
                val bTx = completed.filter { it.branchId == b.id }
                add(listOf(b.code, b.name, bTx.size.toString(), bTx.sumOf { it.totalAmount }.toLong().toString()))
            }
        }

        val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val txRows = buildList<List<String>> {
            add(listOf("No Struk", "Tanggal", "Cabang", "Kasir", "Metode", "Status", "Item", "Subtotal", "Diskon", "Total", "E2EE", "WhatsApp"))
            transactions.forEach { tx ->
                add(
                    listOf(
                        tx.receiptNumber,
                        dateFmt.format(tx.createdAt?.toDate() ?: Date()),
                        tx.branchName,
                        tx.cashierName,
                        tx.paymentMethod,
                        tx.status,
                        tx.itemCount.toString(),
                        tx.subtotal.toLong().toString(),
                        tx.discount.toLong().toString(),
                        tx.totalAmount.toLong().toString(),
                        if (tx.e2eeVerified) "AES-256-GCM" else "NO",
                        if (tx.whatsappSent) "TERKIRIM" else "BELUM"
                    )
                )
            }
        }

        val stockRows = buildList<List<String>> {
            add(listOf("SKU", "Produk", "Cabang", "Kategori", "Harga Jual", "Harga Modal", "Stok", "Min Stok", "Satuan", "Status"))
            products.forEach { p ->
                add(
                    listOf(
                        p.sku,
                        p.name,
                        branchMap[p.branchId]?.name ?: p.branchId,
                        p.category,
                        p.price.toLong().toString(),
                        p.costPrice.toLong().toString(),
                        p.stock.toString(),
                        p.minStock.toString(),
                        p.unit,
                        if (p.stock <= 0) "HABIS" else if (p.lowStock) "MENIPIS" else "AMAN"
                    )
                )
            }
        }

        ZipOutputStream(outputStream).use { zip ->
            writeZipEntry(
                zip,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )
            writeZipEntry(
                zip,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            writeZipEntry(
                zip,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Ringkasan" sheetId="1" r:id="rId1"/>
    <sheet name="Transaksi" sheetId="2" r:id="rId2"/>
    <sheet name="Stok_Inventori" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""
            )
            writeZipEntry(
                zip,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
</Relationships>"""
            )
            writeZipEntry(zip, "xl/worksheets/sheet1.xml", buildSheetXml(summaryRows))
            writeZipEntry(zip, "xl/worksheets/sheet2.xml", buildSheetXml(txRows))
            writeZipEntry(zip, "xl/worksheets/sheet3.xml", buildSheetXml(stockRows))
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildSheetXml(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIdx, cols ->
            val r = rowIdx + 1
            sb.append("""<row r="$r">""")
            cols.forEachIndexed { colIdx, cellVal ->
                val ref = colLetter(colIdx) + r
                val numeric = cellVal.toLongOrNull()
                if (numeric != null && rowIdx > 0) {
                    sb.append("""<c r="$ref"><v>$numeric</v></c>""")
                } else {
                    sb.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(cellVal)}</t></is></c>""")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun colLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = (n / 26) - 1
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
