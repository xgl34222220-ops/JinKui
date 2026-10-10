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
    private val EXPENSE_STRONG = listOf("已支付", "支付成功", "付款成功", "成功付款", "已付款", "消费", "支出", "扣款", "扣费", "转出")
    /** 收入词。 */
    private val INCOME_WORDS = listOf("收款", "到账", "收入", "存入", "入账", "转入", "已收钱", "退款", "向你转账", "向您转账")
    /** 含糊的支出词（“微信支付收款”里也有“支付”二字，所以排在收入词之后）。 */
    private val EXPENSE_WEAK = listOf("付款", "支付")

    private val MERCHANT = listOf(
        Regex("(?:收款方|商户名称|商户|交易对方|付款方|对方户名|对方)[:：]\\s*([^，,。；;\\n]{1,40})"),
        Regex("向\\s*([^，,。；;\\s]{1,30}?)\\s*(?:付款|支付|转账)"),
        Regex("在\\s*([^，,。；;\\s]{1,30}?)\\s*(?:消费|支付|付款)"),
        Regex("([^，,。；;\\s]{1,30}?)\\s*(?:向你|向您)(?:付款|转账)"),
    )

    /** 识别一条通知；不是收付款通知时返回 null。 */
    fun parse(packageName: String, title: String, text: String, postedAt: Long): CapturedPayment? {
        val source = packages[packageName] ?: return null
        val all = (title + " " + text).replace('\n', ' ').trim()
        if (all.isBlank()) return null
        if (IGNORE.any { it in all }) return null
        // 微信里大多是聊天消息：只认“微信支付”“微信收款助手”这类服务通知，朋友发来的“我付款了 ¥50”不算
        if (source == PaySource.WECHAT && !(title.contains("微信支付") || title.contains("收款") || text.startsWith("微信支付"))) return null
        val amount = amountOf(all) ?: return null
        if (amount <= 0) return null
        // “收款方：xx”“付款方：xx”只是字段名，不代表方向
        val words = all.replace("收款方", "").replace("付款方", "").replace("收款人", "")
        val type = when {
            EXPENSE_STRONG.any { it in words } -> TxType.EXPENSE
            INCOME_WORDS.any { it in words } -> TxType.INCOME
            EXPENSE_WEAK.any { it in words } -> TxType.EXPENSE
            else -> return null
        }
        val merchant = MERCHANT.firstNotNullOfOrNull { it.find(all)?.groupValues?.get(1)?.trim() }
            ?.trim('[', ']', '【', '】', '“', '”', '"')
            ?.takeIf { it.isNotEmpty() && amountOf(it) == null }
            ?.take(40)
            .orEmpty()
        return CapturedPayment(source, type, amount, merchant, postedAt)
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
