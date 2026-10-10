package com.xiguli.jinkui.data.importer

import com.xiguli.jinkui.data.ledger.LedgerCategory
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalTime

/**
 * 按关键字给账单与通知猜分类。规则是金匮自己写的一张关键字表，只认预置分类的 ID；
 * 用户隐藏了某个分类、或这个 ID 不存在时，退回它的上级分类，再退回“其他支出 / 其他收入”。
 * 全部在本机完成，不联网。
 */
object BillCategorizer {
    private class Rule(val id: String, val words: List<String>)

    private fun rule(id: String, vararg words: String) = Rule(id, words.toList())

    // 顺序就是优先级：更具体的写在前面
    private val expenseRules = listOf(
        rule("cat-food-4", "咖啡", "瑞幸", "星巴克", "奶茶", "喜茶", "蜜雪", "茶百道", "古茗", "霸王茶姬", "库迪", "饮品", "果茶"),
        rule("cat-food-5", "水果", "零食", "良品铺子", "三只松鼠", "百果园", "面包", "烘焙", "甜品"),
        rule("cat-transport-2", "滴滴", "打车", "出租", "曹操出行", "高德打车", "T3出行", "花小猪", "网约车", "快车"),
        rule("cat-transport-1", "地铁", "公交", "一卡通", "乘车码", "轨道交通", "公共交通"),
        rule("cat-transport-3", "加油", "中石化", "中石油", "壳牌", "充电桩", "加油站", "特来电", "星星充电"),
        rule("cat-transport-4", "停车"),
        rule("cat-transport-5", "12306", "铁路", "高铁", "航空", "机票", "火车票", "携程机票"),
        rule("cat-housing-1", "房租", "租金", "房贷", "自如", "贝壳"),
        rule("cat-housing-2", "电费", "水费", "燃气", "国家电网", "南方电网", "自来水", "水电"),
        rule("cat-housing-3", "物业"),
        rule("cat-housing-4", "话费", "流量", "宽带", "中国移动", "中国联通", "中国电信", "手机充值"),
        rule("cat-fun-1", "电影", "猫眼", "淘票票", "影城", "影院", "演出", "大麦", "KTV"),
        rule("cat-fun-2", "游戏", "Steam", "王者荣耀", "原神", "网易游戏", "腾讯游戏", "米哈游", "点券"),
        rule("cat-fun-3", "酒店", "旅行", "飞猪", "去哪儿", "同程", "景区", "门票", "民宿", "携程"),
        rule("cat-fun-4", "健身", "Keep", "游泳", "瑜伽", "球馆", "运动"),
        rule("cat-medical-2", "药店", "大药房", "药房", "药品", "叮当快药"),
        rule("cat-medical-1", "医院", "诊所", "门诊", "挂号", "体检", "口腔", "卫生院"),
        rule("cat-education-1", "书店", "当当", "图书", "微信读书", "书城"),
        rule("cat-education-2", "课程", "学费", "培训", "网课", "考试", "报名费"),
        rule("cat-social-1", "红包", "群收款"),
        rule("cat-social-2", "礼物", "鲜花", "花店"),
        rule("cat-family-2", "宠物", "猫粮", "狗粮"),
        rule("cat-shopping-3", "数码", "电器", "Apple", "苹果", "小米", "华为", "京东电器", "苏宁"),
        rule("cat-shopping-2", "服饰", "服装", "鞋", "优衣库", "ZARA", "衣"),
        rule("cat-shopping-4", "家居", "宜家", "IKEA"),
        rule("cat-shopping-1", "超市", "便利店", "盒马", "永辉", "沃尔玛", "华润万家", "罗森", "全家", "7-11", "美宜佳", "朴朴", "叮咚", "日用"),
        rule("cat-food", "美团", "饿了么", "外卖", "餐", "饭", "面馆", "粉", "麦当劳", "肯德基", "汉堡", "火锅", "烧烤", "食堂", "小吃", "必胜客", "德克士", "寿司"),
        rule("cat-shopping", "淘宝", "天猫", "京东", "拼多多", "抖音商城", "唯品会", "闲鱼", "购物"),
    )

