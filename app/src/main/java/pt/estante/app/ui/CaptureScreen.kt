package pt.estante.app.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import pt.estante.app.appContainer
import pt.estante.app.data.PhotoEntity
import pt.estante.core.PageType
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(bookId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val repository = container.repository
    val lifecycleOwner = LocalLifecycleOwner.current

    val book by remember(bookId) { repository.observeBook(bookId) }.collectAsStateWithLifecycle(initialValue = null)
    val photos by remember(bookId) { repository.observePhotos(bookId) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedType by rememberSaveable { mutableStateOf(PageType.COVER) }
    var startChosen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(bookId) {
        if (!startChosen) {
            val taken = repository.photosFor(bookId).map { it.pageType }.toSet()
            selectedType = PageType.guidedOrder.firstOrNull { it.name !in taken } ?: PageType.INTERIOR
            startChosen = true
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var photoToDelete by remember { mutableStateOf<PhotoEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            message = null
        }
    }

    DisposableEffect(hasCameraPermission, lifecycleOwner) {
        if (!hasCameraPermission) return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener(
            {
                val cameraProvider = providerFuture.get()
                provider = cameraProvider
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                try {
                    cameraProvider.unbindAll()
                    camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                    )
                } catch (e: Exception) {
                    message = "Camera unavailable: ${e.message}"
                }
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            provider?.unbindAll()
            camera = null
            torchOn = false
        }
    }

    val takePhoto: () -> Unit = {
        if (!capturing) {
            capturing = true
            val pageType = selectedType
            val photoId = UUID.randomUUID().toString()
            val file = repository.newPhotoFile(bookId, photoId)
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        container.applicationScope.launch { repository.addPhoto(bookId, photoId, pageType, file) }
                        val next = PageType.guidedOrder.indexOf(pageType) + 1
                        if (next in 1 until PageType.guidedOrder.size) selectedType = PageType.guidedOrder[next]
                        capturing = false
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        message = "Couldn't take the photo: ${exception.message}"
                    }
                },
            )
        }
    }

    val finish: () -> Unit = {
        container.applicationScope.launch { repository.queueIfPending(bookId) }
        onClose()
    }
    BackHandler(onBack = finish)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(book?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = finish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (camera?.cameraInfo?.hasFlashUnit() == true) {
                        TextButton(
                            onClick = {
                                torchOn = !torchOn
                                camera?.cameraControl?.enableTorch(torchOn)
                            },
                        ) {
                            Text(if (torchOn) "Light off" else "Light on")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(photoCountText(photos.size).replaceFirstChar { it.uppercase() }, modifier = Modifier.weight(1f))
                    Button(onClick = finish, enabled = photos.isNotEmpty()) { Text("Done") }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
            ) {
                if (hasCameraPermission) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Text(
                        "Next: ${selectedType.label}",
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(12.dp)
                            .background(Color(0x99000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                    ShutterButton(
                        enabled = !capturing,
                        onClick = takePhoto,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp),
                    )
                } else {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Estante needs the camera to photograph books.", color = Color.White)
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                        Text(
                            "If nothing happens, allow the camera in Android Settings > Apps > Estante > Permissions.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            PageTypeChips(
                selected = selectedType,
                counts = photos.groupingBy { it.pageType }.eachCount(),
                onSelect = { selectedType = it },
            )
            PhotoStrip(photos = photos, onPhotoClick = { photoToDelete = it })
        }
    }

    photoToDelete?.let { photo ->
        PhotoDialog(
            photo = photo,
            onDelete = {
                container.applicationScope.launch { repository.deletePhoto(photo) }
                photoToDelete = null
            },
            onDismiss = { photoToDelete = null },
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.LightGray)
            .border(4.dp, Color(0x55000000), CircleShape)
            .semantics {
                contentDescription = "Take photo"
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageTypeChips(selected: PageType, counts: Map<String, Int>, onSelect: (PageType) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 8.dp),
    ) {
        items(PageType.entries) { type ->
            val count = counts[type.name] ?: 0
            FilterChip(
                selected = type == selected,
                onClick = { onSelect(type) },
                label = { Text(if (count > 0) "${type.label} ($count)" else type.label) },
            )
        }
    }
}

@Composable
private fun PhotoStrip(photos: List<PhotoEntity>, onPhotoClick: (PhotoEntity) -> Unit) {
    if (photos.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.height(104.dp).padding(bottom = 8.dp),
    ) {
        items(photos, key = { it.id }) { photo ->
            val type = PageType.valueOf(photo.pageType)
            Box(
                Modifier
                    .size(width = 72.dp, height = 96.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onPhotoClick(photo) },
            ) {
                AsyncImage(
                    model = File(photo.localPath),
                    contentDescription = type.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    if (photo.uploaded) "${type.code} ✓" else type.code,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color(0x99000000))
                        .padding(4.dp),
                )
            }
        }
    }
}

@Composable
private fun PhotoDialog(photo: PhotoEntity, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val label = PageType.valueOf(photo.pageType).label.lowercase()
    if (photo.uploaded) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Already in your Sheet") },
            text = { Text("This $label photo is uploaded, so it can't be deleted from the app yet.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Delete this $label photo?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
    }
}
