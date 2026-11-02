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

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = GradientDrawable().apply {
            setColor(Color.parseColor("#2A2A2A")); cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) }
    }

    private fun label(size: Float, color: String) = TextView(this).apply {
        textSize = size; setTextColor(Color.parseColor(color))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(dp(20), dp(48), dp(20), dp(20))
        }
        root.addView(label(24f, "#FFFFFF").apply { text = "Backup Ball x Pit"; gravity = Gravity.START })
        root.addView(label(13f, "#9E9E9E").apply {
            text = "Destino:\n$DEST"
            setPadding(0, dp(6), 0, dp(16))
        })

        status = label(15f, "#FFFFFF")
        root.addView(status)
        btnPerm = button("Conceder permissão do Shizuku") { askShizuku() }
        root.addView(btnPerm)

        root.addView(button("Escolher pasta de origem") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK)
        })
        source = label(14f, "#BB86FC").apply { text = "Nenhuma pasta escolhida"; setPadding(0, dp(8), 0, 0) }
        root.addView(source)

        copyOnly = CheckBox(this).apply {
            text = "Copiar (manter arquivos na origem)"
            setTextColor(Color.WHITE)
        }
        root.addView(copyOnly)

        btnGo = button("Enviar para o jogo") { transfer() }
        root.addView(btnGo)

        log = label(12f, "#B0B0B0").apply { setPadding(0, dp(12), 0, 0); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply {
            addView(log)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
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
