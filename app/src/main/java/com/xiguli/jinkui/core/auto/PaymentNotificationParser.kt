package com.xiguli.jinkui.core.auto

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.TxType
import java.math.BigDecimal
import java.math.RoundingMode

/** 通知来自哪类应用。[key] 写入加密数据，上线后不要改动。 */
enum class PaySource(val key: String, val label: String, val accountKind: AccountKind) {
    WECHAT("wechat", "微信", AccountKind.WECHAT),
    ALIPAY("alipay", "支付宝", AccountKind.ALIPAY),
    BANK("bank", "银行", AccountKind.BANK),
    UNIONPAY("unionpay", "云闪付", AccountKind.BANK);

    companion object {
        fun of(key: String?): PaySource = entries.firstOrNull { it.key == key } ?: BANK
    }
}

/**
 * 从一条通知里识别出的一笔收支：只有来源、方向、金额、商户与时间。
 * 通知的原文不保存，识别完立即丢弃。
 */
data class CapturedPayment(
    val source: PaySource,
    val type: TxType,
    val amount: Long,
    /** 商户 / 交易对方，最多 40 个字；识别不出时为空。 */
    val merchant: String,
    val postedAt: Long,
)

/**
 * 识别微信、支付宝、云闪付与常见银行 App 的收付款通知。纯函数，全部在本机完成。
 *
 * 只认同时满足“来自下面这些包名”“有金额”“有明确的收 / 付方向词”的通知；
 * 广告、验证码、待支付、失败、余额变动提醒等一律不认。
 */
object PaymentNotificationParser {
    val packages: Map<String, PaySource> = mapOf(
        "com.tencent.mm" to PaySource.WECHAT,
        "com.eg.android.AlipayGphone" to PaySource.ALIPAY,
        "com.unionpay" to PaySource.UNIONPAY,
        // 银行（手机银行 App 的包名）
        "com.icbc" to PaySource.BANK, // 工商银行
        "com.chinamworld.main" to PaySource.BANK, // 建设银行
        "com.android.bankabc" to PaySource.BANK, // 农业银行
        "com.chinamworld.bocmbci" to PaySource.BANK, // 中国银行
        "com.bankcomm.Bankcomm" to PaySource.BANK, // 交通银行
        "cmb.pb" to PaySource.BANK, // 招商银行
        "com.cmbchina.ccd.pluto.cmbActivity" to PaySource.BANK, // 掌上生活（招行信用卡）
        "com.yitong.mbank.psbc" to PaySource.BANK, // 邮储银行
        "cn.com.spdb.mobilebank.per" to PaySource.BANK, // 浦发银行
        "com.ecitic.bank.mobile" to PaySource.BANK, // 中信银行
        "com.cebbank.mobile.cemb" to PaySource.BANK, // 光大银行
        "cn.com.cmbc.newmbank" to PaySource.BANK, // 民生银行
        "com.cib.cibmb" to PaySource.BANK, // 兴业银行
        "com.pingan.paces.ccms" to PaySource.BANK, // 平安口袋银行
        "com.hxb.mobile.client" to PaySource.BANK, // 华夏银行
        "com.cgbchina.xpt" to PaySource.BANK, // 广发银行
        "com.bankcomm.maidanba" to PaySource.BANK, // 买单吧（交行信用卡）
        "com.citiccard.mobilebank" to PaySource.BANK, // 动卡空间（中信信用卡）
        "com.spdbccc.app" to PaySource.BANK, // 浦大喜奔（浦发信用卡）
    )

    fun supports(packageName: String): Boolean = packageName in packages

