package com.gsh.app.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gsh.app.ai.AiAdvisor
import com.gsh.app.ai.AiException
import com.gsh.app.ai.AiUiState
import com.gsh.app.data.AiConfig
import com.gsh.app.data.DailyTotal
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Exercise
import com.gsh.app.data.ExerciseCount
import com.gsh.app.data.Food
import com.gsh.app.data.GshRepository
import com.gsh.app.data.MealEntry
import com.gsh.app.data.NutritionTargets
import com.gsh.app.data.Photo
import com.gsh.app.data.UserProfile
import com.gsh.app.data.WeightLog
import com.gsh.app.data.TrainingEntry
import com.gsh.app.data.TrainingSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class GshViewModel(private val repo: GshRepository) : ViewModel() {

    /** 近30天成绩：打卡/达标以有饮食记录的天计；训练日、休息日直接累加账本记录（启动时自动补账保证每天都有字）。 */
    data class Last30Stats(
        val loggedDays: Int,
        val proteinHitDays: Int,
        val trainingDays: Int,
        val restDays: Int
    )

    init {
        viewModelScope.launch { repo.seedFoods() }
        viewModelScope.launch { repo.seedExercises() }
        viewModelScope.launch { repo.backfillRecordTypes() }
        // 近30天没记录的天按默认训练日补账，之后统计可直接累加
        viewModelScope.launch { repo.backfillDayTypes(30) }
    }

    // ---------- 今日/所选日期 ----------

    private val _selectedDate = MutableStateFlow(DateUtils.today())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    /** 上次活跃日期：应用跨天回到前台时自动定位到新的一天。 */
    private var lastActiveDate: String = DateUtils.today()

    /** 当天已自动记账（写默认训练日）的日期，进程内去重。 */
    private var autoLoggedDate: String? = null

    fun refreshIfNewDay() {
        val today = DateUtils.today()
        if (today != lastActiveDate) {
            lastActiveDate = today
            _selectedDate.value = today
        }
        // 每天首次使用自动把当天日类型落库：账本显式化，训练日/休息日之后可直接统计
        logTodayDayType(today)
    }

    /** 账本上没有当天记录时写入默认（训练日）；已标记过的（含休息日）不动。 */
    private fun logTodayDayType(date: String) {
        if (autoLoggedDate == date) return
        autoLoggedDate = date
        viewModelScope.launch {
            if (repo.dayTypeDao.byDate(date) == null) {
                repo.setDayType(date, true)
            }
        }
    }

    val entriesForDate: StateFlow<List<MealEntry>> =
        _selectedDate
            .flatMapLatest { date -> repo.mealEntryDao.observeByDate(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 历史 ----------

    val dailyTotals: StateFlow<List<DailyTotal>> =
        repo.dailyTotalDao.observeDailyTotals()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 连续记录天数：中断一天即归零；今天还没记但昨天有记录时，打卡仍在进行中。 */
    val streak: StateFlow<Int> =
        repo.dailyTotalDao.observeDailyTotals()
            .map { totals -> computeStreak(totals.map { it.date }.toHashSet()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private fun computeStreak(dates: Set<String>): Int {
        var day = DateUtils.parse(DateUtils.today())
        if (!dates.contains(DateUtils.format(day))) {
            day = day.minusDays(1)
        }
        var count = 0
        while (dates.contains(DateUtils.format(day))) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    // ---------- 目标 ----------

    val profile: StateFlow<UserProfile> = repo.settings.profile

    /** 外观：动态取色（Material You） */
    val dynamicColor: StateFlow<Boolean> = repo.settings.dynamicColor

    fun setDynamicColor(enabled: Boolean) {
        repo.settings.updateDynamicColor(enabled)
    }

    /** date -> 是否训练日（未标记的日期默认训练日） */
    val dayTypes: StateFlow<Map<String, Boolean>> =
        repo.dayTypeDao.observeAll()
            .map { list -> list.associate { it.date to it.isTraining } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** 当前所选日期是否为训练日 */
    val isTrainingDay: StateFlow<Boolean> =
        combine(_selectedDate, dayTypes) { date, types -> types[date] ?: true }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** 当前所选日期的营养目标（随日类型切换） */
    val targets: StateFlow<NutritionTargets> =
        combine(profile, isTrainingDay) { p, isTraining ->
            NutritionTargets.from(p, isTraining)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NutritionTargets.from(profile.value, true)
        )

    fun setDayType(date: String, isTraining: Boolean) {
        viewModelScope.launch { repo.setDayType(date, isTraining) }
    }

    /** 近30天成绩：打卡/达标以有饮食记录的天计；训练日、休息日也只数有记录的天（与月历标记一一对应，可逐日核对）。 */
    val last30Stats: StateFlow<Last30Stats> =
        combine(dailyTotals, dayTypes, profile) { totals, types, p ->
            val start = DateUtils.format(DateUtils.parse(DateUtils.today()).minusDays(29))
            val recent = totals.filter { it.date >= start }
            val proteinTarget = p.weightKg * p.proteinPerKg
            val proteinHits = recent.count { it.protein >= proteinTarget }
            var trainingDays = 0
            var restDays = 0
            recent.forEach { t ->
                if (types[t.date] ?: true) trainingDays++ else restDays++
            }
            Last30Stats(recent.size, proteinHits, trainingDays, restDays)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            Last30Stats(0, 0, 0, 0)
        )

    // ---------- 食物搜索 ----------

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val foods: StateFlow<List<Food>> =
        _searchQuery
            .flatMapLatest { q ->
                if (q.isBlank()) repo.foodDao.observeAll() else repo.foodDao.search(q.trim())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ---------- 搜索历史 ----------

    private val _searchHistory = MutableStateFlow(repo.searchHistory.get())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    fun recordSearch(term: String) {
        if (term.isBlank()) return
        repo.searchHistory.add(term)
        _searchHistory.value = repo.searchHistory.get()
    }

    fun clearSearchHistory() {
        repo.searchHistory.clear()
        _searchHistory.value = emptyList()
    }

    // ---------- AI 分析 ----------

    private val advisor = AiAdvisor()

    val aiConfig: StateFlow<AiConfig> = repo.settings.aiConfig

    private val _dailyAnalysis = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val dailyAnalysis: StateFlow<AiUiState> = _dailyAnalysis.asStateFlow()

    private val _weeklyReport = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val weeklyReport: StateFlow<AiUiState> = _weeklyReport.asStateFlow()

    fun updateAiConfig(config: AiConfig) {
        repo.settings.updateAiConfig(config)
    }

    /** 分析当前所选日期的饮食。 */
    fun requestDailyAnalysis() {
        if (_dailyAnalysis.value is AiUiState.Loading) return
        val entries = entriesForDate.value
        if (entries.isEmpty()) {
            _dailyAnalysis.value = AiUiState.Error("这一天还没有饮食记录，先记点什么吧")
            return
        }
        _dailyAnalysis.value = AiUiState.Loading
        viewModelScope.launch {
            _dailyAnalysis.value = try {
                AiUiState.Success(
                    advisor.analyzeDaily(
                        config = aiConfig.value,
                        date = _selectedDate.value,
                        entries = entries,
                        targets = targets.value,
                        isTraining = isTrainingDay.value,
                        profile = profile.value
                    )
                )
            } catch (e: AiException) {
                AiUiState.Error(e.message ?: "分析失败，请稍后重试")
            }
        }
    }

    fun dismissDailyAnalysis() {
        _dailyAnalysis.value = AiUiState.Idle
    }

    /** 近7天周报：无记录的天也会展示给 AI，便于指出断记习惯。 */
    fun requestWeeklyReport() {
        if (_weeklyReport.value is AiUiState.Loading) return
        val today = DateUtils.today()
        val startDate = DateUtils.format(DateUtils.parse(today).minusDays(6))
        val recent = dailyTotals.value.filter { it.date >= startDate }
        if (recent.isEmpty()) {
            _weeklyReport.value = AiUiState.Error("近7天还没有饮食记录，无法生成周报")
            return
        }
        _weeklyReport.value = AiUiState.Loading
        viewModelScope.launch {
            _weeklyReport.value = try {
                AiUiState.Success(
                    advisor.analyzeWeekly(
                        config = aiConfig.value,
                        totals = recent,
                        dayTypes = dayTypes.value,
                        profile = profile.value,
                        today = today
                    )
                )
            } catch (e: AiException) {
                AiUiState.Error(e.message ?: "分析失败，请稍后重试")
            }
        }
    }

    fun dismissWeeklyReport() {
        _weeklyReport.value = AiUiState.Idle
    }

    // ---------- 照片 ----------

    /** 当前所选日期的餐次打卡照片 */
    val mealPhotos: StateFlow<List<Photo>> =
        _selectedDate
            .flatMapLatest { date -> repo.photoDao.observeMealPhotos(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 体型照片时间线（新的日期在前） */
    val bodyPhotos: StateFlow<List<Photo>> =
        repo.photoDao.observeBodyPhotos()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 「我」页头像（相册选择，只保留最新一张；null = 使用默认头像） */
    val avatar: StateFlow<Photo?> =
        repo.photoDao.observeAvatar()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 换头像：裁剪界面确认后的位图；保存成功后清理旧文件。 */
    fun changeAvatar(bitmap: Bitmap) {
        viewModelScope.launch {
            val old = avatar.value
            if (repo.saveAvatar(bitmap)) {
                old?.let { repo.deletePhoto(it) }
            }
        }
    }

    fun photoFile(photo: Photo): File = repo.photoFile(photo)

    /** 保存餐次照片；tempFile 为拍照临时文件，导入完成后清理。 */
    fun addMealPhoto(uri: Uri, mealType: Int, tempFile: File?) {
        viewModelScope.launch {
            repo.savePhoto(uri, Photo.KIND_MEAL, _selectedDate.value, mealType)
            tempFile?.let { runCatching { it.delete() } }
        }
    }

    /** 保存体型照片（记为今天）。 */
    fun addBodyPhoto(uri: Uri, tempFile: File?) {
        viewModelScope.launch {
            repo.savePhoto(uri, Photo.KIND_BODY, DateUtils.today(), Photo.MEAL_NONE)
            tempFile?.let { runCatching { it.delete() } }
        }
    }

    fun deletePhoto(photo: Photo) {
        viewModelScope.launch { repo.deletePhoto(photo) }
    }

    // ---------- 操作 ----------

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectDate(date: String) {
        _selectedDate.value = date
    }

    fun addEntry(food: Food, amount: Double, mealType: Int) {
        viewModelScope.launch {
            repo.addEntry(food, amount, mealType, _selectedDate.value)
        }
    }

    fun deleteEntry(entry: MealEntry) {
        viewModelScope.launch { repo.mealEntryDao.delete(entry) }
    }

    fun updateEntryAmount(entry: MealEntry, amount: Double) {
        viewModelScope.launch { repo.updateEntryAmount(entry, amount) }
    }

    /** 复制饮食：预览日期对应的记录内容。 */
    private val _copyPreviewDate = MutableStateFlow<String?>(null)

    val copyPreviewEntries: StateFlow<List<MealEntry>> =
        _copyPreviewDate
            .flatMapLatest { date ->
                if (date == null) flowOf(emptyList()) else repo.mealEntryDao.observeByDate(date)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setCopyPreviewDate(date: String?) {
        _copyPreviewDate.value = date
    }

    /** 把指定日期的全部记录复制到今天，回调返回复制条数。 */
    fun copyToToday(from: String, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.copyDay(from, DateUtils.today())) }
    }

    /** 导出 CSV，文件生成成功回调非空值，失败为 null。 */
    fun exportCsv(onDone: (File?) -> Unit) {
        viewModelScope.launch { onDone(runCatching { repo.exportCsv() }.getOrNull()) }
    }

    /** 导出 JSON，文件生成成功回调非空值，失败为 null。 */
    fun exportJson(onDone: (File?) -> Unit) {
        viewModelScope.launch { onDone(runCatching { repo.exportJson() }.getOrNull()) }
    }

    fun addCustomFood(
        name: String,
        proteinPerUnit: Double,
        carbsPerUnit: Double,
        fatPerUnit: Double,
        unit: Int
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repo.foodDao.insert(
                Food(
                    name = name.trim(),
                    proteinPerUnit = proteinPerUnit,
                    carbsPerUnit = carbsPerUnit,
                    fatPerUnit = fatPerUnit,
                    unit = unit,
                    isCustom = true
                )
            )
        }
    }

    fun deleteCustomFood(food: Food) {
        viewModelScope.launch { repo.foodDao.deleteCustom(food.id) }
    }

    fun updateProfile(profile: UserProfile) {
        repo.settings.updateProfile(profile)
    }

    // ---------- 体重历史 ----------

    /** 全部体重记录，旧日期在前。 */
    val weightLogs: StateFlow<List<WeightLog>> =
        repo.weightLogDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 记录今日体重（重复记则覆盖），并同步更新设置里的体重。 */
    fun recordWeight(weightKg: Double) {
        viewModelScope.launch { repo.recordWeight(DateUtils.today(), weightKg) }
    }

    // ---------- 训练记录（逐条添加，对齐饮食模式） ----------

    /** 动作库（内置 + 自定义），录入选择用。 */
    val exercises: StateFlow<List<Exercise>> =
        repo.exerciseDao.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 正在查看日期的训练条目（跟随 selectedDate：翻历史日期看/补录那天的）。 */
    val trainingOfDate: StateFlow<List<TrainingEntry>> =
        _selectedDate
            .flatMapLatest { date -> repo.trainingEntryDao.observeByDate(date) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 最近一次训练摘要（「我」页展示用）。 */
    val latestTraining: StateFlow<TrainingSummary?> =
        repo.trainingEntryDao.observeLatestSummary()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 上次某动作的全部组（第 N 组带上次第 N 组；只看选中日期之前，不含当天）。 */
    suspend fun lastSetsOf(exerciseId: Long): List<TrainingEntry> =
        repo.lastSetsOf(exerciseId, _selectedDate.value)

    /** 新增自定义动作（recordType 一律未设置，首次录入时按所填组合 settle），回调返回动作。 */
    fun addCustomExercise(name: String, muscleGroup: String, onDone: (Exercise?) -> Unit) {
        if (name.isBlank()) {
            onDone(null)
            return
        }
        viewModelScope.launch {
            val id = repo.addCustomExercise(name, muscleGroup)
            onDone(
                Exercise(
                    id = id, name = name.trim(),
                    muscleGroup = muscleGroup, isCustom = true, recordType = Exercise.TYPE_UNSET
                )
            )
        }
    }

    /** 添加一条训练记录（落在正在查看的日期）；动作类型未设置时按所填组合实时判定写回。 */
    fun addTrainingEntry(
        exercise: Exercise,
        weightKg: Double?,
        reps: Int?,
        durationSec: Int?
    ) {
        viewModelScope.launch {
            repo.addTrainingEntryAndSettleType(
                TrainingEntry(
                    date = _selectedDate.value,
                    exerciseId = exercise.id,
                    weightKg = weightKg,
                    reps = reps,
                    durationSec = durationSec,
                    createdAt = System.currentTimeMillis()
                ),
                exercise
            )
        }
    }

    fun deleteTrainingEntry(id: Long) {
        viewModelScope.launch { repo.deleteTrainingEntry(id) }
    }

    // ---------- 训练进步看板 ----------

    /** 看板里的一天一点：当日最好一组的值与文案。 */
    data class DayPoint(val date: String, val value: Double, val display: String)

    /** 看板数据：动作 + 每日点（升序）+ 最好成绩 + 较上次变化 + 是否新纪录。 */
    data class ProgressData(
        val exercise: Exercise,
        val points: List<DayPoint>,
        val bestPoint: DayPoint?,
        val lastPoint: DayPoint?,
        val diffText: String?,   // null = 单点或持平不显示
        val isRecord: Boolean
    )

    private val _progressExerciseId = MutableStateFlow<Long?>(null)

    fun setProgressExercise(id: Long) {
        _progressExerciseId.value = id
    }

    /** 有记录的动作及条数（看板动作条）。 */
    val exerciseCounts: StateFlow<List<ExerciseCount>> =
        repo.trainingEntryDao.observeExerciseCounts()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 看板数据：随选中动作切换（默认记录最多的动作）。 */
    val progressData: StateFlow<ProgressData?> =
        combine(exerciseCounts, _progressExerciseId) { counts, selected ->
            selected ?: counts.firstOrNull()?.exerciseId
        }.flatMapLatest { id ->
            if (id == null) flowOf(null)
            else combine(repo.trainingEntryDao.observeByExercise(id), exercises) { entries, exList ->
                val exercise = exList.find { it.id == id } ?: return@combine null
                aggregateProgress(exercise, entries)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 每日聚合成一个点：口径直接读动作 recordType（0=当天最好一组=重量最大、
     * 同重量次数多者；1=当天最大次数；2=当天最长分钟）；-1（存量未回填的
     * 无历史动作，不会有点）按记录字段兜底推断。最好一组从原始记录内比较，
     * 重量与次数天然来自同一组，不存在 MAX 交叉错配。
     */
    private fun aggregateProgress(exercise: Exercise, entries: List<TrainingEntry>): ProgressData? {
        if (entries.isEmpty()) return null
        val metric = when (exercise.recordType) {
            Exercise.TYPE_STRENGTH, Exercise.TYPE_REPS, Exercise.TYPE_DURATION -> exercise.recordType
            else -> when {
                entries.any { it.weightKg != null } -> Exercise.TYPE_STRENGTH
                entries.any { it.reps != null } -> Exercise.TYPE_REPS
                else -> Exercise.TYPE_DURATION
            }
        }
        val points = entries.groupBy { it.date }.toSortedMap().mapNotNull { (date, list) ->
            val best = when (metric) {
                Exercise.TYPE_STRENGTH -> list.filter { it.weightKg != null }
                    .maxWithOrNull(compareBy({ it.weightKg!! }, { it.reps ?: 0 }))
                Exercise.TYPE_REPS -> list.maxByOrNull { it.reps ?: 0 }
                else -> list.maxByOrNull { it.durationSec ?: 0 }
            } ?: return@mapNotNull null
            val value = when (metric) {
                Exercise.TYPE_STRENGTH -> best.weightKg!!
                Exercise.TYPE_REPS -> (best.reps ?: 0).toDouble()
                else -> (best.durationSec ?: 0).toDouble()
            }
            DayPoint(date, value, best.display)
        }
        if (points.isEmpty()) return null
        val best = points.maxByOrNull { it.value }
        val last = points.last()
        val prev = points.getOrNull(points.size - 2)
        val diff = prev?.let { last.value - it.value }
        val diffText = when {
            diff == null || diff == 0.0 -> null
            diff > 0 -> "↑ 较上次 +${trimNum(diff)}${metricUnit(metric)}"
            else -> "↓ 较上次 -${trimNum(-diff)}${metricUnit(metric)}"
        }
        return ProgressData(
            exercise = exercise,
            points = points,
            bestPoint = best,
            lastPoint = last,
            diffText = diffText,
            isRecord = last.value >= (best?.value ?: 0.0)
        )
    }

    private fun metricUnit(metric: Int) = when (metric) {
        Exercise.TYPE_STRENGTH -> " kg"
        Exercise.TYPE_REPS -> " 个"
        else -> " 秒"
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                GshViewModel(GshRepository.get(app))
            }
        }
    }
}
