package org.aulama.iptv.mobile.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.aulama.iptv.mobile.MobileMainViewModel
import org.aulama.iptv.mobile.MobileEpgState
import org.aulama.iptv.mobile.MobileUiState
import org.aulama.iptv.mobile.PairingApprovalState
import org.aulama.iptv.mobile.R
import org.aulama.iptv.mobile.RegionChannels
import org.aulama.iptv.mobile.SelectedChannel
import org.aulama.iptv.mobile.data.auth.AulamaAccountState
import org.aulama.iptv.mobile.data.playback.PlaybackCandidate
import org.aulama.iptv.mobile.ui.components.AuroraBackdrop
import org.aulama.iptv.mobile.ui.components.AuroraScaffold
import org.aulama.iptv.mobile.ui.components.GlassPane
import org.aulama.iptv.mobile.ui.components.glassSurfaceColor
import org.aulama.iptv.mobile.ui.account.AccountScreen
import org.aulama.iptv.mobile.ui.onboarding.WelcomeScreen
import org.aulama.iptv.mobile.ui.pairing.PairingCode
import org.aulama.iptv.mobile.ui.pairing.PairingConfirmationScreen
import org.aulama.iptv.mobile.ui.pairing.QrScannerScreen
import org.aulama.iptv.mobile.ui.player.DirectPlayerState
import org.aulama.iptv.mobile.ui.player.DirectVideoPlayer
import org.aulama.iptv.mobile.ui.player.PlaybackStatus
import org.aulama.iptv.mobile.ui.player.rememberDirectPlayerState
import org.aulama.iptv.mobile.ui.settings.SettingsScreen
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelRoute
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.recentProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.progress
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme.Companion.remainingMinutes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hongKongZone = ZoneId.of("Asia/Hong_Kong")
private val programmeTimeFormatter = DateTimeFormatter
    .ofPattern("MM/dd HH:mm", Locale.TRADITIONAL_CHINESE)
    .withZone(hongKongZone)

private data class BrowserChannel(
    val region: String,
    val channel: Channel,
)

