package com.pansou.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import java.util.concurrent.Executors

class SettingsActivity : AppCompatActivity() {

    private lateinit var editUrl: TextInputEditText
    private lateinit var testResult: TextView
    private lateinit var chipSrc: ChipGroup
    private lateinit var chipRes: ChipGroup
    private lateinit var chipCloud: ChipGroup
    private lateinit var switchRefresh: android.widget.Switch
    private lateinit var switchAutoCheck: android.widget.Switch

    // 频道 / 插件动态勾选（列表来自 /api/health）
    private lateinit var chipChannels: ChipGroup
    private lateinit var chipPlugins: ChipGroup
    private lateinit var countChannels: TextView
    private lateinit var countPlugins: TextView
    private lateinit var loadStatus: TextView

    private var serverInfo: Api.ServerInfo? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(getColor(R.color.on_primary))
        toolbar.setNavigationOnClickListener { finish() }

        editUrl = findViewById(R.id.editServerUrl)
        testResult = findViewById(R.id.testResult)
        chipSrc = findViewById(R.id.chipSrc)
        chipRes = findViewById(R.id.chipRes)
        chipCloud = findViewById(R.id.chipCloud)
        switchRefresh = findViewById(R.id.switchRefresh)
        switchAutoCheck = findViewById(R.id.switchAutoCheck)

        chipChannels = findViewById(R.id.chipChannels)
        chipPlugins = findViewById(R.id.chipPlugins)
        countChannels = findViewById(R.id.countChannels)
        countPlugins = findViewById(R.id.countPlugins)
        loadStatus = findViewById(R.id.loadStatus)

