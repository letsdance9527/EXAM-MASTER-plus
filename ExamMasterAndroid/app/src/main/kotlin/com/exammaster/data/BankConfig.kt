package com.exammaster.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class BankConfig(
    val current_bank: String,
    val banks: List<Bank>
)

data class Bank(
    val id: String,
    val name: String,
    val csv_file: String,
    val question_count: Int
) {
    companion object {
        fun loadFromAssets(context: Context): BankConfig {
            val json = context.assets.open("banks.json").bufferedReader().use { it.readText() }
            return Gson().fromJson(json, BankConfig::class.java)
        }

        fun getActiveBank(context: Context): Bank {
            val prefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            val activeId = prefs.getString("current_bank_id", null)
                ?: loadFromAssets(context).current_bank
            return getBankById(context, activeId) ?: loadFromAssets(context).banks.first()
        }

        fun switchBank(context: Context, bankId: String): Bank? {
            val bank = getBankById(context, bankId) ?: return null
            val prefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("current_bank_id", bankId).apply()
            return bank
        }

        fun getAllBanks(context: Context): List<Bank> {
            val assetBanks = loadFromAssets(context).banks.toMutableList()
            val prefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            val importedJson = prefs.getString("imported_banks", null)
            if (!importedJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<List<Bank>>() {}.type
                    val imported: List<Bank> = Gson().fromJson(importedJson, type)
                    assetBanks.addAll(imported)
                } catch (_: Exception) {}
            }
            return assetBanks
        }

        fun addImportedBank(context: Context, id: String, name: String, csvFileName: String, questionCount: Int) {
            val prefs = context.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
            val importedJson = prefs.getString("imported_banks", null)
            val imported = if (!importedJson.isNullOrEmpty()) {
                try {
                    val type = object : TypeToken<MutableList<Bank>>() {}.type
                    Gson().fromJson<MutableList<Bank>>(importedJson, type)
                } catch (_: Exception) { mutableListOf() }
            } else mutableListOf()
            // Remove existing with same ID
            imported.removeAll { it.id == id }
            imported.add(Bank(id, name, csvFileName, questionCount))
            prefs.edit().putString("imported_banks", Gson().toJson(imported)).apply()
        }

        fun getBankById(context: Context, bankId: String): Bank? {
            return getAllBanks(context).find { it.id == bankId }
        }
    }
}
