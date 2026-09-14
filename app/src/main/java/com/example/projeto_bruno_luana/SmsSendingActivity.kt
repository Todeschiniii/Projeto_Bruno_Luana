package com.example.projeto_bruno_luana

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela mostrada depois que o alerta de queda nao foi cancelado a tempo.
 * Envia o SMS de verdade e mostra o resultado contato por contato.
 */
class SmsSendingActivity : AppCompatActivity() {

    private lateinit var txtCoordenadas: TextView
    private lateinit var txtPrecisao: TextView
    private lateinit var txtMensagem: TextView
    private lateinit var txtSemContatos: TextView
    private lateinit var listaContatos: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sms)

        txtCoordenadas = findViewById(R.id.txtCoordenadas)
        txtPrecisao = findViewById(R.id.txtPrecisao)
        txtMensagem = findViewById(R.id.txtMensagem)
        txtSemContatos = findViewById(R.id.txtSemContatos)
        listaContatos = findViewById(R.id.listaContatos)

        mostrarLocalizacao()
        txtMensagem.text = EnvioDeSms.montarMensagem(this)

        val contatos = carregarContatos(this)

        if (contatos.isEmpty()) {
            txtSemContatos.visibility = View.VISIBLE
        } else {
            montarLista(contatos, STATUS_ENVIANDO)
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnConcluir).setOnClickListener { finish() }

        // O envio de verdade acontece aqui; a lista e remontada com o resultado.
        EnvioDeSms.enviarParaTodos(this) { resumo ->
            runOnUiThread {
                if (contatos.isNotEmpty()) {
                    val situacao = if (resumo.contains(getString(R.string.envio_palavra_falha))) {
                        STATUS_FALHOU
                    } else {
                        STATUS_ENVIADO
                    }
                    montarLista(contatos, situacao)
                }
            }
        }
    }

    private fun mostrarLocalizacao() {
        if (UltimaLocalizacao.existe()) {
            txtCoordenadas.text = UltimaLocalizacao.coordenadas()
            txtPrecisao.text = getString(
                R.string.sms_location_accuracy,
                UltimaLocalizacao.precisaoEmMetros()
            )
        } else {
            txtCoordenadas.text = getString(R.string.sms_location_waiting)
            txtPrecisao.text = getString(R.string.sms_location_sem_gps)
        }
    }

    /** Monta a lista de contatos inflando uma linha por contato. */
    private fun montarLista(contatos: List<EmergencyContact>, situacao: String) {
        listaContatos.removeAllViews()
        txtSemContatos.visibility = View.GONE

        for (contato in contatos) {
            val linha = layoutInflater.inflate(R.layout.item_contato, listaContatos, false)

            val txtInicial = linha.findViewById<TextView>(R.id.txtInicial)
            val txtNome = linha.findViewById<TextView>(R.id.txtNome)
            val txtTelefone = linha.findViewById<TextView>(R.id.txtTelefone)
            val blocoStatus = linha.findViewById<LinearLayout>(R.id.blocoStatus)
            val txtStatus = linha.findViewById<TextView>(R.id.txtStatus)

            txtNome.text = contato.name
            txtTelefone.text = contato.phone

            if (contato.name.isNotEmpty()) {
                txtInicial.text = contato.name.substring(0, 1).uppercase()
            } else {
                txtInicial.text = "?"
            }

            blocoStatus.visibility = View.VISIBLE

            when (situacao) {
                STATUS_ENVIADO -> {
                    blocoStatus.setBackgroundResource(R.drawable.bg_chip_success)
                    txtStatus.text = getString(R.string.sms_status_sent)
                    txtStatus.setTextColor(getColor(R.color.verde))
                }
                STATUS_ENVIANDO -> {
                    blocoStatus.setBackgroundResource(R.drawable.bg_chip_pending)
                    txtStatus.text = getString(R.string.sms_status_sending)
                    txtStatus.setTextColor(getColor(R.color.ambar))
                }
                else -> {
                    blocoStatus.setBackgroundResource(R.drawable.bg_chip_failed)
                    txtStatus.text = getString(R.string.sms_status_failed)
                    txtStatus.setTextColor(getColor(R.color.vermelho))
                }
            }

            listaContatos.addView(linha)
        }
    }
}