    /** 账单自带的分类（支付宝“交易分类”）→ 预置分类。 */
    private val sourceKinds = listOf(
        "餐饮美食" to "cat-food",
        "交通出行" to "cat-transport",
        "爱车养车" to "cat-transport-3",
        "日用百货" to "cat-shopping-1",
        "服饰装扮" to "cat-shopping-2",
        "数码电器" to "cat-shopping-3",
        "家居家装" to "cat-shopping-4",
        "充值缴费" to "cat-housing-4",
        "住房物业" to "cat-housing",
        "酒店旅游" to "cat-fun-3",
        "文化休闲" to "cat-fun",
        "运动户外" to "cat-fun-4",
        "医疗健康" to "cat-medical",
        "教育培训" to "cat-education",
        "母婴亲子" to "cat-family-1",
        "宠物" to "cat-family-2",
        "转账红包" to "cat-social-1",
        "亲友代付" to "cat-social",
    )

    private val incomeRules = listOf(
        rule("cat-salary", "工资", "薪资", "代发", "薪酬"),
        rule("cat-bonus", "奖金", "年终奖", "奖励"),
        rule("cat-refund", "退款", "退货"),
        rule("cat-red-packet", "红包"),
        rule("cat-invest", "理财", "收益", "余额宝", "基金", "利息", "零钱通收益"),
        rule("cat-parttime", "兼职", "稿费", "劳务"),
    )

    /**
     * 猜一个分类 ID。[texts] 是交易对方、商品、类型、备注等文字；[time] 用来区分早 / 午 / 晚餐。
     * 返回 null 表示账本里一个可用的分类都没有。
     */
    fun categorize(type: TxType, texts: List<String>, categories: List<LedgerCategory>, time: LocalTime? = null, kind: String = ""): String? {
        val usable = categories.filter { !it.archived && it.type == type }
        if (usable.isEmpty()) return null
        val byId = usable.associateBy { it.id }
        val text = texts.joinToString(" ")
        fun pick(id: String): String? {
            byId[id]?.let { return it.id }
            // 子分类被隐藏或不存在：退回上级（预置 ID 的上级是去掉末尾的 -n）
            val parent = id.substringBeforeLast('-').takeIf { id.count { it == '-' } >= 2 }
            return parent?.let { byId[it]?.id }
        }
        val guessed: String? = if (type == TxType.EXPENSE) {
            val byKeyword = expenseRules.firstOrNull { r -> r.words.any { text.contains(it, ignoreCase = true) } }?.id
            val byKind = sourceKinds.firstOrNull { kind.contains(it.first) }?.second
            var id = byKeyword ?: byKind
            // 餐饮按时间细分到早 / 午 / 晚餐
            if (id == "cat-food" && time != null) {
                id = when (time.hour) {
                    in 5..9 -> "cat-food-1"
                    in 10..14 -> "cat-food-2"
                    in 16..21 -> "cat-food-3"
                    else -> "cat-food"
                }
            }
            id?.let(::pick)
        } else {
            incomeRules.firstOrNull { r -> r.words.any { text.contains(it, ignoreCase = true) || kind.contains(it) } }?.id?.let(::pick)
        }
        if (guessed != null) return guessed
        val fallback = if (type == TxType.EXPENSE) "cat-other-expense" else "cat-other-income"
        return byId[fallback]?.id ?: usable.filter { it.parentId == null }.minByOrNull { it.position }?.id ?: usable.first().id
    }

