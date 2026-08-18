package com.example.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.FileInputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.util.UUID

object ImgBBRepository {
    private const val API_KEY = "db801e55f83a34710dc37d103f1048a8"

    suspend fun uploadImage(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val boundary = "Boundary-" + UUID.randomUUID().toString()
            val url = URL("https://api.imgbb.com/1/upload?key=$API_KEY")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

            val outputStream: OutputStream = connection.outputStream
            val writer = PrintWriter(OutputStreamWriter(outputStream, "UTF-8"), true)

            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"image\"; filename=\"${file.name}\"\r\n")
            writer.append("Content-Type: application/octet-stream\r\n\r\n")
            writer.flush()

            val fileInputStream = FileInputStream(file)
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            outputStream.flush()
            fileInputStream.close()

            writer.append("\r\n")
            writer.append("--$boundary--\r\n")
            writer.flush()
            writer.close()

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                return@withContext json.getJSONObject("data").getString("url")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }
}
