package com.slidecapture

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class MainActivity : AppCompatActivity() {
    private lateinit var provider: ProcessCameraProvider
    private lateinit var preview: PreviewView
    private lateinit var camSpin: Spinner
    private lateinit var objSpin: Spinner
    private lateinit var fovEdit: EditText
    private lateinit var regW: EditText
    private lateinit var regH: EditText
    private lateinit var planTxt: TextView
    private lateinit var setupBox: LinearLayout
    private lateinit var scanBox: LinearLayout
    private lateinit var statusTxt: TextView
    private lateinit var grid: GridV
    private lateinit var capBtn: Button
    private var capture: ImageCapture? = null
    private val camIds = ArrayList<String>()
    private val objs = intArrayOf(2, 4, 10, 20, 40)
    // Default field widths (mm) = sensor width 6.29 mm / objective, 1x camera port. Calibrate with the ruler!
    private val defFov = doubleArrayOf(3.14, 1.57, 0.63, 0.31, 0.157)
    private val overlap = 0.15
    private val aspect = 0.75
    private var rows = 1
    private var cols = 1
    private var idx = 0
    private var done = BooleanArray(1)
    private var folder = ""
    private val prefs by lazy { getSharedPreferences("cal", MODE_PRIVATE) }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun tv(t: String) = TextView(this).apply { text = t; setPadding(0, dp(8), 0, dp(2)) }
    private fun num(def: String) = EditText(this).apply {
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        setText(def)
    }
    private fun btn(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }
    private fun watch(e: EditText, f: () -> Unit) {
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { f() }
        })
    }
    private fun onSel(s: Spinner, f: (Int) -> Unit) {
        s.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { f(pos) }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        preview = PreviewView(this).apply { scaleType = PreviewView.ScaleType.FIT_CENTER }
        root.addView(preview, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        val side = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val sc = ScrollView(this)
        sc.addView(side)
        root.addView(sc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        setContentView(root)

        // ---- setup panel ----
        setupBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        camSpin = Spinner(this)
        objSpin = Spinner(this)
        fovEdit = num("")
        regW = num("50")
        regH = num("24")
        planTxt = TextView(this)
        setupBox.addView(tv("Camera (plug in USB camera first, then Refresh)"))
        setupBox.addView(camSpin)
        setupBox.addView(btn("Refresh cameras") { loadCameras() })
        setupBox.addView(tv("Objective"))
        setupBox.addView(objSpin)
        setupBox.addView(tv("Field width at this objective (mm). Calibrate: width of ruler visible across the preview"))
        setupBox.addView(fovEdit)
        setupBox.addView(tv("Scan area on slide: width x height (mm)"))
        val rr = LinearLayout(this)
        rr.addView(regW, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rr.addView(regH, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setupBox.addView(rr)
        setupBox.addView(planTxt)
        setupBox.addView(btn("START SCAN") { startScan() })
        side.addView(setupBox)

        // ---- scan panel ----
        scanBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        statusTxt = TextView(this).apply { textSize = 16f }
        grid = GridV(this)
        capBtn = btn("CAPTURE") { takeTile() }
        val br = LinearLayout(this)
        br.addView(btn("Back") { if (idx > 0) { idx--; refresh() } })
        br.addView(capBtn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        br.addView(btn("Stop") { scanBox.visibility = View.GONE; setupBox.visibility = View.VISIBLE })
        scanBox.addView(statusTxt)
        scanBox.addView(grid)
        scanBox.addView(br)
        side.addView(scanBox)

        objSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, objs.map { "${it}x" })
        onSel(objSpin) { p -> fovEdit.setText(prefs.getString("fov$p", defFov[p].toString())) }
        watch(fovEdit) {
            val v = fovEdit.text.toString().toDoubleOrNull()
            if (v != null) prefs.edit().putString("fov${objSpin.selectedItemPosition}", fovEdit.text.toString()).apply()
            updatePlan()
        }
        watch(regW) { updatePlan() }
        watch(regH) { updatePlan() }
        onSel(camSpin) { p -> if (p in camIds.indices) bind(camIds[p]) }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) initCamera()
        else ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) initCamera() else toast("Camera permission is required")
    }

    private fun initCamera() {
        val f = ProcessCameraProvider.getInstance(this)
        f.addListener({ provider = f.get(); loadCameras() }, ContextCompat.getMainExecutor(this))
    }

    private fun loadCameras() {
        camIds.clear()
        val labels = ArrayList<String>()
        for (i in provider.availableCameraInfos) {
            val id = Camera2CameraInfo.from(i).cameraId
            val t = when (i.lensFacing) {
                CameraSelector.LENS_FACING_BACK -> "Back camera"
                CameraSelector.LENS_FACING_FRONT -> "Front camera"
                CameraSelector.LENS_FACING_EXTERNAL -> "USB camera"
                else -> "Camera"
            }
            camIds.add(id)
            labels.add("$t (id $id)")
        }
        camSpin.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        val u = labels.indexOfFirst { it.startsWith("USB") }
        if (u >= 0) camSpin.setSelection(u)
        if (camIds.isEmpty()) toast("No cameras found")
    }

    private fun bind(id: String) {
        val sel = CameraSelector.Builder().addCameraFilter { infos ->
            infos.filter { Camera2CameraInfo.from(it).cameraId == id }
        }.build()
        val rsP = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
        val rsC = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build()
        val pv = Preview.Builder().setResolutionSelector(rsP).build()
        pv.setSurfaceProvider(preview.surfaceProvider)
        val cap = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(rsC).build()
        try {
            provider.unbindAll()
            provider.bindToLifecycle(this, sel, pv, cap)
            capture = cap
        } catch (e: Exception) {
            capture = null
            toast("Could not open this camera: ${e.message}")
        }
    }

    private fun plan(): Pair<Int, Int>? {
        val w = fovEdit.text.toString().toDoubleOrNull() ?: return null
        val rw = regW.text.toString().toDoubleOrNull() ?: return null
        val rh = regH.text.toString().toDoubleOrNull() ?: return null
        if (w <= 0 || rw <= 0 || rh <= 0) return null
        val h = w * aspect
        val c = max(1, ceil((rw - w) / (w * (1 - overlap))).toInt() + 1)
        val r = max(1, ceil((rh - h) / (h * (1 - overlap))).toInt() + 1)
        return Pair(r, c)
    }

    private fun updatePlan() {
        val p = plan()
        val w = fovEdit.text.toString().toDoubleOrNull()
        if (p == null || w == null) { planTxt.text = "Enter valid numbers"; return }
        val n = p.first * p.second
        planTxt.text = "Field %.2f x %.2f mm, step %.2f x %.2f mm (15%% overlap)\nGrid: %d columns x %d rows = %d tiles%s".format(
            w, w * aspect, w * (1 - overlap), w * aspect * (1 - overlap), p.second, p.first, n,
            if (n > 400) "\nWARNING: many tiles - use a smaller area or a lower objective" else ""
        )
    }

    private fun startScan() {
        val p = plan() ?: return toast("Check the numbers")
        if (capture == null) return toast("No camera running")
        rows = p.first; cols = p.second; idx = 0
        done = BooleanArray(rows * cols)
        val o = objs[objSpin.selectedItemPosition]
        folder = "Scan_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date()) + "_${o}x_R${rows}xC$cols"
        setupBox.visibility = View.GONE
        scanBox.visibility = View.VISIBLE
        grid.requestLayout()
        refresh()
    }

    private fun refresh() {
        val r = idx / cols
        val c = if (r % 2 == 0) idx % cols else cols - 1 - idx % cols
        val move = when {
            idx == 0 -> "start at top-left"
            idx % cols == 0 -> "next area is BELOW"
            r % 2 == 0 -> "next area is to the RIGHT"
            else -> "next area is to the LEFT"
        }
        statusTxt.text = "Tile ${idx + 1} / ${rows * cols}  (row ${r + 1}, col ${c + 1})\n$move\nCaptured: ${done.count { it }}"
        grid.invalidate()
    }

    private fun tileName(i: Int) = "tile_%04d.jpg".format(i + 1)

    private fun takeTile() {
        val cap = capture ?: return toast("No camera running")
        val name = tileName(idx)
        try {
            contentResolver.delete(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                "${MediaStore.Images.Media.DISPLAY_NAME}=? AND ${MediaStore.Images.Media.RELATIVE_PATH}=?",
                arrayOf(name, "Pictures/SlideScans/$folder/")
            )
        } catch (_: Exception) {}
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SlideScans/$folder")
        }
        val opts = ImageCapture.OutputFileOptions.Builder(
            contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v
        ).build()
        capBtn.isEnabled = false
        cap.takePicture(opts, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(r: ImageCapture.OutputFileResults) {
                done[idx] = true
                if (idx < rows * cols - 1) idx++ else toast("Scan complete: Pictures/SlideScans/$folder")
                capBtn.isEnabled = true
                refresh()
            }
            override fun onError(e: ImageCaptureException) {
                capBtn.isEnabled = true
                toast("Capture failed: ${e.message}")
            }
        })
    }

    inner class GridV(c: Context) : View(c) {
        private val p = Paint()
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val ww = MeasureSpec.getSize(widthMeasureSpec)
            val cw = ww.toFloat() / cols
            setMeasuredDimension(ww, max(dp(60), (cw * aspect * rows).toInt()))
        }
        override fun onDraw(c: Canvas) {
            val cw = width.toFloat() / cols
            val ch = height.toFloat() / rows
            p.style = Paint.Style.FILL
            for (r in 0 until rows) for (k in 0 until cols) {
                val seq = r * cols + (if (r % 2 == 0) k else cols - 1 - k)
                p.color = when {
                    seq == idx -> Color.rgb(255, 170, 0)
                    seq < done.size && done[seq] -> Color.rgb(40, 160, 70)
                    else -> Color.rgb(80, 80, 80)
                }
                c.drawRect(k * cw + 1f, r * ch + 1f, (k + 1) * cw - 1f, (r + 1) * ch - 1f, p)
            }
        }
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                val k = (e.x / (width.toFloat() / cols)).toInt().coerceIn(0, cols - 1)
                val r = (e.y / (height.toFloat() / rows)).toInt().coerceIn(0, rows - 1)
                idx = r * cols + (if (r % 2 == 0) k else cols - 1 - k)
                refresh()
            }
            return true
        }
    }
}
