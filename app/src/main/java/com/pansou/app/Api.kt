package com.pansou.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** 单条网盘搜索结果 */
data class CloudItem(
    val type: String,      // quark / aliyun / baidu / xunlei / uc ...
    val title: String,
    val url: String,
    val password: String,
    val datetime: String,  // ISO 格式，如 2026-10-16T05:31:15Z
    val source: String,
    val valid: Boolean = false   // 已检测且有效的标志
)

/**
 * PanSou 后端 API 客户端（纯框架实现，无第三方网络库）。
 *
 * 接口：GET {base}/api/search?kw=…&src=…&res=…&cloud_types=…&plugins=…&channels=…&refresh=…
 * 返回：{ code, message, data: { total, merged_by_type: {...}, results: [...] } }
 */
object Api {

    private const val TYPE_LABELS =
        "baidu=百度,aliyun=阿里,quark=夸克,guangya=光鸭,tianyi=天翼,115=115,xunlei=迅雷,uc=UC," +
            "mobile=移动,pikpak=PikPak,123=123,magnet=磁力,ed2k=电驴,other=其他"

    fun typeLabel(type: String): String {
        TYPE_LABELS.split(",").forEach {
            val parts = it.split("=")
            if (parts[0] == type) return parts[1]
        }
        return type
    }

    fun search(
        baseUrl: String,
        keyword: String,
        cfg: SearchConfig = SearchConfig()
    ): List<CloudItem> {
        val params = mutableListOf("kw=" + URLEncoder.encode(keyword, "UTF-8"))
        if (cfg.src != "all") params += "src=${cfg.src}"
        if (cfg.res != "merge") params += "res=${cfg.res}"
        if (cfg.cloudTypes.isNotBlank())
            params += "cloud_types=" + URLEncoder.encode(cfg.cloudTypes, "UTF-8")
        if (cfg.plugins.isNotBlank())
            params += "plugins=" + URLEncoder.encode(cfg.plugins, "UTF-8")
        if (cfg.channels.isNotBlank())
            params += "channels=" + URLEncoder.encode(cfg.channels, "UTF-8")
        if (cfg.refresh) params += "refresh=true"

        val endpoint = baseUrl.trimEnd('/') + "/api/search?" + params.joinToString("&")
        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 120000   // 聚合多源搜索可能较慢
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("服务器返回 HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            return parseResponse(body)
        } finally {
            conn.disconnect()
        }
    }

    /** 解析 /api/search 响应（独立出来便于单元测试） */
    fun parseResponse(body: String): List<CloudItem> {
        val json = JSONObject(body)
        if (json.optInt("code", -1) != 0) {
            throw IllegalStateException(json.optString("message", "搜索失败"))
        }
        val data = json.optJSONObject("data") ?: return emptyList()
        val items = mutableListOf<CloudItem>()

        // 优先解析 merged_by_type（res=merge / all 时存在）
        val grouped = data.optJSONObject("merged_by_type")
        if (grouped != null) {
            for (type in grouped.keys()) {
                val arr = grouped.optJSONArray(type) ?: continue
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val url = o.optString("url")
                    if (url.isBlank()) continue
                    items.add(
                        CloudItem(
                            type = type,
                            title = o.optString("note").ifBlank { "（无标题）" },
                            url = url,
                            password = o.optString("password"),
                            datetime = o.optString("datetime"),
                            source = o.optString("source")
                        )
                    )
                }
            }
            return items
        }

