package com.bittv.iptv.ui

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bittv.iptv.R
import com.bittv.iptv.config.AppConfig
import com.bittv.iptv.config.ConfigStore
import com.bittv.iptv.data.Channel
import com.bittv.iptv.data.M3uParser
import com.bittv.iptv.util.EpgParser
import com.bittv.iptv.util.EpgRepository
import com.bittv.iptv.util.HeaderParser
import com.bittv.iptv.util.LogoLoader
import com.bittv.iptv.util.PlaylistNotification
import com.bittv.iptv.util.PlaylistRepository
import com.bittv.iptv.util.PlaylistUpdateResult
import com.bittv.iptv.util.TebakGambarRepository
import com.bittv.iptv.worker.AppUpdateWorker
import com.bittv.iptv.worker.EpgUpdateWorker
import com.bittv.iptv.worker.FreeNotificationWorker
import com.bittv.iptv.worker.PlaylistUpdateWorker
import java.util.Locale
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity() {

    private lateinit var config: AppConfig
    private lateinit var playlistRepository: PlaylistRepository
    private lateinit var epgRepository: EpgRepository

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
    private lateinit var topBar: View
    private lateinit var statusBar: View
    private lateinit var startupOverlay: View
    private lateinit var bottomNavTv: View
    private lateinit var bottomNavGame: View
    private lateinit var bottomNavTvLabel: TextView
    private lateinit var bottomNavGameLabel: TextView
    private lateinit var bottomNavBar: View
    private lateinit var bottomNavDivider: View

    // --- Panel Game: menu pilihan game + layar "Tebak Gambar", tampil di
    //     layar yang sama, gantiin panel TV pas tab Game aktif. Menu game
    //     dirancang biar gampang nambah game lain di kartu-kartu berikutnya;
    //     "Tebak Gambar" adalah yang pertama, soalnya diambil dari JSON remote. ---
    private lateinit var tvContentContainer: View
    private lateinit var gameContentContainer: View
    private lateinit var gameMenuContainer: View
    private lateinit var tebakGambarContainer: View
    private lateinit var gameCardTebakGambar: View
    private lateinit var gameBackButton: View
    private lateinit var gameFeedbackText: TextView
    private lateinit var gameScoreText: TextView
    private lateinit var gameTimerText: TextView
    private lateinit var gameImageView: android.widget.ImageView
    private lateinit var gameImageLoading: android.widget.ProgressBar
    private lateinit var gameAnswerInput: EditText

    private var isGameTabActive = false
    private var isTebakGambarActive = false
    private var gameScore = 0
    private var gameItems: List<TebakGambarRepository.Item> = emptyList()
    private var gameCurrentItem: TebakGambarRepository.Item? = null
    private val gameUsedIndexes = mutableSetOf<Int>()
    private var gameCountdown: CountDownTimer? = null
    private var gameRemainingMs: Long = GAME_ROUND_MS
    private var gameLoading = false



    private val allChannels = mutableListOf<Channel>()
    private val favorites = linkedSetOf<String>()
    private val history = ArrayDeque<String>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private var player: ExoPlayer? = null
    private var activeChannel: Channel? = null
    private var automaticRetries = 0
    private var currentFilter = "All"
    private var suppressGroupCallback = false
    private var isFullscreen = false
    private var startupComplete = false
    private var playbackToken = 0L
    private var epgProgrammes = emptyList<com.bittv.iptv.util.EpgProgramme>()
    private var retryVisibleBeforeFullscreen = false

    private val prefs by lazy { getSharedPreferences("bittv", MODE_PRIVATE) }

    private val foregroundCheckRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing && !isDestroyed && config.autoUpdateEnabled) {
                checkRemoteInBackground()
                mainHandler.postDelayed(
                    this,
                    config.foregroundCheckSeconds.coerceAtLeast(30L) * 1000L
                )
            }
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
        scheduleBackgroundWorkers()

        startupOverlay.visibility = View.VISIBLE
        playerContainer.visibility = View.GONE
        statusText.text = "LIVE TV • Memuat channel..."

        // The first screen is rendered immediately. Reading/parsing the local M3U
        // happens off the main thread so a large playlist cannot freeze startup.
        mainHandler.post { loadLocalPlaylistAsync() }
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
        bottomNavTv = findViewById(R.id.bottomNavTv)
        bottomNavGame = findViewById(R.id.bottomNavGame)
        bottomNavTvLabel = findViewById(R.id.bottomNavTvLabel)
        bottomNavGameLabel = findViewById(R.id.bottomNavGameLabel)
        bottomNavBar = findViewById(R.id.bottomNavBar)
        bottomNavDivider = findViewById(R.id.bottomNavDivider)

        tvContentContainer = findViewById(R.id.tvContentContainer)
        gameContentContainer = findViewById(R.id.gameContentContainer)
        gameMenuContainer = findViewById(R.id.gameMenuContainer)
        tebakGambarContainer = findViewById(R.id.tebakGambarContainer)
        gameCardTebakGambar = findViewById(R.id.gameCardTebakGambar)
        gameBackButton = findViewById(R.id.gameBackButton)
        gameFeedbackText = findViewById(R.id.gameFeedbackText)
        gameScoreText = findViewById(R.id.gameScoreText)
        gameTimerText = findViewById(R.id.gameTimerText)
        gameImageView = findViewById(R.id.gameImageView)
        gameImageLoading = findViewById(R.id.gameImageLoading)
        gameAnswerInput = findViewById(R.id.gameAnswerInput)

        gameCardTebakGambar.setOnClickListener { openTebakGambar() }
        gameBackButton.setOnClickListener { backToGameMenu() }

        findViewById<Button>(R.id.gameSkipButton).setOnClickListener { nextGameImage(reveal = true) }
        findViewById<Button>(R.id.gameSubmitButton).setOnClickListener { checkGameAnswer() }
        gameAnswerInput.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isDone) checkGameAnswer()
            isDone
        }
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

        // Jaring pengaman lapis terakhir buat bug "keluar-masuk fullscreen jadi
        // gak full lagi": tiap kali sistem nge-apply ulang window insets
        // (habis rotasi, dialog sistem tutup, dll) sementara kita lagi
        // fullscreen tapi status/nav bar-nya somehow kelihatan lagi, langsung
        // sembunyikan ulang. Ini nutupin celah timing yang gak ke-cover sama
        // panggilan di enterFullscreen()/onResume()/onWindowFocusChanged().
        ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, insets ->
            if (isFullscreen && insets.isVisible(WindowInsetsCompat.Type.systemBars())) {
                view.post { if (isFullscreen) applyFullscreenSystemBars() }
            }
            insets
        }
    }

    private fun configureUi() {
        PlaylistNotification.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

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
                        isFullscreen -> exitFullscreen()
                        isGameTabActive && isTebakGambarActive -> backToGameMenu()
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

    private fun scheduleBackgroundWorkers() {
        if (!config.autoUpdateEnabled) return
        PlaylistUpdateWorker.schedule(this)
        AppUpdateWorker.schedule(this)
        EpgUpdateWorker.schedule(this)
        FreeNotificationWorker.schedule(this)
    }

    private fun loadLocalPlaylistAsync() {
        backgroundExecutor.execute {
            val snapshot = runCatching {
                playlistRepository.ensureLocal()
            }.getOrNull()

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
                 * Autoplay channel paling atas.
                 * Delay kecil memberi kesempatan layout selesai sehingga
                 * PlayerView tidak terlihat seperti blank hitam saat startup.
                 */
                mainHandler.postDelayed({
                    if (!isFinishing &&
                        !isDestroyed &&
                        startupComplete &&
                        activeChannel == null
                    ) {
                        playChannel(
                            parsed.first(),
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

    private fun applyParsedChannels(content: String, channels: List<Channel>) {
        if (channels.isEmpty()) return

        updateEpgFromPlaylist(content)

        val oldUrl = activeChannel?.streamUrl
        allChannels.clear()
        allChannels.addAll(channels)

        if (!oldUrl.isNullOrBlank()) {
            activeChannel = allChannels.firstOrNull { it.streamUrl == oldUrl }
        }

        renderGroups()
        applyFilter()
        statusText.text = "LIVE TV • ${channels.size} channel"
    }

    private fun updateEpgFromPlaylist(content: String) {
        val url = M3uParser.playlistEpgUrl(content, config.playlistUrl)
        if (!url.isNullOrBlank()) {
            prefs.edit().putString("epg_url", url).apply()
        }
    }

    private fun checkRemoteInBackground() {
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

                    val currentUrl = activeChannel?.streamUrl
                    applyParsedChannels(result.snapshot.content, parsed)
                    if (!currentUrl.isNullOrBlank()) {
                        activeChannel = allChannels.firstOrNull { it.streamUrl == currentUrl }
                    }

                    if (config.notificationsEnabled && !result.firstRemoteSync) {
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

    private fun setupList() {
        val adapter = ChannelAdapter(
            onChannelClick = { channel -> playChannel(channel, isRetry = false, saveAsLast = true) },
            onFavoriteClick = { toggleFavorite(it) },
            isFavorite = { favorites.contains(it.streamUrl) },
            isSelected = { activeChannel?.streamUrl == it.streamUrl }
        )
        this.channelAdapter = adapter
        channelList.layoutManager = GridLayoutManager(this, computeChannelSpanCount())
        channelList.adapter = adapter
        channelList.setHasFixedSize(false)
        channelList.clipToPadding = false

        // Grid-nya "2 kolom" itu cuma pas buat lebar HP biasa. Di layar
        // sempit (HP kecil) kartu jadi ketekan/kegencet, di layar lebar
        // (tablet/foldable) jadi kegedean & jaraknya boros. Di sini kolomnya
        // dihitung ulang dari lebar RecyclerView yang sebenarnya, bukan
        // angka tetap, jadi ukuran kartu konsisten "pas" di semua ukuran.
        channelList.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            val width = right - left
            val oldWidth = oldRight - oldLeft
            if (width > 0 && width != oldWidth) {
                val spanCount = computeChannelSpanCount()
                (channelList.layoutManager as? GridLayoutManager)?.let { lm ->
                    if (lm.spanCount != spanCount) lm.spanCount = spanCount
                }
            }
        }
    }

    /**
     * Berapa kolom yang pas buat lebar layar sekarang. Target lebar tiap
     * kartu channel dipatok sekitar [CHANNEL_CARD_TARGET_DP] dp, lalu
     * jumlah kolomnya dibagi dari lebar layar asli (bukan lebar
     * RecyclerView, karena itu bisa masih 0 sebelum layout pertama) —
     * minimal 2 kolom biar gak kelewat lebar di HP kecil.
     */
    private fun computeChannelSpanCount(): Int {
        val density = resources.displayMetrics.density
        val widthPx = if (channelList.width > 0) {
            channelList.width
        } else {
            resources.displayMetrics.widthPixels
        }
        val widthDp = widthPx / density
        val target = (widthDp / CHANNEL_CARD_TARGET_DP).toInt()
        return target.coerceIn(2, 6)
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

    private fun showTvTab() {
        if (!isGameTabActive) return
        isGameTabActive = false

        crossFadeSwap(from = gameContentContainer, to = tvContentContainer)
        bottomNavTvLabel.setTextColor(resources.getColor(R.color.brand_blue, theme))
        bottomNavGameLabel.setTextColor(resources.getColor(R.color.text_secondary, theme))

        // Timer dijeda (bukan direset) selama keluar dari tab Game, biar pas
        // balik lagi sisa waktunya masih sama seperti pas ditinggal.
        gameCountdown?.cancel()

        // Video otomatis lanjut muter lagi pas balik ke tab TV.
        player?.playWhenReady = true
        player?.play()

        // Nyalain lagi animasi "LIVE" di channel list (sempat dimatiin
        // pas pindah ke tab Game, biar gak jalan sia-sia di belakang layar).
        resumeChannelListPulses()
    }

    private fun showGameTab() {
        if (isGameTabActive) return
        isGameTabActive = true

        crossFadeSwap(from = tvContentContainer, to = gameContentContainer)
        // Dulu ini salah pakai R.color.accent (merah, punya tab TV/LIVE).
        // Sekarang pakai game_accent (ungu) — warna sendiri buat tab Game.
        bottomNavGameLabel.setTextColor(resources.getColor(R.color.game_accent_light, theme))
        bottomNavTvLabel.setTextColor(resources.getColor(R.color.text_secondary, theme))

        // Video otomatis berhenti selama di tab Game, hemat data/baterai.
        player?.playWhenReady = false
        player?.pause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Channel list-nya lagi disembunyikan total (GONE), jadi animasi
        // "LIVE" yang lagi jalan di row-row-nya cuma buang-buang CPU/baterai
        // tanpa ada yang lihat — matiin dulu.
        pauseChannelListPulses()

        // Kalau lagi di tengah main Tebak Gambar sebelum pindah ke tab TV,
        // lanjutin lagi. Kalau belum pernah pilih game, biarin nunjukin
        // menu game (gameMenuContainer default-nya sudah visible).
        if (isTebakGambarActive) {
            resumeTebakGambar()
        }
    }

    /** Buka layar "Tebak Gambar" dari menu game. */
    private fun openTebakGambar() {
        isTebakGambarActive = true
        gameMenuContainer.visibility = View.GONE
        tebakGambarContainer.visibility = View.VISIBLE
        resumeTebakGambar()
    }

    /** Balik dari layar "Tebak Gambar" ke menu game (bukan ke tab TV). */
    private fun backToGameMenu() {
        if (!isTebakGambarActive) return
        isTebakGambarActive = false

        // Timer dipause (bukan direset) biar kalau user balik lagi ke
        // Tebak Gambar dari menu, sisa waktunya masih sama.
        gameCountdown?.cancel()

        tebakGambarContainer.visibility = View.GONE
        gameMenuContainer.visibility = View.VISIBLE
    }

    private fun resumeTebakGambar() {
        if (gameItems.isEmpty() && !gameLoading) {
            loadGameBankThenStart()
        } else if (gameCurrentItem == null && gameItems.isNotEmpty()) {
            nextGameImage(reveal = false)
        } else if (gameCurrentItem != null) {
            // Lanjutin sisa waktu dari sebelum pindah tab/menu.
            startGameCountdown(gameRemainingMs)
        }
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
                    if (isGameTabActive && isTebakGambarActive) nextGameImage(reveal = false)
                }.onFailure {
                    gameFeedbackText.text = "Gagal memuat soal, coba lagi"
                }
            }
        }
    }

    /** Fade halus antar panel TV/Game, biar berasa gonta-ganti tab, bukan lompat kasar. */
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

    private fun buildPlayer(headers: Map<String, String>, streamUrl: String = ""): ExoPlayer {
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

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(3_000, 15_000, 500, 1_500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
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
        activeChannel = channel
        if (!isRetry) automaticRetries = 0
        if (saveAsLast) saveHistory(channel)
        if (::channelAdapter.isInitialized) channelAdapter.notifyDataSetChanged()

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

        val newPlayer = buildPlayer(channel.headers, channel.streamUrl)
        player = newPlayer
        playerView.player = newPlayer
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
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
        val index = items.indexOfFirst { it.streamUrl == current.streamUrl }
        items.getOrNull(index + offset)?.let {
            playChannel(it, isRetry = false, saveAsLast = true)
        }
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

        applyFullscreenSystemBars()
        // Bug lama: keluar fullscreen lalu masuk lagi kadang gak beneran
        // fullscreen. Penyebabnya, panggilan hide() di atas kadang keburu
        // "ke-override" sama relayout yang dipicu perubahan requestedOrientation
        // barusan (timing-nya beda-beda antar device/OEM). Solusinya: pastikan
        // ada panggilan ulang setelah layout/orientation pass ini selesai.
        mainHandler.post { if (isFullscreen) applyFullscreenSystemBars() }
        mainHandler.postDelayed({ if (isFullscreen) applyFullscreenSystemBars() }, 300L)
    }

    /** Sembunyikan status bar & navigation bar buat mode fullscreen. */
    private fun applyFullscreenSystemBars() {
        window.insetsController?.let { controller ->
            controller.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
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
            applyFullscreenSystemBars()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Jaring pengaman tambahan: beberapa OEM (Xiaomi/Oppo/dll) baru
        // benar-benar menerapkan ulang system bar pas window dapat focus lagi,
        // bukan pas onResume.
        if (hasFocus && isFullscreen) {
            applyFullscreenSystemBars()
        }
    }

    override fun onStart() {
        super.onStart()

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
        // pas lagi di layar Tebak Gambar (bukan di menu game).
        if (isGameTabActive && isTebakGambarActive && gameCurrentItem != null && gameRemainingMs > 0) {
            startGameCountdown(gameRemainingMs)
        }

        if (!isGameTabActive) resumeChannelListPulses()
    }

    override fun onStop() {
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
        player = buildPlayer(channel.headers, channel.streamUrl)
        playerView.player = player
        playerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

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
        backgroundExecutor.shutdownNow()
        val old = player
        player = null
        runCatching { old?.stop() }
        runCatching { old?.release() }
        playerView.player = null
        epgRepository.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 4001
        private const val KEY_LAST_CHANNEL = "last_channel"
        private const val KEY_HISTORY = "history"
        private const val KEY_FAVORITES = "favorites"

        // Banyak server IPTV/CDN nge-block request yang bukan dari browser
        // (User-Agent kosong/khas library kayak "ExoPlayerLib" gampang kena
        // filter anti-leech). Dengan nyamar sebagai Chrome desktop, request
        // dari app jadi diterima server persis kayak dibuka lewat browser.
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        // Waktu per soal Tebak Gambar: 60 detik.
        private const val GAME_ROUND_MS = 60_000L

        // Lebar target satu kartu channel (dp). Dipakai buat ngitung jumlah
        // kolom grid biar konsisten "pas" di HP kecil sampai tablet/foldable,
        // bukan dipatok 2 kolom buat semua ukuran layar.
        private const val CHANNEL_CARD_TARGET_DP = 168f
    }
}

class SimpleTextWatcher(
    private val onChange: () -> Unit
) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
    override fun afterTextChanged(s: android.text.Editable?) { onChange() }
}
