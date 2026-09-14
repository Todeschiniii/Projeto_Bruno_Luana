package com.example.projeto_bruno_luana

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de alerta de queda, ocupando o celular inteiro.
 *
 * Conta 15 segundos com som de alarme. Se ninguem cancelar, abre a tela de
 * envio do SMS - por este caminho o app ja esta em primeiro plano, entao abrir
 * outra Activity e permitido.
 */
class AlertaQuedaActivity : AppCompatActivity() {

    private lateinit var txtContagem: TextView
    private lateinit var barraContagem: ProgressBar
    private lateinit var btnEstouBem: Button
    private lateinit var btnEnviarAgora: Button

    private var alarme: AlarmeDeQueda? = null

    @Volatile
    private var contando = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Faz a tela acender e aparecer por cima do bloqueio.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_alerta_queda)

        txtContagem = findViewById(R.id.txtContagem)
        barraContagem = findViewById(R.id.barraContagem)
        btnEstouBem = findViewById(R.id.btnEstouBem)
        btnEnviarAgora = findViewById(R.id.btnEnviarAgora)

        barraContagem.max = SEGUNDOS_TOTAIS
        barraContagem.progress = SEGUNDOS_TOTAIS
        txtContagem.text = SEGUNDOS_TOTAIS.toString()

        btnEstouBem.setOnClickListener {
            contando = false
            limparNotificacaoDoAlerta()
            finish()
        }

        btnEnviarAgora.setOnClickListener {
            abrirEnvioDeSms()
        }

        // Enquanto o alerta esta na tela, o botao voltar nao encerra a contagem.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
            }
        })

        alarme = AlarmeDeQueda(this)
        alarme?.tocar()

        iniciarContagem()
    }

    private fun iniciarContagem() {
        Thread {
            var segundos = SEGUNDOS_TOTAIS

            while (contando && segundos > 0) {
                Thread.sleep(1000)
                segundos--

                val restante = segundos
                runOnUiThread {
                    txtContagem.text = restante.toString()
                    barraContagem.progress = restante
                }
            }

            if (contando) {
                runOnUiThread {
                    abrirEnvioDeSms()
                }
            }
        }.start()
    }

    private fun abrirEnvioDeSms() {
        if (!contando) {
            return
        }
        contando = false

        limparNotificacaoDoAlerta()

        val envio = Intent(this, SmsSendingActivity::class.java)
        startActivity(envio)
        finish()
    }

    /**
     * setAutoCancel(true) so limpa a notificacao quando o usuario toca nela.
     * Resolvendo pelo botao, ela ficaria guardada com o fullScreenIntent ainda
     * valido e o Android reabriria este alerta sozinho mais tarde.
     */
    private fun limparNotificacaoDoAlerta() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ID_NOTIFICACAO_ALERTA)
    }

    override fun onDestroy() {
        contando = false
        alarme?.parar()
        alarme = null
        // Libera o servico para poder disparar o proximo alerta.
        Monitoramento.liberarAlerta()
        super.onDestroy()
    }

    companion object {
        private const val SEGUNDOS_TOTAIS = 15
    }
}
