package io.jauge.feature.verdict.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.jauge.core.domain.model.Reliability
import io.jauge.core.domain.model.SalaryInsight
import io.jauge.core.domain.model.SkillsInsight
import io.jauge.core.domain.model.TrendInsight
import io.jauge.core.domain.model.getOrNull
import io.jauge.core.domain.model.map
import io.jauge.core.domain.usecase.GetAnalysisUseCase
import io.jauge.core.domain.usecase.GetSalaryInsightUseCase
import io.jauge.core.domain.usecase.GetSkillsInsightUseCase
import io.jauge.core.domain.usecase.GetTrendInsightUseCase
import io.jauge.core.ui.state.ContentState
import io.jauge.core.ui.state.reduce
import io.jauge.core.ui.state.refreshing
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Sections of the details screen, in the order of the segmented control (mockup 05). */
enum class DetailsSection {
    /** Salary of the position in the area. */
    SALARIES,

    /** Skills asked for by twin offers. */
    SKILLS,

    /** Applicants per offer over 12 months. */
    TREND,
}

/**
 * Observed state of the details screen. Each section loads on its own, so that one failing part does not hide the
 * others.
 *
 * @property salary salary section.
 * @property skills skills section.
 * @property trend trend section.
 * @property reliability reliability of the analysis, for the "Comment on estime" link, null until known.
 */
data class DetailsUiState(
    val salary: ContentState<SalaryInsight> = ContentState(),
    val skills: ContentState<SkillsInsight> = ContentState(),
    val trend: ContentState<TrendInsight> = ContentState(),
    val reliability: Reliability? = null,
) {
    /** True while a pull to refresh is running. */
    val isRefreshing: Boolean
        get() = salary.isRefreshing || skills.isRefreshing || trend.isRefreshing

    /** True when every section failed without content: the whole screen shows a friendly error. */
    val showsFullScreenError: Boolean
        get() = salary.showsFullScreenError && skills.showsFullScreenError && trend.showsFullScreenError
}

/**
 * State holder of the details screen (mockup 05).
 *
 * @param analysisId identifier of the analysis.
 * @param getSalary loads the salary insight.
 * @param getSkills loads the skills insight.
 * @param getTrend loads the trend insight.
 * @param getAnalysis loads the analysis (for its reliability).
 */
class DetailsViewModel(
    private val analysisId: String,
    private val getSalary: GetSalaryInsightUseCase,
    private val getSkills: GetSkillsInsightUseCase,
    private val getTrend: GetTrendInsightUseCase,
    private val getAnalysis: GetAnalysisUseCase,
) : ViewModel() {

    private val state = MutableStateFlow(DetailsUiState())

    /** Observed state of the screen. */
    val uiState: StateFlow<DetailsUiState> = state.asStateFlow()

    init {
        refresh()
    }

    /** Loads the three sections in parallel; also called by pull to refresh and by "Réessayer". */
    fun refresh() {
        viewModelScope.launch {
            state.update {
                it.copy(salary = it.salary.refreshing(), skills = it.skills.refreshing(), trend = it.trend.refreshing())
            }
            val salary = async { getSalary(analysisId) }
            val skills = async { getSkills(analysisId) }
            val trend = async { getTrend(analysisId) }
            val reliability = async { getAnalysis(analysisId).map { it.reliability } }
            val salaryOutcome = salary.await()
            val skillsOutcome = skills.await()
            val trendOutcome = trend.await()
            val reliabilityOutcome = reliability.await()
            state.update {
                it.copy(
                    salary = it.salary.reduce(salaryOutcome),
                    skills = it.skills.reduce(skillsOutcome),
                    trend = it.trend.reduce(trendOutcome),
                    reliability = reliabilityOutcome.getOrNull() ?: it.reliability,
                )
            }
        }
    }
}
