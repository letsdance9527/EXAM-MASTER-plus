package com.exammaster.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "questions")
data class Question(
    @PrimaryKey
    val id: String,
    val stem: String,
    val answer: String,
    val difficulty: String? = null,
    val qtype: String? = null,
    val category: String? = null,
    val options: String? = null, // JSON string
    val createdAt: String? = null
) {
    fun getOptionsMap(): Map<String, String> {
        val map = if (options.isNullOrEmpty()) {
            emptyMap<String, String>()
        } else {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                Gson().fromJson(options, type) ?: emptyMap<String, String>()
            } catch (e: Exception) {
                emptyMap<String, String>()
            }
        }
        // 判断题兜底：CSV 中选项列为空时注入"正确"/"错误"
        if (qtype == "判断题" && map.isEmpty()) {
            return mapOf("正确" to "正确", "错误" to "错误")
        }
        return map
    }

    fun getFormattedOptions(): List<Pair<String, String>> {
        return getOptionsMap().entries.sortedBy { it.key }.map { it.key to it.value }
    }
}