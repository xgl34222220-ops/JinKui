package com.xiguli.jinkui.ui.card

import com.xiguli.jinkui.core.crypto.Base32
import com.xiguli.jinkui.data.model.Card
import com.xiguli.jinkui.data.model.CardField
import com.xiguli.jinkui.data.model.CardType
import com.xiguli.jinkui.data.model.FieldKind
import java.time.LocalDateTime
import java.time.ZoneId

/** 卡包界面测试用的一组示例卡片（全部是虚构数据）。 */
object CardSamples {
    private val zone = ZoneId.of("Asia/Shanghai")
    val now: Long = LocalDateTime.of(2026, 10, 10, 9, 30, 12).atZone(zone).toInstant().toEpochMilli()
    private fun t(day: Int) = LocalDateTime.of(2026, 10, day, 10, 0).atZone(zone).toInstant().toEpochMilli()

    val totpSecret = "12345678901234567890".toByteArray()

    val cmb = Card(
        id = "c-cmb", type = CardType.BANK, title = "招商银行储蓄卡", tags = listOf("工资卡"),
        fields = listOf(
            CardField("f1", "卡号", "6225888812345972", true, FieldKind.NUMBER),
            CardField("f2", "持卡人", "林知远", false),
            CardField("f3", "开户行", "招商银行杭州分行", false),
            CardField("f4", "有效期", "08/29", false, FieldKind.DATE),
            CardField("f5", "安全码", "123", true, FieldKind.NUMBER),
            CardField("f6", "预留手机号", "13800001234", false, FieldKind.PHONE),
        ),
        note = "工资每月 10 日到账", pinned = true, createdAt = t(1), updatedAt = t(8), lastUsedAt = t(9),
    )
    val icbc = Card(
        id = "c-icbc", type = CardType.BANK, title = "工商银行信用卡",
        fields = listOf(
            CardField("g1", "卡号", "6222020012348841", true, FieldKind.NUMBER),
            CardField("g2", "有效期", "11/27", false, FieldKind.DATE),
        ),
        createdAt = t(2), updatedAt = t(3), lastUsedAt = t(5),
    )
    val github = Card(
        id = "c-gh", type = CardType.LOGIN, title = "GitHub", tags = listOf("工作"),
        fields = listOf(
            CardField("h1", "网址", "https://github.com", false, FieldKind.URL),
            CardField("h2", "账号", "lin.zhiyuan@example.com", false),
            CardField("h3", "密码", "Gx7#pL2q!vR9", true, FieldKind.PASSWORD),
            CardField("h4", "两步验证码", "otpauth://totp/GitHub:lin?secret=" + Base32.encode(totpSecret) + "&issuer=GitHub", true, FieldKind.TOTP),
        ),
        createdAt = t(3), updatedAt = t(6), lastUsedAt = t(10),
    )
    val idCard = Card(
        id = "c-id", type = CardType.ID_CARD, title = "居民身份证",
        fields = listOf(
            CardField("i1", "姓名", "林知远", false),
            CardField("i2", "公民身份号码", "330102199001011234", true, FieldKind.NUMBER),
            CardField("i3", "有效期限", "2020.01.01-2040.01.01", false, FieldKind.DATE),
        ),
        createdAt = t(4), updatedAt = t(4), lastUsedAt = t(4),
    )
    val wifi = Card(
        id = "c-wifi", type = CardType.WIFI, title = "家里 Wi-Fi",
        fields = listOf(
            CardField("w1", "网络名称", "Lin-Home-5G", false),
            CardField("w2", "密码", "tea-garden-42", true, FieldKind.PASSWORD),
        ),
        createdAt = t(5), updatedAt = t(5), lastUsedAt = t(6),
    )

    val all = listOf(cmb, icbc, github, idCard, wifi)
}
