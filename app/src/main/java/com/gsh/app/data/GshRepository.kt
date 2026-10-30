package com.gsh.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.util.Locale

/**
 * 数据仓库：统一入口，负责数据库 DAO 与用户设置的访问、预置食物的初始化。
 */
class GshRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.get(context)
    val foodDao: FoodDao = db.foodDao()
    val mealEntryDao: MealEntryDao = db.mealEntryDao()
    val dailyTotalDao: DailyTotalDao = db.dailyTotalDao()
    val dayTypeDao: DayTypeDao = db.dayTypeDao()
    val photoDao: PhotoDao = db.photoDao()
    val weightLogDao: WeightLogDao = db.weightLogDao()
    val exerciseDao: ExerciseDao = db.exerciseDao()
    val trainingEntryDao: TrainingEntryDao = db.trainingEntryDao()
    val settings: SettingsStore = SettingsStore(context)
    val searchHistory: SearchHistoryStore = SearchHistoryStore(context)

    /** 导出文件的缓存目录（分享后系统会自动清理）。 */
    private val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }

    suspend fun setDayType(date: String, isTraining: Boolean) {
        dayTypeDao.upsert(DayType(date, isTraining))
    }

    /**
     * 补账：把近 days 天里账本上没记录的天按默认（训练日）写入，
     * 之后训练日/休息日统计可直接累加账本，无需倒推。
     */
    suspend fun backfillDayTypes(days: Int) {
        val today = java.time.LocalDate.now()
        for (offset in 0 until days) {
            val date = DateUtils.format(today.minusDays(offset.toLong()))
            if (dayTypeDao.byDate(date) == null) {
                dayTypeDao.upsert(DayType(date, true))
            }
        }
    }

    /** 记录体重（当天重复记则覆盖），并同步更新设置里的体重，营养目标随之变化。 */
    suspend fun recordWeight(date: String, weightKg: Double) {
        weightLogDao.upsert(WeightLog(date, weightKg, System.currentTimeMillis()))
        val p = settings.profile.value
        if (p.weightKg != weightKg) {
            settings.updateProfile(p.copy(weightKg = weightKg))
        }
    }

    // ---------- 训练记录 ----------

    /** 内置动作：只补插缺失的，不动任何已有（含自定义）动作。 */
    suspend fun seedExercises() {
        BUILT_IN_EXERCISES.forEach { (name, group) ->
            if (exerciseDao.byName(name) == null) {
                exerciseDao.insert(Exercise(name = name, muscleGroup = group, isCustom = false))
            }
        }
    }

    /** 新增自定义动作，返回 id（重名时返回已有 id）。 */
    suspend fun addCustomExercise(name: String, muscleGroup: String, recordType: Int): Long {
        val existing = exerciseDao.byName(name)
        if (existing != null) return existing.id
        return exerciseDao.insert(
            Exercise(name = name.trim(), muscleGroup = muscleGroup, isCustom = true, recordType = recordType)
        )
    }

    /**
     * 存量动作的 recordType 一次性推断回填（启动时幂等执行）：
     * 有历史记录的按记录类型定（重量→0 / 次数→1 / 时长→2）；没练过的不猜保持 -1。
     */
    suspend fun backfillRecordTypes() {
        val metrics = trainingEntryDao.exerciseMetrics()
        metrics.forEach { m ->
            val type = when {
                m.hasWeight > 0 -> Exercise.TYPE_STRENGTH
                m.hasReps > 0 -> Exercise.TYPE_REPS
                else -> Exercise.TYPE_DURATION
            }
            exerciseDao.updateRecordType(m.exerciseId, type)
        }
    }

    /** 首次给未设置类型（-1）的动作录入后，按所填组合写回类型，永久固定。 */
    suspend fun settleRecordType(exerciseId: Long, recordType: Int) {
        exerciseDao.updateRecordType(exerciseId, recordType)
    }

    /**
     * 上次某动作的全部组（多组录入的"第 N 组带上次第 N 组"用）。
     * before 为当前查看日期：严格取其之前最近的一个训练日（不含当天），
     * 补录过去日期、以及当天连续加组都不被当天/其后的记录污染。
     */
    suspend fun lastSetsOf(exerciseId: Long, before: String): List<TrainingEntry> =
        trainingEntryDao.lastSetsOfBefore(exerciseId, before)

    /** 添加一条训练记录；若该动作类型未设置（-1），按所填组合实时判定并写回，永久固定。 */
    suspend fun addTrainingEntryAndSettleType(entry: TrainingEntry, exercise: Exercise) {
        trainingEntryDao.insert(entry)
        setDayType(entry.date, true)
        if (exercise.recordType == Exercise.TYPE_UNSET) {
            val type = when {
                entry.weightKg != null -> Exercise.TYPE_STRENGTH
                entry.reps != null -> Exercise.TYPE_REPS
                else -> Exercise.TYPE_DURATION
            }
            exerciseDao.updateRecordType(exercise.id, type)
        }
    }

    /** 删除一条训练记录。 */
    suspend fun deleteTrainingEntry(id: Long) {
        trainingEntryDao.delete(id)
    }

    /**
     * 按名字同步预置食物库：
     * 1. 补插种子清单中缺失的条目（如新增食物、肉类拆分生熟后的新条目）；
     * 2. 清理已不在种子清单中的旧预置条目（如被生熟拆分替代的旧口径"鸡胸肉"）；
     * 3. 种子数值修订：同名预置条目若与种子值不一致则更新（不动自定义食物）。
     * 不影响历史记录；历史记录保存营养快照，不受条目变动影响。
     */
    suspend fun seedFoods() {
        val seedNames = SeedFoods.list.map { it.name }.toHashSet()
        val existing = foodDao.allNames().toHashSet()

        val missing = SeedFoods.list.filter { it.name !in existing }
        if (missing.isNotEmpty()) {
            foodDao.insertAll(missing)
        }

        val stale = (existing - seedNames).toList()
        if (stale.isNotEmpty()) {
            foodDao.deleteNonCustom(stale)
        }

        SeedFoods.list.forEach { seed ->
            val current = foodDao.byName(seed.name) ?: return@forEach
            if (!current.isCustom &&
                (current.proteinPerUnit != seed.proteinPerUnit ||
                    current.carbsPerUnit != seed.carbsPerUnit ||
                    current.fatPerUnit != seed.fatPerUnit ||
                    current.unit != seed.unit)
            ) {
                foodDao.update(seed.copy(id = current.id))
            }
        }
    }

    suspend fun addEntry(food: Food, amount: Double, mealType: Int, date: String) {
        val snapshot = food.nutrition(amount)
        mealEntryDao.insert(
            MealEntry(
                date = date,
                mealType = mealType,
                foodId = food.id,
                foodName = food.name,
                amount = amount,
                unit = food.unit,
                protein = snapshot.protein,
                carbs = snapshot.carbs,
                fat = snapshot.fat
            )
        )
    }

    /** 修改一条记录的数量：营养快照按新旧数量比例折算，保持记录口径一致。 */
    suspend fun updateEntryAmount(entry: MealEntry, amount: Double) {
        if (amount <= 0.0 || entry.amount <= 0.0) return
        val scale = amount / entry.amount
        mealEntryDao.update(
            entry.copy(
                amount = amount,
                protein = entry.protein * scale,
                carbs = entry.carbs * scale,
                fat = entry.fat * scale
            )
        )
    }

    /** 把 from 日的全部记录复制到 to 日（保留餐别与营养快照），返回复制条数。 */
    suspend fun copyDay(from: String, to: String): Int {
        val source = mealEntryDao.byDate(from)
        if (source.isEmpty()) return 0
        mealEntryDao.insertAll(source.map { it.copy(id = 0, date = to) })
        return source.size
    }

    // ---------- 照片 ----------

    /**
     * 导入一张打卡照片：先按尺寸采样解码（避免大图整个读进内存），
     * 再按 EXIF 旋转矫正、最长边压到 MAX_PHOTO_DIMENSION，存为 JPEG 入库。
     * 返回是否成功；失败时清理半成品文件。
     */
    suspend fun savePhoto(source: Uri, kind: Int, date: String, mealType: Int): Boolean =
        withContext(Dispatchers.IO) {
            val resolver = appContext.contentResolver

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext false

            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_PHOTO_DIMENSION ||
                bounds.outHeight / (sample * 2) >= MAX_PHOTO_DIMENSION
            ) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            var bitmap = resolver.openInputStream(source)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return@withContext false

            // 相机照片的竖拍方向记录在 EXIF 里，解码不会自动应用，需手动转正
            val rotation = runCatching {
                resolver.openInputStream(source)?.use { ExifInterface(it).rotationDegrees } ?: 0
            }.getOrDefault(0)
            if (rotation != 0) {
                val rotated = Bitmap.createBitmap(
                    bitmap, 0, 0, bitmap.width, bitmap.height,
                    Matrix().apply { postRotate(rotation.toFloat()) }, true
                )
                if (rotated !== bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            // 采样只能成倍减，超出边长时再精确缩一次
            val maxSide = maxOf(bitmap.width, bitmap.height)
            if (maxSide > MAX_PHOTO_DIMENSION) {
                val scale = MAX_PHOTO_DIMENSION.toFloat() / maxSide
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== bitmap) {
                    bitmap.recycle()
                    bitmap = scaled
                }
            }

            val relative = "photos/" + if (kind == Photo.KIND_BODY) "body" else "meal"
            val dir = File(appContext.filesDir, relative).apply { mkdirs() }
            val file = File(dir, "${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)) {
                    bitmap.recycle()
                    file.delete()
                    return@withContext false
                }
            }
            bitmap.recycle()

            photoDao.insert(
                Photo(
                    kind = kind,
                    date = date,
                    mealType = mealType,
                    path = "$relative/${file.name}",
                    createdAt = System.currentTimeMillis()
                )
            )
            true
        }

    /** 删除照片：先删记录再删文件。 */
    suspend fun deletePhoto(photo: Photo) {
        photoDao.delete(photo.id)
        withContext(Dispatchers.IO) { File(appContext.filesDir, photo.path).delete() }
    }

    /**
     * 保存头像：接收裁剪界面确认后的正方形位图（EXIF 已转正），
     * 最长边压到 MAX_AVATAR_DIMENSION 存为 JPEG 入库；失败清理半成品文件。
     */
    suspend fun saveAvatar(source: Bitmap): Boolean = withContext(Dispatchers.IO) {
        val maxSide = maxOf(source.width, source.height)
        val bitmap = if (maxSide > MAX_AVATAR_DIMENSION) {
            val scale = MAX_AVATAR_DIMENSION.toFloat() / maxSide
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).toInt().coerceAtLeast(1),
                (source.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else source

        val relative = "photos/avatar"
        val dir = File(appContext.filesDir, relative).apply { mkdirs() }
        val file = File(dir, "${System.currentTimeMillis()}.jpg")
        val ok = FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, PHOTO_JPEG_QUALITY, out)
        }
        if (!ok) {
            file.delete()
            return@withContext false
        }
        photoDao.insert(
            Photo(
                kind = Photo.KIND_AVATAR,
                date = DateUtils.today(),
                mealType = Photo.MEAL_NONE,
                path = "$relative/${file.name}",
                createdAt = System.currentTimeMillis()
            )
        )
        true
    }

    /** 照片对应的私有文件（展示用）。 */
    fun photoFile(photo: Photo): File = File(appContext.filesDir, photo.path)

    /** 导出全部饮食记录为 CSV（带 BOM，Excel 可直接打开）。 */
    suspend fun exportCsv(): File = withContext(Dispatchers.IO) {
        val sb = StringBuilder("\uFEFF")
        sb.append("日期,餐别,食物,数量,单位,蛋白(g),碳水(g),脂肪(g),热量(kcal)\n")
        mealEntryDao.allEntries().forEach { e ->
            sb.append(e.date).append(',')
                .append(MealEntry.mealName(e.mealType)).append(',')
                .append(csvField(e.foodName)).append(',')
                .append(num(e.amount)).append(',')
                .append(e.amountLabel).append(',')
                .append(fixed(e.protein)).append(',')
                .append(fixed(e.carbs)).append(',')
                .append(fixed(e.fat)).append(',')
                .append("%.0f".format(e.calories)).append('\n')
        }
        File(exportDir, "gsh_export.csv").apply {
            writeText(sb.toString(), Charsets.UTF_8)
        }
    }

    /** 导出全部数据为 JSON：身体信息、训练/休息日标记、全部饮食记录。 */
    suspend fun exportJson(): File = withContext(Dispatchers.IO) {
        val profile = settings.profile.value
        val json = JSONObject().apply {
            put("app", "GSH")
            put("format", 1)
            put("exportedAt", LocalDateTime.now().toString())
            put("profile", JSONObject().apply {
                put("weightKg", profile.weightKg)
                put("heightCm", profile.heightCm)
                profile.gender?.let { put("gender", it.name) }
                put("birthYear", profile.birthYear)
                put("proteinPerKg", profile.proteinPerKg)
                put("carbsTrainingPerKg", profile.carbsTrainingPerKg)
                put("carbsRestPerKg", profile.carbsRestPerKg)
                put("fatPerKg", profile.fatPerKg)
            })
            put("dayTypes", JSONArray().apply {
                dayTypeDao.all().forEach { d ->
                    put(JSONObject().put("date", d.date).put("isTraining", d.isTraining))
                }
            })
            put("entries", JSONArray().apply {
                mealEntryDao.allEntries().forEach { e ->
                    put(JSONObject().apply {
                        put("date", e.date)
                        put("mealType", e.mealType)
                        put("foodName", e.foodName)
                        put("amount", e.amount)
                        put("unit", e.unit)
                        put("protein", e.protein)
                        put("carbs", e.carbs)
                        put("fat", e.fat)
                    })
                }
            })
        }
        File(exportDir, "gsh_export.json").apply {
            writeText(json.toString(2), Charsets.UTF_8)
        }
    }

    private fun csvField(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    /** 数量去掉多余的 .0，如 2.0 → 2 */
    private fun num(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    /** CSV 数值固定用点号小数，避免不同语言环境产生逗号 */
    private fun fixed(value: Double): String = String.format(Locale.US, "%.1f", value)

    companion object {
        /** 内置动作：用户实际训练清单，名称按用户口径 */
        private val BUILT_IN_EXERCISES = listOf(
            "负重深蹲" to "腿",
            "双力臂引体器械" to "背",
            "拉背器械" to "背"
        )

        /** 照片最长边像素，超出等比压小以控制存储体积 */
        private const val MAX_PHOTO_DIMENSION = 1600
        private const val MAX_AVATAR_DIMENSION = 512

        /** 照片 JPEG 压缩质量 */
        private const val PHOTO_JPEG_QUALITY = 85

        @Volatile
        private var instance: GshRepository? = null

        fun get(context: Context): GshRepository =
            instance ?: synchronized(this) {
                instance ?: GshRepository(context.applicationContext).also { instance = it }
            }
    }
}
