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

        fun getBankById(context: Context, bankId: String): Bank? {
            val config = loadFromAssets(context)
            return config.banks.find { it.id == bankId }
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
    }
}
