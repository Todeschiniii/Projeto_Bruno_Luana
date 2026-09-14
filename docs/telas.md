# Telas

Todas usam `LinearLayout` / `FrameLayout` / `ScrollView` com `TextView`,
`Button`, `ImageView`, `EditText` e `ProgressBar`. Listas são montadas com
`layoutInflater.inflate(...)` + `addView(...)`.

## 1. Monitoramento (`MainActivity` / `activity_main.xml`)

Tela inicial.

- Cartão de status com escudo dentro de um círculo laranja e um anel que pulsa
  com `view.animate()` enquanto o monitoramento está ligado.
- Botão **Ativar monitoramento** / **Desativar monitoramento**, que alterna o
  estado e dá `startService` / `stopService` no `DetectorQuedaService`. Ao ligar,
  pede de novo a permissão de sobreposição.
- **Leituras em tempo real**: uma caixa preta (`#121212`) com a aceleração
  resultante em laranja (`#FF9800`), grande e em negrito, com a unidade e o
  rótulo em cinza claro. Abaixo, os eixos X, Y e Z e a rotação. Não aparece o
  nome do sensor.
- Botão **Contatos de emergência** no rodapé e ícone de contatos no topo
  direito — os dois abrem a mesma tela.
- Botão **Simular queda (teste)** abre o alerta direto, sem passar pelos
  sensores. **Atenção: esse caminho envia SMS de verdade** se a contagem não for
  cancelada.

A tela registra os sensores no `onResume` só para mostrar os números e os solta
no `onPause`. Quem monitora de verdade é o serviço.

## 2. Alerta de queda (`AlertaQuedaActivity` / `OverlayAlertaQueda` / `activity_alerta_queda.xml`)

Ocupa o celular inteiro, com gradiente vermelho→laranja, contagem regressiva de
15 segundos, barra de progresso e som de alarme. A tela aparece imediatamente
quando a queda é confirmada, sem espera adicional após a imobilidade.

- **Estou bem, cancelar** encerra tudo e cancela a notificação.
- **Enviar agora** pula a espera.
- O botão voltar não encerra a contagem.

O mesmo layout serve para a Activity e para a janela flutuante — ver
[alerta-e-permissoes.md](alerta-e-permissoes.md).

## 3. Contatos de emergência (`EmergencyContactsActivity` / `activity_emergency_contacts.xml`)

- Formulário com nome e telefone e o botão **Adicionar contato**. Campo vazio é
  barrado com `Toast` e o foco volta para o campo que faltou.
- **Buscar na agenda do celular** abre a agenda por
  `Intent(ACTION_PICK, Phone.CONTENT_URI)` e preenche o formulário com o contato
  escolhido. Não exige `READ_CONTACTS`: o Android libera só a linha selecionada.
  A consulta está em `try/catch` porque alguns aparelhos devolvem um endereço
  sem as colunas de telefone.
- Lista dos contatos cadastrados, cada um com botão de excluir. A lista inteira
  é remontada a cada mudança, com `inflate` + `addView`.
- Sem nenhum contato, aparece um aviso no lugar da lista.

## 4. Envio de SMS (`SmsSendingActivity` / `activity_sms.xml`)

Aberta quando o alerta não foi cancelado a tempo, pelo caminho da Activity.

- Localização real capturada pelo `LocationManager`, com a precisão em metros.
  Sem posição, avisa que o SMS vai sem o link do mapa.
- Prévia da mensagem que foi enviada.
- Lista dos contatos com chip de status: **Enviado** (verde), **Enviando…**
  (âmbar) ou **Falhou** (vermelho).
- O envio acontece de verdade, por `EnvioDeSms`.
