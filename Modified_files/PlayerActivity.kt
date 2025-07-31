@file:UnstableApi
package com.hbcugo.tv.ui

import android.app.Activity
import android.app.Application
import android.app.ComponentCaller
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.DisplayMetrics
import android.util.Log
import android.util.Xml
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.AdsConfiguration
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.ima.ImaAdsLoader
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ads.AdsLoader
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultAllocator
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.android.eapx.BrightApi
import com.android.network.model.videos.CuePointData
import com.bumptech.glide.Glide
import com.google.ads.interactivemedia.v3.api.AdErrorEvent
import com.google.ads.interactivemedia.v3.api.AdEvent
import com.google.ads.interactivemedia.v3.api.AdEvent.AdEventListener
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hbcugo.tv.BuildConfig
import com.hbcugo.tv.R
import com.hbcugo.tv.api.APIClient
import com.hbcugo.tv.api.AnalyticsClient
import com.hbcugo.tv.databinding.ActivityPlayerBinding
import com.hbcugo.tv.ui.brddialog.BrightDialog
import com.hbcugo.tv.ui.brddialog.ConfirmationDialog
import com.hbcugo.tv.utils.Constant
import com.hbcugo.tv.utils.Constant.isMediaMelonEnabled
import com.hbcugo.tv.utils.GlobalData
import com.hbcugo.tv.utils.GlobalData.isGridRefreshRequired
import com.hbcugo.tv.utils.GlobalData.isRefreshRequired
import com.hbcugo.tv.utils.GlobalData.isRowRefreshForSearchRequired
import com.hbcugo.tv.utils.GlobalData.IS_USE_TEST_VAST_URL
import com.hbcugo.tv.utils.PlaybackPreferences
import com.hbcugo.tv.utils.Utils
import com.hbcugo.tv.utils.Utils.activityBackArgs
import com.hbcugo.tv.utils.Utils.getSecondsFromTheString
import com.hbcugo.tv.utils.Utils.isFireTV
import com.hbcugo.tv.utils.addTo
import com.hbcugo.tv.utils.isBuffering
import com.mediamelon.mmappanalytics.MMAppAnalytics
import com.mediamelon.qubit.ep.ContentMetadata
import com.mediamelon.smartstreaming.media3_ima.MMSmartStreamingExo2
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess
import com.hbcugo.tv.integration.VideoInterruptionIntegration
import com.example.aellayer.FormSubmissionTracker
import android.widget.FrameLayout

class PlayerActivity : FragmentActivity() {
    /***/
    private var _binding: ActivityPlayerBinding? = null
    private val binding get() = _binding!!

    private var exoplayer: ExoPlayer? = null
    private var isControlVisibled = 0

    private var videoUrl: String? = ""
    private var videoTitle: String? = ""
    private var videoDescription: String? = ""
    private var videoImage: String? = ""
    private var videoID: String? = ""
    private var videoGenre: String? = ""
    private var videoCategory: String? = ""
    private var videoRating: String? = ""
    private var videoTotalDuration: Long? = 0
    private var videoSubtitleUrl: String = ""
    private var videoSeasonName: String = ""
    private var videoSeriesName: String = ""
    private var videoType: String = ""

    private var videoCompleted = false
    private var isVideoLive = 0
    private var sessionID: String = ""
    private var heartBeatInt: Int = 10

    private var deviceId: String = ""
    private var imaAdsLoader: ImaAdsLoader? = null

    private var nextAdPlayTime: Long = 0
    private var lastPosChecked: Long = 0
    private var lastExoPos = 0
    private var isBRDDialogOpen = false

    private var adUrl: String  = ""
    private var isFirstCheckForVast: Boolean = true
    private var isVastCheckingStarted: Boolean = false

    private lateinit var ccOn: ImageButton
    private lateinit var ccOff: ImageButton
    private lateinit var captionLayout: ConstraintLayout
    private var isCCOn: Boolean = false


    private val disposables = CompositeDisposable()
    private val disposablestimer = CompositeDisposable()
    private val disposablesTimer = CompositeDisposable()
    private val disposablesTimerForCue = CompositeDisposable()

    private var isVideoPlayFromGrid: Boolean = true
    private var isVideoPlayFromSearch: Boolean = false

    private var allCuePoints: List<CuePointData> = emptyList()

    private var randomNumber: Int = 0

