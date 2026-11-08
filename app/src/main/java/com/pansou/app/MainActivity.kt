package com.pansou.app

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var editQuery: EditText
    private lateinit var chipGroup: ChipGroup
    private lateinit var textCount: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var errorState: LinearLayout
    private lateinit var errorDetail: TextView
    private lateinit var loadingState: LinearLayout
    private lateinit var historySection: LinearLayout
    private lateinit var historyChips: ChipGroup

    private val adapter = ResultAdapter { item -> showItemActions(item) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var allResults: List<CloudItem> = emptyList()
    private var currentQuery: String = ""
    private var inSearch: Boolean = false   // 是否处于搜索结果/加载/错误状态
    private var currentFilter: String = TYPE_ALL
    private var checking: Boolean = false   // 是否正在批量检测

    companion object {
        private const val TYPE_ALL = "__all__"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setTitleTextColor(getColor(R.color.on_primary))
        toolbar.inflateMenu(R.menu.menu_main)
        toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_refresh -> {
                    if (currentQuery.isNotEmpty()) doSearch(currentQuery, forceRefresh = true)
                    true
                }
                R.id.menu_settings -> { openSettings(); true }
                else -> false
            }
        }

        editQuery = findViewById(R.id.editQuery)
        chipGroup = findViewById(R.id.chipGroup)
        textCount = findViewById(R.id.textCount)
        recycler = findViewById(R.id.recycler)
        emptyState = findViewById(R.id.emptyState)
        errorState = findViewById(R.id.errorState)
        errorDetail = findViewById(R.id.errorDetail)
        loadingState = findViewById(R.id.loadingState)
        historySection = findViewById(R.id.historySection)
        historyChips = findViewById(R.id.historyChips)

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<View>(R.id.btnSearch).setOnClickListener { doSearch(editQuery.text.toString()) }
        findViewById<View>(R.id.btnRetry).setOnClickListener { doSearch(currentQuery) }
        findViewById<View>(R.id.btnValidCheck).setOnClickListener { runValidCheck() }
        findViewById<View>(R.id.btnClearHistory).setOnClickListener {
            Prefs.clearHistory(this)
            refreshHistory()
        }
        editQuery.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(editQuery.text.toString())
                true
            } else false
        }

        // 主页底部显示版本号（动态读取自打包信息）
        findViewById<TextView>(R.id.textVersion).text = getString(
            R.string.about_version_short,
            packageManager.getPackageInfo(packageName, 0).versionName
        )

        // 搜索框内 ✕ 按钮：有文字时显示，点击清空并返回主页
        val clearIcon = findViewById<ImageView>(R.id.clearIcon)
        editQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearIcon.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
        })
        clearIcon.setOnClickListener { goHome() }

        refreshHistory()
    }

    /** 刷新主页搜索历史区块 */
    private fun refreshHistory() {
        val history = Prefs.getHistory(this)
        historySection.visibility = if (history.isEmpty()) View.GONE else View.VISIBLE
        historyChips.removeAllViews()
        history.forEach { kw ->
            val chip = Chip(this).apply {
                text = kw
                setOnClickListener {
                    editQuery.setText(kw)
                    doSearch(kw)
                }
            }
            historyChips.addView(chip)
        }
    }

    /** 清空搜索，返回初始主页 */
    private fun goHome() {
        currentQuery = ""
        inSearch = false
        allResults = emptyList()
        adapter.submit(emptyList())
        chipGroup.removeAllViews()
        editQuery.setText("")
        showState()
        hideKeyboard()
        refreshHistory()
    }

    private fun doSearch(raw: String, forceRefresh: Boolean = false) {
        val kw = raw.trim()
        if (kw.isEmpty()) {
            Toast.makeText(this, R.string.input_keyword, Toast.LENGTH_SHORT).show()
            return
        }
        currentQuery = kw
        Prefs.addHistory(this, kw)
        inSearch = true
        hideKeyboard()
        showState(loading = true)

        val cfg = Prefs.getConfig(this).let {
            if (forceRefresh) it.copy(refresh = true) else it
        }

        executor.execute {
            try {
                val items = Api.search(Prefs.getServerUrl(this), kw, cfg)
                mainHandler.post { showResults(items) }
            } catch (e: Exception) {
                mainHandler.post {
                    errorDetail.text = e.message ?: e.javaClass.simpleName
                    showState(error = true)
                }
            }
        }
    }

    private fun showResults(items: List<CloudItem>) {
        allResults = items
        currentFilter = TYPE_ALL
        if (items.isEmpty()) {
            textCount.text = getString(R.string.no_result)
            adapter.submit(emptyList())
            buildChips(emptyMap())
            showState(results = true)
            return
        }
        val counts = items.groupingBy { it.type }.eachCount()
        buildChips(counts)
        textCount.text = getString(R.string.result_count, items.size)
        adapter.submit(items)
        showState(results = true)
    }

    /** 按网盘类型生成筛选芯片（保持当前选中类型） */
    private fun buildChips(counts: Map<String, Int>) {
        chipGroup.removeAllViews()
        addChip("全部", allResults.size, TYPE_ALL, currentFilter == TYPE_ALL)
        counts.entries.sortedByDescending { it.value }.forEach { (type, count) ->
            addChip(Api.typeLabel(type), count, type, currentFilter == type)
        }
    }

    private fun addChip(label: String, count: Int, key: String, checked: Boolean) {
        val chip = Chip(this).apply {
            text = "$label ($count)"
            isCheckable = true
            isChecked = checked
            setOnClickListener {
                chipGroup.clearCheck()
                isChecked = true
                applyFilter(key)
            }
        }
        chipGroup.addView(chip)
    }

    private fun applyFilter(type: String) {
        currentFilter = type
        val filtered = if (type == TYPE_ALL) allResults else allResults.filter { it.type == type }
        textCount.text = getString(R.string.result_count, filtered.size)
        adapter.submit(filtered)
    }

    /**
     * 手动批量检测：只检测当前选中类型的结果（adapter.currentItems() 即筛选后列表）。
     * 每 20 条一批调用 /api/check/links，失效项从列表移除，有效项打徽标。
     */
    private fun runValidCheck() {
        val targets = adapter.currentItems()
        if (targets.isEmpty() || checking) return
        checking = true
        val base = Prefs.getServerUrl(this)

        executor.execute {
            val removedKeys = mutableSetOf<Pair<String, String>>()
            val validKeys = mutableSetOf<Pair<String, String>>()
            try {
                val chunks = targets.chunked(20)
                chunks.forEachIndexed { idx, chunk ->
                    mainHandler.post {
                        textCount.text = getString(
                            R.string.checking_progress,
                            idx * 20 + chunk.size, targets.size
                        )
                    }
                    val results = Api.checkLinksBatch(base, chunk.map { it.type to it.url })
                    chunk.zip(results).forEach { (item, r) ->
                        when {
                            Api.isValidState(r.state) -> validKeys.add(item.type to item.url)
                            Api.isRemovableState(r.state) -> removedKeys.add(item.type to item.url)
                            // locked（要提取码）/ unsupported（不支持检测）等：保留但不打徽标
                        }
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    checking = false
                    Toast.makeText(
                        this,
                        getString(R.string.test_fail, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_SHORT
                    ).show()
                    textCount.text = getString(R.string.result_count, adapter.itemCount)
                }
                return@execute
            }

            mainHandler.post {
                checking = false
                // 移除失效链接；检测通过的打「有效」标志（保持当前类型筛选）
                allResults = allResults
                    .filterNot { (it.type to it.url) in removedKeys }
                    .map { if ((it.type to it.url) in validKeys) it.copy(valid = true) else it }
                val visible = if (currentFilter == TYPE_ALL) allResults
                else allResults.filter { it.type == currentFilter }
                adapter.submit(visible)
                buildChips(allResults.groupingBy { it.type }.eachCount())
                textCount.text = if (removedKeys.isEmpty()) {
                    getString(R.string.all_valid)
                } else {
                    getString(R.string.removed_invalid, removedKeys.size)
                }
            }
        }
    }

    private fun showState(loading: Boolean = false, results: Boolean = false, error: Boolean = false) {
        loadingState.visibility = if (loading) View.VISIBLE else View.GONE
        errorState.visibility = if (error) View.VISIBLE else View.GONE
        emptyState.visibility =
            if (!loading && !results && !error) View.VISIBLE else View.GONE
        findViewById<View>(R.id.resultContainer).visibility =
            if (results) View.VISIBLE else View.GONE
    }

    /** 液态玻璃操作面板 */
    private fun showItemActions(item: CloudItem) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_item_actions)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        dialog.findViewById<TextView>(R.id.dialogTitle).text = item.title
        dialog.findViewById<TextView>(R.id.dialogUrl).text = item.url

        val rowCopyPassword = dialog.findViewById<TextView>(R.id.rowCopyPassword)
        if (item.password.isBlank()) rowCopyPassword.visibility = View.GONE
        dialog.findViewById<View>(R.id.rowCopyUrl).setOnClickListener {
            copyToClipboard("链接", item.url)
            dialog.dismiss()
        }
        rowCopyPassword.setOnClickListener {
            copyToClipboard("提取码", item.password)
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.rowOpen).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.rowShare).setOnClickListener {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "${item.title}\n${item.url}" +
                        if (item.password.isNotBlank()) "\n提取码：${item.password}" else ""
                )
            }
            startActivity(Intent.createChooser(send, item.title))
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun copyToClipboard(label: String, text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(editQuery.windowToken, 0)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 搜索状态下按返回 = 清空返回主页，主页再按返回 = 退出
        if (inSearch) {
            goHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
