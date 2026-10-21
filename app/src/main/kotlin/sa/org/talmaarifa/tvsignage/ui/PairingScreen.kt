package sa.org.talmaarifa.tvsignage.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import sa.org.talmaarifa.tvsignage.R
import sa.org.talmaarifa.tvsignage.core.PairingState
import sa.org.talmaarifa.tvsignage.pairing.QrCode
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
fun PairingScreen(state: PairingState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.pairing_title),
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )

        when (state) {
            is PairingState.AwaitingApproval -> {
                Spacer(24)
                Text(text = stringResource(R.string.pairing_instructions), color = Color.LightGray, fontSize = 20.sp)
                Spacer(24)
                val qr = remember(state.code) { QrCode.encode(state.code) }
                Image(bitmap = qr.asImageBitmap(), contentDescription = state.code, modifier = Modifier.size(260.dp))
                Spacer(24)
                Text(text = formatCode(state.code), color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
                Spacer(16)
                Text(text = stringResource(R.string.pairing_waiting), color = Color.Gray, fontSize = 18.sp)
            }
            is PairingState.Rejected -> {
                Spacer(24)
                Text(text = stringResource(R.string.pairing_rejected), color = MaterialTheme.colorScheme.error, fontSize = 22.sp)
            }
            is PairingState.Expired -> {
                Spacer(24)
                Text(text = stringResource(R.string.pairing_expired), color = Color.Yellow, fontSize = 22.sp)
            }
            is PairingState.Failed -> {
                Spacer(24)
                Text(text = String.format(stringResource(R.string.pairing_failed), state.message), color = MaterialTheme.colorScheme.error, fontSize = 20.sp)
            }
            is PairingState.Approved -> Unit // MainActivity switches to PlaybackScreen immediately
            is PairingState.Idle -> {
                Spacer(24)
                Text(text = stringResource(R.string.pairing_requesting), color = Color.Gray, fontSize = 18.sp)
            }
        }
    }
}

private fun formatCode(code: String): String = code.chunked(3).joinToString(" - ")

@Composable
private fun Spacer(heightDp: Int) {
    androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(heightDp.dp))
}
