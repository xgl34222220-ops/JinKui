package com.xiguli.jinkui.data.model

import java.util.UUID

/** 每种卡片类型的预设字段。用户可以在编辑页增删改任何字段。 */
object CardTemplates {
    private data class Spec(val label: String, val sensitive: Boolean = false, val kind: FieldKind = FieldKind.TEXT)

    private val specs: Map<CardType, List<Spec>> = mapOf(
        CardType.LOGIN to listOf(
            Spec("网址", kind = FieldKind.URL),
            Spec("账号"),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.BANK to listOf(
            Spec("开户行"),
            Spec("持卡人"),
            Spec("卡号", sensitive = true, kind = FieldKind.NUMBER),
            Spec("有效期", kind = FieldKind.DATE),
            Spec("安全码", sensitive = true, kind = FieldKind.NUMBER),
            Spec("取款密码", sensitive = true, kind = FieldKind.PASSWORD),
            Spec("预留手机号", kind = FieldKind.NUMBER),
        ),
        CardType.UKEY to listOf(
            Spec("所属机构"),
            Spec("设备序列号"),
            Spec("PIN 码", sensitive = true, kind = FieldKind.PASSWORD),
            Spec("恢复码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.ID_CARD to listOf(
            Spec("姓名"),
            Spec("证件号码", sensitive = true),
            Spec("住址"),
            Spec("签发机关"),
            Spec("有效期限", kind = FieldKind.DATE),
        ),
        CardType.PASSPORT to listOf(
            Spec("姓名"),
            Spec("护照号码", sensitive = true),
            Spec("国籍"),
            Spec("签发地"),
            Spec("签发日期", kind = FieldKind.DATE),
            Spec("有效期至", kind = FieldKind.DATE),
        ),
        CardType.SOCIAL to listOf(
            Spec("姓名"),
            Spec("社会保障号码", sensitive = true),
            Spec("卡号", sensitive = true, kind = FieldKind.NUMBER),
            Spec("发卡地"),
            Spec("服务密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.DRIVER to listOf(
            Spec("姓名"),
            Spec("证号", sensitive = true),
            Spec("准驾车型"),
            Spec("初次领证日期", kind = FieldKind.DATE),
            Spec("有效期限", kind = FieldKind.DATE),
            Spec("档案编号"),
        ),
        CardType.MEMBER to listOf(
            Spec("商家"),
            Spec("会员号"),
            Spec("手机号", kind = FieldKind.NUMBER),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.ACCESS to listOf(
            Spec("地点"),
            Spec("卡号"),
            Spec("开门密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.WIFI to listOf(
            Spec("网络名称"),
            Spec("密码", sensitive = true, kind = FieldKind.PASSWORD),
            Spec("加密方式"),
            Spec("路由器管理地址", kind = FieldKind.URL),
            Spec("管理密码", sensitive = true, kind = FieldKind.PASSWORD),
        ),
        CardType.CUSTOM to emptyList(),
    )

    fun fieldsFor(type: CardType): List<CardField> =
        specs.getValue(type).map { CardField(newId(), it.label, "", it.sensitive, it.kind) }

    fun newId(): String = UUID.randomUUID().toString()
}
