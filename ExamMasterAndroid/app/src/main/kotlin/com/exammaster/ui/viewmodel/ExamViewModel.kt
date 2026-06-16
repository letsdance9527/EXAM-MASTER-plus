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

    // 上一题浏览历史栈
    private val questionHistory = mutableListOf<String>()
    private val _hasPreviousQuestion = MutableStateFlow(false)
    val hasPreviousQuestion: StateFlow<Boolean> = _hasPreviousQuestion.asStateFlow()
    private val _reviewMode = MutableStateFlow(false)
    val reviewMode: StateFlow<Boolean> = _reviewMode.asStateFlow()

    private fun pushToHistory(qid: String) {
        if (questionHistory.isEmpty() || questionHistory.last() != qid) {
            questionHistory.add(qid)
            if (questionHistory.size > 100) {
                questionHistory.removeAt(0)
            }
        }
        _hasPreviousQuestion.value = questionHistory.size >= 2
    }

    fun goToPreviousQuestion() {
        if (questionHistory.size < 2) return
        viewModelScope.launch {
            questionHistory.removeAt(questionHistory.lastIndex) // 去掉当前题
            val prevQid = questionHistory.removeAt(questionHistory.lastIndex) // 上一题
            _hasPreviousQuestion.value = questionHistory.size >= 2
            val question = repository.getQuestionById(prevQid)
            if (question != null) {
                _currentQuestion.value = question
                _reviewMode.value = true
                _showResult.value = false
                _selectedAnswers.value = emptySet()
            }
        }
    }

    fun exitReviewMode() {
        _reviewMode.value = false
        _selectedAnswers.value = emptySet()
        _showResult.value = false
    }

    // 题库管理
    private val _currentBank = MutableStateFlow(Bank.getActiveBank(getApplication()))
    val currentBank: StateFlow<Bank> = _currentBank.asStateFlow()

    private val _availableBanks = MutableStateFlow(
        Bank.loadFromAssets(getApplication()).banks
    )
    val availableBanks: StateFlow<List<Bank>> = _availableBanks.asStateFlow()

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
            question?.let { savePosition(it.id) }
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
            question?.let { savePosition(it.id) }
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
            question?.let { savePosition(it.id) }
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
            savePosition(id)
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
            QuizMode.WRONG_ONLY -> loadWrongQuestion()
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