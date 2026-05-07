package com.example.automocklocation

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AdbManager(private val context: Context) {

    private val adbFile = File(context.filesDir, "adb")

    fun extractAdb(): Boolean {
        return try {
            if (!adbFile.exists() || adbFile.length() == 0L) {
                context.assets.open("arm64-v8a/adb").use { inputStream ->
                    FileOutputStream(adbFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                adbFile.setExecutable(true)
            }
            true
        } catch (e: Exception) {
            Log.e("AdbManager", "Failed to extract ADB", e)
            false
        }
    }

    fun pair(port: String, code: String): String {
        return runCommand(listOf(adbFile.absolutePath, "pair", "127.0.0.1:$port", code))
    }

    fun connect(port: String): String {
        return runCommand(listOf(adbFile.absolutePath, "connect", "127.0.0.1:$port"))
    }

    fun grantMockLocation(packageName: String): String {
        return shellCommand(listOf("appops", "set", packageName, "android:mock_location", "allow"))
    }

    fun disconnect(): String {
        return runCommand(listOf(adbFile.absolutePath, "disconnect"))
    }

    fun killServer(): String {
        return runCommand(listOf(adbFile.absolutePath, "kill-server"))
    }

    fun shellCommand(args: List<String>): String {
        val command = mutableListOf(adbFile.absolutePath, "-s", "127.0.0.1", "shell")
        command.addAll(args)
        return runCommand(command)
    }

    private fun runCommand(command: List<String>): String {
        return try {
            val process = ProcessBuilder(command)
                .directory(context.filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = context.filesDir.absolutePath
                    environment()["TMPDIR"] = context.cacheDir.absolutePath
                }
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            Log.e("AdbManager", "Command execution failed", e)
            "Error: ${e.message}"
        }
    }
}
