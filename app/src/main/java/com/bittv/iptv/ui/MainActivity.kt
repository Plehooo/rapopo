package com.bittv.iptv.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.ComponentName
import android.content.Intent
import android.animation.ObjectAnimator
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.config.AppConfig
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.data.Channel
import com.bittv.iptv.data.M3uParser
import com.bittv.iptv.service.MusicPlayerService
import com.bittv.iptv.ews.EwsLocationManager
import com.bittv.iptv.util.AppUpdateChecker
import com.bittv.iptv.util.ClearKeyUtil
import com.bittv.iptv.util.EpgParser
import com.bittv.iptv.util.EpgRepository
import com.bittv.iptv.util.HeaderParser
import com.bittv.iptv.util.LogoLoader
import com.bittv.iptv.util.MusicRepository
import com.bittv.iptv.util.PlaylistNotification
import com.bittv.iptv.util.FreeNotification
import com.bittv.iptv.util.RemotePushManager
import com.bittv.iptv.util.PlaylistRepository
import com.bittv.iptv.util.PlaylistUpdateResult
import com.bittv.iptv.util.TebakGambarRepository
import com.bittv.iptv.util.ThrottlingDataSource
import com.bittv.iptv.util.ViewerPresenceManager
import com.bittv.iptv.worker.AppUpdateWorker
import com.bittv.iptv.worker.EpgUpdateWorker
import com.bittv.iptv.worker.EwsUpdateWorker
import com.bittv.iptv.worker.FreeNotificationWorker
import com.bittv.iptv.worker.PlaylistUpdateWorker
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var config: AppConfig
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var epgRepository: EpgRepository
    private lateinit var viewerPresence: ViewerPresenceManager

    private lateinit var groupSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var retryButton: Button
    private lateinit var fullscreenRetryButton: Button
    private lateinit var playerView: PlayerView
    private lateinit var playerContainer: RatioFrameLayout
    private lateinit var activeChannelText: TextView
    private lateinit var fullscreenButton: Button
    private lateinit var channelList: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var filterMenuButton: View
    private lateinit var filterCard: View
    private lateinit var topBar: View
    private lateinit var statusBar: View
    private lateinit var startupOverlay: View
    private lateinit var bottomNavTv: View
    private lateinit var bottomNavGame: View
    private lateinit var bottomNavTvLabel: TextView
    private lateinit var bottomNavGameLabel: TextView
    private lateinit var bottomNavBar: View
    private lateinit var bottomNavDivider: View

    // --- Overlay "Update Wajib". Muncul kalau update.json bilang
    //     mandatory=true dan ada versi lebih baru dari yang terpasang. ---
    private lateinit var mandatoryUpdateOverlay: View
    private lateinit var mandatoryUpdateMessage: TextView
    private lateinit var mandatoryUpdateProgress: TextView
    private lateinit var mandatoryUpdateButton: Button
    private var mandatoryUpdateApkFile: java.io.File? = null

    // --- Panel Game ("Tebak Gambar"), tampil di layar yang sama, gantiin
    //     panel TV pas tab Game aktif. Soal diambil dari JSON remote. ---
    private lateinit var tvContentContainer: View
    private lateinit var gameContentContainer: View
    private lateinit var gameMenuContainer: View
    private lateinit var gameCardTebakGambar: View
    private lateinit var tebakGambarContainer: View
    private lateinit var gameBackButton: View
    private lateinit var gameFeedbackText: TextView
    private lateinit var gameScoreText: TextView
    private lateinit var gameTimerText: TextView
    private lateinit var gameImageView: android.widget.ImageView
    private lateinit var gameImageLoading: android.widget.ProgressBar
    private lateinit var gameAnswerInput: EditText

    private var isGameTabActive = false
    private var gameScore = 0
    private var gameItems: List<TebakGambarRepository.Item> = emptyList()
    private var gameCurrentItem: TebakGambarRepository.Item? = null
    private val gameUsedIndexes = mutableSetOf<Int>()
    private var gameCountdown: CountDownTimer? = null
    private var gameRemainingMs: Long = GAME_ROUND_MS
    private var gameLoading = false

    // --- Fitur Musik: search + putar lagu lewat MusicPlayerService, biar
    //     bisa lanjut muter di background kayak Spotify (beda dari video TV
    //     yang emang sengaja berhenti kalau gak di tab TV). ---
    private lateinit var gameCardMusik: View
    private lateinit var musicContainer: View
    private lateinit var musicBackButton: View
    private lateinit var musicSearchInput: EditText
    private lateinit var musicSearchButton: Button
    private lateinit var musicFeedbackText: TextView
    private lateinit var musicResultsList: RecyclerView
    private lateinit var musicLoading: android.widget.ProgressBar
    private lateinit var musicPlayerBar: View
    private lateinit var musicPlayerThumbnail: android.widget.ImageView
    private lateinit var musicPlayerTitle: TextView
    private lateinit var musicPlayPauseButton: TextView
    private lateinit var musicAdapter: MusicAdapter

    // --- Layar "Now Playing" musik (full screen megah ala Spotify) ---
    private lateinit var musicNowPlayingContainer: View
    private lateinit var musicNowPlayingCollapseButton: View
    private lateinit var musicNowPlayingArt: android.widget.ImageView
    private lateinit var musicNowPlayingTitle: TextView
    private lateinit var musicNowPlayingSubtitle: TextView
    private lateinit var musicNowPlayingSeekBar: SeekBar
    private lateinit var musicNowPlayingPositionText: TextView
    private lateinit var musicNowPlayingDurationText: TextView
    private lateinit var musicNowPlayingPrevButton: View
    private lateinit var musicNowPlayingPlayPauseButton: TextView
    private lateinit var musicNowPlayingNextButton: View

    private var lastMusicTracks: List<MusicRepository.MusicTrack> = emptyList()
    private var currentMusicTrackIndex: Int = -1
    private var currentMusicTrackSubtitle: String = ""
    private var currentMusicTrackThumbnailUrl: String = ""
    private var musicSeekBarDragging: Boolean = false

    /** Update posisi/durasi tiap 500ms selama layar Now Playing kebuka. */
    private val musicProgressRunnable = object : Runnable {
        override fun run() {
            updateMusicNowPlayingProgress()
            mainHandler.postDelayed(this, 500)
        }
    }
    private var musicSearching = false
    private var musicResolving = false
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var pendingMusicMediaItem: MediaItem? = null



    private val allChannels = mutableListOf<Channel>()
    private val favorites = linkedSetOf<String>()
    private val history = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var dataSaverMaxBitrateBps: Int = 0
    private lateinit var dataSaverRow: android.view.View
    private lateinit var dataSaverValueText: android.widget.TextView
    private var activeChannel: Channel? = null
    private var automaticRetries = 0
    private var currentFilter = "All"
    private var suppressGroupCallback = false
    private var isFullscreen = false
    private var startupComplete = false
    private var activityStarted = false
    // If a remote playlist arrives while the user is in Game/background, keep
    // the active channel logically selected but postpone player rebuild until
    // TV becomes visible again. This preserves the existing player flow while
    // ensuring a rotated M3U8/MPD URL is never played from stale config.
    private var remotePlayerConfigDirty = false
    private var playbackToken = 0L
    private var epgProgrammes = emptyList<com.bittv.iptv.util.EpgProgramme>()
    private var retryVisibleBeforeFullscreen = false

    // BUG FIX: KEY_LAST_CHANNEL sudah lama disimpan di saveHistory() tapi
    // tidak pernah dibaca ulang, jadi app selalu autoplay channel PERTAMA
    // di playlist alih-alih channel terakhir yang ditonton user. Nilainya
    // ditampung di sini pas restoreState(), dipakai sekali pas autoplay awal.
    private var pendingLastChannelUrl: String? = null

    private val prefs by lazy { getSharedPreferences("bittv", MODE_PRIVATE) }

    private val foregroundCheckRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && config.autoUpdateEnabled) {
                checkRemoteInBackground(showPlaylistNotification = true)
                mainHandler.postDelayed(
                    this,
                    config.foregroundCheckSeconds.coerceAtLeast(30L) * 1000L
                )
            }
        }
    }

    private val remotePlaylistReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: android.content.Intent) {
            if (intent.action != PlaylistUpdateWorker.ACTION_REMOTE_PLAYLIST_UPDATED) return
            if (!startupComplete || !activityStarted || isFinishing || isDestroyed) return

            val snapshot = PlaylistRepository.consumeLatestSnapshot() ?: return
            applyLatestRemoteSnapshot(snapshot, forceReconnect = false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // FLAG_KEEP_SCREEN_ON TIDAK dipasang di sini lagi — dulu dipasang
        // permanen sepanjang app dibuka (boros baterai walau cuma buka
        // daftar channel/main game). Sekarang di-toggle otomatis lewat
        // onIsPlayingChanged() di attachPlayerListener(), cuma nyala pas
        // video beneran lagi diputar.

        config = ConfigStore.load(this)
        playlistRepository = PlaylistRepository(this, config)
        epgRepository = EpgRepository(this)

        setContentView(R.layout.activity_main)
        bindViews()
        applyEdgeToEdgeInsets()
        restoreState()
        configureBackHandling()
        configureUi()

        viewerPresence = ViewerPresenceManager(this) { counts ->
            mainHandler.post {
                if (!isFinishing && !isDestroyed && ::channelAdapter.isInitialized) {
                    channelAdapter.updateViewerCounts(counts)
                }
            }
        }
        viewerPresence.setKnownChannels(allChannels)
        viewerPresence.start()
        registerRemotePlaylistReceiver()

        scheduleBackgroundWorkers()
        initializeRemoteNotifications()

        startupOverlay.visibility = View.VISIBLE
        playerContainer.visibility = View.GONE
        statusText.text = "LIVE TV • Memuat channel..."

        // The first screen is rendered immediately. Reading/parsing the local M3U
        // happens off the main thread so a large playlist cannot freeze startup.
        mainHandler.post { loadLocalPlaylistAsync() }

        // BUG FIX: sebelumnya status fullscreen cuma disimpan di variabel biasa
        // (isFullscreen), tidak pernah dipulihkan lewat savedInstanceState.
        // Di banyak HP (Xiaomi/Oppo/dll yang agresif matiin Activity pas app
        // di-background), keluar app pas lagi fullscreen lalu balik lagi bikin
        // Activity dibuat ulang dari nol -> isFullscreen balik ke false ->
        // tampilan balik ke mode normal (gak lebar/gak fullscreen) padahal
        // sebelumnya fullscreen. Di sini status fullscreen dipulihkan lagi.
        if (savedInstanceState?.getBoolean(KEY_WAS_FULLSCREEN, false) == true) {
            mainHandler.post { enterFullscreen() }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WAS_FULLSCREEN, isFullscreen)
    }

    private fun bindViews() {
        topBar = findViewById(R.id.topBar)
        statusBar = findViewById(R.id.statusBar)
        startupOverlay = findViewById(R.id.startupOverlay)
        groupSpinner = findViewById(R.id.groupSpinner)
        statusText = findViewById(R.id.statusText)
        retryButton = findViewById(R.id.retryButton)
        fullscreenRetryButton = findViewById(R.id.fullscreenRetryButton)
        playerView = findViewById(R.id.playerView)
        playerContainer = findViewById(R.id.playerContainer)
        activeChannelText = findViewById(R.id.activeChannelText)
        fullscreenButton = findViewById(R.id.fullscreenButton)
        channelList = findViewById(R.id.channelList)
        searchInput = findViewById(R.id.searchInput)
        previousButton = findViewById(R.id.previousButton)
        nextButton = findViewById(R.id.nextButton)

        // BUG FIX: tombol "⋯" (filterMenuButton) buat buka panel filter
        // (grup channel, prev/next, dan sekarang switch Hemat Data) gak
        // pernah di-wire ke kode, jadi diklik gak ngefek sama sekali —
        // panel filterCard-nya juga gak pernah keluar dari GONE.
        filterMenuButton = findViewById(R.id.filterMenuButton)
        filterCard = findViewById(R.id.filterCard)
        filterMenuButton.setOnClickListener { toggleFilterCard() }
        bottomNavTv = findViewById(R.id.bottomNavTv)
        bottomNavGame = findViewById(R.id.bottomNavGame)
        bottomNavTvLabel = findViewById(R.id.bottomNavTvLabel)
        bottomNavGameLabel = findViewById(R.id.bottomNavGameLabel)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        bottomNavDivider = findViewById(R.id.bottomNavDivider)

        dataSaverRow = findViewById(R.id.dataSaverRow)
        dataSaverValueText = findViewById(R.id.dataSaverValueText)
        dataSaverRow.setOnClickListener { showDataSaverMenu() }

        mandatoryUpdateOverlay = findViewById(R.id.mandatoryUpdateOverlay)
        mandatoryUpdateMessage = findViewById(R.id.mandatoryUpdateMessage)
        mandatoryUpdateProgress = findViewById(R.id.mandatoryUpdateProgress)
        mandatoryUpdateButton = findViewById(R.id.mandatoryUpdateButton)

        tvContentContainer = findViewById(R.id.tvContentContainer)
        gameContentContainer = findViewById(R.id.gameContentContainer)
        gameMenuContainer = findViewById(R.id.gameMenuContainer)
        gameCardTebakGambar = findViewById(R.id.gameCardTebakGambar)
        tebakGambarContainer = findViewById(R.id.tebakGambarContainer)
        gameBackButton = findViewById(R.id.gameBackButton)
        gameFeedbackText = findViewById(R.id.gameFeedbackText)
        gameScoreText = findViewById(R.id.gameScoreText)
        gameTimerText = findViewById(R.id.gameTimerText)
        gameImageView = findViewById(R.id.gameImageView)
        gameImageLoading = findViewById(R.id.gameImageLoading)
        gameAnswerInput = findViewById(R.id.gameAnswerInput)

        // BUG FIX: kartu "Tebak Gambar" dan tombol back di menu game gak pernah
        // di-wire ke kode sama sekali, jadi diklik gak ngapa-ngapain (menu game
        // tampil tapi layar game beneran-nya ketutup terus, GONE).
        gameCardTebakGambar.setOnClickListener { openTebakGambar() }
        gameBackButton.setOnClickListener { closeTebakGambar() }

        findViewById<Button>(R.id.gameSkipButton).setOnClickListener { nextGameImage(reveal = true) }
        findViewById<Button>(R.id.gameSubmitButton).setOnClickListener { checkGameAnswer() }
        gameAnswerInput.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isDone) checkGameAnswer()
            isDone
        }

        // --- Wiring fitur Musik ---
        gameCardMusik = findViewById(R.id.gameCardMusik)
        musicContainer = findViewById(R.id.musicContainer)
        musicBackButton = findViewById(R.id.musicBackButton)
        musicSearchInput = findViewById(R.id.musicSearchInput)
        musicSearchButton = findViewById(R.id.musicSearchButton)
        musicFeedbackText = findViewById(R.id.musicFeedbackText)
        musicResultsList = findViewById(R.id.musicResultsList)
        musicLoading = findViewById(R.id.musicLoading)
        musicPlayerBar = findViewById(R.id.musicPlayerBar)
        musicPlayerThumbnail = findViewById(R.id.musicPlayerThumbnail)
        musicPlayerTitle = findViewById(R.id.musicPlayerTitle)
        musicPlayPauseButton = findViewById(R.id.musicPlayPauseButton)

        musicAdapter = MusicAdapter { track -> playMusicTrack(track) }
        musicResultsList.layoutManager = LinearLayoutManager(this)
        musicResultsList.adapter = musicAdapter

        // Kartu "Musik" diaktifkan lagi: klik kartu -> buka layar musik.
        gameCardMusik.setOnClickListener { openMusic() }
        musicBackButton.setOnClickListener { closeMusic() }
        musicSearchButton.setOnClickListener { performMusicSearch() }
        musicSearchInput.setOnEditorActionListener { _, actionId, event ->
            val isSearch = actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isSearch) performMusicSearch()
            isSearch
        }
        musicPlayPauseButton.setOnClickListener { toggleMusicPlayPause() }

        // --- Wiring layar "Now Playing" (expand dari mini player bar) ---
        musicNowPlayingContainer = findViewById(R.id.musicNowPlayingContainer)
        musicNowPlayingCollapseButton = findViewById(R.id.musicNowPlayingCollapseButton)
        musicNowPlayingArt = findViewById(R.id.musicNowPlayingArt)
        musicNowPlayingTitle = findViewById(R.id.musicNowPlayingTitle)
        musicNowPlayingSubtitle = findViewById(R.id.musicNowPlayingSubtitle)
        musicNowPlayingSeekBar = findViewById(R.id.musicNowPlayingSeekBar)
        musicNowPlayingPositionText = findViewById(R.id.musicNowPlayingPositionText)
        musicNowPlayingDurationText = findViewById(R.id.musicNowPlayingDurationText)
        musicNowPlayingPrevButton = findViewById(R.id.musicNowPlayingPrevButton)
        musicNowPlayingPlayPauseButton = findViewById(R.id.musicNowPlayingPlayPauseButton)
        musicNowPlayingNextButton = findViewById(R.id.musicNowPlayingNextButton)

        // Tap bar mini player (bukan tombol play/pause-nya) buat besarin ke full screen.
        musicPlayerBar.setOnClickListener { openMusicNowPlaying() }
        musicNowPlayingCollapseButton.setOnClickListener { closeMusicNowPlaying() }
        musicNowPlayingPlayPauseButton.setOnClickListener { toggleMusicPlayPause() }
        musicNowPlayingPrevButton.setOnClickListener { playAdjacentTrack(-1) }
        musicNowPlayingNextButton.setOnClickListener { playAdjacentTrack(1) }
        musicNowPlayingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                musicSeekBarDragging = true
            }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                musicSeekBarDragging = false
                mediaController?.seekTo((seekBar?.progress ?: 0) * 1000L)
            }
        })
    }

    /**
     * targetSdk 35 forces edge-to-edge, so without this the top bar and the
     * bottom nav draw underneath the status bar / gesture bar on some phones
     * (that's the overlap you saw in the screenshot). This pushes both bars
     * out by exactly the system inset on whichever device it runs on, instead
     * of a fixed dp guess that would only work on one screen.
     */
    private fun applyEdgeToEdgeInsets() {
        val topBarStartPadding = topBar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = topBarStartPadding + bars.top)
            insets
        }

        val bottomNavParent = bottomNavTv.parent as? View
        if (bottomNavParent != null) {
            val bottomStartPadding = bottomNavParent.paddingBottom
            ViewCompat.setOnApplyWindowInsetsListener(bottomNavParent) { view, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
                view.updatePadding(bottom = bottomStartPadding + bars.bottom)
                insets
            }
        }
    }

    private fun configureUi() {
        PlaylistNotification.ensureChannel(this)
        requestNotificationPermissionIfNeeded()
        requestLocationPermissionIfNeeded()

        setupList()
        setupControls()

        playerView.useController = false
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        playerView.player = null

        activeChannelText.text = "Production by ${config.producer}"
        startupOverlay.findViewById<TextView>(R.id.startupSubtitle).text =
            "by DITZYA"
    }

    private fun configureBackHandling() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        mandatoryUpdateOverlay.visibility == View.VISIBLE -> {
                            // Update wajib: back ditelan, tidak boleh keluar
                            // dari layar ini selain lewat tombol update.
                        }
                        isFullscreen -> exitFullscreen()
                        isGameTabActive -> showTvTab()
                        else -> {
                            isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                        }
                    }
                }
            }
        )
    }

    /**
     * Notification enrollment is independent from M3U loading. A fresh install
     * silently baselines the current remote announcement before joining FCM.
     */
    private fun initializeRemoteNotifications() {
        backgroundExecutor.execute {
            val baseline = FreeNotification.primeBaseline(this@MainActivity)
            if (baseline.isSuccess) {
                RemotePushManager.markBaselineReady(this@MainActivity)
                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post
                    RemotePushManager.ensureTopicSubscription(this@MainActivity)
                    FreeNotificationWorker.scheduleCatchUp(this@MainActivity)
                    FreeNotificationWorker.schedule(this@MainActivity)
                }
            } else {
                FreeNotificationWorker.scheduleBaselineRetry(this@MainActivity)
            }
        }
    }

    private fun scheduleBackgroundWorkers() {
        if (!config.autoUpdateEnabled) return
        PlaylistUpdateWorker.schedule(this)
        AppUpdateWorker.schedule(this)
        EpgUpdateWorker.schedule(this)
        // FreeNotification fallback is started only after initializeRemoteNotifications()
        // establishes the silent baseline, preventing a fresh installation from
        // immediately receiving the current announcement.
        checkMandatoryUpdateOnLaunch()
    }

    /**
     * Cek langsung (tanpa nunggu jadwal periodic worker) tiap kali app
     * dibuka: kalau update.json bilang mandatory=true dan ada versi baru,
     * tampilkan overlay "Update Wajib" yang gak bisa ditutup selain lewat
     * tombol update. Update biasa (mandatory=false) tetap ditangani oleh
     * AppUpdateWorker lewat notifikasi seperti biasa, tidak diganggu di sini.
     */
    private fun checkMandatoryUpdateOnLaunch() {
        backgroundExecutor.execute {
            val result = kotlinx.coroutines.runBlocking {
                AppUpdateChecker.checkAndDownload(this@MainActivity)
            }
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                if (result is AppUpdateChecker.UpdateResult.Available && result.mandatory) {
                    showMandatoryUpdateOverlay(result.versionName, result.apkFile)
                }
            }
        }
    }

    private fun showMandatoryUpdateOverlay(versionName: String, apkFile: java.io.File) {
        mandatoryUpdateApkFile = apkFile
        mandatoryUpdateMessage.text =
            "Versi $versionName wajib dipasang untuk melanjutkan pakai aplikasi ini."
        mandatoryUpdateProgress.text = "Update sudah siap diunduh, tap tombol di bawah."
        mandatoryUpdateButton.setOnClickListener {
            val file = mandatoryUpdateApkFile ?: return@setOnClickListener
            startActivity(AppUpdateChecker.installIntent(this, file))
        }
        mandatoryUpdateOverlay.visibility = View.VISIBLE
        mandatoryUpdateOverlay.bringToFront()
    }

    private fun loadLocalPlaylistAsync() {
        backgroundExecutor.execute {
            val remoteResult = playlistRepository.checkForUpdate()
            val snapshot = when (remoteResult) {
                is PlaylistUpdateResult.Updated -> remoteResult.snapshot
                is PlaylistUpdateResult.NotModified -> remoteResult.snapshot
                is PlaylistUpdateResult.Failed -> null
            }
            val startupUpdate = (remoteResult as? PlaylistUpdateResult.Updated)
                ?.takeIf { !it.firstRemoteSync }

            if (snapshot == null) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) {
                        startupOverlay.visibility = View.GONE
                        channelList.visibility = View.VISIBLE
                        playerContainer.visibility = View.VISIBLE
                        statusText.text = "LIVE TV • playlist remote tidak tersedia"
                    }
                }
                return@execute
            }

            /*
             * Startup hanya menunggu playlist.
             *
             * EPG cached sengaja TIDAK diproses di sini karena parsing EPG
             * yang besar bisa membuat layar awal terasa blank terlalu lama.
             */
            val parsed = runCatching {
                M3uParser.parse(
                    text = snapshot.content,
                    baseUrl = config.playlistUrl,
                    defaultHeaders = emptyMap()
                )
            }.getOrElse {
                emptyList()
            }

            if (isFinishing || isDestroyed) return@execute

            mainHandler.post {
                if (isFinishing || isDestroyed) return@post

                if (parsed.isEmpty()) {
                    startupOverlay.visibility = View.GONE
                    channelList.visibility = View.VISIBLE
                    playerContainer.visibility = View.VISIBLE
                    statusText.text = "LIVE TV • belum ada channel"
                    return@post
                }

                /*
                 * Tampilkan UI secepat mungkin setelah playlist siap.
                 */
                applyParsedChannels(snapshot.content, parsed)


                // The EPG URL becomes known only after M3U parsing.
                // Re-schedule the one-time EPG worker so fresh installs get
                // their first EPG download promptly.
                EpgUpdateWorker.scheduleInitialNow(this@MainActivity)

                startupComplete = true
                startupOverlay.visibility = View.GONE
                channelList.visibility = View.VISIBLE
                playerContainer.visibility = View.VISIBLE

                /*
                 * Autoplay channel terakhir yang ditonton (kalau masih ada
                 * di playlist), kalau tidak ada baru fallback ke channel
                 * paling atas. Delay kecil memberi kesempatan layout selesai
                 * sehingga PlayerView tidak terlihat seperti blank hitam
                 * saat startup.
                 */
                mainHandler.postDelayed({
                    if (!isFinishing &&
                        !isDestroyed &&
                        startupComplete &&
                        activeChannel == null
                    ) {
                        val resumeChannel = pendingLastChannelUrl
                            ?.let { url -> parsed.firstOrNull { it.streamUrl == url } }
                            ?: parsed.first()
                        playChannel(
                            resumeChannel,
                            isRetry = false,
                            saveAsLast = true
                        )
                    }
                }, 100L)

                /*
                 * EPG cached diproses SETELAH layar sudah tampil.
                 * Jadi parsing EPG tidak menghambat startup.
                 */
                backgroundExecutor.execute {
                    val cachedEpg = runCatching {
                        epgRepository.cached()
                    }.getOrNull()

                    if (cachedEpg.isNullOrBlank()) return@execute

                    val parsedEpg = runCatching {
                        EpgParser.parse(cachedEpg)
                    }.getOrDefault(emptyList())

                    if (isFinishing || isDestroyed) return@execute

                    mainHandler.post {
                        if (isFinishing || isDestroyed) return@post

                        epgProgrammes = parsedEpg

                        activeChannel?.let { channel ->
                            val programme = channel.epgId?.let(::epgNow)

                            if (programme != null) {
                                statusText.text =
                                    "LIVE • ${channel.name} • ${programme.title}"
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyParsedChannels(content: String, channels: List<Channel>): Boolean {
        if (channels.isEmpty()) return false

        updateEpgFromPlaylist(content)

        val oldChannel = activeChannel
        val oldUrl = oldChannel?.streamUrl
        val oldIdentity = oldChannel?.let(::channelIdentity)

        allChannels.clear()
        allChannels.addAll(channels)
        if (::viewerPresence.isInitialized) {
            viewerPresence.setKnownChannels(allChannels)
        }

        val replacement = oldIdentity?.let { identity ->
            allChannels.firstOrNull { channelIdentity(it) == identity }
        }
        activeChannel = replacement

        renderGroups()
        applyFilter()
        statusText.text = "LIVE TV • ${channels.size} channel"

        // A rotated M3U8/MPD URL is a change to the same logical channel.
        // Reconnect the existing player immediately without leaving the app
        // or requiring a manual refresh.
        val playerConfigChanged = oldChannel != null && replacement != null && (
            !replacement.streamUrl.equals(oldUrl, ignoreCase = false) ||
                replacement.headers != oldChannel.headers ||
                replacement.drmScheme != oldChannel.drmScheme ||
                replacement.drmLicenseKey != oldChannel.drmLicenseKey
            )

        if (playerConfigChanged) {
            if (startupComplete && !isGameTabActive && activityStarted) {
                reconnectActiveChannel()
            } else {
                remotePlayerConfigDirty = true
            }
        }
        return playerConfigChanged
    }

    private fun channelIdentity(channel: Channel): String {
        val epg = channel.epgId.orEmpty().trim().lowercase(Locale.ROOT)
        val id = channel.id.trim().lowercase(Locale.ROOT)
        val name = channel.name.trim().lowercase(Locale.ROOT)
        val group = channel.group.trim().lowercase(Locale.ROOT)
        return when {
            epg.isNotBlank() -> "epg:$epg"
            id.isNotBlank() && !id.startsWith("channel-") -> "id:$id"
            else -> "name:$name\u0000group:$group"
        }
    }

    private fun updateEpgFromPlaylist(content: String) {
        val url = M3uParser.playlistEpgUrl(content, config.playlistUrl)
        if (!url.isNullOrBlank()) {
            prefs.edit().putString("epg_url", url).apply()
        }
    }

    private fun checkRemoteInBackground(showPlaylistNotification: Boolean) {
        backgroundExecutor.execute {
            val result = runCatching { playlistRepository.checkForUpdate() }
                .getOrElse { PlaylistUpdateResult.Failed(it) }

            if (isFinishing || isDestroyed) return@execute

            if (result is PlaylistUpdateResult.Updated) {
                val parsed = runCatching {
                    M3uParser.parse(
                        result.snapshot.content,
                        config.playlistUrl,
                        emptyMap()
                    )
                }.getOrElse { emptyList() }

                mainHandler.post {
                    if (isFinishing || isDestroyed) return@post

                    applyParsedChannels(result.snapshot.content, parsed)

                    if (showPlaylistNotification && config.notificationsEnabled && !result.firstRemoteSync) {
                        PlaylistNotification.showUpdatedOnce(
                            this,
                            result.snapshot.revision,
                            result.diff,
                            result.totalChannels
                        )
                    }
                    statusText.text = "LIVE TV • ${result.totalChannels} channel"
                }
            }
        }
    }

    // Lebar target 1 kartu channel (item_channel.xml didesain sebagai row
    // lebar, bukan kotak grid kecil). Di bawah ambang ini tetap 1 kolom
    // (semua HP normal, sesuai desain aslinya) — baru nambah kolom kalau
    // layar cukup lebar (tablet, HP layar lebar, mode split-screen).
    private fun calculateSpanCount(): Int {
        val widthDp = resources.configuration.screenWidthDp
        val targetColumnWidthDp = 380
        return (widthDp / targetColumnWidthDp).coerceAtLeast(1)
    }

    private fun setupList() {
        val adapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel, isRetry = false, saveAsLast = true) },
            onFavoriteClick = { toggleFavorite(it) },
            isFavorite = { favorites.contains(it.streamUrl) },
            isSelected = { activeChannel?.streamUrl == it.streamUrl }
        )
        this.channelAdapter = adapter
        channelList.layoutManager = GridLayoutManager(this, calculateSpanCount())
        channelList.adapter = adapter
        channelList.setHasFixedSize(false)
        channelList.clipToPadding = false
    }

    // MainActivity dipertahankan hidup saat rotasi/resize (lihat
    // android:configChanges di manifest), jadi span count grid perlu
    // dihitung ulang manual di sini — kalau tidak, HP yang di-rotate atau
    // di-resize (mode split-screen/foldable) bakal "nyangkut" di jumlah
    // kolom lama sampai app dibuka ulang.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::channelList.isInitialized) {
            (channelList.layoutManager as? GridLayoutManager)?.spanCount = calculateSpanCount()
        }
    }

    private lateinit var channelAdapter: ChannelAdapter

    private fun setupControls() {
        val retryAction = View.OnClickListener {
            activeChannel?.let {
                automaticRetries = 0
                fullscreenRetryButton.visibility = View.GONE
                retryButton.visibility = View.GONE
                playChannel(it, isRetry = true, saveAsLast = true)
            }
        }

        retryButton.setOnClickListener(retryAction)
        fullscreenRetryButton.setOnClickListener(retryAction)
        fullscreenButton.setOnClickListener { toggleFullscreen() }
        previousButton.setOnClickListener { playAdjacent(-1) }
        nextButton.setOnClickListener { playAdjacent(1) }

        bottomNavTv.setOnClickListener { showTvTab() }
        bottomNavGame.setOnClickListener { showGameTab() }

        searchInput.addTextChangedListener(SimpleTextWatcher { applyFilter() })

        groupSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!suppressGroupCallback) {
                    currentFilter = parent?.getItemAtPosition(position)?.toString() ?: "All"
                    applyFilter()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun renderGroups() {
        val groups = listOf("All") + allChannels
            .map { it.group.ifBlank { "Ungrouped" } }
            .toSet()
            .sorted()

        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            groups
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        suppressGroupCallback = true
        groupSpinner.adapter = spinnerAdapter
        val desired = groups.indexOf(currentFilter).takeIf { it >= 0 } ?: 0
        groupSpinner.setSelection(desired, false)
        suppressGroupCallback = false
    }

    // ================= Tab TV <-> Game (satu layar, bukan pindah Activity) =================

    /** Musik harus benar-benar dilepas saat kembali ke tab TV. */
    private fun stopMusicForTvMode() {
        // Jangan biarkan panel musik yang sebelumnya dibuka membuat seluruh
        // area Game menjadi kosong saat user kembali dari TV.
        musicContainer.visibility = View.GONE
        musicPlayerBar.visibility = View.GONE
        mainHandler.removeCallbacks(musicProgressRunnable)
        musicNowPlayingContainer.visibility = View.GONE
        pendingMusicMediaItem = null
        musicResolving = false
        musicSearching = false
        musicLoading.visibility = View.GONE
        musicFeedbackText.text = ""

        // Putuskan controller lama sepenuhnya. Saat user membuka Musik lagi,
        // controller akan dibuat ulang terhadap service yang baru. Ini mencegah
        // state player lama menempel setelah pindah TV -> Game.
        mediaControllerFuture?.let { future ->
            runCatching { future.cancel(true) }
        }
        mediaControllerFuture = null

        mediaController?.let { controller ->
            runCatching { controller.pause() }
            runCatching { controller.stop() }
            runCatching { controller.clearMediaItems() }
            runCatching { controller.release() }
        }
        mediaController = null

        runCatching {
            stopService(Intent(this, MusicPlayerService::class.java))
        }

        // Setelah keluar dari Musik, Game selalu kembali ke menu utama.
        // Tanpa ini gameMenuContainer bisa tetap GONE karena sebelumnya
        // disembunyikan oleh openMusic(), sehingga tab Game tampak hitam/kosong.
        tebakGambarContainer.visibility = View.GONE
        gameMenuContainer.visibility = View.VISIBLE
    }

    private fun showTvTab() {
        if (!isGameTabActive) return
        stopMusicForTvMode()
        isGameTabActive = false

        crossFadeSwap(from = gameContentContainer, to = tvContentContainer)
        bottomNavTvLabel.setTextColor(resources.getColor(R.color.accent, theme))
        bottomNavGameLabel.setTextColor(resources.getColor(R.color.text_secondary, theme))

        // Timer dijeda (bukan direset) selama keluar dari tab Game, biar pas
        // balik lagi sisa waktunya masih sama seperti pas ditinggal.
        gameCountdown?.cancel()

        // Video otomatis lanjut muter lagi pas balik ke tab TV. Jika M3U
        // sempat berubah ketika tab Game sedang aktif, rebuild player dulu
        // supaya URL/headers/DRM terbaru benar-benar dipakai.
        if (remotePlayerConfigDirty && activeChannel != null) {
            remotePlayerConfigDirty = false
            reconnectActiveChannel()
        } else {
            player?.playWhenReady = true
            player?.play()
        }

        // Nyalain lagi animasi "LIVE" di channel list (sempat dimatiin
        // pas pindah ke tab Game, biar gak jalan sia-sia di belakang layar).
        resumeChannelListPulses()
    }

    private fun showGameTab() {
        if (isGameTabActive) return
        isGameTabActive = true

        // Safety net: jika sebelumnya user keluar ke TV dari layar Musik,
        // pastikan panel anak Game tidak semuanya GONE.
        musicContainer.visibility = View.GONE
        musicPlayerBar.visibility = View.GONE
        musicNowPlayingContainer.visibility = View.GONE
        if (tebakGambarContainer.visibility != View.VISIBLE) {
            gameMenuContainer.visibility = View.VISIBLE
        }

        crossFadeSwap(from = tvContentContainer, to = gameContentContainer)
        bottomNavGameLabel.setTextColor(resources.getColor(R.color.accent, theme))
        bottomNavTvLabel.setTextColor(resources.getColor(R.color.text_secondary, theme))

        // Video otomatis berhenti selama di tab Game, hemat data/baterai.
        player?.playWhenReady = false
        player?.pause()
        viewerPresence.setWatching(null, false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Channel list-nya lagi disembunyikan total (GONE), jadi animasi
        // "LIVE" yang lagi jalan di row-row-nya cuma buang-buang CPU/baterai
        // tanpa ada yang lihat — matiin dulu.
        pauseChannelListPulses()

        // Kalau user sebelumnya lagi di tengah main "Tebak Gambar" (bukan di
        // menu pilih game), lanjutin lagi timernya. Kalau masih di menu,
        // biarin di menu — jangan langsung nyelonong ke game.
        if (tebakGambarContainer.visibility == View.VISIBLE && gameCurrentItem != null) {
            startGameCountdown(gameRemainingMs)
        }
    }

    /** Buka layar "Tebak Gambar" beneran, gantiin menu pilih game. */
    private fun openTebakGambar() {
        gameMenuContainer.visibility = View.GONE
        tebakGambarContainer.visibility = View.VISIBLE

        if (gameItems.isEmpty() && !gameLoading) {
            loadGameBankThenStart()
        } else if (gameCurrentItem == null && gameItems.isNotEmpty()) {
            nextGameImage(reveal = false)
        } else if (gameCurrentItem != null) {
            // Lanjutin sisa waktu dari sebelumnya.
            startGameCountdown(gameRemainingMs)
        }
    }

    /** Balik dari layar "Tebak Gambar" ke menu pilih game. */
    private fun closeTebakGambar() {
        gameCountdown?.cancel()
        tebakGambarContainer.visibility = View.GONE
        gameMenuContainer.visibility = View.VISIBLE
    }

    /** Buka layar "Musik", gantiin menu pilih game. */
    private fun openMusic() {
        gameMenuContainer.visibility = View.GONE
        musicContainer.visibility = View.VISIBLE
        // Baru konek ke MusicPlayerService pas beneran dibuka user (lazy) —
        // biar kalau ada masalah binding ke service, cuma fitur Musik yang
        // kena, gak nge-freeze seluruh app pas pertama kali dibuka.
        if (mediaController == null && mediaControllerFuture == null) {
            connectMusicController()
        }
    }

    /** Balik dari layar "Musik" ke menu pilih game. Lagu TETAP lanjut muter
     *  di background lewat MusicPlayerService — cuma layar search-nya yang ditutup. */
    private fun closeMusic() {
        musicContainer.visibility = View.GONE
        gameMenuContainer.visibility = View.VISIBLE
    }

    /** Hubungin MediaController ke MusicPlayerService yang jalan di background. */
    private fun connectMusicController() {
        if (mediaController != null || mediaControllerFuture != null) return
        runCatching {
            val sessionToken = SessionToken(this, ComponentName(this, MusicPlayerService::class.java))
            val future = MediaController.Builder(this, sessionToken).buildAsync()
            mediaControllerFuture = future
            future.addListener({
                val controller = runCatching { future.get() }.getOrNull()
                if (controller == null) {
                    mediaControllerFuture = null
                    musicFeedbackText.text = "Player musik gagal disiapkan, coba lagi."
                    return@addListener
                }
                mediaController = controller
                controller.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        val icon = if (isPlaying) "⏸" else "▶"
                        musicPlayPauseButton.text = icon
                        musicNowPlayingPlayPauseButton.text = icon
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && controller.isPlaying) {
                            musicPlayPauseButton.text = "⏸"
                            musicNowPlayingPlayPauseButton.text = "⏸"
                        }
                        if (playbackState == Player.STATE_ENDED) {
                            musicPlayerBar.visibility = View.GONE
                            musicPlayPauseButton.text = "▶"
                            musicNowPlayingPlayPauseButton.text = "▶"
                            musicFeedbackText.text = "Lagu selesai."
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        musicFeedbackText.text = "Gagal memutar: ${error.errorCodeName}"
                        musicLoading.visibility = View.GONE
                    }
                })

                // Kalau user sempat menekan lagu sebelum MediaController selesai
                // tersambung, jangan hilangkan perintah play. Jalankan sekarang.
                pendingMusicMediaItem?.let { queuedItem ->
                    pendingMusicMediaItem = null
                    controller.setMediaItem(queuedItem)
                    controller.prepare()
                    controller.play()
                }

                val initialIcon = if (controller.isPlaying) "⏸" else "▶"
                musicPlayPauseButton.text = initialIcon
                musicNowPlayingPlayPauseButton.text = initialIcon
            }, MoreExecutors.directExecutor())
        }.onFailure {
            mediaControllerFuture = null
            musicFeedbackText.text = "Player musik gagal disiapkan, coba lagi."
        }
    }

    /** Cari lagu berdasarkan teks di kolom search, tampilin hasilnya di list. */
    private fun performMusicSearch() {
        val query = musicSearchInput.text?.toString()?.trim().orEmpty()
        if (query.isBlank() || musicSearching) return

        musicSearching = true
        musicLoading.visibility = View.VISIBLE
        musicFeedbackText.text = "Mencari \"$query\"..."
        musicAdapter.submitList(emptyList())

        backgroundExecutor.execute {
            val result = MusicRepository.search(query)
            mainHandler.post {
                musicSearching = false
                musicLoading.visibility = View.GONE
                result.onSuccess { tracks ->
                    lastMusicTracks = tracks
                    musicAdapter.submitList(tracks)
                    musicFeedbackText.text = "Ditemukan ${tracks.size} lagu. Tap buat muter."
                }.onFailure {
                    musicFeedbackText.text = "Gagal: ${it.message ?: it.javaClass.simpleName}"
                }
            }
        }
    }

    /** Play lagu yang dipilih langsung dari field mp3 yang dikirim endpoint ytplay. */
    private fun playMusicTrack(track: MusicRepository.MusicTrack) {
        val mp3Url = track.mp3Url.trim()
        if (mp3Url.isBlank()) {
            musicFeedbackText.text = "Gagal: link MP3 tidak tersedia."
            return
        }

        val mediaItem = MediaItem.Builder()
            .setUri(mp3Url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.author.ifBlank { track.channel })
                    .setArtworkUri(track.thumbnailUrl.takeIf { it.isNotBlank() }?.let { android.net.Uri.parse(it) })
                    .build()
            )
            .build()

        val controller = mediaController
        if (controller != null) {
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        } else {
            pendingMusicMediaItem = mediaItem
            connectMusicController()
        }

        musicPlayerBar.visibility = View.VISIBLE
        musicPlayerTitle.text = track.title
        LogoLoader.load(track.thumbnailUrl, musicPlayerThumbnail)
        musicFeedbackText.text = "Memutar: ${track.title}"

        currentMusicTrackIndex = lastMusicTracks.indexOf(track)
        updateNowPlayingMeta(track)
    }

    /** Sinkronkan judul/sampul/subjudul ke layar Now Playing (kalau lagi kebuka). */
    private fun updateNowPlayingMeta(track: MusicRepository.MusicTrack) {
        currentMusicTrackSubtitle = track.author.ifBlank { track.channel }
        currentMusicTrackThumbnailUrl = track.thumbnailUrl
        musicNowPlayingTitle.text = track.title
        musicNowPlayingSubtitle.text = currentMusicTrackSubtitle
        LogoLoader.load(currentMusicTrackThumbnailUrl, musicNowPlayingArt)
        musicNowPlayingSeekBar.progress = 0
        musicNowPlayingPositionText.text = "0:00"
        musicNowPlayingDurationText.text = "0:00"
    }

    /** Buka layar Now Playing full screen (tap mini player bar). */
    private fun openMusicNowPlaying() {
        if (mediaController == null && pendingMusicMediaItem == null) return
        musicNowPlayingPlayPauseButton.text = musicPlayPauseButton.text
        musicContainer.visibility = View.GONE
        musicNowPlayingContainer.visibility = View.VISIBLE
        mainHandler.removeCallbacks(musicProgressRunnable)
        mainHandler.post(musicProgressRunnable)
    }

    /** Kecilin lagi ke layar search Musik. Lagu tetap lanjut muter. */
    private fun closeMusicNowPlaying() {
        mainHandler.removeCallbacks(musicProgressRunnable)
        musicNowPlayingContainer.visibility = View.GONE
        musicContainer.visibility = View.VISIBLE
    }

    /** Update seekbar + label waktu di layar Now Playing tiap tick. */
    private fun updateMusicNowPlayingProgress() {
        if (musicNowPlayingContainer.visibility != View.VISIBLE) return
        val controller = mediaController ?: return
        val duration = controller.duration
        if (duration > 0) {
            musicNowPlayingSeekBar.max = (duration / 1000).toInt()
            if (!musicSeekBarDragging) {
                musicNowPlayingSeekBar.progress = (controller.currentPosition / 1000).toInt()
            }
            musicNowPlayingDurationText.text = formatMillis(duration)
        }
        musicNowPlayingPositionText.text = formatMillis(controller.currentPosition)
    }

    /** Pindah ke lagu sebelum/sesudahnya di hasil pencarian terakhir. */
    private fun playAdjacentTrack(delta: Int) {
        if (lastMusicTracks.isEmpty() || currentMusicTrackIndex < 0) return
        val newIndex = currentMusicTrackIndex + delta
        if (newIndex !in lastMusicTracks.indices) return
        playMusicTrack(lastMusicTracks[newIndex])
    }

    private fun formatMillis(millis: Long): String {
        val totalSeconds = (millis / 1000).coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "%d:%02d".format(minutes, seconds)
    }

    /** Toggle play/pause lagu yang lagi aktif di mini player. */
    private fun toggleMusicPlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    private fun loadGameBankThenStart() {
        gameLoading = true
        gameImageLoading.visibility = View.VISIBLE
        gameFeedbackText.text = "Memuat soal..."

        backgroundExecutor.execute {
            val result = TebakGambarRepository.fetch()
            mainHandler.post {
                gameLoading = false
                gameImageLoading.visibility = View.GONE
                result.onSuccess { items ->
                    gameItems = items
                    if (isGameTabActive) nextGameImage(reveal = false)
                }.onFailure {
                    gameFeedbackText.text = "Gagal memuat soal, coba lagi"
                }
            }
        }
    }

    /** Fade halus antar panel TV/Game, biar berasa gonta-ganti tab, bukan lompat kasar. */
    /** Buka/tutup panel filter (grup channel + prev/next + Hemat Data) pas tombol "⋯" diklik. */
    private fun toggleFilterCard() {
        if (filterCard.visibility == View.VISIBLE) {
            filterCard.visibility = View.GONE
        } else {
            filterCard.visibility = View.VISIBLE
        }
    }

    private fun crossFadeSwap(from: View, to: View) {
        from.animate()
            .alpha(0f)
            .setDuration(140)
            .withEndAction {
                from.visibility = View.GONE
                from.alpha = 1f

                to.alpha = 0f
                to.visibility = View.VISIBLE
                to.animate().alpha(1f).setDuration(180).start()
            }
            .start()
    }

    private fun checkGameAnswer() {
        val item = gameCurrentItem ?: return
        val guess = gameAnswerInput.text.toString().trim()
        if (guess.isEmpty()) return

        if (guess.equals(item.answer, ignoreCase = true)) {
            gameCountdown?.cancel()

            gameScore += 1
            gameScoreText.text = "Skor: $gameScore"
            gamePopIn(gameScoreText, fromScale = 1.35f)

            gameFeedbackText.setTextColor(resources.getColor(R.color.live_dot, theme))
            gameFeedbackText.text = "Benar! 🎉 ${item.answer}"
            gamePopIn(gameFeedbackText)
            gamePopIn(gameImageView)

            gameAnswerInput.postDelayed({ nextGameImage(reveal = false) }, 900)
        } else {
            gameFeedbackText.setTextColor(resources.getColor(R.color.accent, theme))
            gameFeedbackText.text = "Belum tepat, coba lagi"
            gameShake(gameAnswerInput)
        }
    }

    private fun gamePopIn(view: View, fromScale: Float = 1.18f) {
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun gameShake(view: View) {
        ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -18f, 18f, -14f, 14f, -6f, 6f, 0f
        ).apply {
            duration = 380
            start()
        }
    }

    private fun nextGameImage(reveal: Boolean) {
        val currentAnswer = gameCurrentItem?.answer
        if (reveal && currentAnswer != null) {
            gameFeedbackText.setTextColor(resources.getColor(R.color.text_secondary, theme))
            gameFeedbackText.text = "Waktu habis! Jawabannya: $currentAnswer"
        } else {
            gameFeedbackText.text = ""
        }

        if (gameItems.isEmpty()) return

        if (gameUsedIndexes.size >= gameItems.size) gameUsedIndexes.clear()
        var index: Int
        do {
            index = gameItems.indices.random()
        } while (index in gameUsedIndexes && gameUsedIndexes.size < gameItems.size)
        gameUsedIndexes.add(index)

        val item = gameItems[index]
        gameCurrentItem = item

        gameImageView.alpha = 0f
        gameImageLoading.visibility = View.VISIBLE
        LogoLoader.load(item.imageUrl, gameImageView)
        gameImageView.postDelayed({
            gameImageLoading.visibility = View.GONE
            gameImageView.animate().alpha(1f).setDuration(220).start()
        }, 150)

        gameAnswerInput.setText("")
        startGameCountdown(GAME_ROUND_MS)
    }

    /** Timer 60 detik per soal. Merah + shake pas sisa waktu tinggal sedikit. */
    private fun startGameCountdown(durationMs: Long) {
        gameCountdown?.cancel()
        gameCountdown = object : CountDownTimer(durationMs, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                gameRemainingMs = millisUntilFinished
                val seconds = (millisUntilFinished / 1000L).toInt() + 1
                gameTimerText.text = "⏱ $seconds"
                val urgent = seconds <= 10
                gameTimerText.setTextColor(
                    resources.getColor(
                        if (urgent) R.color.accent else R.color.text_primary,
                        theme
                    )
                )
                if (urgent && seconds <= 5) gamePopIn(gameTimerText, fromScale = 1.25f)
            }

            override fun onFinish() {
                gameRemainingMs = 0L
                gameTimerText.text = "⏱ 0"
                gameAnswerInput.postDelayed({ nextGameImage(reveal = true) }, 400)
            }
        }.start()
    }

    /** Matiin animasi "LIVE" di semua row yang lagi ke-render, biar gak
     *  jalan sia-sia pas channel list lagi disembunyikan (tab Game / app
     *  di-background). Row yang discroll keluar layar dan di-recycle udah
     *  otomatis ke-handle lewat onViewRecycled() di adapter. */
    private fun pauseChannelListPulses() {
        for (i in 0 until channelList.childCount) {
            val child = channelList.getChildAt(i) ?: continue
            (channelList.getChildViewHolder(child) as? ChannelAdapter.ChannelViewHolder)
                ?.stopLivePulse()
        }
    }

    /** Nyalain lagi — cukup rebind item yang keliatan, gak perlu reload data. */
    private fun resumeChannelListPulses() {
        if (::channelAdapter.isInitialized) channelAdapter.notifyDataSetChanged()
    }

    private fun applyFilter() {
        val search = searchInput.text.toString().trim().lowercase(Locale.getDefault())
        val filtered = allChannels.filter { channel ->
            (currentFilter == "All" || channel.group == currentFilter) &&
                (search.isEmpty() ||
                    channel.name.lowercase(Locale.getDefault()).contains(search) ||
                    channel.group.lowercase(Locale.getDefault()).contains(search))
        }
        // Channel favorite ditaruh paling atas, sisanya urutan seperti biasa.
        val sorted = filtered.sortedByDescending { favorites.contains(it.streamUrl) }
        channelAdapter.submitList(sorted)
    }

    private fun buildPlayer(
        headers: Map<String, String>,
        streamUrl: String = "",
        drmScheme: String? = null,
        drmLicenseKey: String? = null
    ): ExoPlayer {
        // Default nyamar sebagai browser desktop dulu; kalau channel di
        // playlist punya header sendiri (misal lewat #EXTVLCOPT), itu yang
        // menang, dipasang belakangan lewat putAll().
        //
        // KECUALI stream RCTI+ (*-linier.rctiplus.id — RCTI/MNCTV/GTV dst):
        // signed URL (hdnts=...~hmac=...) mereka nolak kalau ada User-Agent
        // custom, jadi buat domain ini User-Agent dibiarin default ExoPlayer,
        // gak dipasangin apa-apa (kecuali channel itu sendiri sudah set UA
        // manual di playlist).
        val isRctiPlus = streamUrl.contains("rctiplus.id", ignoreCase = true)

        val requestHeaders = linkedMapOf<String, String>()
        if (!isRctiPlus) {
            requestHeaders["User-Agent"] = DEFAULT_USER_AGENT
        }
        requestHeaders.putAll(headers)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(12_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(requestHeaders)

        requestHeaders["User-Agent"]?.let { httpFactory.setUserAgent(it) }

        val baseDataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        // setMaxVideoBitrate() di trackSelector (bawah) cuma manjur buat
        // stream adaptive. ThrottlingDataSource nahan byte-nya langsung di
        // level jaringan, jadi Hemat Data beneran ngaruh ke channel
        // non-adaptive (satu kualitas doang) juga — konversi bps -> byte/s.
        val dataSourceFactory = ThrottlingDataSource.Factory(baseDataSourceFactory) {
            if (dataSaverMaxBitrateBps > 0) (dataSaverMaxBitrateBps / 8).toLong() else 0L
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // BUG FIX: channel yang butuh proteksi ClearKey (ditandai lewat
        // "#KODIPROP:" di M3U, lihat M3uParser) dulu diam-diam diabaikan
        // total — parser buang infonya, player gak pernah tau harus
        // decrypt pakai key apa, jadi channel-nya gagal play tanpa
        // keterangan jelas kenapa. Sekarang, kalau channel punya
        // drmScheme=clearkey + drmLicenseKey yang valid, MediaSourceFactory
        // dipasangin DrmSessionManager lokal (LocalMediaDrmCallback) yang
        // "menjawab" permintaan lisensi langsung dari kid/key di
        // playlist — tanpa perlu hit server lisensi manapun, sesuai
        // sifat ClearKey yang memang biasa dipakai offline/inline begini.
        if (ClearKeyUtil.isClearKey(drmScheme) && !drmLicenseKey.isNullOrBlank()) {
            val licenseJson = ClearKeyUtil.buildLicenseJson(drmLicenseKey)
            if (licenseJson != null) {
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .build(LocalMediaDrmCallback(licenseJson.toByteArray(Charsets.UTF_8)))
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(3_000, 15_000, 500, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        // Mode "Hemat Data": batasi bitrate video maksimum sesuai level yang
        // dipilih user (2 Mbps s/d 150 Kbps). trackSelector dipegang di field
        // biar bisa di-ganti levelnya langsung tanpa perlu ganti channel dulu.
        val newTrackSelector = DefaultTrackSelector(this)
        val paramsBuilder = newTrackSelector.buildUponParameters()
        if (dataSaverMaxBitrateBps > 0) {
            paramsBuilder.setMaxVideoBitrate(dataSaverMaxBitrateBps)
        }
        newTrackSelector.setParameters(paramsBuilder)
        trackSelector = newTrackSelector

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setTrackSelector(newTrackSelector)
            .setAudioAttributes(audioAttributes, true)
            .build()
            .also { it.volume = 1f }
    }

    private fun attachPlayerListener(currentPlayer: ExoPlayer) {
        currentPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (isFullscreen) {
                    fullscreenRetryButton.visibility = View.VISIBLE
                } else {
                    retryButton.visibility = View.VISIBLE
                }
                statusText.text = formatPlaybackError(error)
                automaticRetries++

                if (automaticRetries <= 3) {
                    val token = playbackToken
                    val delay = automaticRetries * 1500L
                    mainHandler.postDelayed({
                        if (!isFinishing && !isDestroyed && token == playbackToken) {
                            activeChannel?.let {
                                playChannel(it, isRetry = true, saveAsLast = false)
                            }
                        }
                    }, delay)
                } else {
                    // Udah gagal 3x nyoba ulang channel yang sama — daripada
                    // nyangkut loading terus-terusan, otomatis pindah ke
                    // channel berikutnya di daftar.
                    val token = playbackToken
                    val brokenChannel = activeChannel
                    statusText.text = "LIVE TV • ${brokenChannel?.name.orEmpty()} bermasalah, pindah channel..."
                    mainHandler.postDelayed({
                        if (!isFinishing && !isDestroyed && token == playbackToken) {
                            automaticRetries = 0
                            playAdjacent(1)
                        }
                    }, 1200L)
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> {
                        activeChannel?.let {
                            statusText.text = "LIVE TV • Menghubungkan ${it.name}"
                        }
                    }
                    Player.STATE_READY -> {
                        automaticRetries = 0
                        retryButton.visibility = View.GONE
                        fullscreenRetryButton.visibility = View.GONE
                        val channel = activeChannel
                        val programme = channel?.epgId?.let(::epgNow)
                        statusText.text = if (programme != null) {
                            "LIVE • ${channel?.name.orEmpty()} • ${programme.title}"
                        } else {
                            "LIVE • ${channel?.name.orEmpty()}"
                        }
                        activeChannelText.text = channel?.name.orEmpty()
                    }
                    Player.STATE_ENDED -> statusText.text = "LIVE TV • siaran selesai"
                    Player.STATE_IDLE -> if (activeChannel != null) statusText.text = "LIVE TV • siap memutar"
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (currentPlayer !== player) return
                if (isPlaying) {
                    activeChannel?.id?.let { viewerPresence.setWatching(it, true) }
                } else {
                    viewerPresence.setWatching(null, false)
                }

                // Layar cuma dipaksa nyala pas video BENERAN lagi diputar
                // (bukan sepanjang app dibuka) — hemat baterai pas cuma
                // buka daftar channel, baca, atau lagi main game.
                if (isPlaying) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        })
    }

    private fun playChannel(
        channel: Channel,
        isRetry: Boolean,
        saveAsLast: Boolean
    ) {
        viewerPresence.setWatching(null, false)
        activeChannel = channel
        if (!isRetry) automaticRetries = 0
        if (saveAsLast) saveHistory(channel)
        if (::channelAdapter.isInitialized) channelAdapter.notifyDataSetChanged()

        remotePlayerConfigDirty = false
        playbackToken++
        val requestToken = playbackToken
        statusText.text = "LIVE TV • ${channel.name}"
        activeChannelText.text = channel.name
        retryButton.visibility = View.GONE
        startupOverlay.visibility = View.GONE
        playerContainer.visibility = View.VISIBLE

        val old = player
        player = null
        runCatching { old?.stop() }
        runCatching { old?.clearMediaItems() }
        runCatching { old?.release() }
        playerView.player = null

        val newPlayer = buildPlayer(
            channel.headers,
            channel.streamUrl,
            channel.drmScheme,
            channel.drmLicenseKey
        )
        player = newPlayer
        playerView.player = newPlayer
        // BUG FIX: sebelumnya baris ini hardcode ke RESIZE_MODE_FIT, jadi
        // tiap kali ganti channel (tombol prev/next, retry, atau tap
        // channel lain) pas lagi fullscreen, tampilan video ikut kereset
        // ke mode kecil/letterbox walaupun status fullscreen (FILL) belum
        // berubah. Sekarang ngikutin status fullscreen yang sebenarnya,
        // sama seperti di reconnectActiveChannel().
        playerView.resizeMode = if (isFullscreen) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        attachPlayerListener(newPlayer)

        val cleanUrl = channel.streamUrl.substringBefore('#')
        val path = cleanUrl.substringBefore('?').lowercase(Locale.getDefault())
        val item = MediaItem.Builder().setUri(channel.streamUrl).apply {
            when {
                path.endsWith(".mpd") -> {
                    setMimeType(MimeTypes.APPLICATION_MPD)
                    setLiveConfiguration(
                        MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(3000)
                            .setMinPlaybackSpeed(0.97f)
                            .setMaxPlaybackSpeed(1.03f)
                            .build()
                    )
                }
                path.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }.build()

        if (requestToken != playbackToken) {
            runCatching { newPlayer.release() }
            return
        }

        newPlayer.setMediaItem(item)
        newPlayer.prepare()
        newPlayer.playWhenReady = true
        newPlayer.play()
    }

    private fun playAdjacent(offset: Int) {
        val current = activeChannel ?: return
        val items = channelAdapter.currentItems()
        if (items.isEmpty()) return
        val index = items.indexOfFirst { it.streamUrl == current.streamUrl }
        val safeIndex = if (index == -1) 0 else index
        // Wraparound: kalau channel yang error kebetulan paling akhir/awal
        // di list, tetap lompat ke ujung satunya, bukan diam gak ngapa-ngapain.
        val nextIndex = ((safeIndex + offset) % items.size + items.size) % items.size
        playChannel(items[nextIndex], isRetry = false, saveAsLast = true)
    }

    private fun toggleFavorite(channel: Channel) {
        if (!favorites.add(channel.streamUrl)) favorites.remove(channel.streamUrl)
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
        applyFilter()
    }

    private fun saveHistory(channel: Channel) {
        history.remove(channel.streamUrl)
        history.addFirst(channel.streamUrl)
        while (history.size > 30) history.removeLast()
        prefs.edit()
            .putString(KEY_HISTORY, history.joinToString("\n"))
            .putString(KEY_LAST_CHANNEL, channel.streamUrl)
            .apply()
    }

    private fun restoreState() {
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.forEach(favorites::add)
        prefs.getString(KEY_HISTORY, null)
            ?.lineSequence()
            ?.filter { it.isNotBlank() }
            ?.forEach(history::addLast)
        pendingLastChannelUrl = prefs.getString(KEY_LAST_CHANNEL, null)

        dataSaverMaxBitrateBps = prefs.getInt(KEY_DATA_SAVER, 0)
        updateDataSaverLabel()
    }

    /** Terapkan batas bitrate video (mode Hemat Data, level dipilih user) ke player yang lagi jalan. */
    private fun applyDataSaverToTrackSelector() {
        val selector = trackSelector ?: return
        val builder = selector.buildUponParameters()
        if (dataSaverMaxBitrateBps > 0) {
            builder.setMaxVideoBitrate(dataSaverMaxBitrateBps)
        } else {
            builder.setMaxVideoBitrate(Int.MAX_VALUE)
        }
        selector.setParameters(builder)
    }

    /** Tampilkan menu pilihan level batas bitrate, dari "Nonaktif" sampe yang paling kecil (Kbps). */
    private fun showDataSaverMenu() {
        val popup = android.widget.PopupMenu(this, dataSaverValueText)
        DATA_SAVER_LEVELS.forEachIndexed { index, level ->
            popup.menu.add(0, index, index, level.label)
        }
        popup.setOnMenuItemClickListener { item ->
            val level = DATA_SAVER_LEVELS.getOrNull(item.itemId) ?: return@setOnMenuItemClickListener false
            dataSaverMaxBitrateBps = level.bitrateBps
            prefs.edit().putInt(KEY_DATA_SAVER, level.bitrateBps).apply()
            updateDataSaverLabel()
            applyDataSaverToTrackSelector()
            true
        }
        popup.show()
    }

    /** Update teks label sesuai level Hemat Data yang lagi aktif. */
    private fun updateDataSaverLabel() {
        val level = DATA_SAVER_LEVELS.firstOrNull { it.bitrateBps == dataSaverMaxBitrateBps }
            ?: DATA_SAVER_LEVELS.first()
        dataSaverValueText.text = "${level.label} ▾"
    }

    private fun toggleFullscreen() {
        if (isFullscreen) exitFullscreen() else enterFullscreen()
    }

    private fun enterFullscreen() {
        isFullscreen = true
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        topBar.visibility = View.GONE
        statusBar.visibility = View.GONE
        searchInput.visibility = View.GONE
        groupSpinner.visibility = View.GONE
        previousButton.visibility = View.GONE
        nextButton.visibility = View.GONE
        retryVisibleBeforeFullscreen =
            retryButton.visibility == View.VISIBLE
        retryButton.visibility = View.GONE
        fullscreenRetryButton.visibility = View.GONE
        channelList.visibility = View.GONE
        startupOverlay.visibility = View.GONE
        bottomNavBar.visibility = View.GONE
        bottomNavDivider.visibility = View.GONE

        playerContainer.visibility = View.VISIBLE
        playerContainer.useFullHeight = true
        playerContainer.layoutParams = playerContainer.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
        playerContainer.requestLayout()
        // Fullscreen: isi penuh layar kanan-kiri-atas-bawah (stretch),
        // beda dengan mode normal yang pakai FIT (letterbox, jaga rasio asli).
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL

        window.insetsController?.let { controller ->
            controller.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun exitFullscreen() {
        isFullscreen = false
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        topBar.visibility = View.VISIBLE
        statusBar.visibility = View.VISIBLE
        searchInput.visibility = View.VISIBLE
        groupSpinner.visibility = View.VISIBLE
        previousButton.visibility = View.VISIBLE
        nextButton.visibility = View.VISIBLE
        channelList.visibility = View.VISIBLE
        bottomNavBar.visibility = View.VISIBLE
        bottomNavDivider.visibility = View.VISIBLE
        retryButton.visibility =
            if (retryVisibleBeforeFullscreen ||
                fullscreenRetryButton.visibility == View.VISIBLE) {
                View.VISIBLE
            } else {
                View.GONE
            }
        fullscreenRetryButton.visibility = View.GONE

        window.insetsController?.show(
            WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
        )

        playerContainer.useFullHeight = false
        playerContainer.layoutParams = playerContainer.layoutParams.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        playerContainer.requestLayout()
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun loadCachedEpgOnly() {
        // Kept as a compatibility method; cached EPG is parsed in background at startup.
    }

    private fun epgNow(channelId: String): com.bittv.iptv.util.EpgProgramme? {
        val now = System.currentTimeMillis()
        return epgProgrammes.firstOrNull { it.channelId == channelId && now >= it.start && now < it.end }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST
            )
        }
    }

    /** Minta izin lokasi buat fitur EWS (peringatan gempa/cuaca/gunung berapi
     *  terdekat). Kalau izin udah ada dari sebelumnya, langsung refresh lokasi
     *  & jadwalin worker EWS-nya tanpa nampilin dialog lagi. */
    private fun requestLocationPermissionIfNeeded() {
        if (EwsLocationManager.hasPermission(this)) {
            startEwsLocationTracking()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    /** Ambil & simpan lokasi terkini sekali di awal (foreground only, sesuai
     *  desain EwsLocationManager), lalu jadwalin worker EWS periodik supaya
     *  notifikasi bahaya terdekat beneran jalan di background. */
    private fun startEwsLocationTracking() {
        CoroutineScope(Dispatchers.IO).launch {
            EwsLocationManager.refreshAndSave(applicationContext)
        }
        EwsUpdateWorker.schedule(applicationContext)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Tetap jadwalin worker walau user nolak izin; EwsRepository bakal
            // balik "NoLocation" dengan aman sampai user kasih izin lewat
            // Setelan HP nanti (gak bikin crash atau notif spam).
            startEwsLocationTracking()
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST &&
            Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is now granted: ensure the current remote state was
            // baselined before joining FCM, then catch up any missed change.
            if (RemotePushManager.isBaselineReady(this)) {
                RemotePushManager.ensureTopicSubscription(this)
                FreeNotification.showPending(this)
                FreeNotificationWorker.scheduleCatchUp(this)
                FreeNotificationWorker.schedule(this)
                EwsUpdateWorker.enqueueNow(this)
            }
        }
    }

    private fun formatPlaybackError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "LIVE TV • koneksi gagal"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "LIVE TV • koneksi timeout"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "LIVE TV • stream tidak ditemukan"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "LIVE TV • manifest tidak valid"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "LIVE TV • server menolak koneksi"
        else -> "LIVE TV • ${error.message ?: error.errorCodeName}"
    }

    override fun onResume() {
        super.onResume()
        // Android otomatis munculin lagi status bar/nav bar tiap Activity balik
        // ke foreground, walaupun tampilan masih dalam mode fullscreen. Ini yang
        // bikin "keluar-masuk app jadi gak full lagi" — jadi harus disembunyikan
        // ulang manual di sini kalau lagi fullscreen.
        if (isFullscreen) {
            window.insetsController?.let { controller ->
                controller.hide(
                    WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
                )
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Jaring pengaman tambahan: beberapa OEM (Xiaomi/Oppo/dll) baru
        // benar-benar menerapkan ulang system bar pas window dapat focus lagi,
        // bukan pas onResume.
        if (hasFocus && isFullscreen) {
            window.insetsController?.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
        }
    }

    private fun applyLatestRemoteSnapshot(
        snapshot: com.bittv.iptv.data.PlaylistSnapshot,
        forceReconnect: Boolean
    ) {
        if (!startupComplete || isFinishing || isDestroyed) return
        backgroundExecutor.execute {
            val parsed = runCatching {
                M3uParser.parse(snapshot.content, config.playlistUrl, emptyMap())
            }.getOrDefault(emptyList())
            if (parsed.isEmpty() || isFinishing || isDestroyed) return@execute
            mainHandler.post {
                if (isFinishing || isDestroyed || !startupComplete || !activityStarted) return@post
                val playerConfigChanged = applyParsedChannels(snapshot.content, parsed)
                if (forceReconnect && !playerConfigChanged && !isGameTabActive && activeChannel != null) {
                    reconnectActiveChannel()
                }
                statusText.text = "LIVE TV • Channel diperbarui otomatis"
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun registerRemotePlaylistReceiver() {
        val filter = IntentFilter(PlaylistUpdateWorker.ACTION_REMOTE_PLAYLIST_UPDATED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(remotePlaylistReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(remotePlaylistReceiver, filter)
        }
    }

    @Suppress("DEPRECATION")
    private fun unregisterRemotePlaylistReceiver() {
        runCatching { unregisterReceiver(remotePlaylistReceiver) }
    }

    override fun onStart() {
        super.onStart()
        activityStarted = true
        if (RemotePushManager.isBaselineReady(this)) {
            RemotePushManager.ensureTopicSubscription(this)
        }

        if (startupComplete && !isGameTabActive) {
            val pending = PlaylistRepository.consumeLatestSnapshot()
            if (pending != null) {
                applyLatestRemoteSnapshot(pending, forceReconnect = true)
            } else if (config.autoUpdateEnabled) {
                // Immediate foreground catch-up: do not make the user wait
                // for the first 60-second foreground polling tick.
                checkRemoteInBackground(showPlaylistNotification = false)
            }
        }

        if (config.autoUpdateEnabled) {
            mainHandler.removeCallbacks(foregroundCheckRunnable)
            mainHandler.postDelayed(
                foregroundCheckRunnable,
                config.foregroundCheckSeconds * 1000L
            )
        }

        /*
         * Ketika Activity kembali dari background, jangan hanya memanggil
         * play(). Live stream bisa kehilangan koneksi setelah aplikasi
         * ditinggal beberapa saat.
         *
         * Kalau channel terakhir sudah tersedia, prepare ulang stream.
         * Tapi JANGAN kalau lagi di tab Game — video harus tetap diam.
         */
        if (startupComplete && !isGameTabActive) {
            val channel = activeChannel

            if (channel != null) {
                mainHandler.postDelayed({
                    if (!isFinishing && !isDestroyed && startupComplete && !isGameTabActive) {
                        reconnectActiveChannel()
                    }
                }, 150L)
            }
        }

        // Lanjutin timer soal Tebak Gambar kalau app balik dari background
        // pas lagi di tab Game.
        if (isGameTabActive && gameCurrentItem != null && gameRemainingMs > 0) {
            startGameCountdown(gameRemainingMs)
        }

        if (!isGameTabActive) resumeChannelListPulses()
    }

    override fun onStop() {
        activityStarted = false
        mainHandler.removeCallbacks(foregroundCheckRunnable)
        gameCountdown?.cancel()
        pauseChannelListPulses()

        /*
         * Jangan release player di sini.
         * Activity hanya kehilangan foreground sementara.
         * Player akan di-reconnect saat onStart().
         */
        player?.playWhenReady = false
        player?.pause()
        viewerPresence.setWatching(null, false)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        super.onStop()
    }

    private fun reconnectActiveChannel() {
        val channel = activeChannel ?: return

        /*
         * Selalu buat ulang player ketika kembali dari background.
         * Ini menghindari kondisi ExoPlayer masih hidup tetapi HTTP
         * connection/manifest live stream sudah stale.
         */
        player?.release()
        player = buildPlayer(
            channel.headers,
            channel.streamUrl,
            channel.drmScheme,
            channel.drmLicenseKey
        )
        playerView.player = player
        // BUG FIX: sebelumnya baris ini hardcode ke RESIZE_MODE_FIT, jadi
        // tiap kali app balik dari background (yang otomatis rebuild player
        // di sini) tampilan video ikut kereset ke mode kecil/letterbox
        // WALAUPUN lagi fullscreen (RESIZE_MODE_FILL). Itu penyebab "keluar
        // masuk app tetep kecil, gak full lagi". Sekarang ngikutin status
        // fullscreen yang sebenarnya, bukan dihardcode.
        playerView.resizeMode = if (isFullscreen) {
            AspectRatioFrameLayout.RESIZE_MODE_FILL
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

        player?.let { attachPlayerListener(it) }

        val cleanUrl = channel.streamUrl.substringBefore("#")
        val path = cleanUrl
            .substringBefore("?")
            .lowercase(Locale.getDefault())

        val itemBuilder = MediaItem.Builder()
            .setUri(channel.streamUrl)

        when {
            path.endsWith(".mpd") -> {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                itemBuilder.setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(3_000)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.03f)
                        .build()
                )
            }

            path.endsWith(".m3u8") -> {
                itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }

        statusText.text = "LIVE TV • Menghubungkan ${channel.name}"
        retryButton.visibility = View.GONE

        player?.setMediaItem(itemBuilder.build())
        player?.prepare()
        player?.playWhenReady = true
        player?.play()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        unregisterRemotePlaylistReceiver()
        backgroundExecutor.shutdownNow()
        val old = player
        player = null
        runCatching { old?.stop() }
        runCatching { old?.release() }
        playerView.player = null
        if (::viewerPresence.isInitialized) viewerPresence.stop()
        epgRepository.shutdown()

        // Cuma lepas KONEKSI controller-nya, bukan matiin service musiknya —
        // MusicPlayerService tetap jalan sendiri di background kalau lagi
        // playing (persis kayak Spotify pas app-nya ditutup).
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        mediaController = null

        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4001
        private const val LOCATION_PERMISSION_REQUEST = 4002
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val KEY_HISTORY = "history"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_WAS_FULLSCREEN = "was_fullscreen"
        // Sekarang nyimpen level bitrate terpilih (bps), bukan cuma on/off lagi.
        private const val KEY_DATA_SAVER = "data_saver_max_bitrate_bps"

        /** Satu opsi level batas bitrate video buat mode "Hemat Data". */
        private data class DataSaverLevel(val label: String, val bitrateBps: Int)

        // Daftar pilihan batas bitrate, dari nonaktif sampe yang paling kecil (Kbps).
        // bitrateBps = 0 artinya nonaktif (gak ada batas / kualitas tertinggi).
        private val DATA_SAVER_LEVELS = listOf(
            DataSaverLevel("Nonaktif", 0),
            DataSaverLevel("2 Mbps", 2_000_000),
            DataSaverLevel("1 Mbps", 1_000_000),
            DataSaverLevel("700 Kbps", 700_000),
            DataSaverLevel("500 Kbps", 500_000),
            DataSaverLevel("300 Kbps", 300_000),
            DataSaverLevel("150 Kbps", 150_000)
        )

        // Banyak server IPTV/CDN nge-block request yang bukan dari browser
        // (User-Agent kosong/khas library kayak "ExoPlayerLib" gampang kena
        // filter anti-leech). Dengan nyamar sebagai Chrome desktop, request
        // dari app jadi diterima server persis kayak dibuka lewat browser.
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // Waktu per soal Tebak Gambar: 60 detik.
        private const val GAME_ROUND_MS = 60_000L
    }
}

class SimpleTextWatcher(
    private val onChange: () -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) { onChange() }
}
