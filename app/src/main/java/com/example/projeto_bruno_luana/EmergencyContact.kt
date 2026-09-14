package com.example.projeto_bruno_luana

// Situacoes possiveis do envio do SMS para um contato de emergencia.
const val STATUS_ENVIADO = "enviado"
const val STATUS_ENVIANDO = "enviando"
const val STATUS_FALHOU = "falhou"

data class EmergencyContact(
    val name: String,
    val phone: String,
    val status: String
)
