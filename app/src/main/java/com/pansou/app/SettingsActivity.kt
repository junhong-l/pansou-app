package com.pansou.app

import android.os.Bundle
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
    private lateinit var editPlugins: TextInputEditText
    private lateinit var editChannels: TextInputEditText
    private lateinit var switchRefresh: android.widget.Switch

    // 链接检测（自动过滤失效链接）
    private lateinit var switchAutoCheck: android.widget.Switch

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
        editPlugins = findViewById(R.id.editPlugins)
        editChannels = findViewById(R.id.editChannels)
        switchRefresh = findViewById(R.id.switchRefresh)

        switchAutoCheck = findViewById(R.id.switchAutoCheck)

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

        findViewById<android.view.View>(R.id.btnSave).setOnClickListener { save() }
        findViewById<android.view.View>(R.id.btnTest).setOnClickListener { test() }
        findViewById<android.view.View>(R.id.btnReset).setOnClickListener {
            editUrl.setText(Prefs.DEFAULT_SERVER_URL)
        }
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
            chip.isChecked = tag(chip) in cloudSet
        }
        editPlugins.setText(cfg.plugins)
        editChannels.setText(cfg.channels)
        switchRefresh.isChecked = cfg.refresh
        switchAutoCheck.isChecked = Prefs.getAutoCheck(this)
    }

    private fun tag(v: android.view.View): String = v.tag?.toString() ?: ""

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

    private fun save() {
        val url = Prefs.normalizeUrl(editUrl.text.toString())
        if (url == null) {
            Toast.makeText(this, R.string.settings_invalid_url, Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setServerUrl(this, url)
        Prefs.setConfig(
            this,
            SearchConfig(
                src = checkedTag(chipSrc).ifEmpty { "all" },
                res = checkedTag(chipRes).ifEmpty { "merge" },
                cloudTypes = checkedTags(chipCloud).joinToString(","),
                plugins = editPlugins.text.toString().trim(),
                channels = editChannels.text.toString().trim(),
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
