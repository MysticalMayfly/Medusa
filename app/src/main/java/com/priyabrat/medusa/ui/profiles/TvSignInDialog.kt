package com.priyabrat.medusa.ui.profiles

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.priyabrat.medusa.data.library.TvGoogleSignIn
import com.priyabrat.medusa.ui.components.Pill
import com.priyabrat.medusa.ui.theme.MedusaColors
import com.priyabrat.medusa.ui.theme.MedusaType

@Composable
fun TvSignInDialog(challenge: TvGoogleSignIn.Challenge?, onCancel: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Dialog(onDismissRequest = onCancel) {
        Column(Modifier.width(600.dp).background(MedusaColors.Bg, RoundedCornerShape(20.dp)).padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Sign in to Medusa", style = MedusaType.PageTitle)
            if (challenge == null) Text("Preparing your sign-in…", style = MedusaType.Body)
            else {
                val qr = remember(challenge.url) {
                    val matrix = QRCodeWriter().encode(challenge.url, BarcodeFormat.QR_CODE, 320, 320)
                    val pixels = IntArray(320 * 320) { i -> if (matrix[i % 320, i / 320]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                    Bitmap.createBitmap(pixels, 320, 320, Bitmap.Config.ARGB_8888).asImageBitmap()
                }
                Text("Scan with your phone and sign in with Google.", style = MedusaType.Body, textAlign = TextAlign.Center)
                Image(qr, contentDescription = "QR code for Google sign-in", modifier = Modifier.size(200.dp).background(Color.White))
                Text("Keep this screen open while you sign in.", style = MedusaType.Body, textAlign = TextAlign.Center)
            }
            Pill("Cancel", onCancel, Modifier.focusRequester(cancelFocus))
        }
    }
}
