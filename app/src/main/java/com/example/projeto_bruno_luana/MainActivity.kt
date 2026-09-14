package com.example.projeto_bruno_luana

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlin.math.sqrt

/**
 * Tela de monitoramento. Liga e desliga o servico de deteccao, pede as
 * permissoes e mostra as leituras do acelerometro em tempo real.
 */
class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var txtStatus: TextView
    private lateinit var txtStatusDetalhe: TextView
    private lateinit var txtMagnitude: TextView
    private lateinit var txtEixoX: TextView
    private lateinit var txtEixoY: TextView
    private lateinit var txtEixoZ: TextView
    private lateinit var txtRotacao: TextView
    private lateinit var btnMonitoramento: Button
    private lateinit var btnSimularQueda: Button
    private lateinit var btnContatos: Button
    private lateinit var btnContatosTopo: ImageView
    private lateinit var anelPulsante: View

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var giroscopio: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatus = findViewById(R.id.txtStatus)
        txtStatusDetalhe = findViewById(R.id.txtStatusDetalhe)
        txtMagnitude = findViewById(R.id.txtMagnitude)
        txtEixoX = findViewById(R.id.txtEixoX)
        txtEixoY = findViewById(R.id.txtEixoY)
        txtEixoZ = findViewById(R.id.txtEixoZ)
        txtRotacao = findViewById(R.id.txtRotacao)
        btnMonitoramento = findViewById(R.id.btnMonitoramento)
        btnSimularQueda = findViewById(R.id.btnSimularQueda)
        btnContatos = findViewById(R.id.btnContatos)
        btnContatosTopo = findViewById(R.id.btnContatosTopo)
        anelPulsante = findViewById(R.id.anelPulsante)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        giroscopio = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        mostrarEstado(Monitoramento.servicoAtivo)

        btnMonitoramento.setOnClickListener {
            // O botao alterna: ligado vira desligado e vice-versa.
            val ligando = !Monitoramento.servicoAtivo

            if (ligando) {
                startService(Intent(this, DetectorQuedaService::class.java))
                // Pedida de novo toda vez que o monitoramento e ligado.
                pedirPermissaoDeSobreposicao()
            } else {
                stopService(Intent(this, DetectorQuedaService::class.java))
                Monitoramento.servicoAtivo = false
            }

            mostrarEstado(ligando)
        }

        btnSimularQueda.setOnClickListener {
            startActivity(Intent(this, AlertaQuedaActivity::class.java))
        }

        // O icone do topo e o botao de baixo levam para a mesma tela.
        btnContatos.setOnClickListener { abrirContatos() }
        btnContatosTopo.setOnClickListener { abrirContatos() }

        pedirPermissoesComuns()
    }

    private fun abrirContatos() {
        startActivity(Intent(this, EmergencyContactsActivity::class.java))
    }

    private fun mostrarEstado(ativo: Boolean) {
        if (ativo) {
            txtStatus.text = getString(R.string.monitoring_status_active)
            txtStatusDetalhe.text = getString(R.string.monitoring_subtitle_active)
            btnMonitoramento.text = getString(R.string.monitoring_turn_off)
            animarAnel()
        } else {
            btnMonitoramento.text = getString(R.string.monitoring_turn_on)
            txtStatus.text = getString(R.string.monitoring_status_paused)
            txtStatusDetalhe.text = getString(R.string.monitoring_subtitle_paused)
            anelPulsante.clearAnimation()
            anelPulsante.animate().cancel()
            anelPulsante.scaleX = 1f
            anelPulsante.scaleY = 1f
            anelPulsante.alpha = 0.4f
        }
    }

    private fun animarAnel() {
        anelPulsante.alpha = 1f
        anelPulsante.scaleX = 1f
        anelPulsante.scaleY = 1f
        anelPulsante.animate()
            .scaleX(1.25f)
            .scaleY(1.25f)
            .alpha(0f)
            .setDuration(1400)
            .setInterpolator(LinearInterpolator())
            .withEndAction {
                if (Monitoramento.servicoAtivo) {
                    animarAnel()
                }
            }
            .start()
    }

    // -----------------------------------------------------------------------
    // Permissoes
    // -----------------------------------------------------------------------

    /**
     * Junta num unico pedido as permissoes que tem pop-up. A permissao de
     * sobreposicao vem depois, no onRequestPermissionsResult, para a tela de
     * Configuracoes aparecer por ultimo.
     */
    private fun pedirPermissoesComuns() {
        val pendentes = mutableListOf<String>()

        val desejadas = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.SEND_SMS
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            desejadas.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        for (permissao in desejadas) {
            if (ActivityCompat.checkSelfPermission(this, permissao)
                != PackageManager.PERMISSION_GRANTED
            ) {
                pendentes.add(permissao)
            }
        }

        if (pendentes.isEmpty()) {
            pedirPermissaoDeSobreposicao()
            return
        }

        ActivityCompat.requestPermissions(this, pendentes.toTypedArray(), CODIGO_PERMISSOES)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == CODIGO_PERMISSOES) {
            pedirPermissaoDeSobreposicao()
        }
    }

    /**
     * "Exibir sobre outros apps" nao tem pop-up: e preciso abrir a tela de
     * Configuracoes. Sem ela o app continua detectando e enviando SMS, so que
     * com a tela ligada o alerta aparece apenas como notificacao.
     */
    private fun pedirPermissaoDeSobreposicao() {
        if (Settings.canDrawOverlays(this)) {
            return
        }

        Toast.makeText(
            this,
            getString(R.string.permissao_sobreposicao_aviso),
            Toast.LENGTH_LONG
        ).show()

        try {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (erro: Exception) {
            // Alguns aparelhos nao tem essa tela.
        }
    }

    // -----------------------------------------------------------------------
    // Leituras mostradas na tela (soltas no onPause)
    // -----------------------------------------------------------------------

    override fun onResume() {
        super.onResume()

        mostrarEstado(Monitoramento.servicoAtivo)

        sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_UI)
        sensorManager.registerListener(this, giroscopio, SensorManager.SENSOR_DELAY_UI)
    }

    override fun onPause() {
        // A tela solta os sensores ao sair: quem monitora de verdade e o servico.
        sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0].toDouble()
            val y = event.values[1].toDouble()
            val z = event.values[2].toDouble()
            val magnitude = sqrt(x * x + y * y + z * z)

            txtEixoX.text = "%.2f".format(x)
            txtEixoY.text = "%.2f".format(y)
            txtEixoZ.text = "%.2f".format(z)
            txtMagnitude.text = "%.2f".format(magnitude)
        }

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0].toDouble()
            val gy = event.values[1].toDouble()
            val gz = event.values[2].toDouble()
            val rotacao = sqrt(gx * gx + gy * gy + gz * gz)

            txtRotacao.text = getString(R.string.monitoring_gyro_valor, rotacao)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    companion object {
        private const val CODIGO_PERMISSOES = 10
    }
}