    /** 记账 App 的二级分类 → 金匮分类（比一级更具体，先查）。键是“方向:名字”。 */
    private val appSub = mapOf(
        "e:打车" to "cat-transport-2", "e:公交" to "cat-transport-1", "e:地铁" to "cat-transport-1", "e:加油" to "cat-transport-3",
        "e:停车" to "cat-transport-4", "e:火车" to "cat-transport-5", "e:飞机" to "cat-transport-5",
        "e:手机数码" to "cat-shopping-3", "e:数码" to "cat-shopping-3", "e:服饰" to "cat-shopping-2", "e:衣服" to "cat-shopping-2",
        "e:日用品" to "cat-shopping-1", "e:家居" to "cat-shopping-4",
        "e:虚拟充值" to "cat-fun-2", "e:游戏" to "cat-fun-2", "e:电影" to "cat-fun-1", "e:旅游" to "cat-fun-3", "e:运动" to "cat-fun-4",
        "e:红包" to "cat-social-1", "e:礼金人情" to "cat-social", "e:礼物" to "cat-social-2", "e:请客" to "cat-social-3",
        "e:早餐" to "cat-food-1", "e:午餐" to "cat-food-2", "e:晚餐" to "cat-food-3", "e:饮料" to "cat-food-4", "e:水果" to "cat-food-5", "e:零食" to "cat-food-5",
        "e:房租" to "cat-housing-1", "e:水电" to "cat-housing-2", "e:话费" to "cat-housing-4", "e:物业" to "cat-housing-3",
        "e:药品" to "cat-medical-2", "e:书籍" to "cat-education-1", "e:培训" to "cat-education-2",
        "e:借出" to "cat-other-expense", "e:理财支出" to "cat-other-expense",
        "i:工资" to "cat-salary", "i:奖金" to "cat-bonus", "i:理财盈利" to "cat-invest", "i:理财收益" to "cat-invest", "i:利息" to "cat-invest",
        "i:兼职外快" to "cat-parttime", "i:兼职" to "cat-parttime", "i:红包" to "cat-red-packet", "i:礼金人情" to "cat-red-packet",
        "i:退款" to "cat-refund",
    )

    /** 记账 App 的一级类别 → 金匮分类。 */
    private val appMain = mapOf(
        "e:食品餐饮" to "cat-food", "e:餐饮" to "cat-food", "e:出行交通" to "cat-transport", "e:交通" to "cat-transport",
        "e:购物消费" to "cat-shopping", "e:购物" to "cat-shopping", "e:居家生活" to "cat-housing", "e:健康医疗" to "cat-medical",
        "e:休闲娱乐" to "cat-fun", "e:文化教育" to "cat-education", "e:送礼人情" to "cat-social", "e:人情" to "cat-social",
        "e:其他" to "cat-other-expense", "i:收入" to "cat-other-income", "i:其他" to "cat-other-income",
    )

    /**
     * 记账 App 导出的“类别 / 二级分类”对应到金匮已有的分类（不新建分类，导入一向只归到已有分类）：
     * 先看二级分类，再看一级类别；都对不上时按备注里的关键词猜，最后落到“其他”。
     */
    fun appCategory(type: TxType, main: String, sub: String, note: String, categories: List<LedgerCategory>, time: LocalTime? = null): String? {
        val usable = categories.filter { !it.archived && it.type == type }
        if (usable.isEmpty()) return null
        val byId = usable.associateBy { it.id }
        val prefix = if (type == TxType.EXPENSE) "e:" else "i:"
        fun pick(id: String): String? {
            byId[id]?.let { return it.id }
            val parent = id.substringBeforeLast('-').takeIf { id.count { c -> c == '-' } >= 2 }
            return parent?.let { byId[it]?.id }
        }
        // 用户自己建过同名分类时直接用它
        val sameName = listOf(sub, main).filter { it.isNotBlank() }.firstNotNullOfOrNull { name -> usable.firstOrNull { it.name == name.trim() }?.id }
        if (sameName != null) return sameName
        appSub[prefix + sub.trim()]?.let { id -> pick(id)?.let { return it } }
        val mainId = appMain[prefix + main.trim()]
        if (mainId == "cat-food" && time != null) {
            categorize(type, listOf(sub, note), categories, time, "餐饮美食")?.let { return it }
        }
        if (mainId != null && mainId != "cat-other-expense" && mainId != "cat-other-income") pick(mainId)?.let { return it }
        return categorize(type, listOf(sub, note), categories, time, "")
    }
}
