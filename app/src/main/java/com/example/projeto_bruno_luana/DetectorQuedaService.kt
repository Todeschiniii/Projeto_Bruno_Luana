package com.example.projeto_bruno_luana

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

// ---------------------------------------------------------------------------
// Limiares da deteccao de queda. Todos com nome e unidade, para poder ajustar
// sem cacar numero solto no meio do codigo.
// ---------------------------------------------------------------------------

/** Aceleracao da gravidade, em m/s2. Celular parado mede aproximadamente isso. */
private const val GRAVIDADE = 9.81

/** Abaixo disso o celular esta praticamente sem peso: comeco da queda livre (m/s2). */
private const val LIMITE_QUEDA_LIVRE = 4.0

/** Acima disso houve pancada contra o chao (m/s2). */
private const val LIMITE_IMPACTO = 25.0

/** Rotacao minima durante a queda para o giroscopio confirmar (rad/s). */
private const val LIMITE_ROTACAO = 1.5

/** Aumento minimo de pressao do ar para o barometro confirmar (hPa). */
private const val LIMITE_PRESSAO = 0.08

/** Ruido minimo durante a queda para o microfone confirmar (dB). */
private const val LIMITE_RUIDO = 72.0

/** Acima desta velocidade a pessoa esta num veiculo e o alerta e suprimido (km/h). */
private const val LIMITE_VELOCIDADE_VEICULO = 24.0

/** Tempo maximo entre a queda livre e o impacto (ms). */
private const val TEMPO_MAXIMO_ATE_IMPACTO = 1500L

/** Quanto |a| pode variar em torno da gravidade e ainda contar como parado (m/s2). */
private const val TOLERANCIA_IMOBILIDADE = 2.5

/** Depois do impacto, tempo maximo esperando a imobilidade (ms). */
private const val TEMPO_MAXIMO_APOS_IMPACTO = 6000L

/** Quantos sensores precisam confirmar a queda. */
private const val CONFIRMACOES_NECESSARIAS = 1

private const val TAG = "FallDetector"

// Estados da maquina de deteccao
private const val ESTADO_NORMAL = 0
private const val ESTADO_QUEDA_LIVRE = 1
private const val ESTADO_IMPACTO = 2

/**
 * Servico de primeiro plano que fica lendo os sensores e decide se houve queda.
 *
 * A deteccao e uma sequencia obrigatoria de tres etapas medidas pelo
 * acelerometro:
 *
 *     NORMAL -> QUEDA LIVRE (|a| < 4) -> IMPACTO (|a| > 25) -> IMOBILIDADE
 *
 * Depois disso, giroscopio, barometro e microfone confirmam, e o GPS bloqueia
 * quando a pessoa esta dentro de um veiculo.
 */
