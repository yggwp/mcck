package com.example.automocklocation

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class AdbManager(private val context: Context) {

    var currentIp = "127.0.0.1"

    private val adbFile = File(context.applicationInfo.nativeLibraryDir, "libadb.so")

    fun extractAdb(): Boolean {
        // No longer needed because libadb.so is automatically extracted by PackageManager
        return adbFile.exists() && adbFile.canExecute()
    }

    fun pair(port: String, code: String): String {
        return runCommand(listOf(adbFile.absolutePath, "pair", "$currentIp:$port", code))
    }

    fun connect(port: String): String {
        return runCommand(listOf(adbFile.absolutePath, "connect", "$currentIp:$port"))
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
        val command = mutableListOf(adbFile.absolutePath, "-s", currentIp, "shell")
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
