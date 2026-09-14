package com.example.projeto_bruno_luana

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Janela flutuante com o alerta de queda, usada quando a tela esta ligada.
 *
 * Nessa situacao o Android bloqueia de proposito um app em segundo plano de
 * abrir uma Activity nova, entao o mesmo layout da tela de alerta e colocado
 * direto no WindowManager, por cima de tudo.
 */
class OverlayAlertaQueda(
    private val context: Context,
    private val aoCancelar: () -> Unit,
    private val aoConfirmar: () -> Unit
) {

    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var view: View? = null
    private var alarme: AlarmeDeQueda? = null

    private val handler = Handler(Looper.getMainLooper())
    private var segundos = SEGUNDOS_TOTAIS
    private var resolvido = false

    private val contagem = object : Runnable {
        override fun run() {
            if (resolvido) {
                return
            }

            segundos--
            atualizarContagem()

            if (segundos <= 0) {
                confirmar()
            } else {
                handler.postDelayed(this, 1000L)
            }
        }
    }

    fun mostrar() {
        val tipo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        // FLAG_LAYOUT_IN_SCREEN faz a janela cobrir a tela inteira, inclusive a
        // faixa da barra de status. Sem ele sobra a cor do app que estava atras.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            tipo,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val inflada = LayoutInflater.from(context).inflate(R.layout.activity_alerta_queda, null)
        view = inflada

        val btnEstouBem = inflada.findViewById<Button>(R.id.btnEstouBem)
        val btnEnviarAgora = inflada.findViewById<Button>(R.id.btnEnviarAgora)

        btnEstouBem.setOnClickListener { cancelar() }
        btnEnviarAgora.setOnClickListener { confirmar() }

        atualizarContagem()

        try {
            windowManager.addView(inflada, params)
        } catch (erro: Exception) {
            // Sem a permissao de sobreposicao o addView falha. O alerta ainda
            // existe como notificacao, entao o app nao para por causa disso.
            view = null
            return
        }

        alarme = AlarmeDeQueda(context)
        alarme?.tocar()

        handler.postDelayed(contagem, 1000L)
    }

    private fun atualizarContagem() {
        val atual = view ?: return
        atual.findViewById<TextView>(R.id.txtContagem).text = segundos.toString()
        atual.findViewById<ProgressBar>(R.id.barraContagem).progress = segundos
    }

    private fun cancelar() {
        if (resolvido) {
            return
        }
        resolvido = true
        remover()
        aoCancelar()
    }

    private fun confirmar() {
        if (resolvido) {
            return
        }
        resolvido = true
        remover()
        aoConfirmar()
    }

    fun remover() {
        resolvido = true
        handler.removeCallbacks(contagem)

        alarme?.parar()
        alarme = null

        val atual = view ?: return
        view = null

        try {
            windowManager.removeView(atual)
        } catch (erro: IllegalArgumentException) {
            // A janela ja tinha sido removida.
        }
    }

    companion object {
        private const val SEGUNDOS_TOTAIS = 15
    }
}
