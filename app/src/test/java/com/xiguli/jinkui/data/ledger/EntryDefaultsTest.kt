package com.xiguli.jinkui.data.ledger

import com.xiguli.jinkui.ui.ledger.LedgerSamples
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryDefaultsTest {
    private val data = LedgerSamples.data
    private val active = data.accounts

    @Test
    fun accountFollowsLastUseOfSameTopCategory() {
        // 房租走的是银行卡；选“居住”的任意子分类都默认银行卡
        assertEquals("a-bank", EntryDefaults.accountFor("cat-housing", data.transactions, data.categories, active))
        assertEquals("a-bank", EntryDefaults.accountFor("cat-housing-2", data.transactions, data.categories, active))
        // 餐饮记在微信上
        assertEquals("a-wechat", EntryDefaults.accountFor("cat-food-1", data.transactions, data.categories, active))
    }

    @Test
    fun unusedCategoryFallsBackToLatestThenFirstAccount() {
        // 从没记过“医疗”：取最近一笔的账户（10 月 10 日 12:18 的午餐，微信）
        assertEquals("a-wechat", EntryDefaults.accountFor("cat-medical", data.transactions, data.categories, active))
        assertEquals("a-cash", EntryDefaults.accountFor("cat-medical", emptyList(), data.categories, active))
        assertEquals(null, EntryDefaults.accountFor("cat-medical", emptyList(), data.categories, emptyList()))
    }

    @Test
    fun archivedAccountIsNeverSuggested() {
        val onlyCash = active.filter { it.id == "a-cash" }
        assertEquals("a-cash", EntryDefaults.accountFor("cat-housing", data.transactions, data.categories, onlyCash))
    }

    @Test
    fun frequentCategoriesRankByRecentUseThenPosition() {
        val now = LedgerSamples.at(10, 11, 0)
        val top = EntryDefaults.frequentCategories(data.transactions, data.categories, now).map { it.id }
        // 近 60 天（8 月 12 日起）：餐饮、购物各 2 笔（同数按分类顺序），交通 / 居住 / 娱乐各 1 笔，不够 6 个用分类顺序补
        assertEquals(listOf("cat-food", "cat-shopping", "cat-transport", "cat-housing", "cat-fun", "cat-medical"), top)
        // 只看最近 30 天：8 月与 9 月 20 日之前的不算
        val recent = EntryDefaults.frequentCategories(data.transactions, data.categories, now, count = 2, days = 30).map { it.id }
        assertEquals(listOf("cat-shopping", "cat-food"), recent)
    }

    @Test
    fun hiddenCategoriesAreSkipped() {
        val categories = data.categories.map { if (it.id == "cat-shopping") it.copy(archived = true) else it }
        val top = EntryDefaults.frequentCategories(data.transactions, categories, LedgerSamples.at(10, 11, 0), count = 3).map { it.id }
        assertEquals(listOf("cat-food", "cat-transport", "cat-housing"), top)
    }
}
