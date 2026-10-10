package com.xiguli.jinkui.core.auto

import com.xiguli.jinkui.data.ledger.AccountKind
import com.xiguli.jinkui.data.ledger.LedgerAccount
import com.xiguli.jinkui.data.ledger.LedgerData
import com.xiguli.jinkui.data.ledger.LedgerDefaults
import com.xiguli.jinkui.data.ledger.LedgerTransaction
import com.xiguli.jinkui.data.ledger.PendingEntry
import com.xiguli.jinkui.data.ledger.PendingMath
import com.xiguli.jinkui.data.ledger.TxType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 通知识别与待确认的纯函数。通知文字都是按常见格式编的样例。 */
class PaymentNotificationParserTest {
    private val wx = "com.tencent.mm"
    private val ali = "com.eg.android.AlipayGphone"
    private val cmb = "cmb.pb"
    private val t = 1_760_000_000_000L

    private fun p(pkg: String, title: String, text: String) = PaymentNotificationParser.parse(pkg, title, text, t)

    @Test
    fun wechatPaymentAndCollection() {
        val pay = p(wx, "微信支付", "已支付¥32.50，收款方：示例面馆")
        assertNotNull(pay)
        assertEquals(TxType.EXPENSE, pay!!.type)
        assertEquals(3_250L, pay.amount)
        assertEquals("示例面馆", pay.merchant)
        assertEquals(PaySource.WECHAT, pay.source)
        val collect = p(wx, "微信收款助手", "微信支付收款0.01元（朋友到店）")
        assertEquals(TxType.INCOME, collect!!.type)
        assertEquals(1L, collect.amount)
        // 聊天消息不算
        assertNull(p(wx, "朋友甲", "我付款了 ¥50 你看下"))
    }

    @Test
    fun alipayAndBanks() {
        val ali1 = p(ali, "支付宝", "你有一笔 ¥45.00 的支出，付款成功，商户名称：示例外卖平台")
        assertEquals(TxType.EXPENSE, ali1!!.type)
        assertEquals(4_500L, ali1.amount)
        assertEquals("示例外卖平台", ali1.merchant)
        val ali2 = p(ali, "收款到账通知", "成功收款 12.00元")
        assertEquals(TxType.INCOME, ali2!!.type)
        val bank = p(cmb, "招商银行", "您账户1234于10月09日12:30在示例超市消费人民币97.00元")
        assertEquals(TxType.EXPENSE, bank!!.type)
        assertEquals(9_700L, bank.amount)
        assertEquals(PaySource.BANK, bank.source)
        assertEquals("示例超市", bank.merchant)
        val salary = p("com.icbc", "工商银行", "您尾号1234卡10月10日09:00工资收入(代发)人民币8,000.00元，余额12,345.67元")
        assertEquals(TxType.INCOME, salary!!.type)
        assertEquals(800_000L, salary.amount)
    }

    @Test
    fun ignoresNoiseAndOtherApps() {
        assertNull(p("com.example.chat", "支付宝", "已支付¥32.50"))
        assertNull(p(ali, "支付宝", "您的验证码 123456，付款时请勿告诉他人"))
        assertNull(p(ali, "支付宝", "你有一笔订单待支付 ¥30.00"))
        assertNull(p(ali, "支付宝", "领取 ¥5 立减金"))
        assertNull(p(cmb, "招商银行", "您的信用卡账单已出，最低还款 ¥100.00"))
        assertNull(p(cmb, "招商银行", "欢迎使用手机银行"))
        assertNull(p(ali, "支付宝", "付款失败 ¥10.00"))
    }

    private val zone = ZoneId.of("Asia/Shanghai")
    private val wechat = LedgerAccount("acc-wx", "微信", AccountKind.WECHAT)
    private val cash = LedgerAccount("acc-cash", "现金", AccountKind.CASH)

    @Test
    fun freshDropsDuplicatesWithinTwoMinutes() {
        val a = CapturedPayment(PaySource.WECHAT, TxType.EXPENSE, 3_250, "示例面馆", t)
        val again = a.copy(postedAt = t + 30_000)
        val other = a.copy(amount = 100, postedAt = t + 10_000)
        assertEquals(listOf(a, other), PendingMath.fresh(listOf(a, again, other), emptyList(), emptyList()).sortedBy { it.amount }.reversed())
        val pending = PendingEntry("p", PaySource.WECHAT, TxType.EXPENSE, 3_250, "", t - 60_000, t)
        assertEquals(listOf(other), PendingMath.fresh(listOf(a, other), listOf(pending), emptyList()))
        val tx = LedgerTransaction("x", TxType.EXPENSE, 100, t + 50_000, cash.id)
        assertEquals(listOf(a), PendingMath.fresh(listOf(a, other), emptyList(), listOf(tx)))
        assertEquals(listOf(a, a.copy(postedAt = t + 5 * 60_000)), PendingMath.fresh(listOf(a, a.copy(postedAt = t + 5 * 60_000)), emptyList(), emptyList()))
    }

    @Test
    fun suggestionPicksAccountAndCategory() {
        val at = LocalDateTime.of(2026, 10, 9, 12, 20).atZone(zone).toInstant().toEpochMilli()
        val entry = PendingEntry("p", PaySource.WECHAT, TxType.EXPENSE, 3_250, "示例面馆", at, at)
        val data = LedgerData(accounts = listOf(cash, wechat), categories = LedgerDefaults.categories())
        val s = PendingMath.suggestion(entry, data, zone)!!
        assertEquals(wechat.id, s.accountId)
        assertEquals("cat-food-2", s.categoryId) // 面馆 + 中午 → 午餐
        assertEquals("示例面馆", s.note)
        assertEquals(at, s.occurredAt)
        // 没有支付宝账户时退回第一个可用账户
        assertEquals(cash.id, PendingMath.accountFor(PaySource.ALIPAY, data.accounts))
        assertNull(PendingMath.suggestion(entry, data.copy(accounts = emptyList()), zone))
        // 载荷往返
        val back = PendingMath.read(entry.id, entry.createdAt, PendingMath.payload(entry))
        assertEquals(entry, back)
        assertTrue(runCatching { PendingMath.read("p", 0, "{\"amount\":0,\"at\":1}") }.isFailure)
    }
}
