package com.gsh.app.ai

import com.gsh.app.data.AiConfig
import com.gsh.app.data.DailyTotal
import com.gsh.app.data.DateUtils
import com.gsh.app.data.Gender
import com.gsh.app.data.HealthMetrics
import com.gsh.app.data.MealEntry
import com.gsh.app.data.NutritionTargets
import com.gsh.app.data.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** AI 分析的 UI 状态：空闲 / 加载中 / 成功 / 失败。 */
sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

/** 带 UI 友好提示的分析异常。 */
class AiException(message: String) : Exception(message)

/**
 * AI 饮食分析：调用 OpenAI 兼容接口（默认智谱 BigModel，可切换 DeepSeek 等）。
 * 上行内容仅包含当日/近7天的营养数据与设置页填写的身体信息，不包含其他个人信息。
 */
class AiAdvisor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** 单日分析：各餐明细、实际摄入 vs 目标，结合训练/休息日给建议。 */
    suspend fun analyzeDaily(
        config: AiConfig,
        date: String,
        entries: List<MealEntry>,
        targets: NutritionTargets,
        isTraining: Boolean,
        profile: UserProfile
    ): String {
        val protein = entries.sumOf { it.protein }
        val carbs = entries.sumOf { it.carbs }
        val fat = entries.sumOf { it.fat }
        val dayType = if (isTraining) "训练日" else "休息日"

        val details = entries.joinToString("\n") { e ->
            "- ${MealEntry.mealName(e.mealType)}：${e.foodName} ${fmt(e.amount)}${e.amountLabel}" +
                "（蛋白 ${fmt1(e.protein)}g、碳水 ${fmt1(e.carbs)}g、脂肪 ${fmt1(e.fat)}g）"
        }

        val user = """
            请分析我${DateUtils.display(date)}的饮食并给出建议。

            身体情况：${bodyInfo(profile)}
            今天是：$dayType
            今日目标：蛋白质 ${fmt(targets.protein)}g、碳水 ${fmt(targets.carbs)}g、脂肪 ${fmt(targets.fat)}g（约 ${fmt(targets.calories)} kcal）
            实际摄入：蛋白质 ${fmt1(protein)}g、碳水 ${fmt1(carbs)}g、脂肪 ${fmt1(fat)}g（约 ${fmt(protein * 4 + carbs * 4 + fat * 9)} kcal）

            饮食记录：
            $details

            回复要求：分三部分，标题单独一行：【总评】【做得好的】【改进建议】。
            【总评】2句话以内；【改进建议】最多4条、每条一行、具体可执行；结合${dayType}的碳水需求给建议；全文不超过300字；不要使用 Markdown、表情或列表符号。
        """.trimIndent()

        return chat(config, user)
    }

    /** 近7天周报：蛋白质达标稳定性、碳水随训练/休息日的执行情况。 */
    suspend fun analyzeWeekly(
        config: AiConfig,
        totals: List<DailyTotal>,
        dayTypes: Map<String, Boolean>,
        profile: UserProfile,
        today: String
    ): String {
        val training = NutritionTargets.from(profile, true)
        val rest = NutritionTargets.from(profile, false)
        val byDate = totals.associateBy { it.date }
        val start = DateUtils.parse(today).minusDays(6)

        val lines = (0..6).joinToString("\n") { offset ->
            val date = DateUtils.format(start.plusDays(offset.toLong()))
            val total = byDate[date]
            val isTraining = dayTypes[date] ?: true
            val label = "${DateUtils.display(date)}（${if (isTraining) "训练日" else "休息日"}）"
            if (total == null) {
                "$label：无记录"
            } else {
                val t = NutritionTargets.from(profile, isTraining)
                "$label：蛋白 ${fmt1(total.protein)}/${fmt(t.protein)}g、" +
                    "碳水 ${fmt1(total.carbs)}/${fmt(t.carbs)}g、" +
                    "脂肪 ${fmt1(total.fat)}/${fmt(t.fat)}g"
            }
        }

        val user = """
            请根据我近7天的饮食数据写一份简短周报。

            身体情况：${bodyInfo(profile)}
            每日目标：训练日 蛋白 ${fmt(training.protein)}g、碳水 ${fmt(training.carbs)}g、脂肪 ${fmt(training.fat)}g；休息日 蛋白 ${fmt(rest.protein)}g、碳水 ${fmt(rest.carbs)}g、脂肪 ${fmt(rest.fat)}g

            近7天数据：
            $lines

            回复要求：分四部分，标题单独一行：【总体评价】【蛋白质执行】【碳水执行】【下周建议】。
            重点分析蛋白质是否稳定达标、碳水是否随训练日与休息日正确增减；【下周建议】最多4条、每条一行；全文不超过350字；不要使用 Markdown、表情或列表符号。
        """.trimIndent()

        return chat(config, user)
    }

    /** 拼接身体情况一行，仅带上已填写的信息（体重/身高/BMI/性别/年龄/基础代谢）。 */
    private fun bodyInfo(profile: UserProfile): String = buildString {
        append("体重 ").append(fmt(profile.weightKg)).append("kg")
        if (profile.heightCm > 0) {
            append("、身高 ").append(fmt(profile.heightCm)).append("cm")
            HealthMetrics.from(profile)?.let { m ->
                append("、BMI ").append(fmt1(m.bmi)).append("（").append(m.bmiLevel.label).append("）")
                m.bmr?.let { append("、基础代谢约 ").append(fmt(it)).append("kcal/天") }
            }
        }
        profile.gender?.let { append("、性别 ").append(if (it == Gender.MALE) "男" else "女") }
        profile.age()?.let { append("、").append(it).append(" 岁") }
    }

    /** 调用 OpenAI 兼容 /chat/completions，返回模型回复文本。 */
    private suspend fun chat(config: AiConfig, user: String): String = withContext(Dispatchers.IO) {
        if (config.apiKey.isBlank()) throw AiException("尚未配置 API Key，请到设置页填写")

        val body = JSONObject().apply {
            put("model", config.model)
            put("temperature", 0.7)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }

        val request = Request.Builder()
            .url(config.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw AiException(httpError(response.code))
                val content = try {
                    JSONObject(text)
                        .optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content").orEmpty()
                } catch (e: JSONException) {
                    throw AiException("AI 服务返回了无法解析的内容，请检查接口地址")
                }
                if (content.isBlank()) throw AiException("AI 返回内容为空，请稍后重试")
                content
            }
        } catch (e: AiException) {
            throw e
        } catch (e: IOException) {
            throw AiException("网络连接失败，请检查网络后重试")
        }
    }

    private fun httpError(code: Int): String = when (code) {
        401 -> "API Key 无效或未授权（HTTP 401），请检查设置"
        403 -> "无访问权限（HTTP 403），请确认账号与所选模型的权限"
        429 -> "请求过于频繁或额度不足（HTTP 429），请稍后再试"
        in 500..599 -> "AI 服务暂时不可用（HTTP $code），请稍后再试"
        else -> "AI 服务返回错误（HTTP $code），请检查接口地址与模型名"
    }

    private companion object {
        const val SYSTEM_PROMPT =
            "你是一位专业的增肌营养教练，帮助用户以增肌为目标调整日常饮食。" +
                "只依据用户提供的数据分析，不编造信息；回复使用简体中文，语气务实、简洁。"

        fun fmt(v: Double): String = "%.0f".format(v)
        fun fmt1(v: Double): String = "%.1f".format(v)
    }
}
