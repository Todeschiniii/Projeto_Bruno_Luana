# Padrões de código das aulas

Este projeto é um trabalho de disciplina. O código precisa usar apenas
construções que o professor apresentou em aula — não o que o Android Studio gera
por padrão nem o que é considerado "moderno" na documentação oficial.

## Regras em vigor

1. **`findViewById`**, nunca ViewBinding nem Jetpack Compose.
2. **`LinearLayout` / `FrameLayout` / `ScrollView`** com `TextView`, `Button`,
   `ImageView`, `EditText`, `ProgressBar`. Nada de ConstraintLayout, CardView,
   RecyclerView ou Material Components. Cartão arredondado se faz com
   `android:background` apontando para um *shape drawable* mais
   `android:elevation`.
3. **Listas** com `layoutInflater.inflate(...)` + `addView(...)`, nunca
   `RecyclerView` + `Adapter`.
4. **O menor número possível de bibliotecas**: só `androidx.appcompat` e
   `androidx.core`. Sem Google Play Services, sem `FusedLocationProviderClient`
   (usa `LocationManager`), sem banco de dados (usa `SharedPreferences`).
5. **Threads** no padrão `Thread { ... }.start()` com um booleano `@Volatile`
   para parar e `runOnUiThread { ... }` para atualizar a tela. Fora de uma
   Activity, `Handler(Looper.getMainLooper()).post { }`.
6. **Permissões** com `ActivityCompat.requestPermissions(...)` e
   `ActivityCompat.checkSelfPermission(...)`.
7. Comentários e textos de tela **em português**.

Estas regras substituíram uma decisão anterior do projeto, que mantinha
ConstraintLayout, Material Components, RecyclerView e tema escuro. A conversão
foi feita por inteiro: a biblioteca Material, a ConstraintLayout e a RecyclerView
saíram do `build.gradle.kts`, o tema passou a ser `Theme.AppCompat.Light` e a
pasta `res/values-night/` foi removida, porque dependia de atributos do
Material3.

## O padrão apresentado em aula

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var txtAlgo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtAlgo = findViewById(R.id.txtAlgo)

        botao.setOnClickListener {
            // ação
        }
    }
}
```

| Assunto | Como foi ensinado |
| --- | --- |
| Ligação XML ↔ Kotlin | `setContentView(R.layout.…)` + `findViewById` |
| Referências de View | `private lateinit var` inicializadas no `onCreate` |
| Navegação | `Intent(this, OutraActivity::class.java)` + `startActivity()`; voltar com `finish()` |
| Fragments | Nunca foram apresentados |
| Trabalho fora da thread principal | `Thread { … }.start()` com `while` e `runOnUiThread` |
| Repetição | `for (i in 1..5)`, `for (item in lista)`, `while`, `do while` |
| Decisão | `if` / `else if` / `else` e `when` |
| Modelo de dados | `data class` |
| Animação | `view.animate().translationX(…).setDuration(…).start()` |
| Liga/desliga | `estado = !estado` e troca do texto do botão |
| Sensores | `AppCompatActivity(), SensorEventListener` + `getSystemService(SENSOR_SERVICE) as SensorManager` + `registerListener` + `onSensorChanged` |
| Microfone | `AudioRecord` com RMS e `20 * log10(rms)` |
| Permissões | `ActivityCompat.requestPermissions(this, arrayOf(…), 1)` |

## Onde o projeto teve que sair do padrão — e por quê

Estes itens não aparecem em nenhum material da disciplina, mas não têm
alternativa dentro do que foi ensinado:

- **`Service` em primeiro plano.** O professor nunca mostrou `Service`. Sem ele
  o monitoramento só funcionaria com o app aberto na tela, o que anula o
  propósito de um detector de quedas.
- **`SharedPreferences`** para salvar os contatos. As opções mostradas em aula
  (MySQL com API REST e Firebase) exigem servidor ou configuração externa. O
  código em `ContatosSalvos.kt` usa só funções soltas, `for`, `if`,
  `mutableListOf` e concatenação de texto.
- **`NotificationCompat` com `setFullScreenIntent`** e canais de notificação.
  É a única forma documentada de o Android abrir uma tela com o aparelho
  bloqueado.
- **`WindowManager` com `TYPE_APPLICATION_OVERLAY`** para o alerta com a tela
  ligada — ver [alerta-e-permissoes.md](alerta-e-permissoes.md).
- **`SmsManager`** para o envio real do SMS.
- **`registerForActivityResult`** para abrir a agenda do celular. Esse é o mesmo
  padrão da aula de Câmera (`TakePicturePreview()`) e da aula de Machine
  Learning (`GetContent()`), aqui com o contrato `StartActivityForResult`.
- **`Button` no lugar de `Switch`** no monitoramento. Além de `Switch` não estar
  na lista de views permitidas, em tema AppCompat o inflater troca `<Switch>` por
  `SwitchCompat`, que não é `android.widget.Switch` — `findViewById<Switch>`
  estouraria `ClassCastException`. O botão que alterna texto é exatamente o
  padrão do aplicativo de ruído da aula de sensores.
