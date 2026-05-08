package com.openchat.app.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.extractor.XWPFWordExtractor
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

data class FileContent(
    val name: String,
    val content: String,
    val type: String
)

@Singleton
class FileProcessor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun processFile(uri: Uri): FileContent? = withContext(Dispatchers.IO) {
        try {
            var fileName = "unknown"
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) fileName = it.getString(nameIndex)
                }
            }

            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val size = inputStream.available()
            
            // Check max 10MB
            if (size > 10 * 1024 * 1024) {
                inputStream.close()
                throw Exception("File size exceeds 10MB limit")
            }

            val extension = fileName.substringAfterLast('.', "").lowercase()
            val textContent = when (extension) {
                "pdf" -> extractTextFromPdf(inputStream)
                "docx" -> extractTextFromDocx(inputStream)
                "csv" -> extractTextFromCsv(inputStream)
                "txt", "md", "json", "py", "js", "ts", "kt", "html", "css", "xml", "java" -> extractTextFromPlainText(inputStream)
                else -> throw Exception("Unsupported file type: $extension")
            }

            inputStream.close()
            FileContent(fileName, textContent, extension)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    private fun extractTextFromPdf(inputStream: InputStream): String {
        val reader = PdfReader(inputStream)
        val n = reader.numberOfPages
        val sb = StringBuilder()
        for (i in 1..n) {
            sb.append(PdfTextExtractor.getTextFromPage(reader, i))
            sb.append("\n")
        }
        reader.close()
        return sb.toString()
    }

    private fun extractTextFromDocx(inputStream: InputStream): String {
        val document = XWPFDocument(inputStream)
        val extractor = XWPFWordExtractor(document)
        val text = extractor.text
        extractor.close()
        document.close()
        return text
    }

    private fun extractTextFromCsv(inputStream: InputStream): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        var isFirstLine = true
        while (reader.readLine().also { line = it } != null) {
            val columns = line!!.split(",").map { it.trim() }
            sb.append("| ").append(columns.joinToString(" | ")).append(" |\n")
            if (isFirstLine) {
                sb.append("|").append(columns.joinToString("|") { "---" }).append("|\n")
                isFirstLine = false
            }
        }
        return sb.toString()
    }

    private fun extractTextFromPlainText(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }
}
