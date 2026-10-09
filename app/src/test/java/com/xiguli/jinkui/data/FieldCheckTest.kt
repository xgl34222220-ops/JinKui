package com.xiguli.jinkui.data

import com.xiguli.jinkui.data.model.FieldCheck
import com.xiguli.jinkui.data.model.FieldKind
import com.xiguli.jinkui.ui.components.BankBrand
import com.xiguli.jinkui.data.model.CardPalette
import com.xiguli.jinkui.data.model.CardType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class FieldCheckTest {
    @Test
    fun numbersAllowSpacesAndDashesOnly() {
        assertNull(FieldCheck.issue(FieldKind.NUMBER, "6225 8888 1234-5972"))
        assertNotNull(FieldCheck.issue(FieldKind.NUMBER, "6225 88AB"))
        assertNull(FieldCheck.issue(FieldKind.NUMBER, ""))
    }

    @Test
    fun phonesUrlsAndDates() {
        assertNull(FieldCheck.issue(FieldKind.PHONE, "+86 138-0000-1234"))
        assertNotNull(FieldCheck.issue(FieldKind.PHONE, "打电话"))
        assertNotNull(FieldCheck.issue(FieldKind.PHONE, "12"))
        assertNull(FieldCheck.issue(FieldKind.URL, "github.com"))
        assertNull(FieldCheck.issue(FieldKind.URL, "https://localhost"))
        assertNotNull(FieldCheck.issue(FieldKind.URL, "not a url"))
        assertNull(FieldCheck.issue(FieldKind.DATE, "08/29"))
        assertNull(FieldCheck.issue(FieldKind.DATE, "长期"))
        assertNotNull(FieldCheck.issue(FieldKind.DATE, "明年"))
        assertNull(FieldCheck.issue(FieldKind.TEXT, "任何内容"))
    }

    @Test
    fun titleIsRequired() {
        assertNotNull(FieldCheck.titleIssue("  "))
        assertNull(FieldCheck.titleIssue("招商银行"))
    }

    @Test
    fun bankBrandMatchesSpecificNamesFirst() {
        val icbc = BankBrand.colorOf("中国工商银行")
        val boc = BankBrand.colorOf("中国银行")
        assertNotNull(icbc)
        assertNotNull(boc)
        assertEquals(BankBrand.colorOf("工行信用卡"), icbc)
        assertEquals(false, icbc == boc)
        assertNull(BankBrand.colorOf("我的会员卡"))
        assertNull(BankBrand.colorOf("ABCDE"))
        // 只在银行卡的默认卡面上生效
        assertNotNull(BankBrand.of(CardType.BANK, CardPalette.AUTO, "招商银行储蓄卡", ""))
        assertNull(BankBrand.of(CardType.BANK, 0, "招商银行储蓄卡", ""))
        assertNull(BankBrand.of(CardType.MEMBER, CardPalette.AUTO, "招商银行", ""))
    }
}
