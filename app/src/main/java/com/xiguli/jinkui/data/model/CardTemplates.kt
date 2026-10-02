package com.xiguli.jinkui.data.model

import java.util.UUID

/** 卡面上除标题之外的信息：左下角一行主信息，右下角一个带小标签的次要信息。 */
data class FaceInfo(
    val primary: String,
    val secondaryLabel: String = "",
    val secondary: String = "",
)

/**
 * 每种卡片类型的默认字段模板，以及由模板决定的展示规则。
 *
 * 模板只在新建卡片时预填；已有卡片的数据不会被改动。
 * 字段按“实物上最先要找的信息在前”排序，详情页也按这个顺序展示。
 */
object CardTemplates {
    private class Spec(
        val label: String,
        val sensitive: Boolean = false,
        val kind: FieldKind = FieldKind.TEXT,
        /** 输入框里的格式提示，不保存。 */
        val hint: String = "",
    )

    private val specs: Map<CardType, List<Spec>> = mapOf(
        CardType.LOGIN to listOf(
            Spec("网址", kind = FieldKind.URL, hint = "https://"),
            Spec("账号", hint = "用户名、邮箱或手机号"),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
            Spec("绑定手机", kind = FieldKind.PHONE),
            Spec("恢复码", sensitive = true, kind = FieldKind.PASSWORD, hint = "两步验证的备用恢复码"),
        ),
        CardType.BANK to listOf(
            Spec("卡号", sensitive = true, kind = FieldKind.NUMBER),
            Spec("持卡人"),
            Spec("开户行"),
            Spec("有效期", kind = FieldKind.DATE, hint = "月/年，如 08/29"),
            Spec("安全码", sensitive = true, kind = FieldKind.NUMBER, hint = "卡背面签名栏末 3 位"),
            Spec("取款密码", sensitive = true, kind = FieldKind.NUMBER, hint = "6 位数字"),
            Spec("预留手机号", kind = FieldKind.PHONE),
        ),
        // U 卡 = USDT 虚拟信用卡，字段按网上支付时要填的信息来排。
        CardType.UKEY to listOf(
            Spec("卡号", sensitive = true, kind = FieldKind.NUMBER),
            Spec("持卡人", hint = "卡面上的拼音或英文姓名"),
            Spec("有效期", kind = FieldKind.DATE, hint = "月/年，如 08/29"),
            Spec("安全码", sensitive = true, kind = FieldKind.NUMBER, hint = "CVV / CVC，3 位"),
            Spec("卡组织", hint = "Visa / Mastercard"),
            Spec("账单地址"),
            Spec("账单邮编"),
            Spec("发卡机构", hint = "发卡平台或银行"),
        ),
        CardType.ID_CARD to listOf(
            Spec("姓名"),
            Spec("公民身份号码", sensitive = true, hint = "18 位"),
            Spec("性别"),
            Spec("民族"),
            Spec("出生日期", kind = FieldKind.DATE, hint = "如 1990-01-01"),
            Spec("住址"),
            Spec("签发机关"),
            Spec("有效期限", kind = FieldKind.DATE, hint = "如 2020.01.01-2040.01.01，或 长期"),
        ),
        CardType.PASSPORT to listOf(
            Spec("姓名"),
            Spec("姓名拼音"),
            Spec("护照号码", sensitive = true),
            Spec("国籍"),
            Spec("出生日期", kind = FieldKind.DATE, hint = "如 1990-01-01"),
            Spec("签发日期", kind = FieldKind.DATE, hint = "如 2024-05-21"),
            Spec("有效期至", kind = FieldKind.DATE, hint = "如 2034-05-20"),
            Spec("签发地点"),
        ),
        CardType.SOCIAL to listOf(
            Spec("姓名"),
            Spec("社会保障号码", sensitive = true),
            Spec("社保卡号"),
            Spec("发卡日期", kind = FieldKind.DATE, hint = "如 2021-06-01"),
            Spec("开户银行"),
            Spec("银行卡号", sensitive = true, kind = FieldKind.NUMBER),
            Spec("服务密码", sensitive = true, kind = FieldKind.NUMBER, hint = "6 位数字"),
        ),
        CardType.DRIVER to listOf(
            Spec("姓名"),
            Spec("证号", sensitive = true),
            Spec("准驾车型", hint = "如 C1"),
            Spec("初次领证日期", kind = FieldKind.DATE, hint = "如 2016-03-01"),
            Spec("有效期限", kind = FieldKind.DATE, hint = "如 2022-03-01 至 2028-03-01"),
            Spec("档案编号"),
            Spec("发证机关"),
        ),
        CardType.MEMBER to listOf(
            Spec("商家"),
            Spec("会员号"),
            Spec("手机号", kind = FieldKind.PHONE),
            Spec("等级"),
            Spec("有效期", kind = FieldKind.DATE, hint = "如 2027-12-31"),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.ACCESS to listOf(
            Spec("地点", hint = "小区、公司或楼宇"),
            Spec("楼栋房号"),
            Spec("卡号"),
            Spec("开门密码", sensitive = true, kind = FieldKind.NUMBER),
            Spec("物业电话", kind = FieldKind.PHONE),
        ),
        CardType.WIFI to listOf(
            Spec("网络名称", hint = "SSID"),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
            Spec("加密方式", hint = "如 WPA2 / WPA3"),
            Spec("路由器管理地址", kind = FieldKind.URL, hint = "如 192.168.1.1"),
            Spec("管理账号"),
            Spec("管理密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.CUSTOM to emptyList(),
    )

    fun fieldsFor(type: CardType): List<CardField> =
        specs.getValue(type).map { CardField(newId(), it.label, "", it.sensitive, it.kind) }

    /** 模板里有、这张卡片还没有的字段（按字段名比对）。用于给旧卡片补齐新模板。 */
    fun missingFields(type: CardType, existing: List<CardField>): List<CardField> {
        val present = existing.map { it.label.trim() }.toSet()
        return specs.getValue(type)
            .filter { it.label !in present }
            .map { CardField(newId(), it.label, "", it.sensitive, it.kind) }
    }

    /** 输入框里的格式提示；模板之外的字段没有提示。 */
    fun hintFor(type: CardType, label: String): String =
        specs.getValue(type).firstOrNull { it.label == label.trim() }?.hint.orEmpty()

    /**
     * 详情页的展示顺序：模板里的字段按模板顺序在前，其余自定义字段保持原有先后。
     * 只影响展示，不改动保存的顺序。
     */
    fun displayOrder(card: Card): List<CardField> {
        val rank = specs.getValue(card.type).mapIndexed { index, spec -> spec.label to index }.toMap()
        return card.fields.withIndex()
            .sortedWith(compareBy({ rank[it.value.label.trim()] ?: Int.MAX_VALUE }, { it.index }))
            .map { it.value }
    }

    /** 卡面信息。主信息为空时退回到通用规则（[Card.faceHint]）。 */
    fun face(card: Card): FaceInfo {
        val info = when (card.type) {
            CardType.LOGIN -> FaceInfo(card.plain("账号"))
            CardType.BANK -> FaceInfo(card.identifier("卡号"), "有效期", card.plain("有效期"))
            // 后两个名字是早期“硬件密钥”模板里的字段，老卡片仍能在卡面上认出来。
            CardType.UKEY -> FaceInfo(card.identifier("卡号", "卡号 / 序列号", "设备序列号"), "有效期", card.plain("有效期"))
            CardType.ID_CARD -> FaceInfo(card.identifier("公民身份号码", "证件号码"), "姓名", card.plain("姓名"))
            CardType.PASSPORT -> FaceInfo(card.identifier("护照号码"), "有效期至", card.plain("有效期至"))
            CardType.SOCIAL -> FaceInfo(card.identifier("社会保障号码"), "姓名", card.plain("姓名"))
            CardType.DRIVER -> FaceInfo(card.identifier("证号"), "准驾车型", card.plain("准驾车型"))
            CardType.MEMBER -> FaceInfo(card.plain("会员号"), "等级", card.plain("等级"))
            CardType.ACCESS -> FaceInfo(card.plain("地点"), "房号", card.plain("楼栋房号"))
            CardType.WIFI -> FaceInfo(card.plain("网络名称"), "加密", card.plain("加密方式"))
            CardType.CUSTOM -> FaceInfo("")
        }
        return if (info.primary.isBlank()) info.copy(primary = card.faceHint) else info
    }

    fun newId(): String = UUID.randomUUID().toString()

    /** 指定名称的明文字段的值；字段被标为敏感时不上卡面。 */
    private fun Card.plain(vararg labels: String): String =
        fields.firstOrNull { it.label.trim() in labels && !it.sensitive && it.value.isNotBlank() }?.value.orEmpty()

    /**
     * 证件号、卡号这类“编号”字段：敏感时只露末四位，够用来辨认是哪一张。
     * 只对这里点名的字段生效，密码类字段不会走到这条规则。
     */
    private fun Card.identifier(vararg labels: String): String {
        val field = fields.firstOrNull { it.label.trim() in labels && it.kind != FieldKind.PASSWORD && it.value.isNotBlank() }
            ?: return ""
        if (!field.sensitive) return field.value
        val compact = field.value.filter { it.isLetterOrDigit() }
        return if (compact.length >= 8) "•••• " + compact.takeLast(4) else "••••"
    }
}