private enum class MobileDestination {
    WELCOME,
    HOME,
    SETTINGS,
    ACCOUNT,
    SCANNER,
    PAIRING_CONFIRMATION,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AulamaTvApp(viewModel: MobileMainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedRegion by viewModel.selectedRegion.collectAsState()
    val selectedChannel by viewModel.selectedChannel.collectAsState()
    val selectedRouteIndex by viewModel.selectedRouteIndex.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val favoritesOnly by viewModel.favoritesOnly.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val darkTheme by viewModel.darkTheme.collectAsState()
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val accountState by viewModel.accountState.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val pairingState by viewModel.pairingState.collectAsState()
    val playbackCandidates by viewModel.playbackCandidates.collectAsState()
    val selectedCandidateIndex by viewModel.selectedCandidateIndex.collectAsState()
    val epgList by viewModel.epgList.collectAsState()
    val epgState by viewModel.epgState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var minimumSplashElapsed by remember { mutableStateOf(false) }
    var initialSplashFinished by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var showRouteSheet by rememberSaveable { mutableStateOf(false) }
    var destinationName by rememberSaveable {
        mutableStateOf(
            if (viewModel.onboardingCompleted.value) {
                MobileDestination.HOME.name
            } else {
                MobileDestination.WELCOME.name
            }
        )
    }
    var accountReturnName by rememberSaveable {
        mutableStateOf(MobileDestination.WELCOME.name)
    }
    var pendingPairingCode by rememberSaveable { mutableStateOf<String?>(null) }
    var epgClockMs by remember { mutableStateOf(System.currentTimeMillis()) }
    val destination = remember(destinationName) {
        runCatching { MobileDestination.valueOf(destinationName) }
            .getOrDefault(MobileDestination.HOME)
    }

    fun navigate(destination: MobileDestination) {
        destinationName = destination.name
    }

    LaunchedEffect(Unit) {
        delay(900)
        minimumSplashElapsed = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            epgClockMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(uiState, minimumSplashElapsed) {
        if (uiState !is MobileUiState.Loading && minimumSplashElapsed) {
            initialSplashFinished = true
        }
    }

    LaunchedEffect(onboardingCompleted, destination) {
        if (onboardingCompleted && destination == MobileDestination.WELCOME) {
            navigate(MobileDestination.HOME)
        }
    }

    LaunchedEffect(accountState) {
        if (accountState is AulamaAccountState.SignedIn && !onboardingCompleted) {
            viewModel.completeOnboarding()
            navigate(MobileDestination.HOME)
        } else if (
            accountState is AulamaAccountState.SignedIn &&
            destination == MobileDestination.ACCOUNT &&
            accountReturnName == MobileDestination.PAIRING_CONFIRMATION.name
        ) {
            navigate(MobileDestination.PAIRING_CONFIRMATION)
        }
    }

    if (!initialSplashFinished) {
        AulamaLoadingScreen()
        return
    }

    BackHandler(
        enabled = destination != MobileDestination.HOME &&
            destination != MobileDestination.WELCOME,
    ) {
        when (destination) {
            MobileDestination.SETTINGS -> navigate(MobileDestination.HOME)
            MobileDestination.ACCOUNT -> {
                navigate(
                    runCatching { MobileDestination.valueOf(accountReturnName) }
                        .getOrDefault(MobileDestination.SETTINGS)
                )
            }
            MobileDestination.SCANNER -> navigate(MobileDestination.SETTINGS)
            MobileDestination.PAIRING_CONFIRMATION -> navigate(MobileDestination.SCANNER)
            else -> Unit
        }
    }

    when (destination) {
        MobileDestination.WELCOME -> {
            WelcomeScreen(
                onContinueAsGuest = {
                    viewModel.completeOnboarding()
                    navigate(MobileDestination.HOME)
                },
                onSignIn = {
                    accountReturnName = MobileDestination.WELCOME.name
                    navigate(MobileDestination.ACCOUNT)
                },
            )
            return
        }

        MobileDestination.SETTINGS -> {
            SettingsScreen(
                darkTheme = darkTheme,
                accountState = accountState,
                syncState = syncState,
                onToggleTheme = viewModel::toggleTheme,
                onOpenAccount = {
                    accountReturnName = MobileDestination.SETTINGS.name
                    navigate(MobileDestination.ACCOUNT)
                },
                onOpenScanner = { navigate(MobileDestination.SCANNER) },
                onBack = { navigate(MobileDestination.HOME) },
            )
            return
        }

        MobileDestination.ACCOUNT -> {
            val returnDestination = runCatching {
                MobileDestination.valueOf(accountReturnName)
            }.getOrDefault(MobileDestination.SETTINGS)
            AccountScreen(
                accountState = accountState,
                showGuestContinue = !onboardingCompleted,
                onGoogleSignIn = viewModel::signInWithGoogle,
                onPasskeySignIn = viewModel::signInWithPasskey,
                onLogout = viewModel::logout,
                onSyncNow = viewModel::syncNow,
                onBack = { navigate(returnDestination) },
                onContinueAsGuest = {
                    viewModel.completeOnboarding()
                    navigate(MobileDestination.HOME)
                },
            )
            return
        }

        MobileDestination.SCANNER -> {
            QrScannerScreen(
                onPairingCodeDetected = {
                    viewModel.resetPairingState()
                    pendingPairingCode = it.value
                    navigate(MobileDestination.PAIRING_CONFIRMATION)
                },
                onBack = { navigate(MobileDestination.SETTINGS) },
            )
            return
        }

        MobileDestination.PAIRING_CONFIRMATION -> {
            val pairingCode = pendingPairingCode?.let(::PairingCode)
            if (pairingCode == null) {
                LaunchedEffect(Unit) { navigate(MobileDestination.SCANNER) }
            } else {
                PairingConfirmationScreen(
                    pairingCode = pairingCode,
                    accountState = accountState,
                    approvalState = pairingState,
                    onApprove = { viewModel.approvePairing(pairingCode.value) },
                    onSignIn = {
                        accountReturnName = MobileDestination.PAIRING_CONFIRMATION.name
                        navigate(MobileDestination.ACCOUNT)
                    },
                    onScanAgain = { navigate(MobileDestination.SCANNER) },
                    onDone = {
                        viewModel.resetPairingState()
                        pendingPairingCode = null
                        navigate(MobileDestination.SETTINGS)
                    },
                )
            }
            return
        }

        MobileDestination.HOME -> Unit
    }

    val currentCandidate = playbackCandidates.getOrNull(selectedCandidateIndex)
    val playerState = rememberDirectPlayerState(
        onPlaybackFailure = {
            if (viewModel.tryNextRoute()) {
                scope.launch { snackbarHostState.showSnackbar("目前線路不穩，已切換後備線路") }
            } else {
                scope.launch { snackbarHostState.showSnackbar("目前頻道所有線路暫時未能播放") }
            }
        },
        onHealthSample = viewModel::recordPlaybackSample,
    )

    FullscreenSystemUi(enabled = fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }

    if (fullscreen) {
        DirectVideoPlayer(
            state = playerState,
            candidate = currentCandidate,
            channelName = selectedChannel?.channel?.name.orEmpty(),
            fullscreen = true,
            onToggleFullscreen = { fullscreen = false },
            modifier = Modifier.fillMaxSize(),
        )
    } else {
        AuroraScaffold(
            topBar = {
                AulamaTopBar(
                    darkTheme = darkTheme,
                    loading = uiState is MobileUiState.Loading || epgState.loading,
                    onToggleTheme = viewModel::toggleTheme,
                    onRefresh = viewModel::refresh,
                    onOpenSettings = { navigate(MobileDestination.SETTINGS) },
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
        ) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .imePadding(),
            ) {
                if (uiState is MobileUiState.Loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val useWideLayout = maxWidth >= 840.dp ||
                        (maxWidth >= 600.dp && maxWidth > maxHeight)
                    if (useWideLayout) {
                        WideLayout(
                            uiState = uiState,
                            playerState = playerState,
                            selectedChannel = selectedChannel,
                            currentCandidate = currentCandidate,
                            selectedRouteIndex = selectedRouteIndex,
                            selectedRegion = selectedRegion,
                            query = query,
                            favoritesOnly = favoritesOnly,
                            favorites = favorites,
                            epgList = epgList,
                            epgState = epgState,
                            currentTimeMs = epgClockMs,
                            viewModel = viewModel,
                            compact = maxHeight < 520.dp,
                            onFullscreen = { fullscreen = true },
                            onShowRoutes = { showRouteSheet = true },
                        )
                    } else {
                        PhoneLayout(
                            uiState = uiState,
                            playerState = playerState,
                            selectedChannel = selectedChannel,
                            currentCandidate = currentCandidate,
                            selectedRouteIndex = selectedRouteIndex,
                            selectedRegion = selectedRegion,
                            query = query,
                            favoritesOnly = favoritesOnly,
                            favorites = favorites,
                            epgList = epgList,
                            epgState = epgState,
                            currentTimeMs = epgClockMs,
                            viewModel = viewModel,
                            onFullscreen = { fullscreen = true },
                            onShowRoutes = { showRouteSheet = true },
                        )
                    }
                }
            }
        }
    }

    if (showRouteSheet && selectedChannel != null) {
        RouteSheet(
            selectedChannel = selectedChannel!!,
            selectedRouteIndex = selectedRouteIndex,
            onSelectRoute = {
                viewModel.selectRoute(it)
                showRouteSheet = false
            },
            priorityRank = { routeUrl ->
                viewModel.routePriorityRank(
                    selectedChannel!!.region,
                    selectedChannel!!.channel,
                    routeUrl,
                )
            },
            onTogglePriority = viewModel::toggleRoutePriority,
            onDismiss = { showRouteSheet = false },
        )
    }
}

@Composable
private fun AulamaLoadingScreen() {
    AuroraBackdrop {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.foundation.Image(
                    painter = painterResource(R.drawable.aulama_tv_logo),
                    contentDescription = "Aulama TV",
                    modifier = Modifier
                        .widthIn(max = 236.dp)
                        .fillMaxWidth(0.62f)
                        .height(100.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "正在準備頻道",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AulamaTopBar(
    darkTheme: Boolean,
    loading: Boolean,
    onToggleTheme: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    TopAppBar(
        title = {
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.aulama_tv_logo),
                contentDescription = "Aulama TV",
                modifier = Modifier
                    .width(108.dp)
                    .height(48.dp),
                contentScale = ContentScale.Fit,
            )
        },
        actions = {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Settings, contentDescription = "設定")
            }
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (darkTheme) "切換淺色模式" else "切換深色模式",
                )
            }
            IconButton(
                onClick = onRefresh,
                enabled = !loading,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "重新整理頻道")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = glassSurfaceColor(),
            scrolledContainerColor = glassSurfaceColor(emphasis = true),
        ),
    )
}

