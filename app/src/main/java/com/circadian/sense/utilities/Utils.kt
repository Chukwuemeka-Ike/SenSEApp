package com.circadian.sense.utilities

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * Takes care of application utilities like data storage and access
 */
class Utils(private val context: Context) {

    /**
     * Writes the data to filename
     * @param [data] - the JSONObject to save
     * @param [filename] to save the JSONObject to
     */
    fun writeData(data: String, filename: String) {
        try {
            val file = File(context.filesDir, filename)
            file.writeText(data)
//            Log.i("Sense Debug", "Wrote to $filename successfully")
        } catch (e: Exception) {
            Log.w(tag, "Write data failed: $e")
        }
    }

    /**
     * Loads a JSONObject from specified file
     * @param [filename] to load the JSONObject from
     * @return [jsonData] - JSONObject
     */
    fun loadJSONData(filename: String): JSONObject {
        var jsonData = JSONObject()

        // Create a JSONObject from the string in filename
        try {
            val file = File(context.filesDir, filename)
            jsonData = JSONObject(file.readText())
//            Log.i(tag, "Successfully loaded from $filename")
        } catch (e: Exception) {
            Log.w(tag, "$e")
        }

        return jsonData
    }

    // Companion object to hold these values for now
    companion object {
        private const val tag = "SenSE Debug"

    }

}