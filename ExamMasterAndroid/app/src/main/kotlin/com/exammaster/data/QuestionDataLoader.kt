package com.exammaster.data

import android.content.Context
import android.net.Uri
import com.exammaster.data.database.entities.Question
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.InputStreamReader

object QuestionDataLoader {
    
    fun loadQuestionsFromAssets(context: Context, bankId: String? = null): List<Question> {
        val questions = mutableListOf<Question>()

        try {
            val bank = if (bankId != null) {
                Bank.getBankById(context, bankId)
            } else {
                Bank.getActiveBank(context)
            }
            val csvFile = bank?.csv_file ?: "移动通信_题库导出.csv"
            val inputStream = context.assets.open(csvFile)
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            
            // Skip header line
            reader.readLine()
            
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { csvLine ->
                    val question = parseCSVLine(csvLine)
                    if (question != null) {
                        questions.add(question)
                    }
                }
            }
            
            reader.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return questions
    }

    fun loadQuestionsFromFile(filePath: String): List<Question> {
        val questions = mutableListOf<Question>()
        try {
            java.io.File(filePath).bufferedReader().use { reader ->
                reader.readLine() // Skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { csvLine ->
                        val question = parseCSVLine(csvLine)
                        if (question != null) questions.add(question)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return questions
    }

    fun loadQuestionsFromUri(context: Context, uri: Uri): List<Question> {
        val questions = mutableListOf<Question>()
        try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                reader.readLine() // Skip header
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    line?.let { csvLine ->
                        val question = parseCSVLine(csvLine)
                        if (question != null) questions.add(question)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return questions
    }

    fun copyCsvFromUri(context: Context, uri: Uri, targetName: String): Boolean {
        return try {
            val targetFile = java.io.File(context.filesDir, "banks/$targetName")
            targetFile.parentFile?.mkdirs()
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun validateCsvFromUri(context: Context, uri: Uri): Pair<Boolean, String> {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                val header = reader.readLine() ?: return Pair(false, "无法读取CSV表头")
                val headers = parseCSVFields(header).map { it.replace("\"", "") }
                val required = listOf("题号", "题干", "答案", "题型")
                val missing = required.filter { it !in headers }
                if (missing.isNotEmpty()) {
                    return Pair(false, "缺少必要列: ${missing.joinToString(", ")}")
                }
                var count = 0
                val validTypes = setOf("单选题", "多选题", "判断题", "")
                reader.readLine() // Read first data line to check format
                count++
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val fields = parseCSVFields(line!!)
                    if (fields.size > 9) {
                        val qtype = fields[9].replace("\"", "")
                        if (qtype.isNotEmpty() && qtype !in validTypes) {
                            return Pair(false, "第${count + 2}行: 无效题型'$qtype'")
                        }
                    }
                    count++
                }
                if (count == 0) return Pair(false, "CSV文件中没有题目数据")
                Pair(true, count.toString())
            } ?: Pair(false, "无法打开文件")
        } catch (e: Exception) {
            Pair(false, "读取失败: ${e.message}")
        }
    }

    private fun parseCSVLine(line: String): Question? {
        try {
            // Simple CSV parsing - handle quoted fields
            val fields = parseCSVFields(line)
            
            if (fields.size >= 8) {
                val id = fields[0].replace("\"", "")
                val stem = fields[1].replace("\"", "")
                val optionA = fields[2].replace("\"", "")
                val optionB = fields[3].replace("\"", "")
                val optionC = fields[4].replace("\"", "")
                val optionD = fields[5].replace("\"", "")
                val optionE = fields[6].replace("\"", "")
                val answer = fields[7].replace("\"", "")
                val difficulty = if (fields.size > 8) fields[8].replace("\"", "") else "无"
                val qtype = if (fields.size > 9) fields[9].replace("\"", "") else "多选题"
                
                // Build options map
                val options = mutableMapOf<String, String>()
                if (optionA.isNotEmpty()) options["A"] = optionA
                if (optionB.isNotEmpty()) options["B"] = optionB
                if (optionC.isNotEmpty()) options["C"] = optionC
                if (optionD.isNotEmpty()) options["D"] = optionD
                if (optionE.isNotEmpty()) options["E"] = optionE
                
                // 判断题：CSV 中选项列为空，注入"正确"/"错误"选项
                if (qtype == "判断题" && options.isEmpty()) {
                    options["正确"] = "正确"
                    options["错误"] = "错误"
                }

                val optionsJson = Gson().toJson(options)
                
                return Question(
                    id = id,
                    stem = stem,
                    answer = answer,
                    difficulty = difficulty,
                    qtype = qtype,
                    category = "未分类",
                    options = optionsJson,
                    createdAt = System.currentTimeMillis().toString()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return null
    }
    
    private fun parseCSVFields(line: String): List<String> {
        val fields = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val char = line[i]
            
            when {
                char == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped quote
                        current.append('"')
                        i++ // Skip next quote
                    } else {
                        // Toggle quote state
                        inQuotes = !inQuotes
                    }
                }
                char == ',' && !inQuotes -> {
                    // Field separator
                    fields.add(current.toString())
                    current = StringBuilder()
                }
                else -> {
                    current.append(char)
                }
            }
            i++
        }
        
        // Add the last field
        fields.add(current.toString())
        
        return fields
    }
    
    // Hardcoded questions as fallback
    fun getDefaultQuestions(): List<Question> {
        return listOf(
            Question(
                id = "1",
                stem = "毛泽东思想产生的社会历史条件有()。",
                answer = "ABCD",
                difficulty = "无",
                qtype = "多选题",
                category = "政治",
                options = Gson().toJson(mapOf(
                    "A" to "十月革命开辟的世界无产阶级革命的新时代",
                    "B" to "近代中国社会矛盾和革命运动的发展",
                    "C" to "工人阶级队伍壮大及工人运动的发展",
                    "D" to "中国共产党领导的中国新民主主义革命的伟大实践"
                )),
                createdAt = System.currentTimeMillis().toString()
            ),
            Question(
                id = "2",
                stem = "毛泽东思想的科学含义是()。",
                answer = "ABC",
                difficulty = "无",
                qtype = "多选题",
                category = "政治",
                options = Gson().toJson(mapOf(
                    "A" to "是马克思列宁主义在中国的运用和发展",
                    "B" to "是被实践证明了的关于中国革命的正确的理论原则和经验总结",
                    "C" to "是中国共产党集体智慧的结晶",
                    "D" to "建设中国特色社会主义的理论"
                )),
                createdAt = System.currentTimeMillis().toString()
            ),
            Question(
                id = "3",
                stem = "毛泽东思想的活的灵魂是()。",
                answer = "ABC",
                difficulty = "无",
                qtype = "多选题",
                category = "政治",
                options = Gson().toJson(mapOf(
                    "A" to "群众路线",
                    "B" to "独立自主",
                    "C" to "实事求是",
                    "D" to "理论与实际相结合"
                )),
                createdAt = System.currentTimeMillis().toString()
            )
        )
    }
}