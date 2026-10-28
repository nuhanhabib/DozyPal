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
    private lateinit var facebookCount: TextView
    private lateinit var instagramCount: TextView
    private lateinit var setupCard: View
    private lateinit var restrictedGroup: View
    private lateinit var counterSwitch: Switch
    private lateinit var sideGroup: RadioGroup
    private lateinit var opacityLabel: TextView
    private lateinit var opacitySeek: SeekBar
    private lateinit var profileSummary: TextView

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
        facebookCount = findViewById(R.id.facebook_count)
        instagramCount = findViewById(R.id.instagram_count)
        setupCard = findViewById(R.id.setup_card)
        restrictedGroup = findViewById(R.id.restricted_group)
        counterSwitch = findViewById(R.id.counter_switch)
        sideGroup = findViewById(R.id.side_group)
        opacityLabel = findViewById(R.id.opacity_label)
        opacitySeek = findViewById(R.id.opacity_seek)
        profileSummary = findViewById(R.id.profile_summary)

        findViewById<Button>(R.id.setup_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.restricted_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        findViewById<Button>(R.id.edit_profile).setOnClickListener { openOnboarding() }
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
        facebookCount.text = store.count(ReelApp.FACEBOOK).toString()
        instagramCount.text = store.count(ReelApp.INSTAGRAM).toString()
        moodTitle.text = "${mood.emoji} ${mood.title}"
        moodLine.text = mood.line
        character.isGirl = store.isFemale
        character.setReelCount(total, animate)
        counterSwitch.isChecked = store.counterEnabled
        opacityLabel.text = getString(R.string.opacity_title, store.overlayOpacity)
        profileSummary.text = getString(
            R.string.profile_summary, name, getString(if (store.isFemale) R.string.female else R.string.male)
        )
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
