package com.pansou.app

import android.content.Context

/** 搜索配置（与网页版配置页字段一致） */
data class SearchConfig(
    val src: String = "all",          // all / tg / plugin
    val res: String = "merge",        // merge / all / results
    val cloudTypes: String = "",      // 逗号分隔，空 = 全部
    val plugins: String = "",         // 逗号分隔，空 = 默认
    val channels: String = "",        // 逗号分隔，空 = 默认
    val refresh: Boolean = false      // 强制刷新（不使用缓存）
)

/**
 * SharedPreferences 封装：后端服务器地址 + 搜索配置。
 */
object Prefs {
    private const val FILE_NAME = "pansou_prefs"
    private const val KEY_SERVER_URL = "server_url"
    private const val KEY_SRC = "src"
    private const val KEY_RES = "res"
    private const val KEY_CLOUD = "cloud_types"
    private const val KEY_PLUGINS = "plugins"
    private const val KEY_CHANNELS = "channels"
    private const val KEY_REFRESH = "refresh"
    private const val KEY_HISTORY = "search_history"

    /** 默认后端地址，可在设置中修改 */
    const val DEFAULT_SERVER_URL = "http://192.168.9.110:1080/"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun getServerUrl(context: Context): String {
        val url = sp(context).getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        return url.trim()
    }

    fun setServerUrl(context: Context, url: String) {
        sp(context).edit().putString(KEY_SERVER_URL, url.trim()).apply()
    }

    fun getConfig(context: Context): SearchConfig {
        val s = sp(context)
        return SearchConfig(
            src = s.getString(KEY_SRC, "all") ?: "all",
            res = s.getString(KEY_RES, "merge") ?: "merge",
            cloudTypes = s.getString(KEY_CLOUD, "") ?: "",
            plugins = s.getString(KEY_PLUGINS, "") ?: "",
            channels = s.getString(KEY_CHANNELS, "") ?: "",
            refresh = s.getBoolean(KEY_REFRESH, false)
        )
    }

    fun setConfig(context: Context, cfg: SearchConfig) {
        sp(context).edit()
            .putString(KEY_SRC, cfg.src)
            .putString(KEY_RES, cfg.res)
            .putString(KEY_CLOUD, cfg.cloudTypes)
            .putString(KEY_PLUGINS, cfg.plugins)
            .putString(KEY_CHANNELS, cfg.channels)
            .putBoolean(KEY_REFRESH, cfg.refresh)
            .apply()
    }

    // ==================== 搜索历史 ====================

    fun getHistory(context: Context): List<String> =
        sp(context).getString(KEY_HISTORY, "")
            ?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()

    fun addHistory(context: Context, keyword: String) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        val list = (listOf(kw) + getHistory(context).filter { it != kw }).take(20)
        sp(context).edit().putString(KEY_HISTORY, list.joinToString("\n")).apply()
    }

    fun clearHistory(context: Context) {
        sp(context).edit().remove(KEY_HISTORY).apply()
    }

    /**
     * 规范化用户输入的地址：去掉首尾空白，补全结尾斜杠；
     * 非 http/https 开头返回 null。
     */
    fun normalizeUrl(input: String): String? {
        var url = input.trim()
        if (url.isEmpty()) return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null
        // 去掉多余的结尾斜杠后再统一补一个
        while (url.endsWith("/")) url = url.dropLast(1)
        return "$url/"
    }
}
