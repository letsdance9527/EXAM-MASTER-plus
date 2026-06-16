package com.exammaster.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportBankDialog(
    onDismiss: () -> Unit,
    onImport: (name: String, uri: Uri) -> String?  // null = success, String = error message
) {
    var bankName by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isImporting by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedUri = it
            selectedFileName = it.lastPathSegment ?: "未知文件"
            errorMessage = null
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = { Text("导入题库", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // Format guide card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "CSV 格式要求",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text("编码: UTF-8", fontSize = 13.sp)
                        Text("必要列: 题号, 题干, 答案, 题型", fontSize = 13.sp)
                        Text("可选列: A, B, C, D, E, 难度, 类别", fontSize = 13.sp)
                        Text("题型: 单选题 / 多选题 / 判断题", fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Bank name input
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it; errorMessage = null },
                    label = { Text("题库名称") },
                    placeholder = { Text("例如: 2025通信原理题库") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isImporting
                )

                Spacer(Modifier.height(12.dp))

                // File picker
                OutlinedButton(
                    onClick = { filePicker.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isImporting
                ) {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedFileName.isEmpty()) "选择CSV文件" else selectedFileName)
                }

                // Error display
                if (errorMessage != null) {
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                errorMessage!!,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                if (isImporting) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = selectedUri
                    if (bankName.isBlank()) {
                        errorMessage = "请输入题库名称"
                    } else if (uri == null) {
                        errorMessage = "请选择CSV文件"
                    } else {
                        isImporting = true
                        errorMessage = null
                        val result = onImport(bankName, uri)
                        if (result != null) {
                            errorMessage = "CSV格式错误: $result"
                            isImporting = false
                        } else {
                            onDismiss()
                        }
                    }
                },
                enabled = !isImporting && bankName.isNotBlank() && selectedUri != null
            ) { Text("导入") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting
            ) { Text("取消") }
        }
    )
}
