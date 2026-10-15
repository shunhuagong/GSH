package com.gsh.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 用户身体信息与增肌营养系数。目标 = 体重 × 每公斤系数，
 * 碳水区分训练日 / 休息日。
 */
data class UserProfile(
    val weightKg: Double = 70.0,
    val proteinPerKg: Double = 2.0,
    val carbsTrainingPerKg: Double = 5.0,
    val carbsRestPerKg: Double = 3.0,
    val fatPerKg: Double = 0.8
)

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

    fun updateProfile(profile: UserProfile) {
        prefs.edit()
            .putFloat(KEY_WEIGHT, profile.weightKg.toFloat())
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
            proteinPerKg = prefs.getFloat(KEY_PROTEIN, 2.0f).toDouble(),
            carbsTrainingPerKg = carbsTraining,
            carbsRestPerKg = prefs.getFloat(KEY_CARBS_REST, 3.0f).toDouble(),
            fatPerKg = prefs.getFloat(KEY_FAT, 0.8f).toDouble()
        )
    }

    private companion object {
        const val KEY_WEIGHT = "weight_kg"
        const val KEY_PROTEIN = "protein_per_kg"
        const val KEY_CARBS_TRAINING = "carbs_training_per_kg"
        const val KEY_CARBS_REST = "carbs_rest_per_kg"
        const val KEY_FAT = "fat_per_kg"
        const val KEY_CARBS_LEGACY = "carbs_per_kg"
        const val KEY_AI_BASE_URL = "ai_base_url"
        const val KEY_AI_API_KEY = "ai_api_key"
        const val KEY_AI_MODEL = "ai_model"
    }
}
