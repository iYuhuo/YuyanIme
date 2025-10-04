package com.yuyan.imemodule.ui.activity

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yuyan.imemodule.R
import com.yuyan.imemodule.prefs.AppPrefs
import com.yuyan.imemodule.voice.VoiceModel
import com.yuyan.imemodule.voice.VoiceModelManager
import java.io.File
import java.util.concurrent.Executors

/**
 * 语音模型管理Activity
 */
class VoiceModelManagementActivity : AppCompatActivity() {
    
    companion object {
        private const val REQUEST_CODE_SELECT_FILE = 1001
    }
    
    private lateinit var modelManager: VoiceModelManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ModelAdapter
    private var models = listOf<VoiceModel>()
    
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var progressDialog: ProgressDialog? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 创建主布局
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.WHITE)
            fitsSystemWindows = true  // 适配系统窗口，避免覆盖状态栏
        }
        
        // 创建Toolbar
        val toolbar = Toolbar(this).apply {
            setTitle(R.string.voice_model_management)
            setTitleTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
            elevation = 4f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 设置返回按钮
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        // 创建导入按钮容器
        val importContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val importButton = Button(this).apply {
            text = getString(R.string.voice_model_import)
            setBackgroundColor(Color.parseColor("#2196F3"))
            setTextColor(Color.WHITE)
            setPadding(24, 12, 24, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                openFilePicker()
            }
        }
        
        importContainer.addView(importButton)
        
        // 创建RecyclerView
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VoiceModelManagementActivity)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            // 添加分割线
            addItemDecoration(DividerItemDecoration(this@VoiceModelManagementActivity, DividerItemDecoration.VERTICAL))
        }
        
        // 添加视图到主布局
        mainLayout.addView(toolbar)
        mainLayout.addView(importContainer)
        mainLayout.addView(recyclerView)
        
        setContentView(mainLayout)
        
        // 初始化
        modelManager = VoiceModelManager.getInstance(this)
        loadModels()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    private fun loadModels() {
        models = modelManager.getAllModels()
        adapter = ModelAdapter(models, ::onModelSelected, ::onModelDeleted)
        recyclerView.adapter = adapter
    }
    
    private fun onModelSelected(model: VoiceModel) {
        // 保存选择的模型
        AppPrefs.getInstance().internal.voiceModelId.setValue(model.id)
        
        // 显示提示信息
        val message = if (model.size > 50 * 1024 * 1024) {
            "正在切换到大模型，将在后台加载..."
        } else {
            "正在切换模型..."
        }
        
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        
        // 在后台线程切换模型，避免阻塞UI
        Thread {
            val voiceRecognizer = com.yuyan.imemodule.voice.VoiceRecognizer.getInstance(this)
            val success = voiceRecognizer.switchModel(model.id)
            
            // 切换回主线程显示结果
            runOnUiThread {
                if (success) {
                    val successMsg = if (model.size > 50 * 1024 * 1024) {
                        "已开始加载大模型，请稍后使用语音输入"
                    } else {
                        getString(R.string.voice_model_switch_success)
                    }
                    Toast.makeText(this, successMsg, Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(
                        this,
                        "模型切换失败，请重试",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // 刷新列表
                adapter.notifyDataSetChanged()
            }
        }.start()
        
        // 返回结果
        setResult(Activity.RESULT_OK)
    }
    
    private fun onModelDeleted(model: VoiceModel) {
        if (model.isBuiltIn) {
            Toast.makeText(
                this,
                "无法删除内置模型",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voice_model_delete))
            .setMessage(getString(R.string.voice_model_delete_confirm, model.name))
            .setPositiveButton("确定") { _, _ ->
                if (modelManager.deleteModel(model.id)) {
                    Toast.makeText(
                        this,
                        getString(R.string.voice_model_delete_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadModels()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.voice_model_delete_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/zip"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_SELECT_FILE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_SELECT_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                importModelFromUri(uri)
            }
        }
    }
    
    private fun importModelFromUri(uri: Uri) {
        try {
            // 显示导入对话框
            showImportDialog(uri)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "${getString(R.string.voice_model_import_failed)}: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun showImportDialog(uri: Uri) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(this).apply {
            hint = getString(R.string.voice_model_name)
        }
        
        val descInput = EditText(this).apply {
            hint = getString(R.string.voice_model_description) + " (可选)"
        }
        
        dialogView.addView(nameInput)
        dialogView.addView(descInput)
        
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.voice_model_import))
            .setView(dialogView)
            .setPositiveButton("导入") { _, _ ->
                val name = nameInput.text.toString().trim()
                val description = descInput.text.toString().trim()
                
                if (name.isBlank()) {
                    Toast.makeText(this, "请输入模型名称", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // 使用默认值：语言=zh, 类型=zipformer
                importModelInBackground(uri, name, "zh", "zipformer", description)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun importModelInBackground(uri: Uri, name: String, language: String, modelType: String, description: String) {
        // 显示进度对话框
        progressDialog = ProgressDialog(this).apply {
            setTitle("导入模型")
            setMessage("正在复制和解压模型文件，请稍候...")
            setCancelable(false)
            setProgressStyle(ProgressDialog.STYLE_SPINNER)
            show()
        }
        
        executor.execute {
            try {
                // 复制ZIP文件到临时位置
                val tempFile = File(cacheDir, "temp_model_${System.currentTimeMillis()}.zip")
                
                mainHandler.post {
                    progressDialog?.setMessage("正在复制文件...")
                }
                
                contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        val fileSize = contentResolver.openFileDescriptor(uri, "r")?.statSize ?: 0L
                        
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                            
                            // 更新进度
                            if (fileSize > 0) {
                                val progress = (totalBytes * 50 / fileSize).toInt()
                                mainHandler.post {
                                    progressDialog?.setMessage("正在复制文件... ${progress}%")
                                }
                            }
                        }
                    }
                }
                
                mainHandler.post {
                    progressDialog?.setMessage("正在解压模型文件...")
                }
                
                // 创建模型信息
                val modelId = "custom_${System.currentTimeMillis()}"
                val model = VoiceModel(
                    id = modelId,
                    name = name,
                    language = language,
                    modelType = modelType,
                    modelDir = modelId,
                    isBuiltIn = false,
                    description = description
                )
                
                // 导入模型
                val success = modelManager.importModelFromZip(tempFile, model)
                
                // 清理临时文件
                tempFile.delete()
                
                mainHandler.post {
                    progressDialog?.dismiss()
                    progressDialog = null
                    
                    if (success) {
                        Toast.makeText(
                            this@VoiceModelManagementActivity,
                            getString(R.string.voice_model_import_success),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadModels()
                    } else {
                        // 显示详细的失败信息
                        val errorMsg = "导入失败，请检查日志或尝试其他文件"
                        Toast.makeText(
                            this@VoiceModelManagementActivity,
                            errorMsg,
                            Toast.LENGTH_LONG
                        ).show()
                        
                        // 显示错误对话框
                        AlertDialog.Builder(this@VoiceModelManagementActivity)
                            .setTitle("导入失败")
                            .setMessage("模型导入失败，可能的原因：\n\n1. ZIP文件格式不正确\n2. 缺少必要的模型文件（.onnx文件和tokens.txt）\n3. 文件损坏\n4. 存储空间不足\n\n请检查Android日志获取详细信息")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
                
            } catch (e: Exception) {
                mainHandler.post {
                    progressDialog?.dismiss()
                    progressDialog = null
                    
                    Toast.makeText(
                        this@VoiceModelManagementActivity,
                        "${getString(R.string.voice_model_import_failed)}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        progressDialog?.dismiss()
    }
    
    /**
     * 模型列表适配器
     */
    inner class ModelAdapter(
        private val models: List<VoiceModel>,
        private val onSelect: (VoiceModel) -> Unit,
        private val onDelete: (VoiceModel) -> Unit
    ) : RecyclerView.Adapter<ModelAdapter.ViewHolder>() {
        
        inner class ViewHolder(val container: LinearLayout) : RecyclerView.ViewHolder(container) {
            val nameText: TextView = TextView(this@VoiceModelManagementActivity).apply {
                textSize = 18f
                setTextColor(Color.BLACK)
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            
            val infoText: TextView = TextView(this@VoiceModelManagementActivity).apply {
                textSize = 14f
                setTextColor(Color.GRAY)
                setPadding(0, 4, 0, 8)
            }
            
            val statusText: TextView = TextView(this@VoiceModelManagementActivity).apply {
                textSize = 12f
                setTextColor(Color.parseColor("#4CAF50"))
                setPadding(0, 0, 0, 12)
            }
            
            val buttonContainer: LinearLayout = LinearLayout(this@VoiceModelManagementActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 8, 0, 0)
            }
            
            val selectButton: Button = Button(this@VoiceModelManagementActivity).apply {
                text = "选择"
                textSize = 14f
                setBackgroundColor(Color.parseColor("#2196F3"))
                setTextColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            
            val versionButton: Button = Button(this@VoiceModelManagementActivity).apply {
                text = "版本"
                textSize = 14f
                setBackgroundColor(Color.parseColor("#FF9800"))
                setTextColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
            }
            
            val deleteButton: Button = Button(this@VoiceModelManagementActivity).apply {
                text = "删除"
                textSize = 14f
                setBackgroundColor(Color.parseColor("#F44336"))
                setTextColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            init {
                buttonContainer.addView(selectButton)
                buttonContainer.addView(versionButton)
                buttonContainer.addView(deleteButton)
                
                container.addView(nameText)
                container.addView(infoText)
                container.addView(statusText)
                container.addView(buttonContainer)
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val container = LinearLayout(this@VoiceModelManagementActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16, 16, 16, 16)
                setBackgroundColor(Color.WHITE)
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                )
            }
            return ViewHolder(container)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val model = models[position]
            val currentModelId = AppPrefs.getInstance().internal.voiceModelId.getValue()
            val isCurrent = model.id == currentModelId
            
            holder.nameText.text = model.name
            
            // 显示模型信息，包括版本
            val versionInfo = if (!model.isBuiltIn) {
                if (model.useInt8) "INT8量化" else "标准版本"
            } else ""
            
            holder.infoText.text = if (versionInfo.isNotEmpty()) {
                "${model.language} | ${model.modelType} | $versionInfo"
            } else {
                "${model.language} | ${model.modelType}"
            }
            
            holder.statusText.text = if (isCurrent) "✓ ${getString(R.string.voice_model_current)}" else ""
            
            if (model.description.isNotBlank()) {
                holder.infoText.text = "${holder.infoText.text}\n${model.description}"
            }
            
            holder.selectButton.isEnabled = !isCurrent
            holder.selectButton.text = if (isCurrent) "已选择" else "选择"
            holder.selectButton.setOnClickListener {
                onSelect(model)
            }
            
            // 版本切换按钮（仅对自定义模型显示）
            if (!model.isBuiltIn) {
                holder.versionButton.visibility = View.VISIBLE
                holder.versionButton.setOnClickListener {
                    showVersionSelectionDialog(model)
                }
                
                holder.deleteButton.visibility = View.VISIBLE
                holder.deleteButton.setOnClickListener {
                    onDelete(model)
                }
            } else {
                holder.versionButton.visibility = View.GONE
                holder.deleteButton.visibility = View.GONE
            }
        }
        
        private fun showVersionSelectionDialog(model: VoiceModel) {
            AlertDialog.Builder(this@VoiceModelManagementActivity)
                .setTitle("选择模型版本")
                .setMessage("${model.name}\n\n当前版本: ${if (model.useInt8) "INT8量化版本" else "标准版本"}")
                .setPositiveButton("使用INT8量化") { _, _ ->
                    updateModelVersion(model, true)
                }
                .setNegativeButton("使用标准版本") { _, _ ->
                    updateModelVersion(model, false)
                }
                .setNeutralButton("取消", null)
                .show()
        }
        
        private fun updateModelVersion(model: VoiceModel, useInt8: Boolean) {
            if (modelManager.updateModel(model.id) { it.copy(useInt8 = useInt8) }) {
                Toast.makeText(
                    this@VoiceModelManagementActivity,
                    "已切换到${if (useInt8) "INT8量化" else "标准"}版本",
                    Toast.LENGTH_SHORT
                ).show()
                loadModels()
            } else {
                Toast.makeText(
                    this@VoiceModelManagementActivity,
                    "切换版本失败",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        override fun getItemCount(): Int = models.size
    }
}

