# Alerta em tela cheia e permissões

## Por que existem dois caminhos

Isto é comportamento **documentado do Android**, não bug:

| Estado da tela | O que o Android permite |
| --- | --- |
| **Apagada ou bloqueada** | Uma notificação com `setFullScreenIntent(...)` acende a tela e abre a Activity sozinha |
| **Ligada e desbloqueada** | O Android **bloqueia de propósito** um app em segundo plano de abrir uma Activity nova. O `fullScreenIntent` vira só uma notificação de topo |

Por isso `DetectorQuedaService.dispararAlerta()` escolhe o caminho por
`PowerManager.isInteractive`:

```kotlin
publicarNotificacaoDeAlerta()   // sempre, nos dois casos

if (!powerManager.isInteractive) {
    startActivity(intentDoAlerta.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
} else if (Settings.canDrawOverlays(this)) {
    mostrarOverlay()
} else {
    startActivity(intentDoAlerta.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
```

## A janela flutuante

[`OverlayAlertaQueda.kt`](../app/src/main/java/com/example/projeto_bruno_luana/OverlayAlertaQueda.kt)
infla **o mesmo layout** da tela de alerta (`activity_alerta_queda.xml`) e o
adiciona ao `WindowManager` com `TYPE_APPLICATION_OVERLAY` (ou
`TYPE_SYSTEM_ALERT` abaixo da API 26).

`FLAG_LAYOUT_IN_SCREEN` é o que faz a janela cobrir a tela inteira, **inclusive
a faixa da barra de status**. Sem ele o fundo para antes dela e sobra a cor do
app que estava aberto atrás, e não parece uma tela de verdade.

`windowManager.removeView(view)` fica dentro de `try/catch (IllegalArgumentException)`.

## A Activity

[`AlertaQuedaActivity.kt`](../app/src/main/java/com/example/projeto_bruno_luana/AlertaQuedaActivity.kt)
usa `setShowWhenLocked(true)` e `setTurnScreenOn(true)` em código (API 27+, com
verificação de `Build.VERSION.SDK_INT`), **não** os atributos
`android:showOnLockScreen` / `android:turnScreenOn` do Manifest.

## Quem envia o SMS em cada caminho

| Caminho | Quem envia |
| --- | --- |
| Activity (tela apagada) | Após os 15 segundos sem cancelamento, abre `SmsSendingActivity`, que envia e mostra o resultado — o app já está em primeiro plano, então abrir outra Activity é permitido |
| Overlay (tela ligada) | Após os 15 segundos sem cancelamento, o **próprio serviço** chama `EnvioDeSms.enviarParaTodos(...)` e confirma com uma notificação. Não tenta abrir Activity nenhuma, porque esbarraria na mesma restrição que criou o overlay |

Esse é o ponto central: **o SMS, que é a função crítica, nunca depende de o
Android permitir mostrar uma tela.** Por isso
[`EnvioDeSms.kt`](../app/src/main/java/com/example/projeto_bruno_luana/EnvioDeSms.kt)
é um `object` que só precisa de um `Context`.

## O som

Alarme padrão do próprio celular, sem arquivo de áudio no projeto
([`AlarmeDeQueda.kt`](../app/src/main/java/com/example/projeto_bruno_luana/AlarmeDeQueda.kt)):
`RingtoneManager.getDefaultUri(TYPE_ALARM)` + `MediaPlayer` com
`AudioAttributes` de `USAGE_ALARM`, que faz o som sair no volume de alarme e
continuar audível com o celular no silencioso. Tudo em `try/catch`, com `stop()`
+ `release()` ao cancelar e no `onDestroy`.

## A notificação fantasma

Quando o alerta é resolvido pelo botão (cancelado **ou** enviado), a notificação
"Queda detectada!" é cancelada explicitamente:

```kotlin
(getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
    .cancel(ID_NOTIFICACAO_ALERTA)
```

`setAutoCancel(true)` só limpa a notificação quando o usuário **toca** nela. Se
o alerta for resolvido pelo botão, ela fica guardada com o `fullScreenIntent`
ainda válido — e mais tarde, quando a tela apagar, o Android reabre a tela de
alerta sozinho, mostrando um alerta fantasma de uma queda já resolvida.

## Não empilhar dois alertas

`Monitoramento.alertaNaTela` marca que um alerta já está visível. É liberada
pelo `onDestroy` da Activity e pelos callbacks do overlay. Há um limite de
60 segundos: passado esse tempo a marca se solta sozinha, para um alerta que
travou não bloquear todos os próximos.

## Permissão "Exibir sobre outros apps"

Não é uma permissão comum: **não existe pop-up**, é preciso abrir uma tela de
Configurações com `Settings.ACTION_MANAGE_OVERLAY_PERMISSION`.

É pedida **ao abrir o aplicativo** e **toda vez que o monitoramento é ligado**.

### Ordem dos pedidos

No `onCreate` da `MainActivity` as permissões com pop-up são pedidas juntas num
único `requestPermissions`: `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`,
`ACCESS_COARSE_LOCATION`, `SEND_SMS` e, no Android 13+, `POST_NOTIFICATIONS`.
Só em `onRequestPermissionsResult` é chamado `pedirPermissaoDeSobreposicao()` —
assim os pop-ups aparecem primeiro e a tela de Configurações por último.

### O que muda para o usuário

> **Se você permitir:** o alerta de queda abre sozinho em tela cheia em qualquer
> situação — com o celular bloqueado, desbloqueado ou com outro aplicativo
> aberto.
>
> **Se você não permitir:** o app continua detectando quedas e continua enviando
> o SMS normalmente. Mas, com o celular ligado e destravado, o alerta vai
> aparecer **apenas como notificação** no topo, e você vai precisar tocar nela
> para abrir a tela de cancelamento. Com a tela apagada, continua abrindo
> sozinho.

**O monitoramento nunca é bloqueado** por falta dessa permissão.

### Caminho num Samsung Galaxy A33 (One UI)

No Samsung a opção **não** se chama "Display over other apps", e sim
**"Appear on top"**:

**Settings → Apps → ⋮ (três pontinhos no canto superior direito) →
Special access → Appear on top → Detector de Quedas → ligar**

Também funciona por: **Settings → Apps → Detector de Quedas → Appear on top**.

## Serviço em primeiro plano

O `DetectorQuedaService` é declarado com `foregroundServiceType="specialUse"` e
a propriedade `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`. Os tipos `health` e
`microphone` foram descartados: no Android 14+ eles exigem que uma permissão
perigosa específica já esteja concedida no momento do `startForeground`, e
lançam `SecurityException` se o usuário tiver negado. `specialUse` não tem esse
acoplamento, então o serviço sobe mesmo com permissões negadas — e cada sensor
se desliga sozinho conforme a permissão que faltar.
