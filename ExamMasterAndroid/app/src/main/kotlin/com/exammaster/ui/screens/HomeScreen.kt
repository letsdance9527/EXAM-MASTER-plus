package com.exammaster.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.exammaster.ui.navigation.Screen
import com.exammaster.ui.viewmodel.ExamViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: ExamViewModel
) {
    val statistics by viewModel.statistics.collectAsState()
    val lastBrowsedId by viewModel.lastBrowsedQuestionId.collectAsState()
    val currentBank by viewModel.currentBank.collectAsState()
    val availableBanks by viewModel.availableBanks.collectAsState()
    var showBankDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // App Title
        Text(
            text = "Exam-Master",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        )
        
        // Current Bank Badge
        TextButton(
            onClick = { showBankDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("当前题库: ${currentBank.name}", fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = "切换题库",
                modifier = Modifier.size(16.dp)
            )
        }

        // Bank Switch Dialog
        if (showBankDialog) {
            AlertDialog(
                onDismissRequest = { showBankDialog = false },
                title = { Text("切换题库") },
                text = {
                    Column {
                        Text("选择后将清除所有答题记录并重新导入题库。")
                        Spacer(Modifier.height(12.dp))
                        availableBanks.forEach { bank ->
                            val isActive = bank.id == currentBank.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isActive,
                                    onClick = {
                                        if (!isActive) {
                                            viewModel.switchBank(bank.id)
                                            showBankDialog = false
                                        }
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(bank.name, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal)
                                    Text("${bank.question_count} 道题目", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBankDialog = false }) {
                        Text("关闭")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showBankDialog = false
                        showImportDialog = true
                    }) { Text("导入题库") }
                }
            )
        }

        // Import Bank Dialog
        if (showImportDialog) {
            ImportBankDialog(
                onDismiss = { showImportDialog = false },
                onImport = { name, uri ->
                    viewModel.importBank(name, uri)
                }
            )
        }

        // Statistics Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "学习统计",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatisticItem("总题数", statistics.totalQuestions.toString())
                    StatisticItem("已答题", statistics.answeredQuestions.toString())
                    StatisticItem("正确率", "%.1f%%".format(statistics.accuracy))
                }
            }
        }
        
        // Sequential position badge
        if (lastBrowsedId != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "顺序答题将从第 $lastBrowsedId 题继续",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Mode Selection Buttons
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ModeButton(
                    title = "浏览题目",
                    subtitle = "查看所有题目内容",
                    icon = Icons.Default.LibraryBooks,
                    onClick = {
                        navController.navigate(Screen.Browse.route)
                    }
                )
            }

            item {
                ModeButton(
                    title = "顺序答题",
                    subtitle = "从上次浏览位置开始顺序答题",
                    icon = Icons.Default.List,
                    onClick = {
                        viewModel.startSequentialFromLastBrowsed()
                        navController.navigate(Screen.Question.route)
                    }
                )
            }

            item {
                ModeButton(
                    title = "随机答题",
                    subtitle = "从题库中随机抽取题目练习",
                    icon = Icons.Default.Shuffle,
                    onClick = {
                        viewModel.loadRandomQuestion()
                        navController.navigate(Screen.Question.route)
                    }
                )
            }

            item {
                ModeButton(
                    title = "错题练习",
                    subtitle = "重新练习答错的题目",
                    icon = Icons.Default.Report,
                    onClick = {
                        viewModel.startWrongQuestionsMode()
                        navController.navigate(Screen.Question.route)
                    }
                )
            }
            
            item {
                ModeButton(
                    title = "收藏题目",
                    subtitle = "查看和练习收藏的题目",
                    icon = Icons.Default.Favorite,
                    onClick = {
                        navController.navigate(Screen.Favorites.route)
                    }
                )
            }
            
            item {
                ModeButton(
                    title = "搜索题目",
                    subtitle = "根据关键词搜索题目",
                    icon = Icons.Default.Search,
                    onClick = {
                        navController.navigate(Screen.Search.route)
                    }
                )
            }
            
            item {
                ModeButton(
                    title = "答题历史",
                    subtitle = "查看答题记录和统计",
                    icon = Icons.Default.History,
                    onClick = {
                        navController.navigate(Screen.History.route)
                    }
                )
            }
        }
    }
}

@Composable
private fun StatisticItem(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Go",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}