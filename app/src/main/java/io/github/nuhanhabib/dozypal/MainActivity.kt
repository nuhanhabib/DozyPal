package io.github.nuhanhabib.dozypal

import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Switch
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var store: CountStore
    private lateinit var character: CharacterView
    private lateinit var moodTitle: TextView
    private lateinit var moodLine: TextView
    private lateinit var totalCount: TextView
    private lateinit var facebookCount: TextView
    private lateinit var instagramCount: TextView
    private lateinit var setupCard: View
    private lateinit var restrictedGroup: View
    private lateinit var counterSwitch: Switch
    private lateinit var sideGroup: RadioGroup
    private lateinit var characterGroup: RadioGroup

    // Counts change in the background service; refresh the screen live when they do.
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> render(animate = true) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CountStore(this)

        character = findViewById(R.id.character)
        moodTitle = findViewById(R.id.mood_title)
        moodLine = findViewById(R.id.mood_line)
        totalCount = findViewById(R.id.total_count)
        facebookCount = findViewById(R.id.facebook_count)
        instagramCount = findViewById(R.id.instagram_count)
        setupCard = findViewById(R.id.setup_card)
        restrictedGroup = findViewById(R.id.restricted_group)
        counterSwitch = findViewById(R.id.counter_switch)
        sideGroup = findViewById(R.id.side_group)
        characterGroup = findViewById(R.id.character_group)

        findViewById<Button>(R.id.setup_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.restricted_button).setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
        restrictedGroup.visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) View.VISIBLE else View.GONE

        counterSwitch.isChecked = store.counterEnabled
        counterSwitch.setOnCheckedChangeListener { _, on -> store.counterEnabled = on }

        sideGroup.check(if (store.overlayOnLeft) R.id.side_left else R.id.side_right)
        sideGroup.setOnCheckedChangeListener { _, id -> store.overlayOnLeft = id == R.id.side_left }

        syncCharacterChoice()
        characterGroup.setOnCheckedChangeListener { _, id ->
            store.character = if (id == R.id.character_girl) CountStore.CHARACTER_GIRL else CountStore.CHARACTER_BOY
        }

        if (store.character == null) askForCharacter()
    }

    override fun onResume() {
        super.onResume()
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
        totalCount.text = total.toString()
        facebookCount.text = store.count(ReelApp.FACEBOOK).toString()
        instagramCount.text = store.count(ReelApp.INSTAGRAM).toString()
        moodTitle.text = "${mood.emoji} ${mood.title}"
        moodLine.text = mood.line
        character.isGirl = store.character == CountStore.CHARACTER_GIRL
        character.setReelCount(total, animate)
        counterSwitch.isChecked = store.counterEnabled
    }

    private fun askForCharacter() {
        AlertDialog.Builder(this)
            .setTitle(R.string.pick_title)
            .setItems(arrayOf(getString(R.string.boy), getString(R.string.girl))) { _, which ->
                store.character = if (which == 1) CountStore.CHARACTER_GIRL else CountStore.CHARACTER_BOY
                syncCharacterChoice()
            }
            .setCancelable(false)
            .show()
    }

    private fun syncCharacterChoice() {
        characterGroup.check(
            if (store.character == CountStore.CHARACTER_GIRL) R.id.character_girl else R.id.character_boy
        )
    }

    private fun isCounterServiceOn(): Boolean {
        val expected = ComponentName(this, ReelCounterService::class.java)
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}
