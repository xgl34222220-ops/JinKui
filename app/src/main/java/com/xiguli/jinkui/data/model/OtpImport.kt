package com.xiguli.jinkui.data.model

import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.core.qr.MigrationEntry

/**
 * 从身份验证器批量导出码导入两步验证码：每个选中的账号新建一张“登录密码”卡片，
 * 名称取发行方（没有时取账号），账号字段填账号，另加一个两步验证码字段。纯函数，便于测试。
 */
object OtpImport {
    /** 已经在金匮里的密钥（任何卡片的两步验证码字段），用来在预览里标出“已存在”。 */
    fun existingSecrets(cards: List<Card>): List<ByteArray> =
        cards.flatMap { card -> card.fields.filter { it.kind == FieldKind.TOTP } }
            .mapNotNull { field -> runCatching { Totp.parse(field.value).secret }.getOrNull() }

    fun isDuplicate(entry: MigrationEntry, existing: List<ByteArray>): Boolean {
        val secret = entry.config?.secret ?: return false
        return existing.any { it.contentEquals(secret) }
    }

    fun cardFor(entry: MigrationEntry, newId: () -> String = CardTemplates::newId): Card? {
        val config = entry.config ?: return null
        val fields = buildList {
            if (entry.account.isNotBlank()) add(CardField(newId(), "账号", entry.account, sensitive = false))
            add(CardField(newId(), "两步验证码", config.toUri(), sensitive = true, kind = FieldKind.TOTP))
        }
        return Card(
            id = newId(),
            type = CardType.LOGIN,
            title = entry.issuer.ifBlank { entry.account.ifBlank { "两步验证码" } }.take(60),
            fields = fields,
        )
    }
}
