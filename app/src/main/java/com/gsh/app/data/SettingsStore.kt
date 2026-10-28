package com.gsh.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

enum class Gender { MALE, FEMALE }

/**
 * 用户身体信息与增肌营养系数。目标 = 体重 × 每公斤系数，
 * 碳水区分训练日 / 休息日。身高 / 性别 / 出生年为选填（0 / null 表示未填写）。
 */
data class UserProfile(
    val weightKg: Double = 70.0,
    val heightCm: Double = 0.0,
    val gender: Gender? = null,
    val birthYear: Int = 0,
    val proteinPerKg: Double = 2.0,
    val carbsTrainingPerKg: Double = 5.0,
    val carbsRestPerKg: Double = 3.0,
    val fatPerKg: Double = 0.8
) {
    /** 由出生年推算当前年龄；未填写或超出合理范围返回 null。 */
    fun age(): Int? =
        if (birthYear in 1900..LocalDate.now().year) LocalDate.now().year - birthYear else null
}

/** BMI 分级（中国成人标准 WS/T 428-2013：<18.5 偏瘦 / 18.5~23.9 健康 / 24~27.9 超重 / ≥28 肥胖）。 */
enum class BmiLevel(val label: String) {
    UNDERWEIGHT("偏瘦"),
    HEALTHY("健康"),
    OVERWEIGHT("超重"),
    OBESE("肥胖")
}

/**
 * 身体健康指标：BMI 与分级、该身高的健康体重范围、基础代谢 BMR。
 * BMR 用 Mifflin-St Jeor 公式，需性别与年龄；未填时为 null。
 */
data class HealthMetrics(
    val bmi: Double,
    val bmiLevel: BmiLevel,
    val healthyWeightRange: ClosedFloatingPointRange<Double>,
    val bmr: Double?
) {
    companion object {
        fun from(profile: UserProfile): HealthMetrics? {
            if (profile.weightKg <= 0 || profile.heightCm <= 0) return null
            val h = profile.heightCm / 100
            val bmi = profile.weightKg / (h * h)
            val level = when {
                bmi < 18.5 -> BmiLevel.UNDERWEIGHT
                bmi < 24.0 -> BmiLevel.HEALTHY
                bmi < 28.0 -> BmiLevel.OVERWEIGHT
                else -> BmiLevel.OBESE
            }
            val age = profile.age()
            val bmr = when {
                profile.gender == null || age == null -> null
                profile.gender == Gender.MALE ->
                    10 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * age + 5
                else ->
                    10 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * age - 161
            }
            return HealthMetrics(bmi, level, 18.5 * h * h..23.9 * h * h, bmr)
        }
    }
}

data class NutritionTargets(
    val protein: Double,
    val carbs: Double,
    val fat: Double
) {
    val calories: Double
        get() = protein * 4 + carbs * 4 + fat * 9

    companion object {
        fun from(profile: UserProfile, isTraining: Boolean) = NutritionTargets(
            protein = profile.weightKg * profile.proteinPerKg,
            carbs = profile.weightKg *
                (if (isTraining) profile.carbsTrainingPerKg else profile.carbsRestPerKg),
            fat = profile.weightKg * profile.fatPerKg
        )
    }
}

/**
 * AI 分析服务的配置：OpenAI 兼容协议，默认智谱 BigModel（glm-4-flash 免费）。
 * 换服务商只需改 baseUrl / model / apiKey。
 */
data class AiConfig(
    val baseUrl: String = DEFAULT_BASE_URL,
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL
) {
    companion object {
        const val DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4"
        const val DEFAULT_MODEL = "glm-4-flash"
    }
}

/**
 * 用 SharedPreferences 持久化用户设置，并通过 StateFlow 通知 UI。
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("gsh_settings", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(readProfile())
    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _aiConfig = MutableStateFlow(readAiConfig())
    val aiConfig: StateFlow<AiConfig> = _aiConfig.asStateFlow()

    private val _dynamicColor = MutableStateFlow(prefs.getBoolean(KEY_DYNAMIC_COLOR, true))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    fun updateDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _dynamicColor.value = enabled
    }

    fun updateProfile(profile: UserProfile) {
        prefs.edit()
            .putFloat(KEY_WEIGHT, profile.weightKg.toFloat())
            .putFloat(KEY_HEIGHT, profile.heightCm.toFloat())
            .putString(KEY_GENDER, profile.gender?.name)
            .putInt(KEY_BIRTH_YEAR, profile.birthYear)
            .putFloat(KEY_PROTEIN, profile.proteinPerKg.toFloat())
            .putFloat(KEY_CARBS_TRAINING, profile.carbsTrainingPerKg.toFloat())
            .putFloat(KEY_CARBS_REST, profile.carbsRestPerKg.toFloat())
            .putFloat(KEY_FAT, profile.fatPerKg.toFloat())
            .apply()
        _profile.value = profile
    }

    fun updateAiConfig(config: AiConfig) {
        prefs.edit()
            .putString(KEY_AI_BASE_URL, config.baseUrl)
            .putString(KEY_AI_API_KEY, config.apiKey)
            .putString(KEY_AI_MODEL, config.model)
            .apply()
        _aiConfig.value = config
    }

    private fun readAiConfig(): AiConfig = AiConfig(
        baseUrl = prefs.getString(KEY_AI_BASE_URL, AiConfig.DEFAULT_BASE_URL)
            ?: AiConfig.DEFAULT_BASE_URL,
        apiKey = prefs.getString(KEY_AI_API_KEY, "") ?: "",
        model = prefs.getString(KEY_AI_MODEL, AiConfig.DEFAULT_MODEL) ?: AiConfig.DEFAULT_MODEL
    )

    private fun readProfile(): UserProfile {
        // 兼容旧版本：单一碳水系数迁移为训练日碳水，休息日用默认值
        val legacyCarbs = prefs.getFloat(KEY_CARBS_LEGACY, Float.MIN_VALUE)
        val carbsTraining = when {
            prefs.contains(KEY_CARBS_TRAINING) -> prefs.getFloat(KEY_CARBS_TRAINING, 5f).toDouble()
            legacyCarbs != Float.MIN_VALUE -> legacyCarbs.toDouble()
            else -> 5.0
        }
        return UserProfile(
            weightKg = prefs.getFloat(KEY_WEIGHT, 70f).toDouble(),
            heightCm = prefs.getFloat(KEY_HEIGHT, 0f).toDouble(),
            gender = prefs.getString(KEY_GENDER, null)
                ?.let { name -> Gender.entries.firstOrNull { it.name == name } },
            birthYear = prefs.getInt(KEY_BIRTH_YEAR, 0),
            proteinPerKg = prefs.getFloat(KEY_PROTEIN, 2.0f).toDouble(),
            carbsTrainingPerKg = carbsTraining,
            carbsRestPerKg = prefs.getFloat(KEY_CARBS_REST, 3.0f).toDouble(),
            fatPerKg = prefs.getFloat(KEY_FAT, 0.8f).toDouble()
        )
    }

    private companion object {
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_HEIGHT = "height_cm"
        const val KEY_GENDER = "gender"
        const val KEY_BIRTH_YEAR = "birth_year"
        const val KEY_PROTEIN = "protein_per_kg"
        const val KEY_CARBS_TRAINING = "carbs_training_per_kg"
        const val KEY_CARBS_REST = "carbs_rest_per_kg"
        const val KEY_FAT = "fat_per_kg"
        const val KEY_CARBS_LEGACY = "carbs_per_kg"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
    }
}
