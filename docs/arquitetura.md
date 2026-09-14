# Arquitetura

## Visão geral

Aplicativo Android nativo em Kotlin para detecção de quedas com alerta de
emergência. Um *foreground service* lê os sensores continuamente, identifica a
queda por uma máquina de três estados, mostra imediatamente um alerta em tela
cheia com contagem de 15 segundos e som de alarme e, se ninguém cancelar, envia
SMS com a localização para os contatos cadastrados.

Documentos por assunto:

- [deteccao-de-queda.md](deteccao-de-queda.md) — o algoritmo, os limiares, os
  logs e como testar sem cair
- [alerta-e-permissoes.md](alerta-e-permissoes.md) — os dois caminhos do alerta
  em tela cheia, o som, as notificações e as permissões
- [telas.md](telas.md) — o que cada tela faz
- [padroes-das-aulas.md](padroes-das-aulas.md) — as regras de código do projeto

## Stack técnica

- **Linguagem**: Kotlin
- **UI**: Android Views (XML) + `findViewById` — sem ViewBinding e sem Compose
- **Bibliotecas**: apenas `androidx.appcompat` e `androidx.core`. Sem Material
  Components, sem ConstraintLayout, sem RecyclerView, sem Google Play Services
- **Localização**: `LocationManager` (não `FusedLocationProviderClient`)
- **Persistência**: `SharedPreferences` (sem banco de dados)
- **Build**: AGP 9.2.1 / compileSdk 37 / minSdk 24 / targetSdk 36
- **Namespace**: `com.example.projeto_bruno_luana`

O AGP 9.2.1 compila Kotlin nativamente — não é necessário aplicar o plugin
`org.jetbrains.kotlin.android`; aplicá-lo à parte trava o build com
`Cannot add extension with name 'kotlin'`.

## Estrutura de pacotes

Todo o código vive em `app/src/main/java/com/example/projeto_bruno_luana/`:

| Arquivo | Papel |
| --- | --- |
| `MainActivity.kt` | Tela de monitoramento: liga/desliga o serviço, pede permissões, mostra as leituras |
| `DetectorQuedaService.kt` | O cérebro: sensores, máquina de estados, confirmações, disparo do alerta |
| `AlertaQuedaActivity.kt` | Alerta em tela cheia (caminho da tela apagada) |
| `OverlayAlertaQueda.kt` | Alerta como janela flutuante (caminho da tela ligada) |
| `AlarmeDeQueda.kt` | Toca o alarme padrão do celular durante o alerta |
| `EnvioDeSms.kt` | Envia o SMS sem depender de nenhuma tela |
| `SmsSendingActivity.kt` | Tela de resultado do envio |
| `EmergencyContactsActivity.kt` | Cadastro, listagem e exclusão de contatos |
| `ContatosSalvos.kt` | Leitura e gravação dos contatos em `SharedPreferences` |
| `EmergencyContact.kt` | Modelo do contato e constantes de status |
| `Monitoramento.kt` | Estado compartilhado, IDs de notificação e última localização |

## Fluxo principal

```
MainActivity (liga)
      |
      v
DetectorQuedaService  --sensores-->  queda confirmada
      |
      +-- tela apagada --> AlertaQuedaActivity --> SmsSendingActivity (envia)
      |
      +-- tela ligada  --> OverlayAlertaQueda  --> EnvioDeSms direto do serviço
```

## Persistência

Os contatos ficam em `SharedPreferences`, arquivo `contatos_emergencia`, chave
`lista`: cada contato é `nome|telefone` e os contatos são separados por quebra
de linha. A flag `ja_abriu` diferencia "primeira execução" de "o usuário apagou
todos os contatos". Na primeira execução são cadastrados três exemplos.

Não há banco de dados nem servidor.

## Paleta e tema

Tema `Theme.AppCompat.Light.NoActionBar` com cores explícitas em
`res/values/colors.xml` — sem atributos do Material3 e sem `values-night`, que
dependiam da biblioteca Material removida. Cartões são `LinearLayout` com
`android:background` apontando para um *shape drawable* mais
`android:elevation`.

A tela de alerta usa `Theme.App.Alerta`, em tela cheia, com gradiente
vermelho→laranja.
