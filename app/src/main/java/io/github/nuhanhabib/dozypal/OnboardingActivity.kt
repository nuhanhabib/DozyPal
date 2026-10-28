package io.github.nuhanhabib.dozypal

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView

/** Welcome screen: asks for the user's name and gender. Shown on first launch and from "Edit profile". */
class OnboardingActivity : Activity() {

    private lateinit var store: CountStore
    private lateinit var nameInput: EditText
    private lateinit var genderGroup: RadioGroup
    private lateinit var error: TextView
    private lateinit var preview: CharacterView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_onboarding)
        store = CountStore(this)

        nameInput = findViewById(R.id.name_input)
        genderGroup = findViewById(R.id.gender_group)
        error = findViewById(R.id.onboarding_error)
        preview = findViewById(R.id.onboarding_character)

        store.userName?.let(nameInput::setText)
        store.gender?.let {
            genderGroup.check(if (it == CountStore.GENDER_FEMALE) R.id.gender_female else R.id.gender_male)
        }
        preview.isGirl = store.isFemale
        preview.setReelCount(0, animate = false)
        genderGroup.setOnCheckedChangeListener { _, id -> preview.isGirl = id == R.id.gender_female }

        findViewById<Button>(R.id.onboarding_continue).setOnClickListener { save() }
    }

    private fun save() {
        val name = CountStore.cleanName(nameInput.text.toString())
        val genderId = genderGroup.checkedRadioButtonId
        val problem = when {
            name == null -> getString(R.string.error_name)
            genderId == View.NO_ID -> getString(R.string.error_gender)
            else -> null
        }
        if (problem != null) {
            error.text = problem
            error.visibility = View.VISIBLE
            return
        }
        store.userName = name
        store.gender = if (genderId == R.id.gender_female) CountStore.GENDER_FEMALE else CountStore.GENDER_MALE
        finish()
    }

    @Deprecated("Still the simplest way to block leaving the first-run screen on all supported versions.")
    override fun onBackPressed() {
        // The profile is required the first time; leaving closes the app instead of skipping it.
        if (store.userName == null) finishAffinity() else super.onBackPressed()
    }
}
