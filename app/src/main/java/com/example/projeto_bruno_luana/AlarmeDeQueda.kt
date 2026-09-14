package com.example.projeto_bruno_luana

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager

/**
 * Toca o alarme padrao do proprio celular durante o alerta de queda.
 *
 * Usa USAGE_ALARM para o som sair no volume de alarme, que continua audivel
 * mesmo com o aparelho no silencioso. Nenhum arquivo de audio e adicionado ao
 * projeto.
 */
class AlarmeDeQueda(private val context: Context) {

    private var tocador: MediaPlayer? = null

    fun tocar() {
        parar()

        try {
            val som = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return

            val novo = MediaPlayer()
            novo.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            novo.setDataSource(context, som)
            novo.isLooping = true
            novo.prepare()
            novo.start()

            tocador = novo
        } catch (erro: Exception) {
            // Sem alarme padrao cadastrado ou audio indisponivel: o alerta
            // continua funcionando, so fica sem som.
            tocador = null
        }
    }

    fun parar() {
        val atual = tocador ?: return
        tocador = null

        try {
            if (atual.isPlaying) {
                atual.stop()
            }
        } catch (erro: Exception) {
        }

        try {
            atual.release()
        } catch (erro: Exception) {
        }
    }
}
