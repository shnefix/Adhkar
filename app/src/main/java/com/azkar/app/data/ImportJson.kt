package com.azkar.app.data

import org.json.JSONException
import org.json.JSONObject

/** Parses and validates an Azkar import file. Pure: no Android classes, no storage. */
object ImportJson {
    data class Item(val text: String, val count: Int)
    data class FolderIn(val name: String, val items: List<Item>)
    sealed class Parsed {
        data class Ok(val folders: List<FolderIn>) : Parsed()
        data class Error(val message: String) : Parsed()
    }

    fun parse(raw: String): Parsed {
        val s = raw.removePrefix("\uFEFF").trim()
        if (s.isEmpty()) return Parsed.Error("The file is empty.")
        val root = try { JSONObject(s) } catch (e: JSONException) {
            return Parsed.Error("This is not valid JSON. " + (e.message ?: "").take(90))
        }
        val arr = root.optJSONArray("folders") ?: return Parsed.Error("The file needs a top-level \"folders\" list.")
        if (arr.length() == 0) return Parsed.Error("The \"folders\" list is empty.")
        val out = ArrayList<FolderIn>()
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: return Parsed.Error("Folder ${i + 1}: expected an object.")
            val name = if (f.isNull("name")) "" else f.optString("name", "").trim()
            if (name.isEmpty()) return Parsed.Error("Folder ${i + 1}: missing \"name\".")
            val az = f.optJSONArray("azkar") ?: return Parsed.Error("Folder \"$name\": missing an \"azkar\" list.")
            val items = ArrayList<Item>()
            for (j in 0 until az.length()) {
                val o = az.optJSONObject(j) ?: return Parsed.Error("Folder \"$name\", zikr ${j + 1}: expected an object.")
                val text = if (o.isNull("text")) "" else o.optString("text", "")
                if (text.isBlank()) return Parsed.Error("Folder \"$name\", zikr ${j + 1}: missing \"text\".")
                val n = when (val c = o.opt("count")) {
                    null, JSONObject.NULL -> 1
                    is Int -> c
                    is Long -> if (c in 1..999) c.toInt() else -1
                    is Double -> if (c % 1.0 == 0.0) c.toInt() else -1
                    is String -> c.trim().toIntOrNull() ?: -1
                    else -> -1
                }
                if (n < 1 || n > 999) return Parsed.Error("Folder \"$name\", zikr ${j + 1}: \"count\" must be a whole number from 1 to 999.")
                items.add(Item(text, n))   // text kept exactly as written (diacritics intact)
            }
            out.add(FolderIn(name, items))
        }
        if (out.sumOf { it.items.size } == 0) return Parsed.Error("The file contains no azkar.")
        return Parsed.Ok(out)
    }
}