    private val trackSelector: DefaultTrackSelector by lazy {
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                    .setPreferredTextLanguage("en")
                    .setMaxVideoBitrate(Int.MAX_VALUE) // Allow highest bitrate
                    .setMinVideoBitrate(3_000_000) // Prioritize 1080p quality
                    .setMinVideoSize(1280, 720)
                    .setMaxVideoSize(1920, 1080)
                    .setForceHighestSupportedBitrate(true)
            )
        }

        ccOn.visibility = View.VISIBLE
        ccOff.visibility = View.GONE
        isCCOn = true
        return@lazy trackSelector
    }
    /***/
    private var isVideoComplete: Boolean = false
    /***/
    private var isAutoPaused: Boolean = false

    private var adIDTOSend = ""

    // ADD VIDEO INTERRUPTION INTEGRATION
    private var videoInterruption: VideoInterruptionIntegration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        super.onCreate(savedInstanceState)
        _binding = ActivityPlayerBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        
        // Initialize FormSubmissionTracker for persistent form state
        com.example.aellayer.FormSubmissionTracker.initialize(this)
        
        randomNumber = Random().nextInt(Int.MAX_VALUE)
        if (isMediaMelonEnabled){
            MMAppAnalytics.trackScreenView("PlayerScreen")
        }

        videoUrl = intent.getStringExtra(VIDEO_URL_EXTRA)
        videoTitle = intent.getStringExtra(VIDEO_NAME_EXTRA)
        videoDescription = intent.getStringExtra(VIDEO_DESCRIPTION_EXTRA)
        videoImage = intent.getStringExtra(VIDEO_IMAGE_EXTRA)
        videoGenre = intent.getStringExtra(VIDEO_GENRE_EXTRA)
        videoCategory = intent.getStringExtra(VIDEO_CATEGORY_EXTRA)
        videoRating = intent.getStringExtra(VIDEO_RATING_EXTRA)
        videoSeasonName = intent.getStringExtra(VIDEO_SEASON_TITLE).toString()
        videoSeriesName = intent.getStringExtra(VIDEO_SERIES_TITLE).toString()
        isVideoPlayFromGrid = intent.getBooleanExtra(VIDEO_IS_FROM_GRID, true)
        isVideoPlayFromSearch = intent.getBooleanExtra(VIDEO_IS_FROM_SEARCH, false)
        val cuePointString = intent.getStringExtra(CUE_POINT_STRING)

        if (TextUtils.isEmpty(cuePointString).not()){
            val gson = Gson()
            val type = object : TypeToken<List<CuePointData>>() {}.type
            allCuePoints = gson.fromJson(cuePointString, type)
        }

        videoType = intent.getStringExtra(VIDEO_TYPE).toString()

        if (videoType != "series"){
            videoSeasonName = ""
            videoSeriesName = ""
        }

        videoID = "${intent.getStringExtra(VIDEO_ID_EXTRA)}"
        isVideoLive = intent.getIntExtra(VIDEO_IS_LIVE,0)
        videoTotalDuration = intent.getLongExtra(VIDEO_DURATION_EXTRA,0)

        videoSubtitleUrl = intent.getStringExtra(VIDEO_SUBTITLE_EXTRA).toString()
        val positionToSeek = intent.getLongExtra(VIDEO_PROGRESS_EXTRA,0)
        deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).toString()
        initializePlayer(positionToSeek)

    }

    private fun createMediaSourceFactory(): MediaSource.Factory {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(30_000)   // Increase from default 8s
            .setReadTimeoutMs(30_000)      // Increase from default 8s
            .setAllowCrossProtocolRedirects(true)

        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val defaultMediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)
            .setLocalAdInsertionComponents({_:AdsConfiguration? ->
                this.getClientSideAdsLoader()
            },binding.simpleExoPlayerView)

        return defaultMediaSourceFactory
    }

    private val combinedAdEventListener: AdEvent.AdEventListener = object : AdEventListener {
        override fun onAdEvent(adEvent: AdEvent) {
            if (adEvent.type == AdEvent.AdEventType.STARTED){
                val adData = adEvent.ad
                adIDTOSend = adData.adId
                if (isMediaMelonEnabled){
                    MMAppAnalytics.reportAdStarted(mapOf(
                        "adProvider" to "springserve",
                        "adUnitId" to adIDTOSend
                    ))
                }
            }else if (adEvent.type == AdEvent.AdEventType.ALL_ADS_COMPLETED || adEvent.type == AdEvent.AdEventType.COMPLETED){
                if (isMediaMelonEnabled){
                    MMAppAnalytics.reportAdCompleted(mapOf(
                        "adProvider" to "springserve",
                        "adUnitId" to adIDTOSend
                    ))
                }
                adIDTOSend = ""
            }

            val analyticsBridgeObject = MMSmartStreamingExo2.getInstance().analyticsBridge

            analyticsBridgeObject.onAdEvent(adEvent) // Call Media Melon's method
        }
    }
    private val combinedAdErrorListener: AdErrorEvent.AdErrorListener = object : AdErrorEvent.AdErrorListener {
        override fun onAdError(adErrorEvent: AdErrorEvent) {
            if (isMediaMelonEnabled){
                MMAppAnalytics.reportAdError(mapOf(
                    "adProvider" to "springserve",
                    "errorCode" to adErrorEvent.error.errorCode
                ))
            }
            adIDTOSend = ""
            val analyticsBridgeObject = MMSmartStreamingExo2.getInstance().analyticsBridge
            analyticsBridgeObject.onAdError(adErrorEvent) // Call Media Melon's method
        }
    }

    private fun getClientSideAdsLoader(): AdsLoader? {
        if (imaAdsLoader == null) {
            imaAdsLoader = ImaAdsLoader
                .Builder(this)
                .setAdEventListener(combinedAdEventListener)
                .setAdErrorListener(combinedAdErrorListener)
                .build()
        }

        imaAdsLoader?.setPlayer(exoplayer)
        return imaAdsLoader
    }

    private fun initializePlayer(positionToSeek: Long) {
        captionLayout = findViewById(R.id.caption_layout)
        ccOn = findViewById(R.id.exo_cc_on)
        ccOff = findViewById(R.id.exo_cc_off)
        releasePlayer()

        val loadControl = DefaultLoadControl.Builder()
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .setAllocator(DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(50_000, 50_000, 2500, 5000)
            .setPrioritizeTimeOverSizeThresholds(DefaultLoadControl.DEFAULT_PRIORITIZE_TIME_OVER_SIZE_THRESHOLDS)
            .build()

        exoplayer = ExoPlayer.Builder(this)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
            .setRenderersFactory(DefaultRenderersFactory(this)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            )
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setMediaSourceFactory(createMediaSourceFactory())
            .build()
        binding.simpleExoPlayerView.player = exoplayer
        binding.simpleExoPlayerView.useController = true
        binding.simpleExoPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)

        // ADD VIDEO INTERRUPTION SETUP
        setupVideoInterruption()

        val title = findViewById<TextView>(R.id.title)
        val seekbar = findViewById<DefaultTimeBar>(R.id.exo_progress)
        val position = findViewById<TextView>(R.id.exo_position)
        val slash = findViewById<TextView>(R.id.slash)
        val duration = findViewById<TextView>(R.id.exo_duration)
        val exoRew = findViewById<ImageButton>(R.id.exo_rew)
        val exoFFWD = findViewById<ImageButton>(R.id.exo_ffwd)


        val videoName = videoTitle
        title.text = videoName
        if (isVideoLive == 1){
            seekbar.visibility = View.INVISIBLE
            slash.visibility = View.INVISIBLE
            position.visibility = View.INVISIBLE
            duration.visibility = View.INVISIBLE
            exoRew.visibility = View.GONE
            exoFFWD.visibility = View.GONE
            binding.simpleExoPlayerView.setShowRewindButton(false)
            binding.simpleExoPlayerView.setShowFastForwardButton(false)
        }else {
            seekbar.visibility = View.VISIBLE
            slash.visibility = View.VISIBLE
            position.visibility = View.VISIBLE
            duration.visibility = View.VISIBLE
            exoRew.visibility = View.VISIBLE
            exoFFWD.visibility = View.VISIBLE
        }

        if (videoUrl != null && videoUrl?.isNotEmpty() == true){
            if (isVideoLive == 1){
                val liveVastUrl = getAdsUrlForLive(videoUrl!!)

                val defaultHttpDataSourceFactory = DefaultHttpDataSource.Factory()
                val mediaItem = MediaItem.Builder()
                    .setMediaId(liveVastUrl)
                    .setUri(liveVastUrl)
                    .setMediaMetadata(MediaMetadata.Builder().setTitle("").build())
                    .build()
                val mediaSource = HlsMediaSource.Factory(defaultHttpDataSourceFactory).createMediaSource(mediaItem)

                exoplayer?.apply {
                    setMediaSource(mediaSource)
                    prepare()
                    play()
                }
                // Video interruption is already set up in initializePlayer()
                if (isMediaMelonEnabled){
                    initMediaMelon()
                    MMAppAnalytics.reportVideoStart(videoID.toString(), mapOf(
                        "assetName" to videoTitle,
                        "duration" to videoTotalDuration
                    ))
                }


            }else{
                getVastADData(positionToSeek)
            }
            addListener()
            sendStatAnalyticsCalls()
        }
        binding.simpleExoPlayerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT)


        ccOn.setOnClickListener {
            toggleSubtitle(false)
        }

        ccOff.setOnClickListener {
            toggleSubtitle(true)
        }

    }

    private fun toggleSubtitle(valueCCOn: Boolean) {
        if (valueCCOn) {
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder()
                .setRendererDisabled(C.TRACK_TYPE_VIDEO, false)
                .setPreferredTextLanguage("en")
                .build()
            isCCOn = true
            ccOn.visibility = View.VISIBLE
            ccOff.visibility = View.GONE
            ccOn.requestFocus()
        } else {
            trackSelector.parameters = DefaultTrackSelector.ParametersBuilder()
                .setRendererDisabled(C.TRACK_TYPE_VIDEO, true)
                .build()
            isCCOn = false
            ccOn.visibility = View.GONE
            ccOff.visibility = View.VISIBLE
            ccOff.requestFocus()
        }
    }

    private fun updateButtonVisibility() {
        val trackGroupArray = trackSelector.currentMappedTrackInfo?.getTrackGroups(2)
        if (exoplayer != null && (trackGroupArray?.length?: 0) > 0  && videoSubtitleUrl.isNotEmpty()) {
            captionLayout.visibility = View.VISIBLE
        } else {
            captionLayout.visibility = View.GONE
        }
    }

    private fun initMediaMelon(){
        MMSmartStreamingExo2.enableLogTrace(true)

        if (MMSmartStreamingExo2.getRegistrationStatus() == false) {
            MMSmartStreamingExo2.getInstance().setContext(applicationContext)
            MMSmartStreamingExo2.registerMMSmartStreaming(getString(com.android.common.R.string.player_name_for_media_melon), Constant.MEDIA_MELON_CUSTOMER_ID, deviceId, getString(com.android.common.R.string.app_name), "Free", "", false)
            MMSmartStreamingExo2.reportPlayerInfo(getString(com.android.common.R.string.player_name_for_media_melon), "Media3", "1.7.1")

            MMSmartStreamingExo2.reportAppInfo(getString(com.android.common.R.string.app_name), BuildConfig.VERSION_NAME)

            //call this function to enable custom error reporting
            MMSmartStreamingExo2.getInstance().enableCustomErrorReporting(false)
        }

        val contentMetaData = ContentMetadata()
        contentMetaData.assetName = videoTitle
        contentMetaData.assetId = videoID
        contentMetaData.videoId = videoID

        contentMetaData.genre = videoGenre
         if (videoType == "series"){
             contentMetaData.contentType ="Episode"
             contentMetaData.seriesTitle = videoSeriesName
             contentMetaData.season = videoSeasonName
             contentMetaData.episodeNumber = ""
        }else{
             contentMetaData.contentType = "Movie"
             contentMetaData.seriesTitle = ""
             contentMetaData.season = ""
             contentMetaData.episodeNumber = ""
        }

        val isLive = isVideoLive == 1
        contentMetaData.videoType = if (isVideoLive == 1){
            "LIVE"
        }else{
            "VOD"
        }

        MMSmartStreamingExo2.getInstance().initializeSession(exoplayer, videoUrl, contentMetaData, isLive)
        MMSmartStreamingExo2.getInstance().reportUserInitiatedPlayback()

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels
        MMSmartStreamingExo2.getInstance().reportPlayerResolution(width, height)
        MMSmartStreamingExo2.getInstance().reportSubPropertyId(getString(com.android.common.R.string.app_name))
        MMSmartStreamingExo2.getInstance().reportExperimentName("JuneTesting")
        MMSmartStreamingExo2.getInstance().reportViewSessionId(randomNumber.toString());
    }

    private fun getVastADData(positionToSeek: Long) {
        val vastApiString = Constant.VAST_API
        val ipAddress = if (GlobalData.publicIPAddress.isNotEmpty()) GlobalData.publicIPAddress else (Utils.getIPAddress(true)?:"")

        val cb = Random().nextInt(Int.MAX_VALUE)

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        var ifaType = ""
        var osString = ""
        val osVersionString = "${Build.VERSION.SDK_INT}"
        if (isFireTV()) {
            ifaType = "afai"
            osString = "FireTV"
        } else {
            ifaType = "aaid"
            osString = "AndroidTV"
        }

        var vastFinalApiWithMacro = vastApiString
            .replace("{cb}","$cb")
            .replace("{{CACHEBUSTER}}","$cb")
            .replace("{device_width}","$width")
            .replace("{device_height}","$height")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{player_height}","$height")
            .replace("{player_width}","$width")
            .replace("{page_url}","")
            .replace("{app_name}",getString(com.android.common.R.string.app_name))
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{app_version}", BuildConfig.VERSION_NAME)
            .replace("{Ad_Position}","preroll")
            .replace("{device_ifa}", deviceId)
            .replace("{{DEVICE_ID}}", deviceId)
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{device_id_type}", ifaType)
            .replace("{IFA_TYPE}", ifaType)
            .replace("{device_make}","Amazon")
            .replace("{{DEVICE_MAKE}}","Amazon")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{device_model}","${Build.MODEL}")
            .replace("{{DEVICE_MODEL}}","${Build.MODEL}")
            .replace("{{DEVICE_BRAND_NAME}}","${Build.BRAND}")
            .replace("{model}","${Build.MODEL}")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{rtb_device}","3")
            .replace("{os}", osString)
            .replace("{osv}", osVersionString)
            .replace("{ua}",System.getProperty("http.agent")?: "")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{{USER_AGENT}}",System.getProperty("http.agent")?: "")
            .replace("{video_id}","$videoID")
            .replace("{{CONTENT_ID}}","$videoID")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{media_title}","$videoTitle")
            .replace("{{CONTENT_TITLE}}","$videoTitle")
            .replace("{series_title}","")
            .replace("{{CONTENT_SERIES}}","")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{content_episode}","")
            .replace("{{CONTENT_EPISODE}}","")
            .replace("{content_season}","")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{{CONTENT_SEASON}}","")
            .replace("{CONTENT_PRODUCER_NAME}}","")
            .replace("{content_genre}","$videoGenre")
            .replace("{{CONTENT_GENRE}}","$videoGenre")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{category}","$videoCategory")
            .replace("{{CONTENT_CATEGORIES}}","$videoCategory")
            .replace("{content_categories}","$videoCategory")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{video_rating}","$videoRating")
            .replace("{{RATING}}","$videoRating")
            .replace("{content_duration}","$videoTotalDuration")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{vid_duration}","$videoTotalDuration")
            .replace("{content_len}","$videoTotalDuration")
            .replace("{{CONTENT_LENGTH}}","$videoTotalDuration")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{content_language}","en")
            .replace("{{LANGUAGE}}","en")
            .replace("{content_rating}","G")
            .replace("{is_livestream}","0")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{ip_addr}", ipAddress)
            .replace("{{IP}}", ipAddress)
            .replace("{ccpa_us_privacy}","1---")
            .replace("{COPPA}","0")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("{GDPR}","1")
            .replace("{CONSENT}","1")
            .replace("{POD_AD_SLOTS}","3")
            .replace("{CONTENT_LIVESTREAM}","0")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace(" ", "%20")
            .replace("(","%28")
            .replace(")","%29")
            .replace("\\","")
        vastFinalApiWithMacro = vastFinalApiWithMacro
            .replace("\"","%22")
            .replace("{","%7B")
            .replace("}","%7D")
            .replace("'","%27")


        APIClient.api.getVastAdUrl(vastFinalApiWithMacro)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({
                if (it.isSuccessful) {
                    adUrl = if (IS_USE_TEST_VAST_URL){
                        GlobalData.TEST_VAST_TAG
                    } else{
                        it.body()?.vastContent?.vastUrl.toString()
                    }
                    Log.e("adUrl","VastUrl: $adUrl")
                    nextAdPlayTime = positionToSeek + AD_GAP_TIME


                    if (isFirstCheckForVast) {
                        isFirstCheckForVast = false
                        openPlayerForVod(positionToSeek)
                    }else{
                        fetchVastAd(adUrl) { vastResponse ->
                            injectAdDuringPlayback(vastResponse)
                        }
                    }
                }else{
                    nextAdPlayTime = positionToSeek + AD_GAP_TIME
                    if (isFirstCheckForVast) {
                        isFirstCheckForVast = false
                        openPlayerForVod(positionToSeek)
                    }
                }
            }, {
                nextAdPlayTime = positionToSeek + AD_GAP_TIME
                if (isFirstCheckForVast) {
                    isFirstCheckForVast = false
                    openPlayerForVod(positionToSeek)
                }
            })
            .addTo(disposables)
    }

    private fun injectAdDuringPlayback(vastResponse: String?) {
        if (vastResponse != null) {
            val vastDataUri = Util.getDataUriForString("text/xml", vastResponse)
            val adsConfiguration = AdsConfiguration.Builder(vastDataUri).build()

            val mediaItem = exoplayer?.currentMediaItem?.buildUpon()
                ?.setAdsConfiguration(adsConfiguration)
                ?.build()

            if (mediaItem != null) {
                exoplayer?.setMediaItem(mediaItem, false) // False ensures playback continues
                exoplayer?.prepare()
            }
        }
        isVastCheckingStarted = false

    }

    private fun fetchVastAd(vastUrl: String, onAdReady: (String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(vastUrl).build()
                val client = OkHttpClient()
                val response = client.newCall(request).execute()
                val vastResponse = response.body?.string()

                withContext(Dispatchers.Main) {
                    var hasLinear = false
                    var hasMediaFile = false
                    val parser = Xml.newPullParser()
                    parser.setInput(StringReader(vastResponse))
                    var eventType = parser.eventType
                    while (eventType != XmlPullParser.END_DOCUMENT) {
                        if (eventType == XmlPullParser.START_TAG) {
                            val tagName = parser.name?.trim()?.lowercase()
                            if (tagName == "linear") {
                                hasLinear = true
                                break
                            } else if (tagName == "mediafile") {
                                hasMediaFile = true
                                break
                            }
                        }
                        eventType = parser.next()
                    }

                    if (hasLinear || hasMediaFile){
                        onAdReady(vastResponse) // Pass response back to UI thread
                    }else{
                        onAdReady(null) // Pass response back to UI thread
                    }

                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onAdReady(null) // Continue playback if no ad is available
                }
            }
        }
    }

    private fun openPlayerForVod(positionToSeek: Long){
        val mediaItemBuilder = MediaItem.Builder()
            .setMediaId(videoUrl.toString())
            .setUri(videoUrl)
            .setAdsConfiguration(AdsConfiguration.Builder(adUrl.toUri()).build())
            .setMediaMetadata(MediaMetadata.Builder().setTitle("").build())

        if (TextUtils.isEmpty(videoSubtitleUrl).not()){
            val sUri = videoSubtitleUrl.toUri()
            val subtitleConfiguration = MediaItem.SubtitleConfiguration.Builder(sUri)
                .setUri(sUri)
                .setId("0")
                .setMimeType(MimeTypes.TEXT_VTT)
                .setSelectionFlags(Format.NO_VALUE)
                .setLanguage("")
                .build()

            val listOfSubtitle = mutableListOf<MediaItem.SubtitleConfiguration>()
            listOfSubtitle.add(subtitleConfiguration)

            mediaItemBuilder.setSubtitleConfigurations(listOfSubtitle)
        }

        val mediaItem = mediaItemBuilder.build()

        nextAdPlayTime = positionToSeek + AD_GAP_TIME


        exoplayer?.apply {
            setMediaItem(mediaItem)
            seekTo(positionToSeek)
            prepare()
            play()
        }
        
        // Video interruption is already set up in initializePlayer()

        toggleSubtitle(false)
        updateButtonVisibility()

        binding.simpleExoPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        if (isMediaMelonEnabled){
            initMediaMelon()
            MMAppAnalytics.reportVideoStart(videoID.toString(), mapOf(
                "assetName" to videoTitle,
                "duration" to videoTotalDuration
            ))
        }
    }

    private fun getAdsUrlForLive(liveUrl: String): String {
        val ipAddress = if (GlobalData.publicIPAddress.isNotEmpty()) GlobalData.publicIPAddress else (Utils.getIPAddress(true)?:"")
        val packageName = applicationContext.packageName
        var cb = Random().nextInt(Int.MAX_VALUE)
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels

        var ifaType = ""
        var platflom = ""
        if (isFireTV()){
            ifaType = "afai"
            platflom = "fire_tv"
        }else {
            ifaType = "aaid"
            platflom = "android"

        }
        var liveVideoUrl = liveUrl
            .replace("[CACHEBUSTER]", "$cb")
            .replace("[PLAYER-WIDTH]", "$width")
            .replace("[PLAYER-HEIGHT]", "$height")
            .replace("[APP-NAME]", resources.getString(com.android.common.R.string.app_name))
            .replace("[WEB-URL]", resources.getString(com.android.common.R.string.app_web_url))
            .replace("[DEVICE-ID]", deviceId)
        liveVideoUrl = liveVideoUrl
            .replace("[DEVICE-MODEL]", "${Build.MODEL}")
            .replace("[USER-AGENT]", System.getProperty("http.agent")?:"")
            .replace("[IP-ADDRESS]", ipAddress)
        liveVideoUrl = liveVideoUrl
            .replace("[APP-BUNDLE]", "$packageName")
            .replace("[APP-STORE-URL]", resources.getString(com.android.common.R.string.app_store_url))
            .replace("[DEVICE-ID-TYPE]", ifaType)
        liveVideoUrl = liveVideoUrl
            .replace("[DNT]", "0")
            .replace("[DEVICE-MAKER]", "FireTV")
            .replace("[DEVICE-TYPE]", "3")
        liveVideoUrl = liveVideoUrl
            .replace("[PLATFORM-NAME]", platflom)
            .replace("[US-PRIVACY]", "1YNN").replace(" ", "%20").replace("(", "%28")
            .replace("|", "%7C").replace(")","%29").replace("\\","").replace("\"","%22")

        return liveVideoUrl

    }

    private fun addListener() {
        binding.simpleExoPlayerView.keepScreenOn = true

        binding.simpleExoPlayerView.setControllerVisibilityListener(object : PlayerView.ControllerVisibilityListener{
            override fun onVisibilityChanged(visibility: Int) {
                isControlVisibled = if (visibility == 0) {
                    1
                } else {
                    0
                }
            }
        })

        exoplayer?.addAnalyticsListener(object : AnalyticsListener{
            override fun onLoadStarted(
                eventTime: AnalyticsListener.EventTime,
                loadEventInfo: LoadEventInfo,
                mediaLoadData: MediaLoadData
            ) {
            }
        })

        exoplayer?.addListener(object : Player.Listener{

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)
                if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST){
                    if (isAutoPaused.not() && isMediaMelonEnabled){
                        if (playWhenReady){
                            MMAppAnalytics.reportVideoResume(videoID.toString(), mapOf(
                                "assetName" to videoTitle,
                                "position" to exoplayer?.currentPosition?.div(1000)
                            ))
                        }else{
                            MMAppAnalytics.reportVideoPause(videoID.toString(), mapOf(
                                "assetName" to videoTitle,
                                "position" to exoplayer?.currentPosition?.div(1000)
                            ))
                        }
                    }
                    isAutoPaused = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
//                Toast.makeText(this@PlayerActivity, "onPlayerError: ${error.message}", Toast.LENGTH_SHORT).show()

                Log.e("onPlayerError", "${error.message}")
                this@PlayerActivity.finish()
            }

            override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
                if (playbackState == ExoPlayer.STATE_ENDED){
                    isVideoComplete = true
                    if (isMediaMelonEnabled){
                        MMAppAnalytics.reportVideoEnd(videoID.toString(), mapOf(
                            "assetName" to videoTitle
                        ))
                    }
                    videoCompleted = true
                    if (videoID != "0"){
                        PlaybackPreferences.from(this@PlayerActivity, videoID)
                            ?.deleteEpisode(videoID)
                        if (isVideoPlayFromGrid==true){
                            isGridRefreshRequired = true
                        }else if (isVideoPlayFromSearch == true){
                            isRowRefreshForSearchRequired = true
                        }else {
                            isRefreshRequired = true
                        }
                    }
                    this@PlayerActivity.finish()
                }
                if (playbackState == ExoPlayer.STATE_READY){
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.progressBarPlayer.visibility = View.GONE
                    },100)
                }
                if (exoplayer?.isPlaying == true){
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.progressBarPlayer.visibility = View.GONE
                    },100)
                    startHearbeatAnalytic()
                    startCheckingForCuePoint()
                    if (isVideoLive == 0){
                        startCheckingForAdPlay()
                    }
                }
                if (exoplayer?.isBuffering() == true && exoplayer?.isPlayingAd == false) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        binding.progressBarPlayer.visibility = View.VISIBLE
                    },100)
                }
            }

            override fun onPositionDiscontinuity(reason: Int) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK){
                    sendSeekAnalyticsData()
                    val currentPosition = exoplayer?.currentPosition

                    if (currentPosition != null) {
                        if (currentPosition >= nextAdPlayTime){
//                            getVastADData(currentPosition, 744)
                        }else if((lastPosChecked - currentPosition) > 60) {
                            nextAdPlayTime = currentPosition + AD_GAP_TIME
                        }
                    }
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                super.onTracksChanged(tracks)
                updateButtonVisibility()
            }
        })

    }

    private fun startCheckingForAdPlay() {
        disposablesTimer.clear()
        Observable.interval(0,1, TimeUnit.SECONDS, Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    checkAndPlayAdIfSuccess()
                },
                {
                })
            .addTo(disposablesTimer)
    }

    private fun startCheckingForCuePoint(){
        disposablesTimerForCue.clear()
        Observable.interval(0,1, TimeUnit.SECONDS, Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    if (exoplayer?.isPlayingAd?.not() == true && exoplayer?.isPlaying == true && isBRDDialogOpen.not()){
                        checkAndShowCueDialog()
                    }
                },
                { t ->
                })
            .addTo(disposablesTimerForCue)
    }

    private fun checkAndShowCueDialog(){
        val currentPosition = exoplayer?.currentPosition?: 0
        val exoPlayerPos = currentPosition.div(1000)

        if (GlobalData.brdSettings?.enable_bright_data == true && allCuePoints.isEmpty().not() == true && BrightApi.getChoice(this) != BrightApi.Choice.PEER) {
            for (item in allCuePoints) {
                val timeCodeInLong = if (item.timecode.contains(":")){
                    item.timecode.getSecondsFromTheString()
                }else{
                    item.timecode.toLong()
                }
                if (item.type == "optin_ad" && item.active){
                    if (exoPlayerPos > 0 && exoPlayerPos == timeCodeInLong){
                        showVideoDetailDialogForCuePoint()
                        break
                    } else if (exoPlayerPos.toInt() > timeCodeInLong && lastExoPos < timeCodeInLong){
                        showVideoDetailDialogForCuePoint()
                        break
                    }
                }

            }
        }
        lastExoPos = exoPlayerPos.toInt()
    }

    private fun showVideoDetailDialogForCuePoint(){
        exoplayer?.pause()
        isBRDDialogOpen = true
        ConfirmationDialog.showCuePointDialog(context = this,
            videoTitle = videoTitle.toString(),
            videoDescription = videoDescription.toString(),
            videoImage = videoImage.toString(),
            onPositiveButtonClick = { dialog ->
                pauseVideoAndShowBrdDialog()
                dialog.dismiss()
            },
            onNegativeButtonClick = { dialog ->
                isBRDDialogOpen = false
                dialog.dismiss()
                this.finish()
            }
        )
    }

    private fun pauseVideoAndShowBrdDialog() {
        BrightDialog.showDialog(this,
            onAgreeButtonClick = { dialog ->
                BrightApi.externalOptIn(this)
                isBRDDialogOpen = false
                dialog.dismiss()
                exoplayer?.play()

            },
            onDisAgreeButtonClick = { dialog ->
                BrightApi.optOut(this)
                isBRDDialogOpen = false
                dialog.dismiss()
                this.finish()
            }
        )
    }

    private fun checkAndPlayAdIfSuccess() {
        val currentPosition = exoplayer?.currentPosition?:0
        if (currentPosition >= nextAdPlayTime && !isVastCheckingStarted){
            isVastCheckingStarted = true
            getVastADData(currentPosition)
        }
        lastPosChecked = currentPosition
    }

    private fun startHearbeatAnalytic() {
        disposablestimer.clear()
        Observable.interval(0, heartBeatInt.toLong(), TimeUnit.SECONDS, Schedulers.computation())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    sendHeartBeatAnalyticsData()
                },{})
            .addTo(disposablestimer)
    }

    private fun sendStatAnalyticsCalls() {
        val headers =  HashMap<String, String>()
        headers.put("channelName",resources.getString(com.android.common.R.string.app_name))
        headers.put("channelId", resources.getString(com.android.common.R.string.app_channel_ID))
        headers.put("videoTitle","$videoTitle")
        headers.put("videoId","$videoID")
        headers.put("startTime","${intent.getLongExtra(VIDEO_PROGRESS_EXTRA,0)}")
        headers.put("deviceId", deviceId)
        headers.put("platform","FireTV")

        AnalyticsClient.api.callStatsAnalyticsData(headers)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({
                if (it.isSuccessful) {
                    sessionID = it.body()?.sessionId.toString()
                    heartBeatInt = it.body()?.heartbeatInt?: 10
                }
            }, {
            })
            .addTo(disposables)
    }

    private fun sendHeartBeatAnalyticsData() {
        val headers =  HashMap<String, String>()
        headers.put("sessionId", sessionID)

        AnalyticsClient.api.callHeartbeatAnalyticsData(headers)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({

            }, {
            })
            .addTo(disposables)
    }

    private fun sendSeekAnalyticsData() {
        val headers =  HashMap<String, String>()
        val currentProgress = (exoplayer?.currentPosition!!) / 1000
        headers.put("seekTime", "$currentProgress")

        AnalyticsClient.api.callSeekAnalyticsData(sessionID, headers)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({

            }, {
            })
            .addTo(disposables)
    }

    private fun sendEndAnalyticsData() {
        val headers =  HashMap<String, String>()
        headers.put("deviceId", deviceId)
        disposablestimer.clear()
        AnalyticsClient.api.callEndAnalyticsData(sessionID, headers)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeOn(Schedulers.io())
            .subscribe({
                if (it.isSuccessful) {
                    saveProgress()
                }else{
                    saveProgress()
                }
            }, {
                saveProgress()
            })
            .addTo(disposables)
    }

    override fun onPause() {
        super.onPause()
        if (exoplayer != null) {
            exoplayer?.playWhenReady = false
        }
        disposablesTimer.clear()
        // ADD VIDEO INTERRUPTION PAUSE
        videoInterruption?.pause()
    }

    /* override fun onBackPressed() {
         var duration = exoplayer?.duration?:0
         var position = exoplayer?.currentPosition?:0
         sendEndAnalyticsData()
     }*/

    private fun saveProgress() {
        if (videoCompleted.not()) {
            val posToSave = exoplayer?.currentPosition
            if (posToSave != null) {
                if ( posToSave < exoplayer?.contentDuration!! ) {
                    if (videoID != "0"){
                        PlaybackPreferences.from(this, videoID)?.programEpisode =
                            exoplayer?.currentPosition
                    }
                    if (isVideoPlayFromGrid==true){
                        isGridRefreshRequired = true
                    }else if (isVideoPlayFromSearch == true){
                        isRowRefreshForSearchRequired = true
                    }else {
                        isRefreshRequired = true
                    }
                }
                this.finish()
            }
            this.finish()
        } else {
            if (videoID != "0"){
                PlaybackPreferences.from(this, videoID)?.deleteEpisode(videoID)
                if (isVideoPlayFromGrid==true){
                    isGridRefreshRequired = true
                }else if (isVideoPlayFromSearch == true){
                    isRowRefreshForSearchRequired = true
                }else {
                    isRefreshRequired = true
                }
            }
            this.finish()
        }
    }

    override fun onResume() {
        super.onResume()
        if (exoplayer != null) {
            exoplayer?.playWhenReady = true
//            binding.simpleExoPlayerView.hideController()
        }
        // ADD VIDEO INTERRUPTION RESUME
        videoInterruption?.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isVideoComplete.not()){
            if (isMediaMelonEnabled){
                MMAppAnalytics.reportVideoStop(videoID.toString(), mapOf(
                    "position" to exoplayer?.currentPosition?.div(1000),
                    "reason" to "UserStopped"
                ))
            }
        }
        if (isMediaMelonEnabled){
            MMSmartStreamingExo2.getInstance().reportPlayerState(false, Player.STATE_ENDED)
        }
        releasePlayer()
        disposables.clear()
        disposablestimer.clear()
        disposablesTimer.clear()
        disposablesTimerForCue.clear()
        allCuePoints = emptyList()
        _binding = null
        // ADD VIDEO INTERRUPTION RELEASE
        videoInterruption?.release()
        Glide.get(this).clearMemory()
