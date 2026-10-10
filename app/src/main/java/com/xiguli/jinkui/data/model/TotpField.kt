package com.xiguli.jinkui.data.model

import com.xiguli.jinkui.core.crypto.Totp
import com.xiguli.jinkui.core.crypto.TotpConfig

/** 两步验证字段的配置；不是两步验证字段或密钥无效时为 null。 */
fun CardField.totpConfig(): TotpConfig? =
    if (kind != FieldKind.TOTP) null else runCatching { Totp.parse(value) }.getOrNull()

/**
 * 复制这个字段时真正放进剪贴板的内容：两步验证字段复制“此刻的验证码”，绝不复制密钥本身；
 * 密钥无效时返回 null（什么也不复制）。其他字段原样复制。
 */
fun CardField.copyText(now: Long = System.currentTimeMillis()): String? =
    if (kind == FieldKind.TOTP) totpConfig()?.let { Totp.code(it, now).code } else value
