package com.bittv.iptv.ads

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.util.concurrent.CopyOnWriteArrayList
import java.lang.ref.WeakReference

/**
 * Monetisasi terpusat. Test IDs sengaja dipakai sampai owner mengganti dengan
 * AdMob App/Ad Unit milik production.
 */
object AdManager {
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    @Volatile private var initStarted = false
    @Volatile private var adsReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val pendingBanners = CopyOnWriteArrayList<WeakReference<AdView>>()

    fun initialize(activity: Activity) {
        if (initStarted) return
        initStarted = true

        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    startAds(activity, consentInformation)
                }
            },
            {
                // Gagal mengambil consent network tidak boleh menjatuhkan app.
                startAds(activity, consentInformation)
            }
        )

        // In some returning sessions UMP already knows that ads can be requested.
        if (consentInformation.canRequestAds()) startAds(activity, consentInformation)
    }

    private fun startAds(activity: Activity, consentInformation: ConsentInformation) {
        if (adsReady) return
        if (!consentInformation.canRequestAds()) return
        MobileAds.initialize(activity) {
            adsReady = true
            handler.post {
                pendingBanners.toList().forEach { reference ->
                    reference.get()?.let { banner ->
                        if (banner.parent != null) banner.loadAd(AdRequest.Builder().build())
                    }
                }
                pendingBanners.clear()
            }
        }
    }

    fun attachBanner(activity: Activity, container: ViewGroup) {
        initialize(activity)
        if (container.findViewWithTag<AdView>("bittv_banner") != null) return

        val adView = AdView(activity).apply {
            tag = "bittv_banner"
            adUnitId = TEST_BANNER
            val density = resources.displayMetrics.density.coerceAtLeast(1f)
            val widthDp = (resources.displayMetrics.widthPixels / density).toInt().coerceAtLeast(320)
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, widthDp))
        }
        container.addView(
            adView,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        pendingBanners += WeakReference(adView)
        if (adsReady) {
            pendingBanners.removeAll { it.get() === adView || it.get() == null }
            adView.loadAd(AdRequest.Builder().build())
        }
    }

    fun showRewarded(activity: Activity, onReward: (RewardItem) -> Unit, onClosed: () -> Unit = {}) {
        initialize(activity)
        RewardedAd.load(
            activity,
            TEST_REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    ad.fullScreenContentCallback = object : com.google.android.gms.ads.FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onClosed()
                        }
                    }
                    ad.show(activity) { reward -> onReward(reward) }
                }

                override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                    onClosed()
                }
            }
        )
    }
}
