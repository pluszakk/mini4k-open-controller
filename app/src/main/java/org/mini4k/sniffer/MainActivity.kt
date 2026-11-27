package org.mini4k.sniffer

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val sniffer: Sniffer get() = (application as SnifferApp).sniffer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SnifferScreen(
                        sniffer = sniffer,
                        onShare = { share(it) },
                    )
                }
            }
        }
    }

    private fun share(snapshot: Sniffer.Snapshot) {
        val file = snapshot.file ?: return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Wyślij nagranie"))
    }
}

@Composable
private fun SnifferScreen(sniffer: Sniffer, onShare: (Sniffer.Snapshot) -> Unit) {
    var snapshot by remember { mutableStateOf(sniffer.snapshot()) }
    var devices by remember { mutableStateOf(sniffer.describeAll()) }

    // Poll the capture state twice a second. Simple and cheap for a test tool.
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = sniffer.snapshot()
            if (!snapshot.running) devices = sniffer.describeAll()
            delay(500)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Mini4K USB Sniffer", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            "Tylko nasłuch. Aplikacja nic nie wysyła do kontrolera ani do drona.",
            fontSize = 13.sp,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(snapshot.status, fontWeight = FontWeight.SemiBold)
                if (snapshot.running) Text("Czas nagrania: ${snapshot.seconds} s")
                Text(verdict(snapshot), modifier = Modifier.padding(top = 6.dp))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (snapshot.running) {
                Button(onClick = { sniffer.stop() }) { Text("Stop") }
            } else {
                Button(onClick = { sniffer.start() }) { Text("Start") }
            }
            OutlinedButton(
                onClick = { onShare(snapshot) },
                enabled = !snapshot.running && snapshot.file != null,
            ) { Text("Wyślij nagranie") }
        }

        snapshot.sources.forEach { s ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(s.name, fontWeight = FontWeight.SemiBold)
                    Text("Odebrane bajty: ${s.bytes}")
                    Text("Pakiety DUML (poprawne CRC): ${s.packets}")
                    Text("Pakiety z flagą szyfrowania: ${s.encrypted}")
                    Text("Udział DUML w danych: ${"%.1f".format(s.dumlPercent)}%")
                    s.topCommands.forEach { (cmd, count) ->
                        Text("${count}x $cmd", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                    }
                }
            }
        }

        if (snapshot.recent.isNotEmpty()) {
            Text("Ostatnie pakiety", fontWeight = FontWeight.SemiBold)
            snapshot.recent.take(30).forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        }

        Text("Wykryte na USB", fontWeight = FontWeight.SemiBold)
        Text(devices, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

/** A plain-language reading of the numbers, so the result is clear without decoding the file. */
private fun verdict(s: Sniffer.Snapshot): String {
    val bytes = s.sources.sumOf { it.bytes }
    val packets = s.sources.sumOf { it.packets }
    val encrypted = s.sources.sumOf { it.encrypted }
    val duml = if (s.sources.isEmpty()) 0.0 else s.sources.maxOf { it.dumlPercent }
    return when {
        s.sources.isEmpty() -> "Wynik: jeszcze nic nie nagrano."
        bytes == 0L -> "Wynik: kontroler na razie milczy (0 bajtów)."
        packets > 0 && encrypted == 0L && duml > 50 -> "Wynik: czytelny DUML. Dobra wiadomość!"
        packets > 0 && encrypted > 0 -> "Wynik: DUML jest, ale część pakietów ma flagę szyfrowania."
        packets > 0 -> "Wynik: są pakiety DUML, ale też dużo innych danych."
        bytes > 10_000 -> "Wynik: dane nie wyglądają na DUML (możliwe szyfrowanie lub inny protokół)."
        else -> "Wynik: za mało danych, nagrywaj dalej."
    }
}
