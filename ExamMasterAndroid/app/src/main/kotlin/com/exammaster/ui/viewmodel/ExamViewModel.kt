package com.exammaster.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.exammaster.data.Bank
import com.exammaster.data.QuestionDataLoader
import com.exammaster.data.repository.ExamRepository
import com.exammaster.data.database.entities.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ExamViewModel(
    application: Application,
    private val repository: ExamRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("exam_prefs", Context.MODE_PRIVATE)
    
    private val _currentQuestion = MutableStateFlow<Question?>(null)
    val currentQuestion: StateFlow<Question?> = _currentQuestion.asStateFlow()
    
    private val _selectedAnswers = MutableStateFlow<Set<String>>(emptySet())
    val selectedAnswers: StateFlow<Set<String>> = _selectedAnswers.asStateFlow()
    
    private val _showResult = MutableStateFlow(false)
    val showResult: StateFlow<Boolean> = _showResult.asStateFlow()
    
    private val _isAnswerCorrect = MutableStateFlow(false)
    val isAnswerCorrect: StateFlow<Boolean> = _isAnswerCorrect.asStateFlow()
    
    private val _statistics = MutableStateFlow(Statistics())
    val statistics: StateFlow<Statistics> = _statistics.asStateFlow()
    
    private val _currentMode = MutableStateFlow(QuizMode.RANDOM)
    val currentMode: StateFlow<QuizMode> = _currentMode.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 持久化：从 SharedPreferences 恢复上次浏览位置
    private val _lastBrowsedQuestionId = MutableStateFlow<String?>(
        prefs.getString("current_seq_qid", null)
    )
    val lastBrowsedQuestionId: StateFlow<String?> = _lastBrowsedQuestionId.asStateFlow()

    /** 保存当前浏览位置到 SharedPreferences */
    private fun savePosition(qid: String?) {
        _lastBrowsedQuestionId.value = qid
        prefs.edit().putString("current_seq_qid", qid).apply()
    }

    // 浏览历史 — 支持回看本次已答的所有题目
    private val questionHistory = mutableListOf<String>()
    // historyIndex: -1 = 非历史模式(当前题), 0..size-1 = 浏览历史中的位置
    private var historyIndex = -1
    private val _hasPreviousQuestion = MutableStateFlow(false)
    val hasPreviousQuestion: StateFlow<Boolean> = _hasPreviousQuestion.asStateFlow()
    private val _hasNextQuestion = MutableStateFlow(false)
    val hasNextQuestion: StateFlow<Boolean> = _hasNextQuestion.asStateFlow()
    private val _reviewMode = MutableStateFlow(false)
    val reviewMode: StateFlow<Boolean> = _reviewMode.asStateFlow()

    private fun pushToHistory(qid: String) {
        // 如果在浏览历史中，放弃当前位置之后的历史
        if (historyIndex >= 0 && historyIndex < questionHistory.size - 1) {
            while (questionHistory.size > historyIndex + 1) {
                questionHistory.removeAt(questionHistory.lastIndex)
            }
        }
        historyIndex = -1
        if (questionHistory.isEmpty() || questionHistory.last() != qid) {
            questionHistory.add(qid)
            if (questionHistory.size > 200) {
                questionHistory.removeAt(0)
            }
        }
        updateHistoryButtons()
    }

    private fun updateHistoryButtons() {
        if (historyIndex < 0) {
            // 在当前题：有上一题当历史栈 >= 2
            _hasPreviousQuestion.value = questionHistory.size >= 2
            _hasNextQuestion.value = false
        } else {
            _hasPreviousQuestion.value = historyIndex > 0
            _hasNextQuestion.value = historyIndex < questionHistory.size - 1
        }
    }

    fun goToPreviousQuestion() {
        viewModelScope.launch {
            val targetIdx = if (historyIndex < 0) {
                // 从当前题进入历史：跳到倒数第二个（倒数第一个是当前题）
                if (questionHistory.size < 2) return@launch
                questionHistory.size - 2
            } else {
                if (historyIndex <= 0) return@launch
                historyIndex - 1
            }
            historyIndex = targetIdx
            val qid = questionHistory[historyIndex]
            val question = repository.getQuestionById(qid)
            if (question != null) {
                _currentQuestion.value = question
                _reviewMode.value = true
                _showResult.value = false
                _selectedAnswers.value = emptySet()
            }
            updateHistoryButtons()
        }
    }

    fun goToNextQuestion() {
        viewModelScope.launch {
            if (historyIndex < 0) return@launch
            val targetIdx = historyIndex + 1
            if (targetIdx >= questionHistory.size) return@launch
            historyIndex = targetIdx
            val qid = questionHistory[historyIndex]
            val question = repository.getQuestionById(qid)
            if (question != null) {
                _currentQuestion.value = question
                _reviewMode.value = true
                _showResult.value = false
                _selectedAnswers.value = emptySet()
            }
            // 回到了最新题？退出历史模式
            if (historyIndex >= questionHistory.size - 1) {
                exitReviewMode()
            }
            updateHistoryButtons()
        }
    }

    fun exitReviewMode() {
        historyIndex = -1
        _reviewMode.value = false
        _selectedAnswers.value = emptySet()
        _showResult.value = false
        updateHistoryButtons()
    }

    // 题库管理
    private val _currentBank = MutableStateFlow(Bank.getActiveBank(getApplication()))
    val currentBank: StateFlow<Bank> = _currentBank.asStateFlow()

    private val _availableBanks = MutableStateFlow(
        Bank.getAllBanks(getApplication())
    )
    val availableBanks: StateFlow<List<Bank>> = _availableBanks.asStateFlow()

    fun importBank(name: String, uri: android.net.Uri): String? {
        // Returns null on success, error message string on failure
        val app = getApplication<Application>()
        val context: android.content.Context = app
        // Validate CSV format
        val result = QuestionDataLoader.validateCsvFromUri(context, uri)
        val valid = result.first
        val msg = result.second
        if (!valid) return msg

        val questionCount = msg.toIntOrNull() ?: return "无法统计题目数量"
        val bankId = "imported_${System.currentTimeMillis()}"

        // Copy to internal storage
        val csvFileName = "$bankId.csv"
        if (!QuestionDataLoader.copyCsvFromUri(context, uri, csvFileName)) {
            return "保存文件失败"
        }

        // Register
        Bank.addImportedBank(context, bankId, name, csvFileName, questionCount)

        // Switch to new bank
        viewModelScope.launch {
            Bank.switchBank(context, bankId)
            _currentBank.value = Bank.getBankById(context, bankId) ?: Bank.getActiveBank(context)
            _availableBanks.value = Bank.getAllBanks(context)
            // Re-import questions
            _isLoading.value = true
            repository.clearAllUserData()
            repository.deleteAllQuestions()
            val targetPath = java.io.File(context.filesDir, "banks/$csvFileName").absolutePath
            val questions = QuestionDataLoader.loadQuestionsFromFile(targetPath)
            if (questions.isNotEmpty()) {
                repository.insertQuestions(questions)
            }
            savePosition(null)
            _currentQuestion.value = null
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            loadStatistics()
            _isLoading.value = false
        }
        return null
    }

    fun refreshAvailableBanks() {
        _availableBanks.value = Bank.getAllBanks(getApplication())
    }

    fun switchBank(bankId: String) {
        viewModelScope.launch {
            val bank = Bank.switchBank(getApplication(), bankId) ?: return@launch
            _currentBank.value = bank
            _isLoading.value = true
            // Clear DB and re-import from new bank
            repository.clearAllUserData()
            repository.deleteAllQuestions()
            val questions = QuestionDataLoader.loadQuestionsFromAssets(getApplication(), bankId)
            if (questions.isNotEmpty()) {
                repository.insertQuestions(questions)
            }
            savePosition(null)
            _currentQuestion.value = null
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            loadStatistics()
            _isLoading.value = false
        }
    }
    
    val allQuestions = repository.getAllQuestions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val allHistory = repository.getAllHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    val allFavorites = repository.getAllFavorites().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    init {
        loadStatistics()
    }
    
    fun loadRandomQuestion() {
        viewModelScope.launch {
            _isLoading.value = true
            val question = repository.getRandomUncompletedQuestion() ?: repository.getRandomQuestion()
            _currentQuestion.value = question
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            _currentMode.value = QuizMode.RANDOM
            question?.let { savePosition(it.id); pushToHistory(it.id) }
            _isLoading.value = false
        }
    }
    
    fun loadSequentialQuestion() {
        viewModelScope.launch {
            _isLoading.value = true
            val currentId = _currentQuestion.value?.id
            val question = repository.getNextSequentialQuestion(currentId)
            _currentQuestion.value = question
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            _currentMode.value = QuizMode.SEQUENTIAL
            question?.let { savePosition(it.id); pushToHistory(it.id) }
            _isLoading.value = false
        }
    }
    
    fun startSequentialFromLastBrowsed() {
        viewModelScope.launch {
            _isLoading.value = true
            val startFromId = _lastBrowsedQuestionId.value
            val question = repository.getSequentialQuestionStartingFrom(startFromId)
            _currentQuestion.value = question
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            _currentMode.value = QuizMode.SEQUENTIAL
            question?.let { savePosition(it.id); pushToHistory(it.id) }
            _isLoading.value = false
        }
    }
    
    fun loadQuestionById(id: String) {
        viewModelScope.launch {
            _isLoading.value = true
            val question = repository.getQuestionById(id)
            _currentQuestion.value = question
            _selectedAnswers.value = emptySet()
            _showResult.value = false
            _reviewMode.value = false
            savePosition(id)
            pushToHistory(id)
            _isLoading.value = false
        }
    }
    
    fun selectAnswer(option: String) {
        val question = _currentQuestion.value
        val isSingleChoice = question?.qtype == "单选题" || question?.qtype == "判断题"
        val currentAnswers = _selectedAnswers.value.toMutableSet()
        if (isSingleChoice) {
            // 单选/判断：替换选择
            currentAnswers.clear()
            currentAnswers.add(option)
        } else {
            // 多选：切换
            if (currentAnswers.contains(option)) {
                currentAnswers.remove(option)
            } else {
                currentAnswers.add(option)
            }
        }
        _selectedAnswers.value = currentAnswers
    }
    
    fun submitAnswer() {
        val question = _currentQuestion.value ?: return
        val userAnswer = _selectedAnswers.value.sorted().joinToString("")
        val correctAnswer = question.answer
        
        val isCorrect = userAnswer == correctAnswer
        _isAnswerCorrect.value = isCorrect
        _showResult.value = true
        
        // Save to history
        viewModelScope.launch {
            val history = History(
                questionId = question.id,
                userAnswer = userAnswer,
                correct = isCorrect
            )
            repository.insertHistory(history)
            loadStatistics()
        }
    }
    
    fun nextQuestion() {
        when (_currentMode.value) {
            QuizMode.RANDOM -> loadRandomQuestion()
            QuizMode.SEQUENTIAL -> loadSequentialQuestion()
            QuizMode.WRONG_ONLY -> viewModelScope.launch { loadWrongQuestion() }
            else -> loadRandomQuestion()
        }
    }

    // 错题练习
    fun startWrongQuestionsMode() {
        viewModelScope.launch {
            _isLoading.value = true
            _currentMode.value = QuizMode.WRONG_ONLY
            loadWrongQuestion()
            _isLoading.value = false
        }
    }

    private suspend fun loadWrongQuestion() {
        val wrongQuestions = repository.getWrongQuestions()
        if (wrongQuestions.isEmpty()) {
            _currentQuestion.value = null
            return
        }
        // 随机选一道未完成的错题
        val answeredIds = repository.getAnsweredQuestionIds()
        val unanswered = wrongQuestions.filter { it.id !in answeredIds }
        val question = if (unanswered.isNotEmpty()) {
            unanswered.random()
        } else {
            wrongQuestions.random()
        }
        _currentQuestion.value = question
        _selectedAnswers.value = emptySet()
        _showResult.value = false
        _reviewMode.value = false
        savePosition(question.id)
        pushToHistory(question.id)
    }
    
    fun toggleFavorite() {
        val question = _currentQuestion.value ?: return
        viewModelScope.launch {
            val existing = repository.getFavoriteByQuestionId(question.id)
            if (existing != null) {
                repository.deleteFavoriteByQuestionId(question.id)
            } else {
                val favorite = Favorite(questionId = question.id)
                repository.insertFavorite(favorite)
            }
        }
    }
    
    fun isFavorite(questionId: String): Flow<Boolean> = flow {
        emit(repository.isFavorite(questionId))
    }
    
    fun searchQuestions(query: String): Flow<List<Question>> = flow {
        emit(repository.searchQuestions(query))
    }
    
    fun getWrongQuestions(): Flow<List<Question>> = flow {
        emit(repository.getWrongQuestions())
    }
    
    fun getFavoriteQuestions(): Flow<List<Question>> = flow {
        emit(repository.getFavoriteQuestions())
    }
    
    fun resetHistory() {
        viewModelScope.launch {
            repository.deleteAllHistory()
            savePosition(null)  // 清除浏览位置
            loadStatistics()
        }
    }
    
    fun clearAllUserData() {
        viewModelScope.launch {
            repository.clearAllUserData()
            loadStatistics()
            // Reset current states
            _currentQuestion.value = null
            _selectedAnswers.value = emptySet()
            _showResult.value = false
        }
    }
    
    private fun loadStatistics() {
        viewModelScope.launch {
            val totalQuestions = repository.getQuestionCount()
            val answeredQuestions = repository.getAnsweredQuestionCount()
            val totalAnswers = repository.getTotalAnswerCount()
            val correctAnswers = repository.getCorrectAnswerCount()
            
            val accuracy = if (totalAnswers > 0) {
                (correctAnswers.toFloat() / totalAnswers.toFloat()) * 100f
            } else 0f
            
            _statistics.value = Statistics(
                totalQuestions = totalQuestions,
                answeredQuestions = answeredQuestions,
                totalAnswers = totalAnswers,
                correctAnswers = correctAnswers,
                accuracy = accuracy
            )
        }
    }
    
    data class Statistics(
        val totalQuestions: Int = 0,
        val answeredQuestions: Int = 0,
        val totalAnswers: Int = 0,
        val correctAnswers: Int = 0,
        val accuracy: Float = 0f
    )
    
    enum class QuizMode {
        RANDOM, SEQUENTIAL, TIMED, EXAM, WRONG_ONLY
    }
}