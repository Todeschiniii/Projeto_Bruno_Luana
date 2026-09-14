# Detecção de queda

Toda a lógica vive em [`DetectorQuedaService.kt`](../app/src/main/java/com/example/projeto_bruno_luana/DetectorQuedaService.kt),
um *foreground service*. A tela principal não detecta nada — ela só mostra
números e liga/desliga o serviço.

## A grandeza medida

```
|a| = raiz(ax² + ay² + az²)
```

Com o celular parado vale ≈ 9,81 m/s² (a gravidade). Em queda livre tende a zero.

## Máquina de três estados

```
NORMAL ──|a| < 4,0──> QUEDA LIVRE ──|a| > 25,0──> IMPACTO ──primeira leitura parada──> CONFIRMA
             (até 1,5 s para o impacto)                         (até 6 s para ficar parado)
```

| Etapa | Condição | Por quê |
| --- | --- | --- |
| Queda livre | `\|a\| < 4,0` | Sem perda de gravidade não houve queda, houve batida |
| Impacto | `\|a\| > 25,0` em até 1,5 s | Separa queda de celular sendo abaixado devagar |
| Imobilidade | primeira leitura com `abs(\|a\| − 9,81) < 2,5` | Dispara o alerta assim que o aparelho fica parado |

Se o impacto não vier em 1,5 s, ou se a pessoa voltar a se mexer em até 6 s
depois do impacto, o estado volta para NORMAL e nada dispara. Ao receber a
primeira leitura de repouso, o serviço confirma a queda e dispara o alerta na
mesma execução do callback do acelerômetro. Não há `Handler`, `Runnable` nem
tempo de espera entre a imobilidade e o alerta.

## Taxa de amostragem — o detalhe que faz a detecção funcionar

O acelerômetro é registrado com **`SENSOR_DELAY_GAME`** (≈ 20 ms), não com
`SENSOR_DELAY_NORMAL` (≈ 200 ms). O pico do impacto dura poucos milissegundos:
com a taxa normal ele cai entre duas leituras e a queda nunca é detectada. Este
é o motivo mais comum de um detector de quedas "não detectar nada".

## Sensores de confirmação

Depois da sequência do acelerômetro, três sensores **confirmam** e um
**bloqueia**. Basta **um** confirmar. Sensor que o aparelho não tem é ignorado;
se nenhum existir, a sequência do acelerômetro vale sozinha.

| Sensor | Papel | Regra |
| --- | --- | --- |
| Giroscópio | Confirma | `raiz(gx²+gy²+gz²)` máximo durante a queda ≥ **1,5 rad/s** |
| Barômetro (`TYPE_PRESSURE`) | Confirma | Pressão sobe ≥ **0,08 hPa** desde o início da queda |
| Microfone (`AudioRecord`) | Confirma | Ruído máximo durante a queda ≥ **72 dB** |
| GPS (`LocationManager`) | **Bloqueia** | Acima de **24 km/h** descarta o alerta |

### Por que o GPS bloqueia em vez de ativar

A especificação original (GPS acima de 24 km/h, picos de 256 G, barômetro de
airbag, som de metal retorcido) descreve **detecção de acidente de carro**, não
queda de pessoa. Monitorar só acima de 24 km/h nunca detectaria queda nenhuma:
uma pessoa andando faz 5 km/h.

Por isso a regra foi invertida: **acima de 24 km/h o alerta é suprimido**,
porque a pessoa está num veículo e um solavanco de estrada produz exatamente o
mesmo padrão de uma queda. Essa é a maior fonte de alarme falso deste tipo de
app.

A velocidade vem de `location.speed * 3.6` num `LocationListener` registrado com
intervalo de 5000 ms — o bastante para saber se está num veículo, sem gastar
bateria como uma navegação.

### O barômetro

Cair de ~1,5 m aumenta a pressão do ar em ~0,18 hPa. A pressão é guardada no
instante em que a queda livre começa e comparada durante a queda. Muitos
aparelhos intermediários não têm barômetro: `getDefaultSensor(TYPE_PRESSURE)`
nulo é tratado sem quebrar.

### O microfone

