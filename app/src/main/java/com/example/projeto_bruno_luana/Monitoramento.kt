package com.example.projeto_bruno_luana

import android.location.Location

// Identificadores das notificacoes e dos canais, num lugar so.
const val CANAL_SERVICO = "canal_monitoramento"
const val CANAL_ALERTA = "canal_alerta_queda"

const val ID_NOTIFICACAO_SERVICO = 1001
const val ID_NOTIFICACAO_ALERTA = 1002
const val ID_NOTIFICACAO_ENVIO = 1003

/**
 * Estado compartilhado entre o servico de deteccao e a tela principal.
 * E so memoria: nada aqui sobrevive ao fechamento do aplicativo.
 */
object Monitoramento {

    /** True enquanto o DetectorQuedaService estiver rodando. */
    @Volatile
    var servicoAtivo = false

    /**
     * True enquanto um alerta de queda esta visivel, para nao empilhar dois.
     * E zerada pela Activity de alerta e pelo overlay ao serem fechados; o
     * momento e guardado para o aviso se soltar sozinho caso algo trave.
     */
    @Volatile
    var alertaNaTela = false

    @Volatile
    var alertaDesde = 0L

    /** Registra que um alerta acabou de aparecer. */
    fun marcarAlertaNaTela() {
        alertaNaTela = true
        alertaDesde = System.currentTimeMillis()
    }

    /** Libera o aviso quando o alerta e fechado. */
    fun liberarAlerta() {
        alertaNaTela = false
        alertaDesde = 0L
    }

    /**
     * Diz se ja existe um alerta na tela. Passado o tempo limite o aviso e
     * ignorado, para um alerta que travou nao bloquear os proximos.
     */
    fun temAlertaNaTela(): Boolean {
        if (!alertaNaTela) {
            return false
        }
        if (System.currentTimeMillis() - alertaDesde > TEMPO_MAXIMO_DE_ALERTA) {
            liberarAlerta()
            return false
        }
        return true
    }

    /** Depois disso um alerta e considerado perdido (ms). */
    private const val TEMPO_MAXIMO_DE_ALERTA = 60_000L

    /** Ultima leitura do acelerometro, para a tela principal mostrar. */
    @Volatile
    var eixoX = 0.0

    @Volatile
    var eixoY = 0.0

    @Volatile
    var eixoZ = 0.0

    @Volatile
    var magnitude = 0.0

    fun publicarLeitura(x: Double, y: Double, z: Double, resultante: Double) {
        eixoX = x
        eixoY = y
        eixoZ = z
        magnitude = resultante
    }
}

/**
 * Guarda a ultima posicao conhecida para o SMS poder incluir o link do mapa
 * sem depender de nenhuma tela estar aberta.
 */
object UltimaLocalizacao {

    @Volatile
    private var latitude = 0.0

    @Volatile
    private var longitude = 0.0

    @Volatile
    private var precisao = 0f

    @Volatile
    private var temPosicao = false

    fun guardar(location: Location) {
        latitude = location.latitude
        longitude = location.longitude
        precisao = location.accuracy
        temPosicao = true
    }

    fun existe(): Boolean = temPosicao

    fun coordenadas(): String = "%.4f, %.4f".format(latitude, longitude)

    fun precisaoEmMetros(): String = "%.0f m".format(precisao)

    fun linkDoMapa(): String = "maps.google.com/?q=%.6f,%.6f".format(latitude, longitude)
}
