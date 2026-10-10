package com.xiguli.jinkui.core.auto

import android.content.Context
import android.util.Base64
import com.xiguli.jinkui.core.security.KeystoreManager
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** 一条没认出来的受支持应用通知：只有来源、时间和数字被换成 # 的文字形状。 */
data class UnrecognizedNotice(val id: Long, val source: PaySource, val postedAt: Long, val shape: String)

/**
 * “未识别通知”：受支持应用里像是收付款、但规则没认全的通知，最多留最近 [MAX] 条，帮助用户（和我们）调整识别规则。
 * 数字全部换成 #（不保存金额、卡号、验证码），再用 Keystore 里的收件箱密钥加密，存在应用私有目录，从不离开本机。
 */
class UnrecognizedLog(context: Context, private val keystore: KeystoreManager) {
    private val file = File(context.applicationContext.filesDir, "unrecognized.spool")
    private val lock = Any()
    private val state = MutableStateFlow<List<UnrecognizedNotice>>(emptyList())
    val items: StateFlow<List<UnrecognizedNotice>> = state.asStateFlow()

    fun add(source: PaySource, postedAt: Long, shape: String) = synchronized(lock) {
        val list = readAll() + UnrecognizedNotice(postedAt * 1000 + (System.nanoTime() % 1000).let { if (it < 0) -it else it }, source, postedAt, shape.take(120))
        writeAll(list.takeLast(MAX))
    }

    /** 解锁后读出来给界面看。 */
    fun refresh() = synchronized(lock) { state.value = readAll().sortedByDescending { it.postedAt } }

    fun remove(id: Long) = synchronized(lock) { writeAll(readAll().filterNot { it.id == id }) }

    fun clear() = synchronized(lock) {
        file.delete()
        state.value = emptyList()
    }

    private fun readAll(): List<UnrecognizedNotice> {
        if (!file.exists()) return emptyList()
        return runCatching { file.readLines() }.getOrDefault(emptyList()).mapNotNull { line ->
            runCatching {
                if (line.isBlank()) return@mapNotNull null
                val json = JSONObject(String(keystore.intakeUnwrap(Base64.decode(line, Base64.NO_WRAP), AAD), Charsets.UTF_8))
                UnrecognizedNotice(json.getLong("id"), PaySource.of(json.optString("s")), json.getLong("at"), json.optString("x").take(120))
            }.getOrNull()
        }
    }

    private fun writeAll(list: List<UnrecognizedNotice>) {
        val text = list.joinToString("") { n ->
            val json = JSONObject().put("id", n.id).put("s", n.source.key).put("at", n.postedAt).put("x", n.shape).toString()
            Base64.encodeToString(keystore.intakeWrap(json.toByteArray(Charsets.UTF_8), AAD), Base64.NO_WRAP) + "\n"
        }
        if (list.isEmpty()) file.delete() else file.writeText(text)
        state.value = list.sortedByDescending { it.postedAt }
    }

    companion object {
        const val MAX = 20
        private val AAD = "jinkui:unrecognized:v1".toByteArray(Charsets.UTF_8)
    }
}
