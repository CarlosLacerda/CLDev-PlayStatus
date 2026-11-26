package com.cldev.presence


import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideogameAsset
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.discord.socialsdk.DiscordSocialSdkInit


class MainActivity : ComponentActivity() {



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o SDK Social do Discord com a Activity Android
        DiscordSocialSdkInit.setEngineActivity(this)

        // Carrega nossa biblioteca nativa depois do SDK
        System.loadLibrary("cldev_presence")

        val gameCache = GameCache(applicationContext)

        setContent {
            MaterialTheme {
                PresenceScreen(gameCache)
            }
        }
    }

    override fun onDestroy() {

        super.onDestroy()
    }


}


@Composable
private fun PresenceScreen(cache: GameCache) {

    var host by remember { mutableStateOf("192.168.15.5") }
    var showLibrary by remember {
        mutableStateOf(false)
    }

    var libraryGames by remember {
        mutableStateOf(cache.getAll())
    }
    var status by remember { mutableStateOf("Monitoramento parado") }
    var gameTitle by remember { mutableStateOf<String?>(null) }
    var titleId by remember { mutableStateOf<String?>(null) }
    var httpCode by remember { mutableStateOf<Int?>(null) }
    var monitoring by remember { mutableStateOf(false) }
    var lastUpdate by remember {
        mutableStateOf("Nenhuma consulta realizada")
    }

    // Informações obtidas na TheGamesDB
    var gameMetadata by remember {
        mutableStateOf<GameMetadata?>(null)
    }

    var metadataStatus by remember {
        mutableStateOf("Aguardando identificação do jogo")
    }

    var imageStatus by remember {
        mutableStateOf("Imagem aguardando carregamento")
    }

    // Evita pesquisar novamente o mesmo Title ID
    var metadataTitleId by remember {
        mutableStateOf<String?>(null)
    }

    val context = LocalContext.current

    fun startMonitoring() {
        if (monitoring) return

        status = "Iniciando monitoramento..."

        val intent = Intent(context, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_START
            putExtra(MonitoringService.EXTRA_HOST, host)
        }

        ContextCompat.startForegroundService(context, intent)

        monitoring = true
    }

    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) {
            startMonitoring()
        }

    fun requestNotificationPermissionAndStart() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            startMonitoring()
        }
    }

    fun stopMonitoring() {
        val intent = Intent(context, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP
        }

        context.startService(intent)

        monitoring = false
        status = "Monitoramento parado"
    }

        DisposableEffect(context) {

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?
            ) {
                intent ?: return

                monitoring = intent.getBooleanExtra(
                    MonitoringService.EXTRA_MONITORING,
                    monitoring
                )

                if (intent.hasExtra(MonitoringService.EXTRA_STATUS)) {
                    status = intent.getStringExtra(
                        MonitoringService.EXTRA_STATUS
                    ) ?: status
                }

                if (intent.hasExtra(MonitoringService.EXTRA_GAME_TITLE)) {
                    gameTitle = intent.getStringExtra(
                        MonitoringService.EXTRA_GAME_TITLE
                    )
                }

                if (intent.hasExtra(MonitoringService.EXTRA_TITLE_ID)) {
                    titleId = intent.getStringExtra(
                        MonitoringService.EXTRA_TITLE_ID
                    )
                }

                if (intent.hasExtra(MonitoringService.EXTRA_HTTP_CODE)) {
                    val code = intent.getIntExtra(
                        MonitoringService.EXTRA_HTTP_CODE,
                        -1
                    )

                    httpCode = if (code >= 0) code else null
                }

                if (intent.hasExtra(MonitoringService.EXTRA_LAST_UPDATE)) {
                    lastUpdate = intent.getStringExtra(
                        MonitoringService.EXTRA_LAST_UPDATE
                    ) ?: lastUpdate
                }

                if (intent.hasExtra(MonitoringService.EXTRA_METADATA_STATUS)) {
                    metadataStatus = intent.getStringExtra(
                        MonitoringService.EXTRA_METADATA_STATUS
                    ) ?: metadataStatus
                }

                val metadataId = intent.getIntExtra(
                    MonitoringService.EXTRA_METADATA_ID,
                    -1
                )

                val metadataTitle = intent.getStringExtra(
                    MonitoringService.EXTRA_METADATA_TITLE
                )

                if (metadataId > 0 && !metadataTitle.isNullOrBlank()) {
                    gameMetadata = GameMetadata(
                        id = metadataId,
                        title = metadataTitle,
                        releaseDate = intent.getStringExtra(
                            MonitoringService.EXTRA_RELEASE_DATE
                        ),
                        coverUrl = intent.getStringExtra(
                            MonitoringService.EXTRA_COVER_URL
                        )
                    )

                    imageStatus = "Imagem aguardando carregamento"

                    libraryGames = cache.getAll()
                }

                if (gameTitle == null) {
                    gameMetadata = null
                    metadataTitleId = null
                    metadataStatus = "Aguardando identificação do jogo"
                } else {
                    metadataTitleId = titleId
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(MonitoringService.ACTION_UPDATE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    if (showLibrary) {
        LibraryScreen(
            games = libraryGames,
            onBack = {
                libraryGames = cache.getAll()
                showLibrary = false
            }
        )
        return
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {

            Text(
                "CLDev",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                "Discord Presence",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Text("Fase 2 • Monitoramento automático")

            OutlinedButton(
                onClick = {
                    libraryGames = cache.getAll()
                    showLibrary = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.VideogameAsset,
                    contentDescription = null
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text("Minha Biblioteca (${libraryGames.size})")
            }

            // Conexão com o PS3
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "PlayStation 3",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = host,
                        onValueChange = { host = it },
                        label = { Text("IP do PS3") },
                        singleLine = true,
                        enabled = !monitoring,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        "Porta HTTP: 80",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Button(
                        onClick = {
                            if (monitoring) {
                                stopMonitoring()
                            } else {
                                requestNotificationPermissionAndStart()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = if (monitoring) {
                            ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        } else {
                            ButtonDefaults.buttonColors()
                        }
                    ) {
                        Text(
                            if (monitoring) {
                                "Parar monitoramento"
                            } else {
                                "Iniciar monitoramento"
                            }
                        )
                    }

                    Text(
                        if (monitoring) {
                            "Consultando o PS3 a cada 10 segundos."
                        } else {
                            "Monitoramento aguardando início."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Status da conexão
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Status da conexão",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(status, fontWeight = FontWeight.SemiBold)

                    Text(
                        "HTTP: ${httpCode?.toString() ?: "Aguardando"}",
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "Última consulta: $lastUpdate",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Jogo detectado pelo WebMAN
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Jogo detectado",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        gameTitle ?: "Nenhum jogo identificado",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    HorizontalDivider()

                    Text(
                        "Title ID",
                        style = MaterialTheme.typography.labelMedium
                    )

                    Text(
                        titleId ?: "Aguardando identificação",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Informações da TheGamesDB
            Card {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Informações do jogo",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        metadataStatus,
                        style = MaterialTheme.typography.bodySmall
                    )

                    gameMetadata?.let { game ->

                        if (!game.coverUrl.isNullOrBlank()) {

                            AsyncImage(
                                model = game.coverUrl,
                                contentDescription = "Capa de ${game.title}",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentScale = ContentScale.Fit,
                                onLoading = {
                                    imageStatus = "Carregando imagem..."
                                },
                                onSuccess = {
                                    imageStatus = "Imagem carregada com sucesso!"
                                },
                                onError = { state ->
                                    imageStatus = "Erro ao carregar: ${
                                        state.result.throwable.message ?: "Erro desconhecido"
                                    }"
                                }
                            )

                            Text(
                                imageStatus,
                                style = MaterialTheme.typography.bodySmall
                            )

                            Text(
                                "URL: ${game.coverUrl}",
                                style = MaterialTheme.typography.bodySmall
                            )

                        } else {
                            Text("A API não retornou o endereço da capa.")
                        }

                        Text(
                            game.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text("ID TheGamesDB: ${game.id}")

                        Text(
                            "Lançamento: ${game.releaseDate ?: "Não informado"}"
                        )

                        Text(
                            "Plataforma: PlayStation 3"
                        )
                    }
                }
            }

            Text(
                "CLDev Discord Presence • Monitoramento em segundo plano " +
                        "com serviço em primeiro plano. Esta versão somente " +
                        "consulta informações do PS3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryScreen(
    games: List<CachedGame>,
    onBack: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    "Minha Biblioteca",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                TextButton(onClick = onBack) {
                    Text("Voltar")
                }
            }

            Text(
                "${games.size} jogos cadastrados",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider()

            if (games.isEmpty()) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideogameAsset,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text("Sua biblioteca está vazia.")

                        Text(
                            "Os jogos serão adicionados automaticamente conforme forem detectados.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

            } else {

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = games,
                        key = { it.titleId }
                    ) { cachedGame ->

                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {

                                if (!cachedGame.metadata.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = cachedGame.metadata.coverUrl,
                                        contentDescription = "Capa de ${cachedGame.metadata.title}",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(190.dp),
                                        contentAlignment = androidx.compose.ui.Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VideogameAsset,
                                            contentDescription = null,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }
                                }

                                Text(
                                    cachedGame.metadata.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )

                                Text(
                                    "Title ID: ${cachedGame.titleId}",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    "Lançamento: ${cachedGame.metadata.releaseDate ?: "Não informado"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}