    private val AMOUNT = listOf(
        Regex("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
        Regex("(?:人民币|RMB|CNY)\\s*([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"),
        Regex("([0-9][0-9,]*(?:\\.[0-9]{1,2})?)\\s*元"),
    )

    private val IGNORE = listOf(
        "验证码", "校验码", "待支付", "未支付", "支付失败", "失败", "已取消", "撤销", "优惠券", "红包封面", "立减金", "积分",
        "额度", "账单已出", "还款提醒", "最低还款", "待还", "冻结", "预授权", "申请", "领取", "抽奖", "活动", "邀请",
    )

    /** 明确的支出词。 */
    private val EXPENSE_STRONG = listOf(
        "已支付", "支付成功", "付款成功", "成功付款", "已付款", "消费", "支出", "扣款", "扣费", "转出", "支付了", "付款了", "交易成功", "完成付款",
    )
    /** 收入词。 */
    private val INCOME_WORDS = listOf(
        "收款", "到账", "收入", "存入", "入账", "转入", "已收钱", "退款", "向你转账", "向您转账", "收到转账", "已收款", "转账收款", "汇入", "代发",
    )
    /** 含糊的支出词（“微信支付收款”里也有“支付”二字，所以排在收入词之后）。 */
    private val EXPENSE_WEAK = listOf("付款", "支付")

    /** 微信里只有这些“服务号 / 助手”的通知可能是收付款，其余是聊天。 */
    private val WECHAT_SERVICE_TITLES = listOf("微信支付", "收款", "微信转账", "转账助手", "零钱", "服务通知", "微信红包")

    /** 聊天消息的样子：“[3条]张三: …”“张三：…”“群名(12)”。 */
    private val CHAT_PREFIX = Regex("^\\[\\d+条]|^[^\\s:：¥￥]{1,20}[:：]\\s*\\S")
    private val GROUP_TITLE = Regex("[(（]\\d+[)）]\\s*$")

    private val MERCHANT = listOf(
        Regex("(?:收款方|商户名称|商户|交易对方|付款方|对方户名|对方)[:：]\\s*([^，,。；;\\n]{1,40})"),
        Regex("向\\s*([^，,。；;\\s]{1,30}?)\\s*(?:付款|支付|转账)"),
        Regex("在\\s*([^，,。；;\\s]{1,30}?)\\s*(?:消费|支付|付款)"),
        Regex("([^，,。；;\\s]{1,30}?)\\s*(?:向你|向您)(?:付款|转账)"),
    )

    /** 识别结果。 */
    sealed interface Outcome {
        /** 认出了一笔收支。 */
        data class Payment(val payment: CapturedPayment) : Outcome
        /** 像是收付款通知（有金额或收付词），但没认全；[reason] 给“粘贴测试”看。 */
        data class Unrecognized(val reason: String) : Outcome
        /** 不是收付款通知（聊天、广告、验证码、不支持的应用等），[reason] 同上。 */
        data class NotPayment(val reason: String) : Outcome
    }

    /** 识别一条通知；不是收付款通知时返回 null。 */
    fun parse(packageName: String, title: String, text: String, postedAt: Long): CapturedPayment? =
        (analyze(packageName, title, listOf(text), postedAt) as? Outcome.Payment)?.payment

    /**
     * 识别一条通知的全部文字：[texts] 是 EXTRA_TEXT、EXTRA_BIG_TEXT、EXTRA_SUB_TEXT、EXTRA_TEXT_LINES、tickerText 等，
     * 重复的会合并。[chat] 为 true 表示系统把它标成了聊天消息（MessagingStyle 等），直接忽略。
     */
    fun analyze(packageName: String, title: String, texts: List<String>, postedAt: Long, chat: Boolean = false): Outcome {
        val source = packages[packageName] ?: return Outcome.NotPayment("不是支持的应用")
        val body = texts.map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }
            .distinct()
            // 长文本通常已经包含短文本：只留不被别的条目包含的那些
            .let { list -> list.filter { a -> list.none { b -> b != a && b.contains(a) } } }
            .joinToString(" ")
        val cleanTitle = title.replace('\n', ' ').trim()
        val all = (cleanTitle + " " + body).trim()
        if (all.isBlank()) return Outcome.NotPayment("通知没有文字")
        val serviceTitle = WECHAT_SERVICE_TITLES.any { it in cleanTitle } || body.startsWith("微信支付")
        // 微信里大多是聊天消息：只认“微信支付”“微信收款助手”这类服务通知，朋友发来的“我付款了 ¥50”不算
        if (source == PaySource.WECHAT && !serviceTitle) return Outcome.NotPayment("微信聊天消息（不是微信支付的服务通知）")
        if (chat && !(source == PaySource.WECHAT && serviceTitle)) return Outcome.NotPayment("聊天消息")
        if (!serviceTitle && (CHAT_PREFIX.containsMatchIn(body) && amountOf(body) == null || GROUP_TITLE.containsMatchIn(cleanTitle))) {
            return Outcome.NotPayment("聊天或群消息")
        }
        IGNORE.firstOrNull { it in all }?.let { return Outcome.NotPayment("含有“$it”，不是一笔已完成的收支") }
        val amount = amountOf(all)
        // “收款方：xx”“付款方：xx”只是字段名，不代表方向
        val words = all.replace("收款方", "").replace("付款方", "").replace("收款人", "")
        val type = when {
            EXPENSE_STRONG.any { it in words } && !(INCOME_WORDS.any { it in words } && "退款" in words) -> TxType.EXPENSE
            INCOME_WORDS.any { it in words } -> TxType.INCOME
            EXPENSE_WEAK.any { it in words } -> TxType.EXPENSE
            else -> null
        }
        if (amount == null || amount <= 0) {
            return if (type != null) Outcome.Unrecognized("有收付字样，但没有找到金额") else Outcome.NotPayment("没有金额，也没有收付字样")
        }
        if (type == null) return Outcome.Unrecognized("有金额，但看不出是收入还是支出")
        val merchant = MERCHANT.firstNotNullOfOrNull { it.find(all)?.groupValues?.get(1)?.trim() }
            ?.trim('[', ']', '【', '】', '“', '”', '"')
            ?.takeIf { it.isNotEmpty() && amountOf(it) == null }
            ?.take(40)
            .orEmpty()
        return Outcome.Payment(CapturedPayment(source, type, amount, merchant, postedAt))
    }

    /** 未识别通知的“文字形状”：数字一律换成 #，最多 120 字。只用于本机调试识别规则。 */
    fun mask(title: String, texts: List<String>): String {
        val joined = (listOf(title) + texts).map { it.replace('\n', ' ').trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
        return joined.replace(Regex("[0-9０-９]"), "#").take(120)
    }

    private fun amountOf(text: String): Long? {
        for (pattern in AMOUNT) {
            val raw = pattern.find(text)?.groupValues?.get(1) ?: continue
            return runCatching {
                BigDecimal(raw.replace(",", "")).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
            }.getOrNull()?.takeIf { it in 1..99_999_999_99L }
        }
        return null
    }
}