Medido continuamente numa `Thread`, com `AudioRecord` (`MIC`, 44100 Hz,
`CHANNEL_IN_MONO`, `ENCODING_PCM_16BIT`, buffer 4096), pelo mesmo cálculo da
aula de sensores:

```
RMS = raiz(soma dos quadrados das amostras / quantidade)
dB  = 20 * log10(RMS)
```

Só conta o barulho ocorrido **durante** a queda (estado diferente de NORMAL).

A permissão `RECORD_AUDIO` é verificada **dentro da Thread**, logo antes de
construir o `AudioRecord`: se for verificada só do lado de fora, o lint acusa
`MissingPermission` e a permissão pode ter sido revogada nesse meio-tempo.

## Limiares

Todos são `private const val` no topo do arquivo, com a unidade no comentário.

| Constante | Valor | Unidade |
| --- | --- | --- |
| `GRAVIDADE` | 9.81 | m/s² |
| `LIMITE_QUEDA_LIVRE` | 4.0 | m/s² |
| `LIMITE_IMPACTO` | 25.0 | m/s² |
| `LIMITE_ROTACAO` | 1.5 | rad/s |
| `LIMITE_PRESSAO` | 0.08 | hPa |
| `LIMITE_RUIDO` | 72.0 | dB |
| `LIMITE_VELOCIDADE_VEICULO` | 24.0 | km/h |
| `TEMPO_MAXIMO_ATE_IMPACTO` | 1500 | ms |
| `TOLERANCIA_IMOBILIDADE` | 2.5 | m/s² |
| `TEMPO_MAXIMO_APOS_IMPACTO` | 6000 | ms |
| `CONFIRMACOES_NECESSARIAS` | 1 | — |

## Logs de diagnóstico

Tag `FallDetector`, em cada transição e em cada rejeição:

```
1/3 QUEDA LIVRE detectada (|a|=0.50 < 4.0). Esperando impacto em ate 1500 ms.
2/3 IMPACTO detectado (|a|=30.00 > 25.0), 84 ms depois da queda livre.
3/3 IMOBILIDADE confirmada sem espera.
Confirmacoes: 1 de 3 | rotacao 5.20 rad/s | pressao +0.000 hPa | ruido 12.4 dB | 0.0 km/h
QUEDA CONFIRMADA. Abrindo tela de alerta.
Descartado: queda livre sem impacto em ate 1500 ms.
REJEITADO: nenhum sensor confirmou a queda.
REJEITADO: velocidade de 55.0 km/h (acima de 24). A pessoa esta num veiculo.
```

Para filtrar:

```bash
adb logcat -s FallDetector
```

## Como testar sem cair de verdade

No emulador, injetando os valores do acelerômetro e do giroscópio:

```bash
adb emu sensor set gyroscope 5:5:5
adb emu sensor set acceleration 0:0:0
```

```bash
adb emu sensor set acceleration 0:30:0
```

```bash
adb emu sensor set acceleration 0:9.81:0
```

Entre os dois primeiros comandos deve passar menos de 1,5 s. Assim que o
terceiro comando entregar a primeira leitura parada, o alerta deve aparecer,
sem espera adicional. O giroscópio precisa estar diferente de zero durante a
queda, senão nenhum sensor confirma e o alerta é rejeitado (o que também é um
teste válido).

## Encerramento

Com o monitoramento desligado o consumo é zero. `onDestroy` solta tudo:

```kotlin
sensorManager.unregisterListener(this)   // acelerômetro, giroscópio, barômetro
gravando = false                         // encerra a Thread do microfone
locationManager?.removeUpdates(...)      // GPS
```

Não há `WorkManager`, `AlarmManager` nem `BroadcastReceiver` de boot. A
notificação fixa sai junto com o serviço.

Para comprovar, com o monitoramento desligado e o app em segundo plano:

```bash
adb shell dumpsys sensorservice | sed -n '/active connections/,/Previous Registrations/p'
```

Nenhuma linha pode citar `com.example.projeto_bruno_luana` — só devem aparecer
conexões do próprio Android (`FaceDownDetector`, `WindowOrientationListener`).

```bash
adb shell dumpsys activity services com.example.projeto_bruno_luana
```

Não pode existir `ServiceRecord`.

```bash
adb shell dumpsys power | grep com.example.projeto_bruno_luana
```

Não pode haver wakelock.
