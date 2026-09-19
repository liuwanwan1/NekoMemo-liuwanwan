package mirujam.nekomemo.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import mirujam.nekomemo.data.repository.PracticeStatsRepository
import mirujam.nekomemo.domain.model.OverallPracticeStats
import mirujam.nekomemo.domain.model.PracticeSession
import javax.inject.Inject

private const val HISTORY_LIMIT = 100

@HiltViewModel
class StatsViewModel @Inject constructor(
    statsRepository: PracticeStatsRepository
) : ViewModel() {

    val overallStats: StateFlow<OverallPracticeStats> = statsRepository.getOverallStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OverallPracticeStats.EMPTY)

    val sessions: StateFlow<List<PracticeSession>> = statsRepository.getRecentSessions(HISTORY_LIMIT)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}
