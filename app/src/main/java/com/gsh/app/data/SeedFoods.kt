package com.gsh.app.data

/**
 * 预置的常见食物营养库。
 * 营养含量基准：克/毫升食物为每 100g / 100ml，"个"计食物为每个。
 * 肉类区分（生）（熟）口径：生重与熟重因失水密度不同，务必按称重状态选择条目。
 * 数值主要参考 USDA FoodData Central 与《中国食物成分表》。
 */
object SeedFoods {

    val list: List<Food> = listOf(
        // 禽肉（生熟分条）
        Food(name = "鸡胸肉（生）", proteinPerUnit = 22.5, carbsPerUnit = 0.0, fatPerUnit = 2.6),
        Food(name = "鸡胸肉（熟）", proteinPerUnit = 31.0, carbsPerUnit = 0.0, fatPerUnit = 3.6),
        Food(name = "鸡腿肉（去皮，生）", proteinPerUnit = 19.5, carbsPerUnit = 0.0, fatPerUnit = 5.0),
        Food(name = "鸡腿肉（去皮，熟）", proteinPerUnit = 25.0, carbsPerUnit = 0.0, fatPerUnit = 7.0),
        Food(name = "鸡腿肉（带皮带骨，生）", proteinPerUnit = 14.0, carbsPerUnit = 0.0, fatPerUnit = 10.0),
        Food(name = "鸡蛋", proteinPerUnit = 6.7, carbsPerUnit = 1.4, fatPerUnit = 4.4, unit = Food.UNIT_PIECE),
        Food(name = "蛋清", proteinPerUnit = 11.6, carbsPerUnit = 3.6, fatPerUnit = 0.2),
        // 畜肉（生熟分条）
        Food(name = "牛肉（瘦，生）", proteinPerUnit = 21.0, carbsPerUnit = 0.0, fatPerUnit = 4.5),
        Food(name = "牛肉（瘦，熟）", proteinPerUnit = 26.0, carbsPerUnit = 0.0, fatPerUnit = 6.0),
        Food(name = "猪里脊（生）", proteinPerUnit = 20.2, carbsPerUnit = 0.7, fatPerUnit = 5.5),
        Food(name = "猪里脊（熟）", proteinPerUnit = 26.0, carbsPerUnit = 0.0, fatPerUnit = 6.5),
        // 水产（生熟分条；罐头即食为熟制基准）
        Food(name = "三文鱼（生）", proteinPerUnit = 20.4, carbsPerUnit = 0.0, fatPerUnit = 13.4),
        Food(name = "三文鱼（熟）", proteinPerUnit = 25.4, carbsPerUnit = 0.0, fatPerUnit = 12.4),
        Food(name = "虾（生）", proteinPerUnit = 18.6, carbsPerUnit = 1.0, fatPerUnit = 1.1),
        Food(name = "虾（熟）", proteinPerUnit = 23.0, carbsPerUnit = 0.0, fatPerUnit = 1.0),
        Food(name = "金枪鱼（罐头）", proteinPerUnit = 25.5, carbsPerUnit = 0.0, fatPerUnit = 1.0),
        Food(name = "豆腐", proteinPerUnit = 8.1, carbsPerUnit = 1.9, fatPerUnit = 3.7),
        Food(name = "豆浆（无糖）", proteinPerUnit = 3.0, carbsPerUnit = 1.2, fatPerUnit = 1.6, unit = Food.UNIT_ML),
        Food(name = "牛奶", proteinPerUnit = 3.3, carbsPerUnit = 5.0, fatPerUnit = 3.6, unit = Food.UNIT_ML),
        Food(name = "希腊酸奶", proteinPerUnit = 9.0, carbsPerUnit = 4.0, fatPerUnit = 5.0),
        Food(name = "奶酪", proteinPerUnit = 25.0, carbsPerUnit = 2.0, fatPerUnit = 33.0),
        Food(name = "蛋白粉", proteinPerUnit = 80.0, carbsPerUnit = 8.0, fatPerUnit = 6.0),
        Food(name = "米饭（熟）", proteinPerUnit = 2.6, carbsPerUnit = 25.9, fatPerUnit = 0.3),
        Food(name = "馒头", proteinPerUnit = 7.0, carbsPerUnit = 47.0, fatPerUnit = 1.1),
        Food(name = "面条（熟）", proteinPerUnit = 4.5, carbsPerUnit = 25.0, fatPerUnit = 0.5),
        Food(name = "全麦面包", proteinPerUnit = 9.0, carbsPerUnit = 45.0, fatPerUnit = 3.4),
        Food(name = "燕麦片", proteinPerUnit = 15.0, carbsPerUnit = 61.0, fatPerUnit = 7.0),
        Food(name = "红薯", proteinPerUnit = 1.1, carbsPerUnit = 23.0, fatPerUnit = 0.1),
        Food(name = "土豆", proteinPerUnit = 2.0, carbsPerUnit = 17.0, fatPerUnit = 0.2),
        Food(name = "玉米", proteinPerUnit = 4.0, carbsPerUnit = 22.8, fatPerUnit = 1.2),
        Food(name = "贝贝南瓜", proteinPerUnit = 1.3, carbsPerUnit = 21.3, fatPerUnit = 0.3),
        Food(name = "西兰花", proteinPerUnit = 2.8, carbsPerUnit = 3.7, fatPerUnit = 0.4),
        Food(name = "白菜", proteinPerUnit = 1.5, carbsPerUnit = 3.2, fatPerUnit = 0.1),
        Food(name = "小白菜", proteinPerUnit = 1.5, carbsPerUnit = 2.7, fatPerUnit = 0.3),
        Food(name = "卷心菜", proteinPerUnit = 1.5, carbsPerUnit = 6.0, fatPerUnit = 0.2),
        Food(name = "菠菜", proteinPerUnit = 2.6, carbsPerUnit = 4.5, fatPerUnit = 0.3),
        Food(name = "生菜", proteinPerUnit = 1.3, carbsPerUnit = 2.0, fatPerUnit = 0.3),
        Food(name = "番茄", proteinPerUnit = 0.9, carbsPerUnit = 4.0, fatPerUnit = 0.2),
        Food(name = "黄瓜", proteinPerUnit = 0.8, carbsPerUnit = 2.9, fatPerUnit = 0.2),
        Food(name = "胡萝卜", proteinPerUnit = 1.0, carbsPerUnit = 8.8, fatPerUnit = 0.2),
        Food(name = "冬瓜", proteinPerUnit = 0.4, carbsPerUnit = 2.6, fatPerUnit = 0.2),
        Food(name = "茄子", proteinPerUnit = 1.1, carbsPerUnit = 4.9, fatPerUnit = 0.2),
        Food(name = "豆角", proteinPerUnit = 2.0, carbsPerUnit = 5.7, fatPerUnit = 0.4),
        Food(name = "青椒", proteinPerUnit = 1.4, carbsPerUnit = 5.4, fatPerUnit = 0.3),
        Food(name = "洋葱", proteinPerUnit = 1.1, carbsPerUnit = 9.0, fatPerUnit = 0.2),
        Food(name = "香菇", proteinPerUnit = 2.2, carbsPerUnit = 5.2, fatPerUnit = 0.3),
        Food(name = "金针菇", proteinPerUnit = 2.4, carbsPerUnit = 6.0, fatPerUnit = 0.4),
        Food(name = "毛豆（去荚）", proteinPerUnit = 13.1, carbsPerUnit = 6.5, fatPerUnit = 5.0),
        Food(name = "豌豆（鲜）", proteinPerUnit = 7.4, carbsPerUnit = 21.2, fatPerUnit = 0.3),
        Food(name = "香蕉", proteinPerUnit = 1.7, carbsPerUnit = 26.4, fatPerUnit = 0.2, unit = Food.UNIT_PIECE),
        Food(name = "苹果", proteinPerUnit = 0.3, carbsPerUnit = 13.5, fatPerUnit = 0.2),
        Food(name = "花生酱", proteinPerUnit = 25.0, carbsPerUnit = 16.0, fatPerUnit = 50.0),
        Food(name = "混合坚果", proteinPerUnit = 20.0, carbsPerUnit = 20.0, fatPerUnit = 50.0)
    )
}