//        System.gc()
    }

    private fun releasePlayer() {
        binding.simpleExoPlayerView.player = null
        exoplayer?.stop()
        exoplayer?.release()
        exoplayer = null
        imaAdsLoader?.setPlayer(null)
        imaAdsLoader?.release()
        imaAdsLoader = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        try {
            if (isControlVisibled == 0 && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                        || keyCode == KeyEvent.KEYCODE_DPAD_DOWN || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_DOWN_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_UP_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                        || keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE
                        || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                        || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND
                        || keyCode == KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD
                        || keyCode == KeyEvent.KEYCODE_MEDIA_STEP_FORWARD
                        || keyCode == KeyEvent.KEYCODE_MEDIA_NEXT
                        || keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        || keyCode == KEYCODE_MEDIA_PLAY_PAUSE)
            ) {
                binding.simpleExoPlayerView.showController()
                return true
            } else if (keyCode == KeyEvent.KEYCODE_BACK) {

                sendEndAnalyticsData()
            }

        } catch (e: Exception) {
            e.message
        }

        return super.onKeyDown(keyCode, event)


    }

    // ADD VIDEO INTERRUPTION SETUP METHOD
    private fun setupVideoInterruption() {
        exoplayer?.let { player ->
            // Get the parent container for overlay
            val parentContainer = findViewById<FrameLayout>(android.R.id.content)
            
            // Initialize video interruption
            videoInterruption = VideoInterruptionIntegration(
                context = this,
                player = player,
                parentContainer = parentContainer
            )
            
            // Set up listener for interruption events
            videoInterruption?.setListener(object : VideoInterruptionIntegration.InterruptionListener {
                override fun onFormSubmitted() {
                    Log.d("VideoInterruption", "User submitted the form")
                    // Add analytics tracking here if needed
                    if (isMediaMelonEnabled) {
                        // Track form submission event
                        MMAppAnalytics.trackEvent("form_submitted")
                    }
                }
                
                override fun onFormSkipped(newPosition: Long) {
                    Log.d("VideoInterruption", "User skipped form, jumped to: $newPosition")
                    // Add analytics tracking here if needed
                    if (isMediaMelonEnabled) {
                        // Track form skip event
                        MMAppAnalytics.trackEvent("form_skipped")
                    }
                }
                
                override fun onTimestampReached(timestamp: String) {
                    Log.d("VideoInterruption", "Reached timestamp: $timestamp")
                }
            })
            
            // Initialize the system with video ID
            val currentVideoId = videoID ?: "6870be205513b72caa32621e" // Use actual video ID or fallback
            videoInterruption?.initialize(currentVideoId)
            
            Log.d("VideoInterruption", "Initializing video interruption for video ID: $currentVideoId")
            
            // Debug: Log current submission status
            Log.d("VideoInterruption", "Current submission status:")
            Log.d("VideoInterruption", "Video has submissions: ${FormSubmissionTracker.hasVideoSubmissions(currentVideoId)}")
            Log.d("VideoInterruption", "Submitted forms count: ${FormSubmissionTracker.getVideoSubmittedFormsCount(currentVideoId)}")
        }
    }
    
    /**
     * Debug method to clear form submissions for current video (for testing)
     * Call this method if you want to reset form submissions for testing
     */
    private fun clearCurrentVideoFormSubmissions() {
        videoID?.let { videoId ->
            FormSubmissionTracker.clearVideoSubmissions(videoId)
            Log.d("VideoInterruption", "Cleared form submissions for video: $videoId")
        }
    }

    companion object {
        const val VIDEO_URL_EXTRA = "video_url_extra"
        const val VIDEO_ID_EXTRA = "video_ID_extra"
        const val VIDEO_NAME_EXTRA = "video_name_extra"
        const val VIDEO_IMAGE_EXTRA = "video_image_extra"
        const val VIDEO_DESCRIPTION_EXTRA = "video_description_extra"
        const val VIDEO_GENRE_EXTRA = "video_genre_extra"
        const val VIDEO_CATEGORY_EXTRA = "video_category_extra"
        const val VIDEO_RATING_EXTRA = "video_rating_extra"
        const val VIDEO_PROGRESS_EXTRA = "video_progress"
        const val VIDEO_DURATION_EXTRA = "video_duration"
        const val VIDEO_SUBTITLE_EXTRA = "video_subtitle"
        const val VIDEO_SERIES_TITLE = "series_title"
        const val VIDEO_SEASON_TITLE = "season_title"
        const val VIDEO_IS_FROM_GRID = "isVideoPlayFromGrid"
        const val VIDEO_IS_FROM_SEARCH = "isVideoPlayFromSearch"
        const val VIDEO_TYPE = "video_type"
        const val VIDEO_IS_LIVE = "video_is_live"
        const val PLAYLIST_VAST_URL = "playlist_vast_url"
        const val CUE_POINT_STRING = "cuepoint_string"

        private const val AD_GAP_TIME: Long = 7 * 60 * 1000

        fun launchActivity(
            activity: Activity,
            videoID: String?,
            videoUrl: String?,
            videoName: String?,
            videoGenre: String?,
            videoCategory: String?,
            videoRating: String?,
            itemPosition: Long?,
            itemDuration: Long?,
            videoSeasonName: String = "",
            videoSeriesName: String = "",
            videoType: String = "",
            isLive: Int,
            videoSubtitleUrl: String,
            isComeFromGrid: Boolean,
            isComeFromSearch: Boolean = false,
            playListVastUrl: String? = "",
            cuePointData: String,
            videoImageUrl: String,
            videoDescription: String
        ) {
            val intent = Intent(activity, PlayerActivity::class.java)
            intent.putExtra(VIDEO_URL_EXTRA, videoUrl)
            intent.putExtra(VIDEO_ID_EXTRA, videoID)
            intent.putExtra(VIDEO_NAME_EXTRA, videoName)
            intent.putExtra(VIDEO_NAME_EXTRA, videoName)
            intent.putExtra(VIDEO_IMAGE_EXTRA, videoImageUrl)
            intent.putExtra(VIDEO_DESCRIPTION_EXTRA, videoDescription)
            intent.putExtra(VIDEO_GENRE_EXTRA, videoGenre)
            intent.putExtra(VIDEO_CATEGORY_EXTRA, videoCategory)
            intent.putExtra(VIDEO_RATING_EXTRA, videoRating)
            intent.putExtra(VIDEO_PROGRESS_EXTRA, itemPosition)
            intent.putExtra(VIDEO_DURATION_EXTRA, itemDuration)
            intent.putExtra(VIDEO_SUBTITLE_EXTRA, videoSubtitleUrl)
            intent.putExtra(VIDEO_IS_LIVE, isLive)
            intent.putExtra(PLAYLIST_VAST_URL, playListVastUrl)
            intent.putExtra(VIDEO_SERIES_TITLE, videoSeriesName)
            intent.putExtra(VIDEO_SEASON_TITLE, videoSeasonName)
            intent.putExtra(VIDEO_TYPE, videoType)
            intent.putExtra(VIDEO_IS_FROM_GRID, isComeFromGrid)
            intent.putExtra(VIDEO_IS_FROM_SEARCH, isComeFromSearch)
            intent.putExtra(CUE_POINT_STRING, cuePointData)

            ActivityCompat.startActivity(
                activity,
                intent,
                null
            )
        }

        fun launchEPGProgramActivity(
            activity: Activity,
            channelID: String,
            channelURL: String,
            programTitle: String
        ) {
            val intent = Intent(activity, PlayerActivity::class.java)
            intent.putExtra(VIDEO_URL_EXTRA, channelURL)
            intent.putExtra(VIDEO_ID_EXTRA, channelID)
            intent.putExtra(VIDEO_NAME_EXTRA, programTitle)
            intent.putExtra(VIDEO_IMAGE_EXTRA, "")
            intent.putExtra(VIDEO_DESCRIPTION_EXTRA, "")
            intent.putExtra(VIDEO_SERIES_TITLE, "")
            intent.putExtra(VIDEO_SEASON_TITLE, "")
            intent.putExtra(VIDEO_GENRE_EXTRA, "")
            intent.putExtra(VIDEO_CATEGORY_EXTRA, "")
            intent.putExtra(VIDEO_RATING_EXTRA, "")
            intent.putExtra(VIDEO_SUBTITLE_EXTRA, "")
            intent.putExtra(VIDEO_PROGRESS_EXTRA, 0)
            intent.putExtra(VIDEO_DURATION_EXTRA, 0)
            intent.putExtra(VIDEO_IS_LIVE, 1)
            intent.putExtra(PLAYLIST_VAST_URL, "")
            intent.putExtra(VIDEO_TYPE, "")
            intent.putExtra(CUE_POINT_STRING, "")

            ActivityCompat.startActivity(
                activity,
                intent,
                null
            )
        }
    }
}