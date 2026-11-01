package com.pansou.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实后端抓取的 /api/search 返回数据验证解析逻辑。
 * 样本：app/src/test/resources/search-sample.json（kw=python 实测返回）
 */
class ApiTest {

    private fun loadSample(): String =
        javaClass.classLoader!!.getResourceAsStream("search-sample.json")!!
            .bufferedReader(Charsets.UTF_8).readText()

    @Test
    fun `parse real search response`() {
        val items = Api.parseResponse(loadSample())
        assertEquals(135, items.size)

        val quark = items.filter { it.type == "quark" }
        assertTrue(quark.isNotEmpty())

        val first = quark.first()
        assertEquals("https://pan.quark.cn/s/2233294c0f47", first.url)
        assertEquals("【老男孩教育】Python全栈开发脱产班16期", first.title)
        assertEquals("tg:leoziyuan", first.source)

        // 类型分组覆盖真实返回的 5 种网盘
        val types = items.map { it.type }.toSet()
        assertEquals(setOf("aliyun", "quark", "xunlei", "baidu", "uc"), types)

        // 所有条目必须有可点击的链接
        assertTrue(items.all { it.url.startsWith("http") })
    }

    @Test
    fun `format time`() {
        assertEquals("2026-10-16 05:31", Api.formatTime("2026-10-16T05:31:15Z"))
        assertEquals("", Api.formatTime(""))
    }

    @Test
    fun `type label mapping`() {
        assertEquals("夸克", Api.typeLabel("quark"))
        assertEquals("阿里云盘", Api.typeLabel("aliyun"))
        assertEquals("unknown", Api.typeLabel("unknown"))
    }

    @Test
    fun `parse check link response`() {
        val sample = """{"results":[{"disk_type":"quark","url":"https://pan.quark.cn/s/abc",
            "normalized_url":"https://pan.quark.cn/s/abc","state":"ok","summary":"链接有效"}]}"""
        val r = Api.parseCheckResponses(sample).first()
        assertEquals("ok", r.state)
        assertEquals("链接有效", r.summary)
        assertEquals("quark", r.diskType)
    }

    @Test
    fun `state label mapping`() {
        assertEquals("链接有效", Api.stateLabel("ok"))
        assertEquals("链接失效", Api.stateLabel("bad"))
        assertEquals("需要提取码", Api.stateLabel("locked"))
        assertEquals("未检测", Api.stateLabel("whatever"))
    }

    @Test
    fun `removable and valid state rules`() {
        // 移除：明确失效 + 无法确认有效（实测死链返回 uncertain/need verify）
        assertTrue(Api.isRemovableState("bad"))
        assertTrue(Api.isRemovableState("uncertain"))
        // 保留：有效、要提取码、不支持检测
        assertFalse(Api.isRemovableState("ok"))
        assertFalse(Api.isRemovableState("locked"))
        assertFalse(Api.isRemovableState("unsupported"))
        // 徽标：仅确认有效
        assertTrue(Api.isValidState("ok"))
        assertFalse(Api.isValidState("locked"))
        assertFalse(Api.isValidState("uncertain"))
    }

    @Test
    fun `parse batch check responses`() {
        val sample = """{"results":[
            {"disk_type":"quark","url":"https://a","state":"ok","summary":"链接有效"},
            {"disk_type":"baidu","url":"https://b","state":"bad","summary":"链接失效"}]}"""
        val rs = Api.parseCheckResponses(
            sample,
            listOf("quark" to "https://a", "baidu" to "https://b")
        )
        assertEquals(2, rs.size)
        assertEquals("ok", rs[0].state)
        assertEquals("bad", rs[1].state)
        assertEquals("链接失效", rs[1].summary)
    }

    @Test(expected = IllegalStateException::class)
    fun `error response throws`() {
        Api.parseResponse("""{"code":1,"message":"boom"}""")
    }
}
