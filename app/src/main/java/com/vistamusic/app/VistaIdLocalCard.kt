package com.vistamusic.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Optional LOCAL link to the installed Vista ID app.
 * Never stores the Vista ID password. Data and LocalTaste remain in their
 * original local storage and are not uploaded; cloud sync requires a backend.
 */
@Composable
fun VistaIdLocalCard() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("vista_id_link", Context.MODE_PRIVATE) }
    var linkedCode by remember { mutableStateOf(prefs.getString("profile_id", null)) }
    var linkedName by remember { mutableStateOf(prefs.getString("profile_name", null)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val id = result.data?.getStringExtra("com.vista.id.profile_id")
            val name = result.data?.getStringExtra("com.vista.id.profile_name")
            val mode = result.data?.getStringExtra("com.vista.id.mode")
            if (id != null && id.matches(Regex("V[A-Z2-9]{12}")) && mode == "local") {
                prefs.edit().putString("profile_id", id)
                    .putString("profile_name", name?.take(48) ?: "")
                    .apply()
                linkedCode = id
                linkedName = name
            } else {
                Toast.makeText(context, "Resposta Vista ID inválida.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Vista ID · opcional", color = VmWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (linkedCode == null) {
            Text("Continua sem conta, ou associa um Vista ID local. As tuas playlists, favoritos e recomendações mantêm-se neste telemóvel.", color = VmMuted, fontSize = 12.sp)
            Button(onClick = {
                try {
                    launcher.launch(Intent("com.vista.id.action.LINK_LOCAL_PROFILE").setPackage("com.vista.id"))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, "Instala primeiro o Vista ID v0.2 ou superior.", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(context, "Vista ID indisponível: ${ex.message}", Toast.LENGTH_LONG).show()
                }
            }) { Text("Ligar Vista ID local") }
        } else {
            Text("Perfil associado neste dispositivo: ${linkedName.orEmpty()} · ${linkedCode}", color = VmWhite, fontSize = 12.sp)
            Text("Modo local — sem sincronização cloud. Histórico e playlists anteriores são preservados.", color = VmMuted, fontSize = 12.sp)
            OutlinedButton(onClick = {
                prefs.edit().remove("profile_id").remove("profile_name").apply()
                linkedCode = null
                linkedName = null
            }) { Text("Desligar Vista ID (sem apagar dados)") }
        }
        HorizontalDivider()
    }
}
