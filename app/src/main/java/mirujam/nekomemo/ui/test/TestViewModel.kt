package mirujam.nekomemo.ui.test

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mirujam.nekomemo.R
import mirujam.nekomemo.data.preferences.TestPreferenceRepository
import mirujam.nekomemo.data.repository.PracticeStatsRepository
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.QuestionType
import mirujam.nekomemo.ui.model.QuestionUiModel
import mirujam.nekomemo.ui.model.ScoreModel
import mirujam.nekomemo.ui.model.UiText
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

private const val AUTO_NEXT_DELAY_MS = 300L
private const val QUESTIONS_LOAD_TIMEOUT_MS = 10_000L

data class TestUiState(
    val currentIndex: Int = 0,
    val selectedAnswers: Map<Int, Set<Int>> = emptyMap(),
    val revealedQuestions: Set<Int> = emptySet(),
    val isFinished: Boolean = false,
    val isReviewing: Boolean = false,
    val isLoading: Boolean = true,
    val bankTitle: UiText = UiText.StringResource(R.string.test_mode_title),
    val questions: List<QuestionUiModel> = emptyList()
)

@HiltViewModel
class TestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: QuestionRepository,
    private val practiceStatsRepository: PracticeStatsRepository,
    testPreferenceRepository: TestPreferenceRepository
) : ViewModel() {

    private val bankId: Long = savedStateHandle["bankId"] ?: -1L
    private val questionCount: Int = savedStateHandle["questionCount"] ?: 0
    private val shuffleQuestions: Boolean = savedStateHandle["shuffleQuestions"] ?: false
    private val shuffleOptions: Boolean = savedStateHandle["shuffleOptions"] ?: false
    private val wrongOnly: Boolean = savedStateHandle["wrongOnly"] ?: false
    private var startedAt: Long = System.currentTimeMillis()
    private var hasRecordedSession = false

    private val questionSource = if (wrongOnly) repository.getWrongQuestions() else repository.getQuestionsForBank(bankId)

    private val rawQuestions: StateFlow<List<QuestionUiModel>> = questionSource
        .map { domainQuestions ->
            val models = QuestionUiModel.fromDomainModels(domainQuestions)
            if (shuffleOptions) {
                models.map { model ->
                    if (model.type == QuestionType.SINGLE_CHOICE || model.type == QuestionType.MULTIPLE_CHOICE) {
                        shuffleOptionsForModel(model)
                    } else {
                        model
                    }
                }
            } else {
                models
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val directAnswer: StateFlow<Boolean> = testPreferenceRepository.directAnswer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val autoNextOnCorrect: StateFlow<Boolean> = testPreferenceRepository.autoNextOnCorrect
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private var autoNextJob: Job? = null

    private val _uiState = MutableStateFlow(TestUiState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    private var hasShuffledQuestions = false
    private var bankTitlePlain: String = ""

    init {
        if (wrongOnly) {
            bankTitlePlain = ""
            _uiState.update { it.copy(bankTitle = UiText.StringResource(R.string.wrong_book_review_title)) }
        } else {
            viewModelScope.launch {
                val bank = repository.getBankById(bankId)
                bankTitlePlain = bank?.title.orEmpty()
                _uiState.update { it.copy(bankTitle = bank?.title?.let(UiText::DynamicString) ?: UiText.StringResource(R.string.test_mode_title)) }
            }
        }

        viewModelScope.launch {
            try {
                val models = withTimeout(QUESTIONS_LOAD_TIMEOUT_MS.milliseconds) {
                    rawQuestions.first { it.isNotEmpty() }
                }
                val finalQuestions = selectQuestions(models, shuffleQuestions)
                hasShuffledQuestions = shuffleQuestions
                _uiState.update { it.copy(isLoading = false, questions = finalQuestions) }
            } catch (e: Exception) {
                Timber.e(e, "Error loading questions for test")
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun toggleAnswer(questionIndex: Int, optionIndex: Int, isSingleChoice: Boolean = false) {
        val shouldReveal = directAnswer.value
        _uiState.update { state ->
            val current = state.selectedAnswers[questionIndex] ?: emptySet()
            val newSet = if (optionIndex in current) {
                current - optionIndex
            } else if (isSingleChoice) {
                setOf(optionIndex)
            } else {
                current + optionIndex
            }
            state.copy(selectedAnswers = state.selectedAnswers.toMutableMap().apply { this[questionIndex] = newSet })
        }
        if (shouldReveal && isSingleChoice) {
            revealAnswer(questionIndex)
        }
    }

    fun revealAnswer(questionIndex: Int) {
        _uiState.update { it.copy(revealedQuestions = it.revealedQuestions + questionIndex) }
        scheduleAutoNextIfCorrect(questionIndex)
    }

    private fun scheduleAutoNextIfCorrect(questionIndex: Int) {
        autoNextJob?.cancel()
        if (!autoNextOnCorrect.value) return
        val state = _uiState.value
        val question = state.questions.getOrNull(questionIndex) ?: return
        val selected = state.selectedAnswers[questionIndex]
        if (selected.isNullOrEmpty()) return
        if (selected != question.correctIndices.toSet()) return
        autoNextJob = viewModelScope.launch {
            delay(AUTO_NEXT_DELAY_MS.milliseconds)
            nextQuestion(state.questions.size)
        }
    }

    fun nextQuestion(total: Int) {
        autoNextJob?.cancel()
        _uiState.update { state ->
            if (state.currentIndex < total - 1) state.copy(currentIndex = state.currentIndex + 1) else state
        }
    }

    fun previousQuestion() {
        autoNextJob?.cancel()
        _uiState.update { state ->
            if (state.currentIndex > 0) state.copy(currentIndex = state.currentIndex - 1) else state
        }
    }

    fun finishTest() {
        val alreadyFinished = _uiState.value.isFinished
        _uiState.update { it.copy(isFinished = true) }
        if (alreadyFinished || hasRecordedSession) return
        hasRecordedSession = true

        val questions = _uiState.value.questions
        val selectedAnswers = _uiState.value.selectedAnswers
        val score = ScoreModel.calculate(questions, selectedAnswers)
        val objectiveResults = questions.mapIndexedNotNull { index, question ->
            if (question.type != QuestionType.SINGLE_CHOICE && question.type != QuestionType.MULTIPLE_CHOICE && question.type != QuestionType.TRUE_FALSE) {
                return@mapIndexedNotNull null
            }
            val selected = selectedAnswers[index] ?: return@mapIndexedNotNull null
            question.id to (selected == question.correctIndices.toSet())
        }

        viewModelScope.launch {
            practiceStatsRepository.recordSession(
                questionBankId = if (wrongOnly) null else bankId.takeIf { it >= 0 },
                bankTitleSnapshot = bankTitlePlain,
                startedAt = startedAt,
                finishedAt = System.currentTimeMillis(),
                totalCount = score.total,
                correctCount = score.correct,
                objectiveResults = objectiveResults
            )
        }
    }

    fun startReview() {
        _uiState.update { it.copy(isReviewing = true, currentIndex = 0) }
    }

    fun exitReview() {
        _uiState.update { it.copy(isReviewing = false) }
    }

    fun resetTest() {
        autoNextJob?.cancel()
        hasRecordedSession = false
        startedAt = System.currentTimeMillis()
        val baseQuestions = rawQuestions.value
        val finalQuestions = selectQuestions(baseQuestions, hasShuffledQuestions)
        _uiState.update {
            it.copy(
                selectedAnswers = emptyMap(),
                revealedQuestions = emptySet(),
                currentIndex = 0,
                isFinished = false,
                isReviewing = false,
                questions = finalQuestions
            )
        }
    }

    fun calculateScore(questions: List<QuestionUiModel>): ScoreModel {
        return ScoreModel.calculate(questions, _uiState.value.selectedAnswers)
    }

    /**
     * 按用户在 TestConfigDialog 中选择的题量截取题目。此前该逻辑缺失，
     * 导致"自定义题量测试"始终使用全部题目而不是用户选择的数量。
     */
    private fun selectQuestions(models: List<QuestionUiModel>, shuffle: Boolean): List<QuestionUiModel> {
        val shuffled = if (shuffle) models.shuffled() else models
        return if (questionCount in 1 until shuffled.size) shuffled.take(questionCount) else shuffled
    }

    private fun shuffleOptionsForModel(model: QuestionUiModel): QuestionUiModel {
        val shuffledOptions = model.options.shuffled()
        val newCorrectIndices = model.correctIndices.mapNotNull { oldIdx ->
            if (oldIdx in model.options.indices) {
                shuffledOptions.indexOf(model.options[oldIdx])
            } else null
        }
        return model.copy(options = shuffledOptions, correctIndices = newCorrectIndices)
    }
}