        // 关于：版本号动态读取自打包信息
        val pkg = packageManager.getPackageInfo(packageName, 0)
        findViewById<TextView>(R.id.aboutVersion).text = getString(
            R.string.about_version_format,
            pkg.versionName,
            androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pkg)
        )
        findViewById<TextView>(R.id.aboutBackend).text =
            getString(R.string.about_backend_format, Prefs.getServerUrl(this))

        loadValues()
        setupSourceSections()

        findViewById<View>(R.id.btnSave).setOnClickListener { save() }
        findViewById<View>(R.id.btnTest).setOnClickListener { test() }
        findViewById<View>(R.id.btnReset).setOnClickListener {
            editUrl.setText(Prefs.DEFAULT_SERVER_URL)
        }

        loadServerInfo()
    }

    /** 频道 / 插件区块：展开收起、全选清空、重试加载 */
    private fun setupSourceSections() {
        findViewById<View>(R.id.headerChannels).setOnClickListener {
            toggleSection(R.id.bodyChannels, R.id.toggleChannels)
        }
        findViewById<View>(R.id.headerPlugins).setOnClickListener {
            toggleSection(R.id.bodyPlugins, R.id.togglePlugins)
        }
        findViewById<View>(R.id.btnAllChannels).setOnClickListener { setAll(chipChannels, true) }
        findViewById<View>(R.id.btnNoneChannels).setOnClickListener { setAll(chipChannels, false) }
        findViewById<View>(R.id.btnAllPlugins).setOnClickListener { setAll(chipPlugins, true) }
        findViewById<View>(R.id.btnNonePlugins).setOnClickListener { setAll(chipPlugins, false) }
        loadStatus.setOnClickListener { loadServerInfo() }
    }

    private fun toggleSection(bodyId: Int, toggleId: Int) {
        val body = findViewById<View>(bodyId)
        val toggle = findViewById<TextView>(toggleId)
        val opening = body.visibility == View.GONE
        body.visibility = if (opening) View.VISIBLE else View.GONE
        toggle.text = getString(if (opening) R.string.collapse else R.string.expand)
    }

    private fun setAll(group: ChipGroup, checked: Boolean) {
        for (i in 0 until group.childCount) {
            (group.getChildAt(i) as? Chip)?.isChecked = checked
        }
        updateCounts()
    }

    /** 从后端拉取频道 / 插件列表 */
    private fun loadServerInfo() {
        val base = Prefs.normalizeUrl(editUrl.text.toString()) ?: Prefs.getServerUrl(this)
        loadStatus.visibility = View.VISIBLE
        loadStatus.text = getString(R.string.loading_lists)
        executor.execute {
            try {
                val info = Api.fetchServerInfo(base)
                runOnUiThread {
                    serverInfo = info
                    populateSourceChips(info)
                    loadStatus.visibility = View.GONE
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadStatus.text = getString(R.string.load_failed_retry)
                }
            }
        }
    }

    private fun populateSourceChips(info: Api.ServerInfo) {
        val cfg = Prefs.getConfig(this)
        buildNameChips(chipChannels, info.channels, cfg.channels)
        buildNameChips(chipPlugins, info.plugins, cfg.plugins)
        updateCounts()
    }

    /** 频道 / 插件选择芯片；saved 为空表示全部选中 */
    private fun buildNameChips(group: ChipGroup, names: List<String>, saved: String) {
        group.removeAllViews()
        val selected = saved.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        names.forEach { name ->
            val chip = Chip(this).apply {
                text = name
                tag = name
                isCheckable = true
                isChecked = saved.isEmpty() || name in selected
                setOnClickListener { updateCounts() }
            }
            group.addView(chip)
        }
    }

    private fun updateCounts() {
        countChannels.text = getString(
            R.string.selected_format, checkedTags(chipChannels).size, chipChannels.childCount
        )
        countPlugins.text = getString(
            R.string.selected_format, checkedTags(chipPlugins).size, chipPlugins.childCount
        )
    }

    /** 从 Prefs 恢复当前配置到界面 */
    private fun loadValues() {
        editUrl.setText(Prefs.getServerUrl(this))
        val cfg = Prefs.getConfig(this)
        checkByTag(chipSrc, cfg.src)
        checkByTag(chipRes, cfg.res)
        val cloudSet = cfg.cloudTypes.split(",").map { it.trim() }.toSet()
        for (i in 0 until chipCloud.childCount) {
            val chip = chipCloud.getChildAt(i) as? Chip ?: continue
            chip.isChecked = if (cfg.cloudTypes.isEmpty()) true else tag(chip) in cloudSet
        }
        switchRefresh.isChecked = cfg.refresh
        switchAutoCheck.isChecked = Prefs.getAutoCheck(this)
        updateCounts()
    }

    private fun tag(v: View): String = v.tag?.toString() ?: ""

    private fun checkByTag(group: ChipGroup, value: String) {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            chip.isChecked = tag(chip) == value
        }
    }

    private fun checkedTag(group: ChipGroup): String {
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) return tag(chip)
        }
        return ""
    }

    private fun checkedTags(group: ChipGroup): List<String> {
        val out = mutableListOf<String>()
        for (i in 0 until group.childCount) {
            val chip = group.getChildAt(i) as? Chip ?: continue
            if (chip.isChecked) out.add(tag(chip))
        }
        return out
    }

    /** 全选或未加载时返回空串（= 使用服务端默认全量），否则返回勾选子集 */
    private fun pickSubset(group: ChipGroup, total: Int, fallback: String): String {
        if (serverInfo == null || total == 0) return fallback
        val sel = checkedTags(group)
        return if (sel.size == total) "" else sel.joinToString(",")
    }

    private fun save() {
        val url = Prefs.normalizeUrl(editUrl.text.toString())
        if (url == null) {
            Toast.makeText(this, R.string.settings_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setServerUrl(this, url)
        val prev = Prefs.getConfig(this)
        Prefs.setConfig(
            this,
            SearchConfig(
                src = checkedTag(chipSrc).ifEmpty { "all" },
                res = checkedTag(chipRes).ifEmpty { "merge" },
                cloudTypes = checkedTags(chipCloud).joinToString(","),
                plugins = pickSubset(chipPlugins, chipPlugins.childCount, prev.plugins),
                channels = pickSubset(chipChannels, chipChannels.childCount, prev.channels),
                refresh = switchRefresh.isChecked
            )
        )
        Prefs.setAutoCheck(this, switchAutoCheck.isChecked)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    /** 后台线程测试连接 */
    private fun test() {
        val url = Prefs.normalizeUrl(editUrl.text.toString())
        if (url == null) {
            Toast.makeText(this, R.string.settings_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        testResult.text = getString(R.string.testing)
        executor.execute {
            val text = try {
                getString(R.string.test_ok, Api.ping(url))
            } catch (e: Exception) {
                getString(R.string.test_fail, e.message ?: e.javaClass.simpleName)
            }
            runOnUiThread { testResult.text = text }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
