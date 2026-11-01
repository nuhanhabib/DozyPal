package io.github.nuhanhabib.dozypal

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: CountStore
    private lateinit var character: CharacterView
    private lateinit var greeting: TextView
    private lateinit var moodTitle: TextView
    private lateinit var moodLine: TextView
    private lateinit var totalCount: TextView
    private lateinit var appCounts: Map<ReelApp, TextView>
    private lateinit var setupCard: View
    private lateinit var restrictedGroup: View
    private lateinit var counterSwitch: Switch
    private lateinit var sideGroup: RadioGroup
    private lateinit var opacityLabel: TextView
    private lateinit var opacitySeek: SeekBar
    private lateinit var limitSwitch: Switch
    private lateinit var limitSettings: View
    private lateinit var limitValue: TextView
    private lateinit var limitSoundSwitch: Switch
    private lateinit var limitPresets: Map<Int, TextView>
    private lateinit var limitProgressGroup: View
    private lateinit var limitProgress: ProgressBar
    private lateinit var limitProgressText: TextView

    // Counts change in the background service; refresh the screen live when they do.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render(animate = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CountStore(this)

        character = findViewById(R.id.character)
        greeting = findViewById(R.id.greeting)
        moodTitle = findViewById(R.id.mood_title)
        moodLine = findViewById(R.id.mood_line)
        totalCount = findViewById(R.id.total_count)
        appCounts = mapOf(
            ReelApp.FACEBOOK to findViewById(R.id.facebook_count),
            ReelApp.INSTAGRAM to findViewById(R.id.instagram_count),
            ReelApp.TIKTOK to findViewById(R.id.tiktok_count),
            ReelApp.YOUTUBE to findViewById(R.id.youtube_count),
        )
        setupCard = findViewById(R.id.setup_card)
        restrictedGroup = findViewById(R.id.restricted_group)
        counterSwitch = findViewById(R.id.counter_switch)
        sideGroup = findViewById(R.id.side_group)
        opacityLabel = findViewById(R.id.opacity_label)
        opacitySeek = findViewById(R.id.opacity_seek)
        limitSwitch = findViewById(R.id.limit_switch)
        limitSettings = findViewById(R.id.limit_settings)
        limitValue = findViewById(R.id.limit_value)
        limitSoundSwitch = findViewById(R.id.limit_sound_switch)
        limitProgressGroup = findViewById(R.id.limit_progress_group)
        limitProgress = findViewById(R.id.limit_progress)
        limitProgressText = findViewById(R.id.limit_progress_text)
        setUpLimit()

        findViewById<Button>(R.id.setup_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.restricted_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        findViewById<View>(R.id.edit_profile).setOnClickListener { openOnboarding() }
        findViewById<View>(R.id.open_reports).setOnClickListener {
            startActivity(Intent(this, ReportsActivity::class.java))
        }
        findViewById<View>(R.id.open_battles).setOnClickListener {
            startActivity(Intent(this, FriendsActivity::class.java))
        }
        restrictedGroup.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE

        counterSwitch.isChecked = store.counterEnabled
        counterSwitch.setOnCheckedChangeListener { _, on -> store.counterEnabled = on }

        sideGroup.check(if (store.overlayOnLeft) R.id.side_left else R.id.side_right)
        sideGroup.setOnCheckedChangeListener { _, id -> store.overlayOnLeft = id == R.id.side_left }

        opacitySeek.max = 100 - CountStore.MIN_OPACITY
        opacitySeek.progress = store.overlayOpacity - CountStore.MIN_OPACITY
        opacitySeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) store.overlayOpacity = progress + CountStore.MIN_OPACITY
            }
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
    }

    override fun onResume() {
        super.onResume()
        if (store.userName == null) {
            openOnboarding()
            return
        }
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        setupCard.visibility = if (isCounterServiceOn()) View.GONE else View.VISIBLE
        render(animate = false)
        if (Social.isJoined(this, store)) Social.requestSync(store)
    }

    override fun onPause() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onPause()
    }

    private fun render(animate: Boolean) {
        val total = store.total()
        val mood = Mood.forCount(total)
        val name = store.userName ?: Nudges.DEFAULT_NAME
        greeting.text = getString(R.string.greeting, name)
        totalCount.text = total.toString()
        appCounts.forEach { (app, view) -> view.text = store.count(app).toString() }
        moodTitle.text = "${mood.emoji} ${mood.title}"
        moodLine.text = mood.line
        character.isGirl = store.isFemale
        character.setReelCount(total, animate)
        counterSwitch.isChecked = store.counterEnabled
        opacityLabel.text = getString(R.string.opacity_title, store.overlayOpacity)
        renderLimit(total)
    }

    private fun setUpLimit() {
        val presetsRow = findViewById<LinearLayout>(R.id.limit_presets)
        limitPresets = DailyLimit.PRESETS.associateWith { value ->
            TextView(this, null, 0, R.style.SegmentTab).apply {
                text = value.toString()
                setOnClickListener { store.dailyLimit = value }
                presetsRow.addView(this, LinearLayout.LayoutParams(0, (40 * resources.displayMetrics.density).toInt(), 1f))
            }
        }
        limitSwitch.setOnCheckedChangeListener { _, on -> if (on != store.limitEnabled) store.limitEnabled = on }
        limitSoundSwitch.setOnCheckedChangeListener { _, on -> store.limitSound = on }
        val minus = findViewById<Button>(R.id.limit_minus)
        val plus = findViewById<Button>(R.id.limit_plus)
        minus.setOnClickListener { changeLimit(-DailyLimit.STEP) }
        plus.setOnClickListener { changeLimit(DailyLimit.STEP) }
        minus.setOnLongClickListener { changeLimit(-5 * DailyLimit.STEP); true }
        plus.setOnLongClickListener { changeLimit(5 * DailyLimit.STEP); true }
    }

    /** Moves the limit to the next multiple of [by]'s size, so 205 + 50 gives 250. */
    private fun changeLimit(by: Int) {
        val size = kotlin.math.abs(by)
        val current = store.dailyLimit
        val next = if (by > 0) (current / size + 1) * size else ((current + size - 1) / size - 1) * size
        store.dailyLimit = next
    }

    private fun renderLimit(total: Int) {
        val enabled = store.limitEnabled
        val limit = store.dailyLimit
        limitSwitch.isChecked = enabled
        limitSoundSwitch.isChecked = store.limitSound
        limitSettings.alpha = if (enabled) 1f else 0.45f
        setEnabledDeep(limitSettings, enabled)
        limitValue.text = limit.toString()
        limitPresets.forEach { (value, view) -> view.isSelected = value == limit }

        limitProgressGroup.visibility = if (enabled) View.VISIBLE else View.GONE
        if (!enabled) return
        limitProgress.max = limit
        limitProgress.progress = total.coerceAtMost(limit)
        val color = when {
            total >= limit -> 0xFFDC2626.toInt()
            total >= limit * 0.8f -> 0xFFF59E0B.toInt()
            else -> getColor(R.color.purple)
        }
        limitProgress.progressTintList = android.content.res.ColorStateList.valueOf(color)
        limitProgressText.text = when {
            total > limit -> getString(R.string.limit_progress_over, total, limit, total - limit)
            total == limit -> getString(R.string.limit_progress_reached, total, limit)
            else -> getString(R.string.limit_progress_left, total, limit, limit - total)
        }
    }

    private fun setEnabledDeep(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) setEnabledDeep(view.getChildAt(i), enabled)
    }

    private fun openOnboarding() {
        startActivity(Intent(this, OnboardingActivity::class.java))
    }

    private fun isCounterServiceOn(): Boolean {
        val expected = ComponentName(this, ReelCounterService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
