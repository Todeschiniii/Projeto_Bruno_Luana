package com.example.projeto_bruno_luana

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * Tela de contatos de emergencia: cadastra, lista e exclui quem recebe o SMS.
 * A lista e montada com layoutInflater.inflate(...) + addView(...).
 */
class EmergencyContactsActivity : AppCompatActivity() {

    private lateinit var campoNome: EditText
    private lateinit var campoTelefone: EditText
    private lateinit var listaContatos: LinearLayout
    private lateinit var txtListaVazia: TextView

    private lateinit var contatos: MutableList<EmergencyContact>

    /**
     * Abre a agenda do celular. O Android devolve nome e telefone da linha
     * escolhida sem exigir READ_CONTACTS, porque o acesso vale so para ela.
     */
    private val escolherDaAgenda = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) {
            val dados = resultado.data
            if (dados != null) {
                preencherComContatoEscolhido(dados.data)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emergency_contacts)

        campoNome = findViewById(R.id.campoNome)
        campoTelefone = findViewById(R.id.campoTelefone)
        listaContatos = findViewById(R.id.listaContatos)
        txtListaVazia = findViewById(R.id.txtListaVazia)

        contatos = carregarContatos(this)
        montarLista()

        findViewById<View>(R.id.btnAdicionar).setOnClickListener {
            adicionarContato()
        }

        findViewById<View>(R.id.btnBuscarAgenda).setOnClickListener {
            abrirAgendaDoCelular()
        }

        findViewById<View>(R.id.btnVoltar).setOnClickListener {
            finish()
        }
    }

    /** Remonta a lista inteira: apaga o que estava e infla uma linha por contato. */
    private fun montarLista() {
        listaContatos.removeAllViews()

        if (contatos.isEmpty()) {
            txtListaVazia.visibility = View.VISIBLE
            return
        }

        txtListaVazia.visibility = View.GONE

        for (posicao in contatos.indices) {
            val contato = contatos[posicao]
            val linha = layoutInflater.inflate(R.layout.item_contato, listaContatos, false)

            val txtInicial = linha.findViewById<TextView>(R.id.txtInicial)
            val txtNome = linha.findViewById<TextView>(R.id.txtNome)
            val txtTelefone = linha.findViewById<TextView>(R.id.txtTelefone)
            val btnExcluir = linha.findViewById<ImageView>(R.id.btnExcluir)

            txtNome.text = contato.name
            txtTelefone.text = contato.phone

            if (contato.name.isNotEmpty()) {
                txtInicial.text = contato.name.substring(0, 1).uppercase()
            } else {
                txtInicial.text = "?"
            }

            btnExcluir.visibility = View.VISIBLE
            btnExcluir.setOnClickListener {
                excluirContato(contato)
            }

            listaContatos.addView(linha)
        }
    }

    private fun adicionarContato() {
        val nome = campoNome.text.toString().trim()
        val telefone = campoTelefone.text.toString().trim()

        if (nome.isEmpty()) {
            Toast.makeText(this, getString(R.string.contacts_error_name), Toast.LENGTH_SHORT).show()
            campoNome.requestFocus()
            return
        }

        if (telefone.isEmpty()) {
            Toast.makeText(this, getString(R.string.contacts_error_phone), Toast.LENGTH_SHORT).show()
            campoTelefone.requestFocus()
            return
        }

        contatos.add(EmergencyContact(nome, telefone, STATUS_ENVIADO))
        salvarContatos(this, contatos)
        montarLista()

        campoNome.text.clear()
        campoTelefone.text.clear()
        campoNome.requestFocus()

        Toast.makeText(this, getString(R.string.contacts_added, nome), Toast.LENGTH_SHORT).show()
    }

    private fun excluirContato(contato: EmergencyContact) {
        contatos.remove(contato)
        salvarContatos(this, contatos)
        montarLista()

        Toast.makeText(
            this,
            getString(R.string.contacts_removed, contato.name),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun abrirAgendaDoCelular() {
        val escolha = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        try {
            escolherDaAgenda.launch(escolha)
        } catch (erro: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.contacts_import_error), Toast.LENGTH_SHORT).show()
        }
    }

    private fun preencherComContatoEscolhido(endereco: Uri?) {
        if (endereco == null) {
            return
        }

        val colunas = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        // Alguns aparelhos devolvem um endereco sem as colunas de telefone e a
        // consulta falha; nesse caso o app so avisa.
        val cursor = try {
            contentResolver.query(endereco, colunas, null, null, null)
        } catch (erro: Exception) {
            null
        }

        if (cursor == null) {
            Toast.makeText(this, getString(R.string.contacts_import_error), Toast.LENGTH_SHORT).show()
            return
        }

        if (cursor.moveToFirst()) {
            campoNome.setText(cursor.getString(0) ?: "")
            campoTelefone.setText(cursor.getString(1) ?: "")
            Toast.makeText(this, getString(R.string.contacts_import_ok), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, getString(R.string.contacts_import_error), Toast.LENGTH_SHORT).show()
        }

        cursor.close()
    }
}
