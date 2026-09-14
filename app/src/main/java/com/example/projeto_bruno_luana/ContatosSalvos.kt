package com.example.projeto_bruno_luana

import android.content.Context

/**
 * Guarda os contatos de emergencia no proprio aparelho, usando SharedPreferences.
 *
 * Cada contato e gravado no formato "nome|telefone" e os contatos ficam
 * separados por quebra de linha dentro de um unico texto. Nao usa banco de
 * dados nem servidor: e tudo salvo localmente pelo proprio Android.
 */

private const val ARQUIVO_CONTATOS = "contatos_emergencia"
private const val CHAVE_LISTA = "lista"
private const val CHAVE_JA_ABRIU = "ja_abriu"

private const val SEPARADOR_CAMPO = "|"
private const val SEPARADOR_CONTATO = "\n"

/**
 * Devolve os contatos salvos. Na primeira vez que o aplicativo e aberto,
 * cadastra alguns contatos de exemplo para a tela nao comecar vazia.
 */
fun carregarContatos(context: Context): MutableList<EmergencyContact> {
    val prefs = context.getSharedPreferences(ARQUIVO_CONTATOS, Context.MODE_PRIVATE)

    if (!prefs.getBoolean(CHAVE_JA_ABRIU, false)) {
        val exemplos = mutableListOf(
            EmergencyContact("Ana Beatriz", "(11) 98888-1234", STATUS_ENVIADO),
            EmergencyContact("Carlos Eduardo", "(11) 97777-5678", STATUS_ENVIADO),
            EmergencyContact("SAMU", "192", STATUS_ENVIADO)
        )
        salvarContatos(context, exemplos)
        return exemplos
    }

    val texto = prefs.getString(CHAVE_LISTA, "") ?: ""
    val contatos = mutableListOf<EmergencyContact>()

    if (texto.isEmpty()) {
        return contatos
    }

    for (linha in texto.split(SEPARADOR_CONTATO)) {
        val campos = linha.split(SEPARADOR_CAMPO)
        if (campos.size == 2 && campos[0].isNotEmpty()) {
            contatos.add(EmergencyContact(campos[0], campos[1], STATUS_ENVIADO))
        }
    }

    return contatos
}

/** Grava a lista inteira de contatos por cima do que estava salvo antes. */
fun salvarContatos(context: Context, contatos: List<EmergencyContact>) {
    var texto = ""

    for (contato in contatos) {
        if (texto.isNotEmpty()) {
            texto += SEPARADOR_CONTATO
        }
        texto += limparSeparadores(contato.name) + SEPARADOR_CAMPO + limparSeparadores(contato.phone)
    }

    val prefs = context.getSharedPreferences(ARQUIVO_CONTATOS, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(CHAVE_LISTA, texto)
        .putBoolean(CHAVE_JA_ABRIU, true)
        .apply()
}

/**
 * Tira do texto digitado os caracteres que sao usados como separador,
 * para que um nome com "|" nao embaralhe o arquivo salvo.
 */
private fun limparSeparadores(texto: String): String {
    return texto.replace(SEPARADOR_CAMPO, " ").replace(SEPARADOR_CONTATO, " ").trim()
}