class DetectorQuedaService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var giroscopio: Sensor? = null
    private var barometro: Sensor? = null

    private var locationManager: LocationManager? = null

    // ---- estado da maquina ----
    private var estado = ESTADO_NORMAL
    private var momentoQuedaLivre = 0L
    private var momentoImpacto = 0L
    // ---- valores coletados durante a queda, usados pelas confirmacoes ----
    private var rotacaoMaxima = 0.0
    private var pressaoInicial = 0.0
    private var variacaoPressao = 0.0
    private var ruidoMaximo = 0.0
    private var velocidadeKmh = 0.0

    /** Enquanto true a Thread do microfone continua medindo. */
    @Volatile
    private var gravando = false

    private var overlay: OverlayAlertaQueda? = null

    private val ouvinteDeLocalizacao = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            velocidadeKmh = location.speed * 3.6
            UltimaLocalizacao.guardar(location)
        }

        // Metodos antigos, obrigatorios em Android mais velho.
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        criarCanaisDeNotificacao()
        startForeground(ID_NOTIFICACAO_SERVICO, montarNotificacaoDoServico())

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        barometro = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        // SENSOR_DELAY_GAME (~20 ms). Com SENSOR_DELAY_NORMAL (~200 ms) o pico
        // do impacto passa entre duas leituras e a queda nunca e detectada.
        sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_GAME)
        sensorManager.registerListener(this, barometro, SensorManager.SENSOR_DELAY_NORMAL)

        Log.d(TAG, "Servico iniciado. Giroscopio: ${giroscopio != null}, barometro: ${barometro != null}")

        iniciarGps()
        iniciarMedicaoDeRuido()

        Monitoramento.servicoAtivo = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // -----------------------------------------------------------------------
    // Sensores
    // -----------------------------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> tratarAcelerometro(event)
            Sensor.TYPE_GYROSCOPE -> tratarGiroscopio(event)
            Sensor.TYPE_PRESSURE -> tratarBarometro(event)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun tratarAcelerometro(event: SensorEvent) {
        val x = event.values[0].toDouble()
        val y = event.values[1].toDouble()
        val z = event.values[2].toDouble()

        // |a| = raiz(ax2 + ay2 + az2)
        val magnitude = sqrt(x * x + y * y + z * z)
        // Relogio monotono: mudar a hora do celular nao pode alterar a janela
        // de deteccao entre a queda livre e o impacto.
        val agora = SystemClock.elapsedRealtime()

        Monitoramento.publicarLeitura(x, y, z, magnitude)

        when (estado) {
            ESTADO_NORMAL -> {
                if (magnitude < LIMITE_QUEDA_LIVRE) {
                    estado = ESTADO_QUEDA_LIVRE
                    momentoQuedaLivre = agora
                    rotacaoMaxima = 0.0
                    ruidoMaximo = 0.0
                    variacaoPressao = 0.0
                    pressaoInicial = 0.0
                    Log.d(
                        TAG,
                        "1/3 QUEDA LIVRE detectada (|a|=%.2f < %.1f). Esperando impacto em ate %d ms."
                            .format(magnitude, LIMITE_QUEDA_LIVRE, TEMPO_MAXIMO_ATE_IMPACTO)
                    )
                }
            }

            ESTADO_QUEDA_LIVRE -> {
                if (magnitude > LIMITE_IMPACTO) {
                    estado = ESTADO_IMPACTO
                    momentoImpacto = agora
                    Log.d(
                        TAG,
                        "2/3 IMPACTO detectado (|a|=%.2f > %.1f), %d ms depois da queda livre."
                            .format(magnitude, LIMITE_IMPACTO, agora - momentoQuedaLivre)
                    )
                } else if (agora - momentoQuedaLivre > TEMPO_MAXIMO_ATE_IMPACTO) {
                    Log.d(
                        TAG,
                        "Descartado: queda livre sem impacto em ate $TEMPO_MAXIMO_ATE_IMPACTO ms."
                    )
                    estado = ESTADO_NORMAL
                }
            }

            ESTADO_IMPACTO -> {
                val parado = abs(magnitude - GRAVIDADE) < TOLERANCIA_IMOBILIDADE

                if (parado) {
                    // A primeira leitura de repouso apos o impacto confirma a
                    // imobilidade. Nao ha espera agendada: o alerta sai nesta
                    // propria leitura do acelerometro.
                    Log.d(TAG, "3/3 IMOBILIDADE confirmada sem espera.")
                    estado = ESTADO_NORMAL
                    avaliarConfirmacoes()
                    return
                }

                if (agora - momentoImpacto > TEMPO_MAXIMO_APOS_IMPACTO) {
                    Log.d(
                        TAG,
                        "Descartado: a pessoa voltou a se mexer dentro de $TEMPO_MAXIMO_APOS_IMPACTO ms."
                    )
                    estado = ESTADO_NORMAL
                }
            }
        }
    }

    private fun tratarGiroscopio(event: SensorEvent) {
        if (estado == ESTADO_NORMAL) {
            return
        }

        val gx = event.values[0].toDouble()
        val gy = event.values[1].toDouble()
        val gz = event.values[2].toDouble()

        val rotacao = sqrt(gx * gx + gy * gy + gz * gz)
        if (rotacao > rotacaoMaxima) {
            rotacaoMaxima = rotacao
        }
    }

    private fun tratarBarometro(event: SensorEvent) {
        val pressao = event.values[0].toDouble()

        if (estado == ESTADO_NORMAL) {
            pressaoInicial = pressao
            return
        }

        // Caindo, o celular se aproxima do chao e a pressao do ar sobe.
        if (pressaoInicial > 0.0) {
            val diferenca = pressao - pressaoInicial
            if (diferenca > variacaoPressao) {
                variacaoPressao = diferenca
            }
        }
    }

    // -----------------------------------------------------------------------
    // Decisao final
    // -----------------------------------------------------------------------

    private fun avaliarConfirmacoes() {
        var confirmacoes = 0

        if (giroscopio != null && rotacaoMaxima >= LIMITE_ROTACAO) {
            confirmacoes++
        }
        if (barometro != null && variacaoPressao >= LIMITE_PRESSAO) {
            confirmacoes++
        }
        if (ruidoMaximo >= LIMITE_RUIDO) {
            confirmacoes++
        }

        // Se o aparelho nao tem nenhum sensor de confirmacao, a sequencia do
        // acelerometro vale sozinha.
        val temAlgumConfirmador = giroscopio != null || barometro != null || gravando

        Log.d(
            TAG,
            "Confirmacoes: %d de 3 | rotacao %.2f rad/s | pressao +%.3f hPa | ruido %.1f dB | %.1f km/h"
                .format(confirmacoes, rotacaoMaxima, variacaoPressao, ruidoMaximo, velocidadeKmh)
        )

        if (velocidadeKmh > LIMITE_VELOCIDADE_VEICULO) {
            // Dentro de um veiculo um solavanco produz o mesmo padrao de uma
            // queda. E a maior fonte de alarme falso deste tipo de app.
            Log.d(
                TAG,
                "REJEITADO: velocidade de %.1f km/h (acima de %.0f). A pessoa esta num veiculo."
                    .format(velocidadeKmh, LIMITE_VELOCIDADE_VEICULO)
            )
            return
        }

        if (temAlgumConfirmador && confirmacoes < CONFIRMACOES_NECESSARIAS) {
            Log.d(TAG, "REJEITADO: nenhum sensor confirmou a queda.")
            return
        }

        Log.d(TAG, "QUEDA CONFIRMADA. Abrindo tela de alerta.")
        dispararAlerta()
    }

    // -----------------------------------------------------------------------
    // Alerta
    // -----------------------------------------------------------------------

    /**
     * Mostra o alerta pelo caminho que o Android permitir no momento.
     *
     * Com a tela apagada, a notificacao de tela cheia acende o aparelho e abre
     * a Activity sozinha. Com a tela ligada, o Android bloqueia de proposito um
     * app em segundo plano de abrir Activity, entao usamos a janela flutuante.
     */
    private fun dispararAlerta() {
        if (Monitoramento.temAlertaNaTela()) {
            Log.d(TAG, "Ja existe um alerta na tela. Ignorando.")
            return
        }
        Monitoramento.marcarAlertaNaTela()

        publicarNotificacaoDeAlerta()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val intentDoAlerta = Intent(this, AlertaQuedaActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        if (!powerManager.isInteractive) {
            Log.d(TAG, "Tela apagada: abrindo a Activity de alerta.")
            startActivity(intentDoAlerta)
        } else if (Settings.canDrawOverlays(this)) {
            Log.d(TAG, "Tela ligada e permissao de sobreposicao concedida: usando overlay.")
            mostrarOverlay()
        } else {
            Log.d(TAG, "Tela ligada sem permissao de sobreposicao: tentando abrir a Activity.")
            startActivity(intentDoAlerta)
        }
    }

    private fun mostrarOverlay() {
        overlay?.remover()
        overlay = OverlayAlertaQueda(
            this,
            aoCancelar = {
                Monitoramento.liberarAlerta()
                cancelarNotificacaoDeAlerta()
                overlay = null
            },
            aoConfirmar = {
                cancelarNotificacaoDeAlerta()
                overlay = null
                // Pelo caminho do overlay nao da para abrir outra Activity, entao
                // o SMS sai direto daqui. O envio nunca depende de mostrar tela.
                EnvioDeSms.enviarParaTodos(applicationContext) { resumo ->
                    notificarResultadoDoEnvio(resumo)
                    Monitoramento.liberarAlerta()
                }
            }
        )
        overlay?.mostrar()
    }

    // -----------------------------------------------------------------------
    // Microfone
    // -----------------------------------------------------------------------

    /**
     * Mede o nivel de ruido continuamente numa Thread separada:
     * RMS = raiz(soma dos quadrados / quantidade) e dB = 20 * log10(RMS).
     * So guarda o barulho ocorrido durante a queda.
     */
    private fun iniciarMedicaoDeRuido() {
        gravando = true

        Thread {
            // A permissao e checada aqui dentro, logo antes de construir o
            // AudioRecord: ela pode ter sido revogada nesse meio-tempo.
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(TAG, "Sem permissao de microfone: confirmacao por ruido desligada.")
                gravando = false
                return@Thread
            }

            val buffer = ShortArray(1024)

            val audio = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    44100,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    4096
                )
            } catch (erro: Exception) {
                Log.d(TAG, "Nao foi possivel abrir o microfone: ${erro.message}")
                gravando = false
                return@Thread
            }

            try {
                audio.startRecording()

                while (gravando) {
                    val quantidade = audio.read(buffer, 0, buffer.size)

                    if (quantidade > 0 && estado != ESTADO_NORMAL) {
                        var soma = 0.0
                        for (i in 0 until quantidade) {
                            val valor = buffer[i].toDouble()
                            soma += valor * valor
                        }

                        val rms = sqrt(soma / quantidade)
                        if (rms > 0) {
                            val db = 20 * log10(rms)
                            if (db > ruidoMaximo) {
                                ruidoMaximo = db
                            }
                        }
                    }
                }
            } catch (erro: Exception) {
                Log.d(TAG, "Medicao de ruido interrompida: ${erro.message}")
            } finally {
                try {
                    audio.stop()
                } catch (erro: Exception) {
                }
                audio.release()
            }
        }.start()
    }

    // -----------------------------------------------------------------------
    // GPS
    // -----------------------------------------------------------------------

    private fun iniciarGps() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(TAG, "Sem permissao de localizacao: bloqueio por velocidade desligado.")
            return
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            // 5000 ms e o bastante para saber se a pessoa esta num veiculo,
            // sem gastar bateria como uma navegacao.
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                0f,
                ouvinteDeLocalizacao
            )
        } catch (erro: Exception) {
            Log.d(TAG, "GPS indisponivel: ${erro.message}")
        }

        try {
            locationManager?.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000L,
                0f,
                ouvinteDeLocalizacao
            )
        } catch (erro: Exception) {
            Log.d(TAG, "Localizacao por rede indisponivel: ${erro.message}")
        }
    }

    // -----------------------------------------------------------------------
    // Notificacoes
    // -----------------------------------------------------------------------

    private fun criarCanaisDeNotificacao() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val gerenciador = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val canalServico = NotificationChannel(
            CANAL_SERVICO,
            getString(R.string.canal_servico),
            NotificationManager.IMPORTANCE_LOW
        )
        gerenciador.createNotificationChannel(canalServico)

        val canalAlerta = NotificationChannel(
            CANAL_ALERTA,
            getString(R.string.canal_alerta),
            NotificationManager.IMPORTANCE_HIGH
        )
        canalAlerta.description = getString(R.string.canal_alerta_descricao)
        gerenciador.createNotificationChannel(canalAlerta)
    }

    private fun montarNotificacaoDoServico(): Notification {
        val abrirApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CANAL_SERVICO)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(getString(R.string.notificacao_servico_titulo))
            .setContentText(getString(R.string.notificacao_servico_texto))
            .setContentIntent(abrirApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun publicarNotificacaoDeAlerta() {
        val intentDoAlerta = PendingIntent.getActivity(
            this,
            1,
            Intent(this, AlertaQuedaActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificacao = NotificationCompat.Builder(this, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_warning)
            .setContentTitle(getString(R.string.fall_dialog_title))
            .setContentText(getString(R.string.notificacao_alerta_texto))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(intentDoAlerta)
            .setFullScreenIntent(intentDoAlerta, true)
            .setAutoCancel(true)
            .build()

        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_NOTIFICACAO_ALERTA, notificacao)
        } catch (erro: SecurityException) {
            Log.d(TAG, "Sem permissao de notificacao: ${erro.message}")
        }
    }

    private fun cancelarNotificacaoDeAlerta() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(ID_NOTIFICACAO_ALERTA)
    }

    private fun notificarResultadoDoEnvio(resumo: String) {
        val notificacao = NotificationCompat.Builder(this, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_sms)
            .setContentTitle(getString(R.string.notificacao_envio_titulo))
            .setContentText(resumo)
            .setAutoCancel(true)
            .build()

        try {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(ID_NOTIFICACAO_ENVIO, notificacao)
        } catch (erro: SecurityException) {
        }
    }

    // -----------------------------------------------------------------------
    // Encerramento: com o monitoramento desligado nada pode continuar rodando
    // -----------------------------------------------------------------------

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        gravando = false

        try {
            locationManager?.removeUpdates(ouvinteDeLocalizacao)
        } catch (erro: SecurityException) {
        }

        overlay?.remover()
        overlay = null

        Monitoramento.liberarAlerta()
        Monitoramento.servicoAtivo = false
        Log.d(TAG, "Servico encerrado. Sensores, microfone e GPS liberados.")

        super.onDestroy()
    }
}