        // 回退解析 results（res=results 时）：results[].links[].{type,url}
        val results = data.optJSONArray("results") ?: return emptyList()
        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val title = r.optString("note").ifBlank {
                r.optString("title").ifBlank { "（无标题）" }
            }
            val links = r.optJSONArray("links") ?: continue
            for (j in 0 until links.length()) {
                val l = links.optJSONObject(j) ?: continue
                val url = l.optString("url")
                if (url.isBlank()) continue
                items.add(
                    CloudItem(
                        type = l.optString("type"),
                        title = title,
                        url = url,
                        password = r.optString("password").ifBlank { l.optString("password") },
                        datetime = r.optString("datetime"),
                        source = r.optString("source")
                    )
                )
            }
        }
        return items
    }

    /** 简易连通性测试，返回 HTTP 状态码 */
    fun ping(baseUrl: String): Int {
        val conn = URL(baseUrl).openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.requestMethod = "GET"
        return try {
            conn.responseCode
        } finally {
            conn.disconnect()
        }
    }

    // ==================== 链接检测 ====================

    /** 链接检测结果 */
    data class CheckResult(
        val diskType: String,
        val url: String,
        val state: String,
        val summary: String
    )

    /** state 字段的中文文案（与网页版一致） */
    fun stateLabel(state: String): String = when (state) {
        "ok" -> "链接有效"
        "bad" -> "链接失效"
        "locked" -> "需要提取码"
        "unsupported" -> "暂不支持检测"
        "uncertain" -> "状态未知"
        "pending" -> "检测中"
        else -> "未检测"
    }

    /** 检测后应从列表移除：明确失效 / 无法确认有效（实测死链即返回 uncertain） */
    fun isRemovableState(state: String): Boolean =
        state == "bad" || state == "uncertain"

    /** 检测后可打「有效」徽标 */
    fun isValidState(state: String): Boolean = state == "ok"

    /**
     * 批量链接检测：POST {base}/api/check/links
     * 请求：{"items":[{"disk_type":"quark","url":"https://…"}, …]}
     * 返回：{ results: [{ disk_type, url, normalized_url, state, summary, ... }] }
     */
    fun checkLinksBatch(baseUrl: String, items: List<Pair<String, String>>): List<CheckResult> {
        if (items.isEmpty()) return emptyList()
        val endpoint = baseUrl.trimEnd('/') + "/api/check/links"
        val arr = JSONArray()
        items.forEach { (type, url) ->
            arr.put(JSONObject().put("disk_type", type).put("url", url))
        }
        val payload = JSONObject().put("items", arr).toString()

        val conn = URL(endpoint).openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 60000
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Accept", "application/json")
        try {
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("服务器返回 HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            return parseCheckResponses(body, items)
        } finally {
            conn.disconnect()
        }
    }

    /** 解析 /api/check/links 批量响应（独立出来便于单元测试） */
    fun parseCheckResponses(
        body: String,
        requested: List<Pair<String, String>> = emptyList()
    ): List<CheckResult> {
        val arr = JSONObject(body).optJSONArray("results")
            ?: throw IllegalStateException("检测接口返回格式错误")
        val byKey = mutableMapOf<Pair<String, String>, CheckResult>()
        val inOrder = mutableListOf<CheckResult>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val state = o.optString("state")
            val r = CheckResult(
                diskType = o.optString("disk_type"),
                url = o.optString("url"),
                state = state,
                summary = o.optString("summary").ifBlank { stateLabel(state) }
            )
            inOrder.add(r)
            byKey[r.diskType to r.url] = r
        }
        if (requested.isEmpty()) return inOrder
        // 按请求顺序对齐：优先精确匹配 type+url，其次按顺序兜底
        return requested.mapIndexed { i, (type, url) ->
            byKey[type to url] ?: inOrder.getOrNull(i)
                ?: CheckResult(type, url, "uncertain", stateLabel("uncertain"))
        }
    }

    /** ISO 时间转友好格式：2026-10-16T05:31:15Z -> 2026-10-16 05:31 */
    fun formatTime(iso: String): String {
        return if (iso.length >= 16) {
            iso.substring(0, 10) + " " + iso.substring(11, 16)
        } else {
            iso
        }
    }

    // ==================== 服务器信息（频道 / 插件列表） ====================

    /** 服务器可用的搜索来源列表（/api/health） */
    data class ServerInfo(val channels: List<String>, val plugins: List<String>)

    /** 拉取 /api/health 获取可配置的 TG 频道与搜索插件列表 */
    fun fetchServerInfo(baseUrl: String): ServerInfo {
        val conn = URL(baseUrl.trimEnd('/') + "/api/health").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.requestMethod = "GET"
        try {
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("服务器返回 HTTP $code")
            }
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            return parseHealth(body)
        } finally {
            conn.disconnect()
        }
    }

    /** 解析 /api/health 响应（独立出来便于单元测试） */
    fun parseHealth(body: String): ServerInfo {
        val json = JSONObject(body)
        fun names(key: String): List<String> {
            val arr = json.optJSONArray(key) ?: return emptyList()
            return (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).takeIf { it.isNotBlank() }
            }
        }
        return ServerInfo(channels = names("channels"), plugins = names("plugins"))
    }
}
