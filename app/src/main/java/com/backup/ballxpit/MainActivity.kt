package com.backup.ballxpit

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.Canvas
import android.graphics.Paint
import android.content.Context
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
    private var extractMode = false
    private lateinit var lblFixed: TextView
    private lateinit var lblPick: TextView
    private lateinit var btnPick: Button
    private lateinit var btnSend: Button
    private lateinit var btnExtract: Button
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

    private val cBg = Color.parseColor("#0F0D0C")
    private val cCard = Color.parseColor("#1C1815")
    private val cStroke = Color.parseColor("#3D332B")
    private val cOrange = Color.parseColor("#E9A63A")
    private val cTeal = Color.parseColor("#8FB05A")
    private val cText = Color.parseColor("#E8DDC9")
    private val cMuted = Color.parseColor("#8C7F70")

    private fun darken(c: Int): Int {
        val f = 0.55f
        return Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())
    }

    // Caixa "pixel": borda reta de 2dp + sombra dura embaixo, sem cantos arredondados
    @Suppress("UNUSED_PARAMETER")
    private fun card(fill: Int = cCard, stroke: Int = cStroke, radius: Int = 0): android.graphics.drawable.Drawable {
        val shadow = GradientDrawable().apply { setColor(darken(stroke)) }
        val top = GradientDrawable().apply { setColor(fill); setStroke(dp(2), stroke) }
        return LayerDrawable(arrayOf(shadow, top)).apply {
            setLayerInset(1, 0, 0, 0, dp(4))
            setPaddingMode(LayerDrawable.PADDING_MODE_STACK)
        }
    }

    private fun button(text: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        typeface = android.graphics.Typeface.MONOSPACE
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(if (primary) Color.parseColor("#1A0F05") else cText)
        background = if (primary) card(cOrange, cOrange, 14) else card(cCard, cTeal, 14)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12) }
    }

    private fun label(size: Float, color: Int) = TextView(this).apply {
        textSize = size; setTextColor(color)
        typeface = android.graphics.Typeface.MONOSPACE
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
        header.addView(PixelBall(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(12) }
        })
        header.addView(label(26f, cText).apply {
            text = "BALL x PIT"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        header.addView(label(26f, cOrange).apply {
            text = "  BACKUP"
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(header)
        root.addView(label(13f, cMuted).apply {
            text = "Ballbylon caiu. Proteja seus saves."
            setPadding(0, dp(6), 0, dp(10))
        })
        root.addView(BrickRow(this).apply { layoutParams = LinearLayout.LayoutParams(-1, dp(26)) })

        val pStatus = panel()
        status = label(15f, cText).apply { typeface = android.graphics.Typeface.DEFAULT_BOLD }
        pStatus.addView(status)
        btnPerm = button("Conceder permissão do Shizuku") { askShizuku() }
        pStatus.addView(btnPerm)
        root.addView(pStatus)

        val pFiles = panel()
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        btnSend = button("Enviar ao jogo") { setMode(false) }
        btnExtract = button("Extrair do jogo") { setMode(true) }
        btnSend.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) }
        btnExtract.layoutParams = LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) }
        modeRow.addView(btnSend); modeRow.addView(btnExtract)
        pFiles.addView(modeRow)
        lblFixed = label(12f, cMuted).apply { setPadding(0, dp(14), 0, 0) }
        pFiles.addView(lblFixed)
        pFiles.addView(label(12f, cTeal).apply {
            text = DEST
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(4), 0, dp(14))
        })
        lblPick = label(12f, cMuted)
        pFiles.addView(lblPick)
        source = label(13f, cOrange).apply {
            text = "Nenhuma pasta escolhida"
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, dp(4), 0, 0)
        }
        pFiles.addView(source)
        btnPick = button("") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), REQ_PICK)
        }
        pFiles.addView(btnPick)
        copyOnly = CheckBox(this).apply {
            text = "Copiar (manter arquivos na origem)"
            setTextColor(cText)
            buttonTintList = android.content.res.ColorStateList.valueOf(cOrange)
        }
        pFiles.addView(copyOnly)
        root.addView(pFiles)

        btnGo = button("Enviar para o jogo", primary = true) { transfer() }
        setMode(false)
        root.addView(btnGo)

        log = label(12f, cMuted).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply {
            addView(log)
            background = card(Color.parseColor("#0A0807"), cStroke, 12)
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

    private fun setMode(extract: Boolean) {
        extractMode = extract
        sourcePath = null
        source.text = "Nenhuma pasta escolhida"
        lblFixed.text = if (extract) "ORIGEM (O POÇO)" else "DESTINO (O POÇO)"
        lblPick.text = if (extract) "DESTINO" else "ORIGEM"
        btnPick.text = if (extract) "Escolher pasta de destino" else "Escolher pasta de origem"
        copyOnly.text = if (extract) "Copiar (manter arquivos no jogo)" else "Copiar (manter arquivos na origem)"
        btnGo.text = if (extract) "Extrair do jogo" else "Enviar para o jogo"
        btnSend.background = if (!extract) card(cOrange, cOrange, 14) else card(cCard, cTeal, 14)
        btnSend.setTextColor(if (!extract) Color.parseColor("#1A0F05") else cText)
        btnExtract.background = if (extract) card(cOrange, cOrange, 14) else card(cCard, cTeal, 14)
        btnExtract.setTextColor(if (extract) Color.parseColor("#1A0F05") else cText)
        refresh()
    }

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
        val extract = extractMode
        btnGo.isEnabled = false
        append("Iniciando (${if (extract) "extrair" else "enviar"}, ${if (move) "mover" else "copiar"})…")
        thread {
            try {
                val cmd = if (extract) {
                    val tpl = resources.openRawResource(R.raw.extract_script).bufferedReader().readText()
                    val moveCmd = if (move) "find \"\$S\" -mindepth 1 -delete" else ""
                    tpl.replace("@DEST@", q(src)).replace("@MOVE@", moveCmd)
                } else {
                    val tpl = resources.openRawResource(R.raw.copy_script).bufferedReader().readText()
                    val moveCmd = if (move) "find ${q(src)} -mindepth 1 -delete" else ""
                    tpl.replace("@SRC@", q(src)).replace("@MOVE@", moveCmd)
                }
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


// Sprite de bola em pixel art (11x11), mesmas cores do ícone
private class PixelBall(ctx: Context) : View(ctx) {
    private val paint = Paint().apply { isAntiAlias = false }
    private val n = 11
    override fun onDraw(c: Canvas) {
        val u = minOf(width, height) / n.toFloat()
        for (y in 0 until n) for (x in 0 until n) {
            val d = Math.hypot(x + .5 - n / 2.0, y + .5 - n / 2.0)
            if (d > n / 2.0) continue
            var col = if (d > n / 2.0 - 1) "#3B2410" else "#E9A63A"
            if (col == "#E9A63A") {
                if (d > 3.2 && x + y > n) col = "#B8741F"
                if ((x == 2 && y == 3) || (x == 3 && y == 2) || (x == 3 && y == 3) || (x == 4 && y == 2) || (x == 2 && y == 4)) col = "#FFE7A0"
            }
            paint.color = Color.parseColor(col)
            c.drawRect(x * u, y * u, (x + 1) * u, (y + 1) * u, paint)
        }
    }
}

// Fileira de tijolos estilo Breakout, com alguns quebrados
private class BrickRow(ctx: Context) : View(ctx) {
    private val paint = Paint()
    private val cols = intArrayOf(
        Color.parseColor("#8E3B2A"), Color.parseColor("#B5702F"),
        Color.parseColor("#6E7A3E"), Color.parseColor("#3F6B66")
    )
    override fun onDraw(c: Canvas) {
        val rows = 2
        val gap = 3f
        val bw = width / 9f
        val bh = (height - gap) / rows
        for (r in 0 until rows) {
            val off = if (r % 2 == 0) 0f else -bw / 2
            for (i in 0..9) {
                val x = off + i * bw
                if ((r == 0 && i == 6) || (r == 1 && i == 2)) continue
                paint.color = cols[(i + r * 2) % cols.size]
                c.drawRect(x + gap / 2, r * (bh + gap), x + bw - gap / 2, r * (bh + gap) + bh, paint)
                paint.color = Color.argb(46, 255, 255, 255)
                c.drawRect(x + gap / 2, r * (bh + gap), x + bw - gap / 2, r * (bh + gap) + 3f, paint)
            }
        }
    }
}
