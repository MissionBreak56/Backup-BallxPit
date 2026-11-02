package com.backup.ballxpit

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.Gravity
import android.view.View
import android.widget.*
import rikka.shizuku.Shizuku
import kotlin.concurrent.thread

class MainActivity : Activity() {

    companion object {
        const val DEST = "/storage/emulated/0/Android/data/com.devolverdigital.ballxpit/files"
        const val REQ_PICK = 1
        const val REQ_SHIZUKU = 2
    }

    private var sourcePath: String? = null
    private lateinit var status: TextView
    private lateinit var source: TextView
    private lateinit var log: TextView
    private lateinit var scroll: ScrollView
    private lateinit var copyOnly: CheckBox
    private lateinit var btnPerm: Button
    private lateinit var btnGo: Button

    private val binderListener = Shizuku.OnBinderReceivedListener { runOnUiThread { refresh() } }
    private val deadListener = Shizuku.OnBinderDeadListener { runOnUiThread { refresh() } }
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> runOnUiThread { refresh() } }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private val cBg = Color.parseColor("#140B24")
    private val cCard = Color.parseColor("#24143D")
    private val cStroke = Color.parseColor("#3B2466")
    private val cOrange = Color.parseColor("#FF9F1C")
    private val cTeal = Color.parseColor("#2EE6C5")
    private val cText = Color.parseColor("#F3E9FF")
    private val cMuted = Color.parseColor("#A596C2")

    private fun card(fill: Int = cCard, stroke: Int = cStroke, radius: Int = 16) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat()
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.parseColor("#1A0E2E") else cText)
        background = if (primary) card(cOrange, cOrange, 14) else card(cCard, cTeal, 14)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
    }

    private fun label(size: Float, color: Int) = TextView(this).apply {
        textSize = size; setTextColor(color)
    }

    private fun panel() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = card()
        setPadding(dp(16), dp(14), dp(16), dp(14))
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dp(22).toFloat()
                setGradientCenter(0.35f, 0.35f)
                colors = intArrayOf(Color.parseColor("#FFE3A8"), cOrange)
            }
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34)).apply { rightMargin = dp(12) }
        })
        header.addView(label(26f, cText).apply {
            text = "Ball x Pit"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(label(26f, cOrange).apply {
            text = "  Backup"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(header)
        root.addView(label(13f, cMuted).apply {
            text = "Jogue seus saves na cova"
            setPadding(0, dp(4), 0, 0)
        })

        val pStatus = panel()
        status = label(15f, cText).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
        pStatus.addView(status)
        btnPerm = button("Conceder permissão do Shizuku") { askShizuku() }
        pStatus.addView(btnPerm)
        root.addView(pStatus)

        val pFiles = panel()
        pFiles.addView(label(12f, cMuted).apply { text = "DESTINO (A COVA)" })
        pFiles.addView(label(12f, cTeal).apply {
            text = DEST
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(14))
        })
        pFiles.addView(label(12f, cMuted).apply { text = "ORIGEM" })
        source = label(13f, cOrange).apply {
            text = "Nenhuma pasta escolhida"
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        pFiles.addView(source)
        pFiles.addView(button("Escolher pasta de origem") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK)
        })
        copyOnly = CheckBox(this).apply {
            text = "Copiar (manter arquivos na origem)"
            setTextColor(cText)
            buttonTintList = android.content.res.ColorStateList.valueOf(cOrange)
        }
        pFiles.addView(copyOnly)
        root.addView(pFiles)

        btnGo = button("Enviar para o jogo", primary = true) { transfer() }
        root.addView(btnGo)

        log = label(12f, cMuted).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply {
            addView(log)
            background = card(Color.parseColor("#0F081C"), cStroke, 12)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) }
        }
        root.addView(scroll)
        setContentView(root)

        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permListener)
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permListener)
        super.onDestroy()
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun shizukuReady() = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    private fun refresh() {
        val running = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        val granted = running && try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) { false }
        status.text = when {
            !running -> "Shizuku: não está em execução. Abra o app Shizuku e inicie o serviço."
            !granted -> "Shizuku: em execução, permissão pendente."
            else -> "Shizuku: pronto ✔"
        }
        status.setTextColor(if (granted) cTeal else cOrange)
        btnPerm.isEnabled = running && !granted
        btnGo.isEnabled = granted && sourcePath != null
        btnGo.alpha = if (btnGo.isEnabled) 1f else 0.4f
        btnPerm.alpha = if (btnPerm.isEnabled) 1f else 0.4f
    }

    private fun askShizuku() {
        try {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                append("Permissão negada permanentemente. Libere o Backup Ball x Pit dentro do app Shizuku.")
            } else Shizuku.requestPermission(REQ_SHIZUKU)
        } catch (e: Throwable) { append("Erro: ${e.message}") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PICK && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val path = treeToPath(uri)
            if (path == null) {
                append("Não foi possível converter a pasta escolhida em caminho.")
            } else {
                sourcePath = path
                source.text = path
            }
            refresh()
        }
    }

    private fun treeToPath(uri: Uri): String? {
        val id = DocumentsContract.getTreeDocumentId(uri)
        val parts = id.split(":", limit = 2)
        if (parts.size < 2) return null
        val rel = parts[1]
        val base = if (parts[0] == "primary") "/storage/emulated/0" else "/storage/${parts[0]}"
        return if (rel.isEmpty()) base else "$base/$rel"
    }

    private fun q(s: String) = "'" + s.replace("'", "'\\''") + "'"

    private fun shell(cmd: String): Pair<Int, String> {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = p.inputStream.bufferedReader().readText()
        val err = p.errorStream.bufferedReader().readText()
        return p.waitFor() to (out + err).trim()
    }

    private fun transfer() {
        val src = sourcePath ?: return
        val move = !copyOnly.isChecked
        btnGo.isEnabled = false
        append("Iniciando (${if (move) "mover" else "copiar"})…")
        thread {
            try {
                val tpl = resources.openRawResource(R.raw.copy_script).bufferedReader().readText()
                val moveCmd = if (move) "find ${q(src)} -mindepth 1 -delete" else ""
                val cmd = tpl.replace("@SRC@", q(src)).replace("@MOVE@", moveCmd)
                val (code, out) = shell(cmd)
                runOnUiThread {
                    append(if (code == 0) "Concluído ✔" else "Falhou (código $code)")
                    if (out.isNotEmpty()) append(out)
                    refresh()
                }
            } catch (e: Throwable) {
                runOnUiThread { append("Erro: ${e.message}"); refresh() }
            }
        }
    }

    private fun append(msg: String) {
        log.append(msg + "\n")
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
