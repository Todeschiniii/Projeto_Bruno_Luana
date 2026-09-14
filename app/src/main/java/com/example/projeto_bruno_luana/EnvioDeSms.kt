package com.example.projeto_bruno_luana

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * Envia o SMS de emergencia sem depender de nenhuma tela.
 *
 * Este e o ponto central do aplicativo: a funcao critica nao pode depender de
 * o Android permitir mostrar uma Activity. Por isso o envio mora aqui, num
 * objeto que so precisa de um Context.
 */
object EnvioDeSms {

    private const val TAG = "FallDetector"

    /** Monta o texto que vai no SMS, com o link do mapa quando ha posicao. */
    fun montarMensagem(context: Context): String {
        val base = context.getString(R.string.sms_texto_base)

        return if (UltimaLocalizacao.existe()) {
            base + " " + context.getString(
                R.string.sms_texto_local,
                UltimaLocalizacao.linkDoMapa()
            )
        } else {
            base + " " + context.getString(R.string.sms_texto_sem_local)
        }
    }

    /**
     * Envia a mensagem para todos os contatos cadastrados e devolve um resumo
     * curto pelo aoTerminar (ex.: "SMS enviado para 3 contato(s)").
     */
    fun enviarParaTodos(context: Context, aoTerminar: (String) -> Unit) {
        val contatos = carregarContatos(context)

        if (contatos.isEmpty()) {
            Log.d(TAG, "Nenhum contato cadastrado: SMS nao enviado.")
            aoTerminar(context.getString(R.string.envio_sem_contatos))
            return
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Sem permissao SEND_SMS: envio bloqueado.")
            aoTerminar(context.getString(R.string.envio_sem_permissao))
            return
        }

        val mensagem = montarMensagem(context)
        val gerenciador = obterSmsManager(context)

        if (gerenciador == null) {
            aoTerminar(context.getString(R.string.envio_falhou_todos))
            return
        }

        var enviados = 0
        var falharam = 0

        for (contato in contatos) {
            val numero = somenteNumeros(contato.phone)

            if (numero.isEmpty()) {
                falharam++
                continue
            }

            try {
                // Mensagens longas precisam ser divididas em partes.
                val partes = gerenciador.divideMessage(mensagem)
                if (partes.size > 1) {
                    gerenciador.sendMultipartTextMessage(numero, null, partes, null, null)
                } else {
                    gerenciador.sendTextMessage(numero, null, mensagem, null, null)
                }
                enviados++
                Log.d(TAG, "SMS enviado para ${contato.name}.")
            } catch (erro: Exception) {
                falharam++
                Log.d(TAG, "Falha ao enviar para ${contato.name}: ${erro.message}")
            }
        }

        val resumo = if (falharam == 0) {
            context.getString(R.string.envio_ok, enviados)
        } else {
            context.getString(R.string.envio_parcial, enviados, falharam)
        }

        Log.d(TAG, resumo)
        aoTerminar(resumo)
    }

    private fun obterSmsManager(context: Context): SmsManager? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (erro: Exception) {
            Log.d(TAG, "SmsManager indisponivel: ${erro.message}")
            null
        }
    }

    /** Tira parenteses, espacos e tracos, deixando so o que o SmsManager aceita. */
    private fun somenteNumeros(telefone: String): String {
        var limpo = ""
        for (caractere in telefone) {
            if (caractere.isDigit() || (caractere == '+' && limpo.isEmpty())) {
                limpo += caractere
            }
        }
        return limpo
    }
}
