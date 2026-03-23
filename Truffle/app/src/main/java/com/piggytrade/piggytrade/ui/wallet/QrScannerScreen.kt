package com.piggytrade.piggytrade.ui.wallet

import com.piggytrade.piggytrade.ui.theme.*
import com.piggytrade.piggytrade.ui.common.*

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * Full-screen QR code scanner using CameraX + ML Kit.
 * Returns scanned content via callbacks for addresses and ErgoPay URLs.
 */
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onAddressScanned: (String) -> Unit,
    onErgoPayScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCameraPermission) {
            // Camera preview
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val barcodeScanner = BarcodeScanning.getClient()
                        val imageAnalyzer = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx)
                                ) { imageProxy ->
                                    if (scannedOnce) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    @androidx.annotation.OptIn(ExperimentalGetImage::class)
                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        val inputImage = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(inputImage)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    if (barcode.valueType == Barcode.TYPE_TEXT ||
                                                        barcode.valueType == Barcode.TYPE_URL
                                                    ) {
                                                        val raw = barcode.rawValue ?: continue
                                                        if (!scannedOnce) {
                                                            scannedOnce = true
                                                            handleScannedContent(
                                                                raw,
                                                                onAddressScanned,
                                                                onErgoPayScanned,
                                                                onBack
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("QrScanner", "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Overlay with viewfinder
            Box(modifier = Modifier.fillMaxSize()) {
                // Semi-transparent overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                // Clear viewfinder area
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, ColorAccent, RoundedCornerShape(16.dp))
                            .background(Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Scan QR Code",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Ergo address or ErgoPay QR",
                        color = ColorTextDim,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }

                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "✕",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            // No camera permission
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Camera Permission Required",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Please grant camera permission to scan QR codes.",
                    color = ColorTextDim,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = ColorAccent)
                ) {
                    Text("Grant Permission", color = ColorBg)
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onBack) {
                    Text("Go Back", color = ColorTextDim)
                }
            }
        }
    }
}

/**
 * Categorize the scanned QR content and route to the right callback.
 */
private fun handleScannedContent(
    content: String,
    onAddressScanned: (String) -> Unit,
    onErgoPayScanned: (String) -> Unit,
    onBack: () -> Unit
) {
    val trimmed = content.trim()
    when {
        // ErgoPay URL (reduced TX)
        trimmed.startsWith("ergopay:", ignoreCase = true) -> {
            onErgoPayScanned(trimmed)
        }
        // Ergo mainnet address (starts with 9, no fixed max length)
        trimmed.startsWith("9") && trimmed.length >= 40 -> {
            onAddressScanned(trimmed)
        }
        // Ergo testnet/P2S address (starts with 3, no fixed max length)
        trimmed.startsWith("3") && trimmed.length >= 40 -> {
            onAddressScanned(trimmed)
        }
        // URL that might contain ErgoPay
        (trimmed.startsWith("http://") || trimmed.startsWith("https://")) &&
                trimmed.contains("ergopay", ignoreCase = true) -> {
            onErgoPayScanned(trimmed)
        }
        // Fallback: treat as address if it looks plausible
        trimmed.length >= 40 && trimmed.all { it.isLetterOrDigit() } -> {
            onAddressScanned(trimmed)
        }
        else -> {
            // Unrecognized content, go back
            onBack()
        }
    }
}
