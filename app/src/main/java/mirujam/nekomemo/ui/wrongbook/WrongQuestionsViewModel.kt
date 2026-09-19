package mirujam.nekomemo.ui.wrongbook

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mirujam.nekomemo.data.repository.QuestionRepository
import mirujam.nekomemo.domain.model.Question
import javax.inject.Inject

@Immutable
data class WrongQuestionUiModel(
    val question: Question,
    val bankTitle: String
)

@HiltViewModel
class WrongQuestionsViewModel @Inject constructor(
    private val repository: QuestionRepository
) : ViewModel() {

    private val wrongQuestions = repository.getWrongQuestions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val bankTitles = repository.getAllBanks()
        .map { banks -> banks.associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val wrongQuestionModels: StateFlow<List<WrongQuestionUiModel>> = combine(
        wrongQuestions,
        bankTitles
    ) { questions, titles ->
        questions.map { question -> WrongQuestionUiModel(question, titles[question.questionBankId].orEmpty()) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markAsMastered(id: Long) {
        viewModelScope.launch {
            repository.markQuestionAsMastered(id)
        }
    }
}