@Composable
private fun PhoneLayout(
    uiState: MobileUiState,
    playerState: DirectPlayerState,
    selectedChannel: SelectedChannel?,
    currentCandidate: PlaybackCandidate?,
    selectedRouteIndex: Int,
    selectedRegion: String,
    query: String,
    favoritesOnly: Boolean,
    favorites: Set<String>,
    epgList: EpgList,
    epgState: MobileEpgState,
    currentTimeMs: Long,
    viewModel: MobileMainViewModel,
    onFullscreen: () -> Unit,
    onShowRoutes: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        DirectVideoPlayer(
            state = playerState,
            candidate = currentCandidate,
            channelName = selectedChannel?.channel?.name.orEmpty(),
            fullscreen = false,
            onToggleFullscreen = onFullscreen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 6.dp)
                .aspectRatio(16f / 9f),
        )
        NowPlayingStrip(
            selectedChannel = selectedChannel,
            route = currentCandidate?.route,
            routeIndex = selectedRouteIndex,
            status = playerState.status,
            epgList = epgList,
            epgState = epgState,
            currentTimeMs = currentTimeMs,
            onShowRoutes = onShowRoutes,
            compact = false,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        ChannelBrowser(
            uiState = uiState,
            selectedChannel = selectedChannel,
            selectedRegion = selectedRegion,
            query = query,
            favoritesOnly = favoritesOnly,
            favorites = favorites,
            epgList = epgList,
            viewModel = viewModel,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WideLayout(
    uiState: MobileUiState,
    playerState: DirectPlayerState,
    selectedChannel: SelectedChannel?,
    currentCandidate: PlaybackCandidate?,
    selectedRouteIndex: Int,
    selectedRegion: String,
    query: String,
    favoritesOnly: Boolean,
    favorites: Set<String>,
    epgList: EpgList,
    epgState: MobileEpgState,
    currentTimeMs: Long,
    viewModel: MobileMainViewModel,
    compact: Boolean,
    onFullscreen: () -> Unit,
    onShowRoutes: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1.6f)
                .fillMaxHeight(),
        ) {
            DirectVideoPlayer(
                state = playerState,
                candidate = currentCandidate,
                channelName = selectedChannel?.channel?.name.orEmpty(),
                fullscreen = false,
                onToggleFullscreen = onFullscreen,
                modifier = if (compact) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .heightIn(min = 168.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                },
            )
            NowPlayingStrip(
                selectedChannel = selectedChannel,
                route = currentCandidate?.route,
                routeIndex = selectedRouteIndex,
                status = playerState.status,
                epgList = epgList,
                epgState = epgState,
                currentTimeMs = currentTimeMs,
                onShowRoutes = onShowRoutes,
                compact = compact,
                modifier = Modifier.padding(top = if (compact) 6.dp else 10.dp),
            )
        }
        GlassPane(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            ChannelBrowser(
                uiState = uiState,
                selectedChannel = selectedChannel,
                selectedRegion = selectedRegion,
                query = query,
                favoritesOnly = favoritesOnly,
                favorites = favorites,
                epgList = epgList,
                viewModel = viewModel,
                modifier = Modifier.fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun NowPlayingStrip(
    selectedChannel: SelectedChannel?,
    route: ChannelRoute?,
    routeIndex: Int,
    status: PlaybackStatus,
    epgList: EpgList,
    epgState: MobileEpgState,
    currentTimeMs: Long,
    onShowRoutes: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val programmes = selectedChannel?.channel?.let { epgList.recentProgramme(it) }
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (compact) 50.dp else 58.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = selectedChannel?.channel?.name ?: "揀選頻道開始播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.SignalCellularAlt,
                        contentDescription = null,
                        tint = statusColor(status),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusLabel(status),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    route?.let {
                        Text(
                            text = " · ${it.quality.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if ((selectedChannel?.channel?.routes?.size ?: 0) > 1) {
                TextButton(
                    onClick = onShowRoutes,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Text("線路 ${routeIndex + 1}/${selectedChannel!!.channel.routes.size}")
                }
            }
        }

        programmes?.now?.let { now ->
            ProgrammeSummary(
                now = now,
                next = programmes.next,
                currentTimeMs = currentTimeMs,
                compact = compact,
            )
        } ?: selectedChannel?.let {
            val message = when {
                epgState.loading -> "正在載入節目單"
                epgState.error != null -> epgState.error
                else -> null
            }
            if (message != null) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ProgrammeSummary(
    now: EpgProgramme,
    next: EpgProgramme?,
    currentTimeMs: Long,
    compact: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 4.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "現正播放",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(68.dp),
            )
            Text(
                text = now.title,
                style = if (compact) MaterialTheme.typography.titleSmall
                else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        if (!compact) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = programmeWindow(now),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "尚餘 ${now.remainingMinutes(currentTimeMs)} 分鐘",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        LinearProgressIndicator(
            progress = { now.progress(currentTimeMs) },
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp),
        )
        if (!compact) next?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "下一節",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.width(68.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = it.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = programmeWindow(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelBrowser(
    uiState: MobileUiState,
    selectedChannel: SelectedChannel?,
    selectedRegion: String,
    query: String,
    favoritesOnly: Boolean,
    favorites: Set<String>,
    epgList: EpgList,
    viewModel: MobileMainViewModel,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val regions = (uiState as? MobileUiState.Ready)?.regions.orEmpty()
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(regions, key = RegionChannels::name) { region ->
                FilterChip(
                    selected = selectedRegion == region.name && !favoritesOnly,
                    onClick = {
                        if (favoritesOnly) viewModel.toggleFavoritesOnly()
                        viewModel.selectRegion(region.name)
                    },
                    label = {
                        Text(
                            text = region.name,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    leadingIcon = if (selectedRegion == region.name && !favoritesOnly) {
                        { Icon(Icons.Rounded.CheckCircle, null, Modifier.size(18.dp)) }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::updateSearchQuery,
                modifier = Modifier.weight(1f),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                placeholder = { Text("搜尋頻道") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "清除搜尋")
                        }
                    }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
            )
            IconToggleButton(
                checked = favoritesOnly,
                onCheckedChange = { viewModel.toggleFavoritesOnly() },
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "只顯示收藏",
                    tint = if (favoritesOnly) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when (uiState) {
            MobileUiState.Loading -> LoadingState()
            is MobileUiState.Error -> ErrorState(uiState.message, viewModel::refresh)
            is MobileUiState.Ready -> {
                val channels = browserChannels(
                    regions = uiState.regions,
                    selectedRegion = selectedRegion,
                    query = query,
                    favoritesOnly = favoritesOnly,
                    favorites = favorites,
                    favoriteKey = viewModel::favoriteKey,
                )
                if (channels.isEmpty()) {
                    EmptyState(favoritesOnly = favoritesOnly, query = query)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 8.dp,
                            end = 8.dp,
                            top = 8.dp,
                            bottom = 18.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = channels,
                            key = { "${it.region}|${it.channel.name}" },
                        ) { item ->
                            ChannelRow(
                                item = item,
                                selected = selectedChannel?.region == item.region &&
                                    selectedChannel.channel.name == item.channel.name,
                                favorite = viewModel.favoriteKey(item.region, item.channel) in favorites,
                                currentProgramme = epgList.recentProgramme(item.channel)?.now,
                                onSelect = { viewModel.selectChannel(item.region, item.channel) },
                                onToggleFavorite = { viewModel.toggleFavorite(item.region, item.channel) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    item: BrowserChannel,
    selected: Boolean,
    favorite: Boolean,
    currentProgramme: EpgProgramme?,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Surface(
        onClick = onSelect,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SubcomposeAsyncImage(
                model = item.channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                loading = { ChannelLogoFallback() },
                error = { ChannelLogoFallback() },
                modifier = Modifier
                    .size(52.dp)
                    .padding(3.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(item.region)
                        val routeCount = item.channel.routes.size
                        if (routeCount > 1) append(" · $routeCount 條線路")
                        item.channel.routes.firstOrNull()?.quality?.label?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                currentProgramme?.let {
                    Text(
                        text = "現正播放 · ${it.title}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (favorite) "取消收藏" else "加入收藏",
                    tint = if (favorite) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChannelLogoFallback() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Rounded.LiveTv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(14.dp))
            Text("正在更新頻道清單", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(message, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onRetry) { Text("重新載入") }
        }
    }
}

@Composable
private fun EmptyState(favoritesOnly: Boolean, query: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = when {
                query.isNotBlank() -> "搵唔到相關頻道"
                favoritesOnly -> "未有收藏頻道"
                else -> "暫時未有頻道"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RouteSheet(
    selectedChannel: SelectedChannel,
    selectedRouteIndex: Int,
    onSelectRoute: (Int) -> Unit,
    priorityRank: (String) -> Int?,
    onTogglePriority: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
        containerColor = glassSurfaceColor(emphasis = true),
        tonalElevation = 0.dp,
    ) {
        Text(
            text = selectedChannel.channel.name,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Text(
            text = "選擇播放線路",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(12.dp))
        selectedChannel.channel.routes.forEachIndexed { index, route ->
            val rank = priorityRank(route.url)
            Surface(
                onClick = { onSelectRoute(index) },
                color = if (selectedRouteIndex == index)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 68.dp)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = selectedRouteIndex == index,
                        onClick = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = route.label.ifBlank { "線路 ${index + 1}" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                        )
                        Text(
                            text = "${route.quality.label} · 線路 ${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onTogglePriority(route.url) }) {
                        Icon(
                            imageVector = if (rank == null) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                            contentDescription = if (rank == null) "設為優先" else "取消優先 $rank",
                            tint = if (rank == null) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.tertiary,
                        )
                    }
                    if (rank != null) {
                        Text(
                            text = rank.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.width(20.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private fun browserChannels(
    regions: List<RegionChannels>,
    selectedRegion: String,
    query: String,
    favoritesOnly: Boolean,
    favorites: Set<String>,
    favoriteKey: (String, Channel) -> String,
): List<BrowserChannel> {
    val source = if (favoritesOnly) {
        regions.flatMap { region -> region.channels.map { BrowserChannel(region.name, it) } }
            .filter { favoriteKey(it.region, it.channel) in favorites }
    } else {
        regions.firstOrNull { it.name == selectedRegion }
            ?.channels
            ?.map { BrowserChannel(selectedRegion, it) }
            .orEmpty()
    }

    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return source
    return source.filter {
        it.channel.name.contains(normalizedQuery, ignoreCase = true) ||
            it.channel.epgName.contains(normalizedQuery, ignoreCase = true)
    }
}

private fun programmeWindow(programme: EpgProgramme): String =
    "${programmeTimeFormatter.format(Instant.ofEpochMilli(programme.startAt))} - " +
        programmeTimeFormatter.format(Instant.ofEpochMilli(programme.endAt))

@Composable
private fun statusColor(status: PlaybackStatus) = when (status) {
    PlaybackStatus.PLAYING -> Color(0xFF42D392)
    PlaybackStatus.BUFFERING -> MaterialTheme.colorScheme.secondary
    PlaybackStatus.ERROR -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: PlaybackStatus) = when (status) {
    PlaybackStatus.IDLE -> "等待播放"
    PlaybackStatus.BUFFERING -> "正在連線"
    PlaybackStatus.PLAYING -> "直播中"
    PlaybackStatus.PAUSED -> "已暫停"
    PlaybackStatus.ERROR -> "正在切換線路"
}

@Composable
private fun FullscreenSystemUi(enabled: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(enabled, activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (enabled && activity != null && window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (activity != null && window != null && controller != null) {
            WindowCompat.setDecorFitsSystemWindows(window, true)
            controller.show(WindowInsetsCompat.Type.systemBars())
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        onDispose {
            if (enabled && activity != null && window != null && controller != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                controller.show(WindowInsetsCompat.Type.systemBars())
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
