package com.xiguli.jinkui.core.auto

import android.content.Context
import android.util.Base64
import com.xiguli.jinkui.core.security.KeystoreManager
import com.xiguli.jinkui.data.ledger.TxType
import java.io.File
import org.json.JSONObject

/**
 * 自动记账的暂存：通知到达时金匮通常是锁着的，识别出的一笔（来源、方向、金额、商户、时间）
 * 先用 Keystore 里的收件箱密钥（AES-256-GCM，不可导出）加密，追加到应用私有目录的一个文件里；
 * 解锁后由 [drain] 取出、转进加密数据库的“待确认”表，然后删除暂存。
 *
 * 每行是一条独立的密文（Base64），一行坏了不影响其他行。通知原文从不写进来。
 */
class PendingSpool(context: Context, private val keystore: KeystoreManager) {
    private val file = File(context.applicationContext.filesDir, "intake.spool")
    private val draining = File(context.applicationContext.filesDir, "intake.draining")
    private val lock = Any()

    fun append(payment: CapturedPayment) = synchronized(lock) {
        val json = JSONObject()
            .put("s", payment.source.key)
            .put("t", payment.type.key)
            .put("a", payment.amount)
            .put("m", payment.merchant)
            .put("at", payment.postedAt)
            .toString()
        val blob = keystore.intakeWrap(json.toByteArray(Charsets.UTF_8), AAD)
        // 上限：暂存最多 2000 行（几个月不解锁也够用），超出时不再追加
        if (file.length() > MAX_BYTES) return@synchronized
        file.appendText(Base64.encodeToString(blob, Base64.NO_WRAP) + "\n")
    }

    /**
     * 取出全部暂存：先把文件改名（之后到达的通知写进新文件，不会丢），读完交给 [consume]，成功后删除。
     * [consume] 抛异常时保留改名后的文件，下次解锁再试。
     */
    suspend fun drain(consume: suspend (List<CapturedPayment>) -> Unit) {
        val items = synchronized(lock) {
            if (!draining.exists()) {
                if (!file.exists()) return
                if (!file.renameTo(draining)) return
            } else if (file.exists()) {
                // 上次没处理完的文件还在：把新到的接到后面
                draining.appendText(file.readText())
                file.delete()
            }
            draining.readLines().mapNotNull { line -> decode(line) }
        }
        consume(items)
        synchronized(lock) { draining.delete() }
    }

    val hasItems: Boolean get() = file.length() > 0 || draining.length() > 0

    fun deleteAll() = synchronized(lock) {
        file.delete()
        draining.delete()
    }

    private fun decode(line: String): CapturedPayment? = runCatching {
        if (line.isBlank()) return null
        val plain = keystore.intakeUnwrap(Base64.decode(line, Base64.NO_WRAP), AAD)
        val json = JSONObject(String(plain, Charsets.UTF_8))
        CapturedPayment(
            source = PaySource.of(json.optString("s")),
            type = if (json.optString("t") == TxType.INCOME.key) TxType.INCOME else TxType.EXPENSE,
            amount = json.getLong("a"),
            merchant = json.optString("m", "").take(40),
            postedAt = json.getLong("at"),
        )
    }.getOrNull()

    private companion object {
        val AAD = "jinkui:intake:v1".toByteArray(Charsets.UTF_8)
        const val MAX_BYTES = 2000L * 200
    }
}
