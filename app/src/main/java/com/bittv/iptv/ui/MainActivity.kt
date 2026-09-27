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
import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.ProgressBar
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
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
import com.bittv.iptv.BuildConfig
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
import com.bittv.iptv.util.MabarRepository
import com.bittv.iptv.util.RpgGameStore
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
import kotlin.math.roundToInt
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var config: AppConfig
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var epgRepository: EpgRepository
    private lateinit var viewerPresence: ViewerPresenceManager
    private lateinit var rpgStore: RpgGameStore
    private lateinit var mabarRepository: MabarRepository

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

    // Game Hub profile + RPG/Mabar state. Kept separate from the existing TV/player
    // state so a game failure can never take down playback.
    private var playerName = ""
    private var mabarDialog: Dialog? = null
    private var currentMabarRoom: String? = null
    private var mabarRewardedRooms = mutableSetOf<String>()
    private lateinit var gameProfileSummary: TextView

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
        rpgStore = RpgGameStore(this)
        mabarRepository = MabarRepository(this)

        setContentView(R.layout.activity_main)
        bindViews()
        applyEdgeToEdgeInsets()
        restoreState()
        configureBackHandling()
        configureUi()
        buildExtendedGameHub()

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

        mainHandler.post {
            showProfileSetupIfNeeded()
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

    // ================= Extended Game Hub =================

    private fun buildExtendedGameHub() {
        val scroll = gameMenuContainer as? ScrollView ?: return
        val menu = scroll.getChildAt(0) as? LinearLayout ?: return
        if (menu.findViewWithTag<View>("extended-rpg") != null) return

        val profileCard = createGameCard(
            tag = "extended-profile",
            icon = "👤",
            title = "Profil Pemain",
            description = "Nama ini dipakai di RPG dan Mabar realtime"
        ) {
            showSettingsDialog()
        }
        gameProfileSummary = profileCard.findViewWithTag("card-description") as TextView
        profileCard.setOnClickListener { showSettingsDialog() }

        val rpgCard = createGameCard(
            tag = "extended-rpg",
            icon = "⚔️",
            title = "BITTV RPG",
            description = "Level up, monster, dungeon, loot, class dan quest"
        ) { showRpgGameDialog() }

        val mabarCard = createGameCard(
            tag = "extended-mabar",
            icon = "🛡️",
            title = "Mabar Raid",
            description = "Bikin/join room sampai 4 player, lawan boss bareng"
        ) { showMabarDialog() }

        val dailyCard = createGameCard(
            tag = "extended-daily",
            icon = "🎁",
            title = "Daily Claim",
            description = "Ambil XP, gold, dan potion sekali setiap hari"
        ) { claimDailyFromHub() }

        val settingsCard = createGameCard(
            tag = "extended-settings",
            icon = "⚙️",
            title = "Settings",
            description = "Nama, Hemat Data, reset progres RPG, dan info app"
        ) { showSettingsDialog() }

        val insertIndex = 3.coerceAtMost(menu.childCount)
        menu.addView(profileCard, insertIndex)
        menu.addView(rpgCard, (insertIndex + 1).coerceAtMost(menu.childCount))
        menu.addView(mabarCard, (insertIndex + 2).coerceAtMost(menu.childCount))
        menu.addView(dailyCard, (insertIndex + 3).coerceAtMost(menu.childCount))
        menu.addView(settingsCard, (insertIndex + 4).coerceAtMost(menu.childCount))
        updateGameHubProfileSummary()
    }

    private fun createGameCard(
        tag: String,
        icon: String,
        title: String,
        description: String,
        action: () -> Unit
    ): LinearLayout {
        val card = LinearLayout(this).apply {
            this.tag = tag
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 18f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12) }
        }

        val iconView = TextView(this).apply {
            text = icon
            textSize = 28f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                rightMargin = dp(12)
            }
        }
        card.addView(iconView)

        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        copy.addView(TextView(this).apply {
            text = title
            textSize = 15f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val desc = TextView(this).apply {
            tag = "card-description"
            text = description
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(3), 0, 0)
        }
        copy.addView(desc)
        card.addView(copy)
        card.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.game_accent_light))
            setPadding(dp(8), 0, 0, 0)
        })
        return card
    }

    private fun updateGameHubProfileSummary() {
        if (!::gameProfileSummary.isInitialized) return
        val state = rpgStore.load()
        gameProfileSummary.text = "${playerName.ifBlank { "Player" }} • Lv.${state.level} • ${state.gold} gold"
    }

    private fun showProfileSetupIfNeeded() {
        if (playerName.isNotBlank()) {
            updateGameHubProfileSummary()
            syncRpgProfile()
            return
        }

        val input = EditText(this).apply {
            hint = "Contoh: Adit"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(24))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_tertiary))
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedDrawable(R.color.bg_root_soft, R.color.surface_stroke, 1f, 14f)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), dp(4))
        }
        container.addView(TextView(this).apply {
            text = "Buat profil dulu"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        container.addView(TextView(this).apply {
            text = "Nama ini tampil di Game Hub dan Mabar. Cukup sekali di perangkat ini."
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, dp(6), 0, dp(16))
        })
        container.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        val dialog = AlertDialog.Builder(this)
            .setView(container)
            .setPositiveButton("Lanjut", null)
            .setCancelable(false)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(ContextCompat.getColor(this, R.color.bg_root)))
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this, R.color.brand_blue_light)
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clean = input.text.toString().trim().replace("\\s+".toRegex(), " ")
                when {
                    clean.length !in 2..24 -> input.error = "Nama 2–24 karakter"
                    clean == "Player" -> input.error = "Pakai nama yang berbeda"
                    else -> {
                        playerName = clean
                        prefs.edit().putString(KEY_PLAYER_NAME, playerName).apply()
                        updateGameHubProfileSummary()
                        syncRpgProfile()
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun claimDailyFromHub() {
        var state = rpgStore.load()
        val claimed = rpgStore.claimDaily(state)
        if (claimed == null) {
            showGameToast("Daily sudah di-claim hari ini. Balik lagi besok.")
            return
        }
        state = claimed
        updateGameHubProfileSummary()
        syncRpgProfile()
        showGameToast("Daily masuk: +${RpgGameStore.DAILY_GOLD} gold, +${RpgGameStore.DAILY_XP} XP, +1 potion")
    }

    private fun showSettingsDialog() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), 0, dp(4), 0)
        }
        val nameInput = EditText(this).apply {
            setText(playerName)
            hint = "Nama pemain"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(24))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_tertiary))
            background = roundedDrawable(R.color.bg_root_soft, R.color.surface_stroke, 1f, 14f)
            setPadding(dp(14), 0, dp(14), 0)
        }
        box.addView(TextView(this).apply {
            text = "Nama pemain"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
        })
        box.addView(nameInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(6)
            bottomMargin = dp(14)
        })

        val saverButton = Button(this).apply {
            text = "Hemat Data: ${dataSaverValueText.text}"
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 14f)
            setOnClickListener {
                showDataSaverMenuFor(this)
                text = "Hemat Data: ${dataSaverValueText.text}"
            }
        }
        box.addView(saverButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            bottomMargin = dp(10)
        })

        val resetButton = Button(this).apply {
            text = "Reset progres RPG"
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_light))
            background = roundedDrawable(R.color.accent_soft, R.color.accent_dark, 1f, 14f)
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Reset progres?")
                    .setMessage("Level, gold, potion, HP, stamina, dan statistik RPG akan kembali ke awal.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Reset") { _, _ ->
                        rpgStore.reset()
                        updateGameHubProfileSummary()
                        syncRpgProfile()
                        showGameToast("Progres RPG direset.")
                    }
                    .show()
            }
        }
        box.addView(resetButton)

        box.addView(TextView(this).apply {
            text = "BITTV ${BuildConfig.VERSION_NAME}\nTV dan Game berjalan terpisah supaya playback tetap stabil."
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, dp(14), 0, 0)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("⚙️ Settings")
            .setView(box)
            .setNegativeButton("Tutup", null)
            .setPositiveButton("Simpan", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(
                ContextCompat.getColor(this, R.color.brand_blue_light)
            )
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val clean = nameInput.text.toString().trim().replace("\\s+".toRegex(), " ")
                if (clean.length !in 2..24) {
                    nameInput.error = "Nama 2–24 karakter"
                    return@setOnClickListener
                }
                playerName = clean
                prefs.edit().putString(KEY_PLAYER_NAME, playerName).apply()
                updateGameHubProfileSummary()
                syncRpgProfile()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun showRpgGameDialog() {
        var state = rpgStore.load()
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg_root))
            clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(20))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), 0)
        }
        scroll.addView(panel)
        dialog.setContentView(scroll)

        val titleRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "⚔️ BITTV RPG"
            textSize = 22f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleRow.addView(title)
        val close = Button(this).apply {
            text = "Tutup"
            isAllCaps = false
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 14f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        titleRow.addView(close, LinearLayout.LayoutParams(dp(80), dp(44)))
        panel.addView(titleRow)

        val profile = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(dp(2), dp(5), dp(2), 0)
        }
        panel.addView(profile)

        val statRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        val levelText = rpgStatChip(statRow, "LEVEL")
        val goldText = rpgStatChip(statRow, "GOLD")
        val staminaText = rpgStatChip(statRow, "ENERGY")
        val hpText = rpgStatChip(statRow, "HP")
        panel.addView(statRow)

        val xpBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.game_accent_light)
            )
            background = roundedDrawable(R.color.bg_root_soft, R.color.surface_stroke, 1f, 8f)
        }
        panel.addView(xpBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply {
            topMargin = dp(10)
        })

        val classButton = Button(this).apply {
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.game_accent_dark, R.color.game_accent, 1f, 14f)
        }
        panel.addView(classButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(10)
        })

        val enemyCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 18f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        val enemyNameText = TextView(this).apply {
            textSize = 18f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val enemyHpText = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(0, dp(3), 0, dp(8))
        }
        val enemyBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.accent)
            )
        }
        enemyCard.addView(enemyNameText)
        enemyCard.addView(enemyHpText)
        enemyCard.addView(enemyBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)))
        panel.addView(enemyCard, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })

        var enemyName = "Tidak ada monster"
        var enemyHp = 0
        var enemyMaxHp = 0
        var enemyIsBoss = false
        val logText = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            background = roundedDrawable(R.color.bg_root_soft, R.color.divider, 1f, 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            text = "Pilih Jelajah untuk menemukan monster."
        }
        panel.addView(logText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(10)
        })

        val actionRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val attackButton = rpgActionButton("⚔️ Serang", R.color.game_accent)
        val skillButton = rpgActionButton("✨ Skill", R.color.game_accent_dark)
        actionRow1.addView(attackButton, weightParams())
        actionRow1.addView(skillButton, weightParams(dp(8)))
        panel.addView(actionRow1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(12)
        })

        val actionRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val exploreButton = rpgActionButton("🗺️ Jelajah", R.color.surface_elevated)
        val dungeonButton = rpgActionButton("🏰 Dungeon", R.color.surface_elevated)
        actionRow2.addView(exploreButton, weightParams())
        actionRow2.addView(dungeonButton, weightParams(dp(8)))
        panel.addView(actionRow2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        val actionRow3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val potionButton = rpgActionButton("🧪 Potion", R.color.brand_blue_dark)
        val healButton = rpgActionButton("💚 Heal 50G", R.color.brand_blue_dark)
        val questButton = rpgActionButton("📜 Quest", R.color.surface_elevated)
        actionRow3.addView(potionButton, weightParams())
        actionRow3.addView(healButton, weightParams(dp(6)))
        actionRow3.addView(questButton, weightParams(dp(6)))
        panel.addView(actionRow3, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(8)
        })

        val dailyButton = Button(this).apply {
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.accent_soft, R.color.accent_dark, 1f, 14f)
        }
        panel.addView(dailyButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            topMargin = dp(10)
        })

        close.setOnClickListener { dialog.dismiss() }

        fun refresh() {
            state = rpgStore.normalize(state)
            val xpNeed = rpgStore.xpToNext(state.level)
            val classLabel = if (state.classId.isBlank()) "Belum pilih class" else
                "${rpgStore.classEmoji(state.classId)} ${rpgStore.className(state.classId)}"
            profile.text = "$playerName • $classLabel"
            levelText.text = "Lv.${state.level}"
            goldText.text = "${state.gold}G"
            staminaText.text = "${state.stamina}/${state.maxStamina}"
            hpText.text = "${state.hp}/${state.maxHp}"
            xpBar.progress = ((state.xp * 100) / xpNeed.coerceAtLeast(1)).coerceIn(0, 100)
            classButton.text = "Class: $classLabel • ${state.xp}/${xpNeed} XP"
            enemyNameText.text = enemyName
            enemyHpText.text = if (enemyHp > 0) "HP $enemyHp / $enemyMaxHp" else "Aman dulu"
            enemyBar.max = enemyMaxHp.coerceAtLeast(1)
            enemyBar.progress = enemyHp.coerceAtLeast(0)
            dailyButton.text = if (state.dailyClaimDate == java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date())) {
                "✅ Daily sudah di-claim"
            } else "🎁 Claim Daily • +100G +50XP +1 Potion"
            val enabled = state.classId.isNotBlank()
            attackButton.isEnabled = enabled && enemyHp > 0
            skillButton.isEnabled = enabled && enemyHp > 0
            exploreButton.isEnabled = enabled
            dungeonButton.isEnabled = enabled
            potionButton.isEnabled = enabled && state.potions > 0 && state.hp < state.maxHp
            healButton.isEnabled = enabled && state.hp < state.maxHp && state.gold >= 50
            attackButton.alpha = if (attackButton.isEnabled) 1f else 0.5f
            skillButton.alpha = if (skillButton.isEnabled) 1f else 0.5f
            exploreButton.alpha = if (exploreButton.isEnabled) 1f else 0.5f
            dungeonButton.alpha = if (dungeonButton.isEnabled) 1f else 0.5f
            potionButton.alpha = if (potionButton.isEnabled) 1f else 0.5f
            healButton.alpha = if (healButton.isEnabled) 1f else 0.5f
            updateGameHubProfileSummary()
        }

        fun chooseClass() {
            val labels = arrayOf(
                "⚔️ Warrior — HP tebal, damage stabil",
                "🧙 Mage — skill paling sakit, energi lebih boros",
                "🏹 Ranger — peluang critical lebih tinggi"
            )
            val ids = arrayOf(RpgGameStore.CLASS_WARRIOR, RpgGameStore.CLASS_MAGE, RpgGameStore.CLASS_RANGER)
            AlertDialog.Builder(this)
                .setTitle("Pilih class")
                .setItems(labels) { _, which ->
                    state = rpgStore.setClass(state, ids[which])
                    logText.text = "Class ${rpgStore.className(ids[which])} dipilih. Gas jelajah."
                    syncRpgProfile(state)
                    refresh()
                }
                .show()
        }

        fun ensureEnemy(boss: Boolean) {
            if (enemyHp > 0) return
            enemyIsBoss = boss
            enemyName = if (boss) "👑 Abyss Warden" else listOf("👹 Goblin", "🐺 Dire Wolf", "🧟 Shadow Ghoul", "🐉 Mini Dragon").random()
            enemyMaxHp = if (boss) 240 + state.level * 45 else 65 + state.level * 18 + (0..25).random()
            enemyHp = enemyMaxHp
            logText.text = if (boss) "Dungeon boss muncul! Hajar bareng di solo mode." else "$enemyName muncul di depanmu."
            refresh()
        }

        fun playerDamage(skill: Boolean): Int {
            val base = when (state.classId) {
                RpgGameStore.CLASS_MAGE -> if (skill) 48 else 22
                RpgGameStore.CLASS_RANGER -> if (skill) 42 else 24
                else -> if (skill) 40 else 28
            }
            val scale = state.level * 4
            val crit = state.classId == RpgGameStore.CLASS_RANGER && (0..99).random() < 28
            return ((base + scale) * if (crit) 2 else 1) + (0..10).random()
        }

        fun performAttack(skill: Boolean) {
            if (enemyHp <= 0) {
                logText.text = "Nggak ada target. Jelajah atau masuk Dungeon dulu."
                return
            }
            val cost = if (skill) 2 else 1
            val nextState = rpgStore.spendStamina(state, cost)
            if (nextState == null) {
                logText.text = "Stamina habis. Ambil waktu atau pakai Jelajah dengan sisa energi."
                return
            }
            state = nextState
            val damage = playerDamage(skill)
            enemyHp = (enemyHp - damage).coerceAtLeast(0)
            val actionName = if (skill) "Skill" else "Serang"
            if (enemyHp <= 0) {
                val xpGain = if (enemyIsBoss) 120 else 30 + state.level * 3
                val goldGain = if (enemyIsBoss) 180 else 35 + state.level * 5
                val beforeLevel = state.level
                state = rpgStore.addRewards(state, xpGain, goldGain)
                state = rpgStore.markWin(state)
                if ((0..99).random() < 24) state = rpgStore.save(state.copy(potions = state.potions + 1))
                logText.text = "$actionName kena $damage. $enemyName tumbang • +$xpGain XP • +$goldGainG${if (state.level > beforeLevel) " • LEVEL UP!" else ""}"
                enemyName = "Tidak ada monster"
                enemyHp = 0
                enemyMaxHp = 0
                enemyIsBoss = false
            } else {
                val incoming = (5..(11 + state.level.coerceAtMost(15))).random()
                state = rpgStore.save(state.copy(hp = (state.hp - incoming).coerceAtLeast(1)))
                logText.text = "$actionName menghasilkan $damage damage. Musuh balas $incoming damage."
            }
            syncRpgProfile(state)
            refresh()
        }

        attackButton.setOnClickListener { performAttack(false) }
        skillButton.setOnClickListener { performAttack(true) }
        exploreButton.setOnClickListener {
            val spent = rpgStore.spendStamina(state, 1)
            if (spent == null) {
                logText.text = "Stamina habis. Gunakan Heal atau tunggu regenerasi saat kamu buka app lagi."
                return@setOnClickListener
            }
            state = rpgStore.markExplore(spent)
            when ((0..99).random()) {
                in 0..56 -> ensureEnemy(false)
                in 57..82 -> {
                    val gold = (15..60).random() + state.level * 3
                    val xp = (10..25).random()
                    state = rpgStore.addRewards(state, xp, gold)
                    logText.text = "Jelajah aman. Kamu menemukan $gold gold dan $xp XP."
                    syncRpgProfile(state)
                    refresh()
                }
                else -> {
                    state = rpgStore.save(state.copy(potions = state.potions + 1))
                    logText.text = "Kamu menemukan potion langka. +1 potion."
                    syncRpgProfile(state)
                    refresh()
                }
            }
        }
        dungeonButton.setOnClickListener {
            val spent = rpgStore.spendStamina(state, 3)
            if (spent == null) {
                logText.text = "Dungeon butuh 3 energy."
                return@setOnClickListener
            }
            state = spent
            ensureEnemy(true)
        }
        potionButton.setOnClickListener {
            val healed = rpgStore.usePotion(state)
            if (healed == null) return@setOnClickListener
            state = healed
            logText.text = "Potion dipakai. HP pulih."
            syncRpgProfile(state)
            refresh()
        }
        healButton.setOnClickListener {
            val healed = rpgStore.fullHeal(state)
            if (healed == null) return@setOnClickListener
            state = healed
            logText.text = "Healer bekerja. HP penuh. -50G"
            syncRpgProfile(state)
            refresh()
        }
        questButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("📜 Quest")
                .setMessage(
                    "Pemburu Pemula\nMenang 3 monster → progres ${state.wins}/3\n\n" +
                        "Penjelajah\nJelajah 5 kali → progres ${state.explores}/5\n\n" +
                        "Dungeon\nMasuk dungeon dan kalahkan boss untuk loot besar.\n\n" +
                        "Quest dan reward berkembang dari progres RPG kamu."
                )
                .setPositiveButton("Oke", null)
                .show()
        }
        classButton.setOnClickListener { chooseClass() }
        dailyButton.setOnClickListener {
            val claimed = rpgStore.claimDaily(state)
            if (claimed == null) {
                logText.text = "Daily sudah kamu ambil hari ini."
            } else {
                state = claimed
                logText.text = "Daily claim sukses: +100G +50XP +1 potion."
                syncRpgProfile(state)
            }
            refresh()
        }

        if (state.classId.isBlank()) {
            chooseClass()
        }
        // Gentle stamina regeneration while this screen is open.
        val regenHandler = Handler(Looper.getMainLooper())
        val regenRunnable = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val before = state.stamina
                state = rpgStore.regenerateTick(state)
                if (state.stamina != before) refresh()
                regenHandler.postDelayed(this, 15_000L)
            }
        }
        dialog.setOnDismissListener { regenHandler.removeCallbacksAndMessages(null) }
        refresh()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            dialog.window?.setDimAmount(0.78f)
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            regenHandler.postDelayed(regenRunnable, 15_000L)
        }
        dialog.show()
    }

    private fun rpgStatChip(parent: LinearLayout, initial: String): TextView {
        val chip = TextView(this).apply {
            text = initial
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 12f)
            setPadding(dp(4), dp(7), dp(4), dp(7))
        }
        parent.addView(chip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            rightMargin = dp(5)
        })
        return chip
    }

    private fun rpgActionButton(text: String, colorRes: Int): Button = Button(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 12f
        setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        background = roundedDrawable(colorRes, R.color.surface_stroke, 1f, 14f)
        minHeight = 0
        minimumHeight = 0
        stateListAnimator = null
    }

    private fun weightParams(marginStart: Int = 0): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
            if (marginStart > 0) leftMargin = marginStart
        }

    private fun showMabarDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg_root))
            setPadding(dp(12), dp(12), dp(12), dp(20))
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), 0)
        }
        scroll.addView(panel)
        dialog.setContentView(scroll)

        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val heading = TextView(this).apply {
            text = "🛡️ MABAR RAID"
            textSize = 21f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val close = Button(this).apply {
            text = "Tutup"
            isAllCaps = false
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 14f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        header.addView(heading)
        header.addView(close, LinearLayout.LayoutParams(dp(80), dp(44)))
        panel.addView(header)
        panel.addView(TextView(this).apply {
            text = "$playerName • 4 slot • realtime Firebase"
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            setPadding(dp(2), dp(4), 0, dp(12))
        })

        val roomChooser = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val roomInput = EditText(this).apply {
            hint = "Kode room 6 karakter"
            singleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            filters = arrayOf(InputFilter.LengthFilter(6))
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_tertiary))
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 14f)
            setPadding(dp(14), 0, dp(14), 0)
        }
        val createRoom = Button(this).apply {
            text = "🏰 Buat Room"
            isAllCaps = false
            background = roundedDrawable(R.color.game_accent_dark, R.color.game_accent, 1f, 14f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        val joinRoom = Button(this).apply {
            text = "🔗 Gabung Room"
            isAllCaps = false
            background = roundedDrawable(R.color.surface_elevated, R.color.surface_stroke, 1f, 14f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
        }
        roomChooser.addView(roomInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        roomChooser.addView(createRoom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })
        roomChooser.addView(joinRoom, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })
        panel.addView(roomChooser)

        val roomCode = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.brand_blue_light))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = roundedDrawable(R.color.brand_blue_soft, R.color.brand_blue_dark, 1f, 16f)
            setPadding(0, dp(12), 0, dp(12))
            visibility = View.GONE
        }
        panel.addView(roomCode, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { topMargin = dp(10) })

        val playerListText = TextView(this).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 16f)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            visibility = View.GONE
        }
        panel.addView(playerListText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })

        val readyButton = Button(this).apply {
            text = "✅ Ready"
            isAllCaps = false
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.game_accent_dark, R.color.game_accent, 1f, 14f)
        }
        val startButton = Button(this).apply {
            text = "🚀 Mulai Raid (Host)"
            isAllCaps = false
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.accent_dark, R.color.accent, 1f, 14f)
        }
        panel.addView(readyButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(10) })
        panel.addView(startButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })

        val bossName = TextView(this).apply {
            text = "👑 Abyss Warden"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            visibility = View.GONE
            setPadding(dp(2), dp(14), 0, dp(4))
        }
        val bossHp = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = MabarRepository.BOSS_HP
            progress = MabarRepository.BOSS_HP
            progressTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(this@MainActivity, R.color.accent)
            )
            visibility = View.GONE
        }
        val bossHpText = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            visibility = View.GONE
        }
        panel.addView(bossName)
        panel.addView(bossHp, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
        panel.addView(bossHpText)

        val raidRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val raidAttack = rpgActionButton("⚔️ Hit Boss", R.color.game_accent)
        val raidSkill = rpgActionButton("✨ Power Hit", R.color.game_accent_dark)
        raidRow.addView(raidAttack, weightParams())
        raidRow.addView(raidSkill, weightParams(dp(8)))
        panel.addView(raidRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply {
            topMargin = dp(10)
        })
        raidAttack.visibility = View.GONE
        raidSkill.visibility = View.GONE

        val chatText = TextView(this).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_secondary))
            background = roundedDrawable(R.color.bg_root_soft, R.color.divider, 1f, 14f)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            minLines = 4
            visibility = View.GONE
        }
        val chatRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chatInput = EditText(this).apply {
            hint = "Chat room..."
            singleLine = true
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_tertiary))
            background = roundedDrawable(R.color.surface, R.color.surface_stroke, 1f, 14f)
            setPadding(dp(12), 0, dp(12), 0)
        }
        val chatSend = Button(this).apply {
            text = "Kirim"
            isAllCaps = false
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_primary))
            background = roundedDrawable(R.color.brand_blue_dark, R.color.brand_blue, 1f, 14f)
        }
        chatRow.addView(chatInput, LinearLayout.LayoutParams(0, dp(50), 1f).apply { rightMargin = dp(8) })
        chatRow.addView(chatSend, LinearLayout.LayoutParams(dp(76), dp(50)))
        chatRow.visibility = View.GONE
        panel.addView(chatText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        panel.addView(chatRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(8) })

        val leaveButton = Button(this).apply {
            text = "Keluar Room"
            isAllCaps = false
            visibility = View.GONE
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.accent_light))
            background = roundedDrawable(R.color.accent_soft, R.color.accent_dark, 1f, 14f)
        }
        panel.addView(leaveButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(10) })

        val realDialog = dialog
        mabarDialog = realDialog
        realDialog.setContentView(scroll)

        fun showRoomUi(show: Boolean) {
            roomChooser.visibility = if (show) View.VISIBLE else View.GONE
            roomCode.visibility = if (show) View.GONE else View.VISIBLE
            playerListText.visibility = if (show) View.GONE else View.VISIBLE
            readyButton.visibility = if (show) View.GONE else View.VISIBLE
            startButton.visibility = if (show) View.GONE else View.VISIBLE
            bossName.visibility = if (show) View.GONE else View.VISIBLE
            bossHp.visibility = if (show) View.GONE else View.VISIBLE
            bossHpText.visibility = if (show) View.GONE else View.VISIBLE
            raidAttack.visibility = if (show) View.GONE else View.VISIBLE
            raidSkill.visibility = if (show) View.GONE else View.VISIBLE
            chatText.visibility = if (show) View.GONE else View.VISIBLE
            chatRow.visibility = if (show) View.GONE else View.VISIBLE
            leaveButton.visibility = if (show) View.GONE else View.VISIBLE
        }

        var currentReady = false

        fun syncStateAndRoom() {
            syncRpgProfile()
        }

        fun leaveCurrentRoom() {
            currentMabarRoom?.let { mabarRepository.leaveRoom(it) }
            currentMabarRoom = null
            mabarRepository.stopObserving()
        }

        fun renderRoom(room: MabarRepository.RoomSnapshot) {
            if (!realDialog.isShowing) return
            showRoomUi(false)
            roomCode.text = "ROOM ${room.code}"
            val me = mabarRepository.currentUid()
            val lines = room.players.mapIndexed { i, player ->
                val marker = if (player.uid == room.hostUid) "👑" else "•"
                val ready = if (player.ready) " READY" else ""
                "${i + 1}. $marker ${player.name} • Lv.${player.level}$ready"
            }
            playerListText.text = if (lines.isEmpty()) "Menunggu pemain..." else lines.joinToString("\n")
            val readySelf = room.players.firstOrNull { it.uid == me }?.ready == true
            currentReady = readySelf
            readyButton.text = if (readySelf) "🟢 Ready ON — tap untuk batal" else "✅ Ready"
            val readyCount = room.players.count { it.ready }
            startButton.isEnabled = room.hostUid == me && readyCount >= 2 && room.status == "lobby"
            startButton.alpha = if (startButton.isEnabled) 1f else 0.5f
            startButton.text = if (room.hostUid == me) "🚀 Mulai Raid • $readyCount/${room.players.size} ready" else "Menunggu Host memulai..."
            val raidVisible = room.status == "raid" || room.status == "ended"
            bossName.visibility = if (raidVisible) View.VISIBLE else View.GONE
            bossHp.visibility = if (raidVisible) View.VISIBLE else View.GONE
            bossHpText.visibility = if (raidVisible) View.VISIBLE else View.GONE
            raidAttack.visibility = if (raidVisible) View.VISIBLE else View.GONE
            raidSkill.visibility = if (raidVisible) View.VISIBLE else View.GONE
            bossHp.max = room.bossMaxHp.coerceAtLeast(1)
            bossHp.progress = room.bossHp.coerceAtLeast(0)
            bossHpText.text = "Boss HP ${room.bossHp}/${room.bossMaxHp}"
            if (room.status == "ended") {
                bossName.text = "🏆 RAID SELESAI"
                raidAttack.isEnabled = false
                raidSkill.isEnabled = false
                if (mabarRewardedRooms.add(room.code)) {
                    val old = rpgStore.load()
                    val reward = rpgStore.addRewards(old, 90, 140)
                    rpgStore.markWin(reward)
                    syncStateAndRoom()
                    showGameToast("Raid clear! +140G +90XP")
                }
            } else {
                bossName.text = "👑 Abyss Warden"
                raidAttack.isEnabled = room.status == "raid"
                raidSkill.isEnabled = room.status == "raid"
            }
            raidAttack.alpha = if (raidAttack.isEnabled) 1f else 0.5f
            raidSkill.alpha = if (raidSkill.isEnabled) 1f else 0.5f
            chatText.text = room.messages.takeLast(18).joinToString("\n").ifBlank { "Chat masih kosong." }
        }

        fun openRoom(code: String) {
            currentMabarRoom = code
            mabarRepository.observeRoom(code,
                onUpdate = { room -> renderRoom(room) },
                onError = { error ->
                    showGameToast(error)
                    leaveCurrentRoom()
                    showRoomUi(true)
                }
            )
        }

        createRoom.setOnClickListener {
            createRoom.isEnabled = false
            mabarRepository.createRoom(playerName, rpgStore.load()) { code, error ->
                createRoom.isEnabled = true
                if (code == null) {
                    showGameToast(error ?: "Gagal membuat room")
                    return@createRoom
                }
                roomInput.setText(code)
                openRoom(code)
            }
        }
        joinRoom.setOnClickListener {
            val code = roomInput.text.toString().trim()
            joinRoom.isEnabled = false
            mabarRepository.joinRoom(code, playerName, rpgStore.load()) { joined, error ->
                joinRoom.isEnabled = true
                if (joined == null) {
                    showGameToast(error ?: "Gagal join")
                    return@joinRoom
                }
                openRoom(joined)
            }
        }
        readyButton.setOnClickListener {
            currentMabarRoom?.let { code ->
                mabarRepository.setReady(code, !currentReady)
            }
        }
        startButton.setOnClickListener { currentMabarRoom?.let(mabarRepository::startRaid) }
        raidAttack.setOnClickListener {
            val state = rpgStore.load()
            val spent = rpgStore.spendStamina(state, 1) ?: run {
                showGameToast("Energy RPG habis.")
                return@setOnClickListener
            }
            val damage = 22 + spent.level * 4 + (0..12).random()
            rpgStore.save(spent)
            currentMabarRoom?.let { code ->
                mabarRepository.attackBoss(code, damage) { ok, _ ->
                    if (!ok) showGameToast("Serangan gagal, coba lagi.") else syncStateAndRoom()
                }
            }
        }
        raidSkill.setOnClickListener {
            val state = rpgStore.load()
            val spent = rpgStore.spendStamina(state, 2) ?: run {
                showGameToast("Butuh 2 energy untuk Power Hit.")
                return@setOnClickListener
            }
            val damage = 45 + spent.level * 6 + (0..18).random()
            rpgStore.save(spent)
            currentMabarRoom?.let { code ->
                mabarRepository.attackBoss(code, damage) { ok, _ ->
                    if (!ok) showGameToast("Power Hit gagal, coba lagi.") else syncStateAndRoom()
                }
            }
        }
        chatSend.setOnClickListener {
            currentMabarRoom?.let { code ->
                mabarRepository.sendMessage(code, playerName, chatInput.text.toString())
                chatInput.setText("")
            }
        }
        chatInput.setOnEditorActionListener { _, actionId, event ->
            val send = actionId == EditorInfo.IME_ACTION_SEND ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (send) chatSend.performClick()
            send
        }
        leaveButton.setOnClickListener {
            leaveCurrentRoom()
            showRoomUi(true)
        }
        close.setOnClickListener {
            leaveCurrentRoom()
            realDialog.dismiss()
        }
        realDialog.setOnDismissListener {
            leaveCurrentRoom()
            mabarDialog = null
        }

        showRoomUi(true)
        realDialog.setOnShowListener {
            realDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            realDialog.window?.setDimAmount(0.82f)
            realDialog.window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            realDialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        realDialog.show()
    }

    private fun syncRpgProfile(state: RpgGameStore.State = rpgStore.load()) {
        if (playerName.isNotBlank() && ::mabarRepository.isInitialized) {
            mabarRepository.syncProfile(playerName, state)
        }
    }

    private fun roundedDrawable(
        fillRes: Int,
        strokeRes: Int,
        strokeWidth: Float,
        radiusDp: Float
    ): GradientDrawable = GradientDrawable().apply {
        setColor(ContextCompat.getColor(this@MainActivity, fillRes))
        setStroke(dp(strokeWidth.toInt().coerceAtLeast(1)), ContextCompat.getColor(this@MainActivity, strokeRes))
        cornerRadius = dp(radiusDp.toInt()).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun showGameToast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()


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
        // Android 13+ only allows one runtime-permission dialog flow at a time.
        // Jangan langsung menembakkan dialog lokasi di frame yang sama dengan
        // dialog notifikasi; tunggu callback notifikasi, lalu lanjut ke lokasi.
        if (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermissionIfNeeded()
        }

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
        playerName = prefs.getString(KEY_PLAYER_NAME, "").orEmpty().trim()
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
        showDataSaverMenuFor(dataSaverValueText)
    }

    private fun showDataSaverMenuFor(anchor: View) {
        val popup = android.widget.PopupMenu(this, anchor)
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
        // Pasang jadwal periodik sekarang, tapi jangan enqueue scan instan dulu:
        // refresh lokasi butuh beberapa detik. Scan instan baru dikirim setelah
        // koordinat terbaru benar-benar berhasil disimpan.
        EwsUpdateWorker.schedule(applicationContext, enqueueImmediate = false)
        CoroutineScope(Dispatchers.IO).launch {
            val location = EwsLocationManager.refreshAndSave(applicationContext)
            if (location != null) {
                EwsUpdateWorker.enqueueNow(applicationContext)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // EWS benar-benar aktif hanya setelah izin lokasi diberikan.
            // Kalau ditolak, jangan bikin periodic worker kosong yang terus
            // bangun tiap 15 menit tanpa bisa menentukan lokasi.
            if (EwsLocationManager.hasPermission(this)) {
                startEwsLocationTracking()
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST) {
            // Alur EWS terpisah dari FCM. Setelah dialog notifikasi selesai
            // (baik Allow maupun Don't allow), lanjutkan meminta lokasi agar
            // dua izin yang memang dibutuhkan EWS tidak saling memblokir.
            if (Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                if (RemotePushManager.isBaselineReady(this)) {
                    RemotePushManager.ensureTopicSubscription(this)
                    FreeNotification.showPending(this)
                    FreeNotificationWorker.scheduleCatchUp(this)
                    FreeNotificationWorker.schedule(this)
                }
            }
            requestLocationPermissionIfNeeded()
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
        private const val KEY_PLAYER_NAME = "player_name"
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
