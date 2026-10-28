package io.github.nuhanhabib.dozypal

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/** Join Scroll Battles, share your friend code, add friends, and see the leaderboards. */
class FriendsActivity : Activity() {

    private enum class Board(val app: ReelApp?) {
        BATTLE(null), FACEBOOK(ReelApp.FACEBOOK), INSTAGRAM(ReelApp.INSTAGRAM),
        TIKTOK(ReelApp.TIKTOK), YOUTUBE(ReelApp.YOUTUBE)
    }

    private lateinit var store: CountStore
    private lateinit var unavailable: View
    private lateinit var joinSection: View
    private lateinit var joinedSection: View
    private lateinit var nicknameInput: EditText
    private lateinit var joinError: TextView
    private lateinit var busy: ProgressBar
    private lateinit var codeText: TextView
    private lateinit var friendCodeInput: EditText
    private lateinit var addStatus: TextView
    private lateinit var boardSubtitle: TextView
    private lateinit var boardRows: LinearLayout
    private lateinit var boardTabs: Map<Board, TextView>
    private var board = Board.BATTLE
    private var players: List<Player> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_friends)
        store = CountStore(this)

        unavailable = findViewById(R.id.friends_unavailable)
        joinSection = findViewById(R.id.join_section)
        joinedSection = findViewById(R.id.joined_section)
        nicknameInput = findViewById(R.id.nickname_input)
        joinError = findViewById(R.id.join_error)
        busy = findViewById(R.id.join_busy)
        codeText = findViewById(R.id.my_code)
        friendCodeInput = findViewById(R.id.friend_code_input)
        addStatus = findViewById(R.id.add_status)
        boardSubtitle = findViewById(R.id.board_subtitle)
        boardRows = findViewById(R.id.board_rows)
        boardTabs = mapOf(
            Board.BATTLE to findViewById(R.id.board_battle),
            Board.FACEBOOK to findViewById(R.id.board_facebook),
            Board.INSTAGRAM to findViewById(R.id.board_instagram),
            Board.TIKTOK to findViewById(R.id.board_tiktok),
            Board.YOUTUBE to findViewById(R.id.board_youtube),
        )
        boardTabs.forEach { (b, view) -> view.setOnClickListener { board = b; renderBoard() } }

        findViewById<View>(R.id.friends_back).setOnClickListener { finish() }
        findViewById<View>(R.id.join_button).setOnClickListener { join() }
        findViewById<View>(R.id.share_code).setOnClickListener { shareCode() }
        findViewById<View>(R.id.add_friend).setOnClickListener { addFriend() }
        findViewById<View>(R.id.refresh_board).setOnClickListener { loadBoard() }
        findViewById<View>(R.id.leave_battles).setOnClickListener { confirmLeave() }
        codeText.setOnClickListener { copyCode() }

        store.userName?.let { Social.cleanNickname(it) }?.let(nicknameInput::setText)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (Social.isAvailable(this) && Social.isJoined(this, store) && Social.hasOldCode(store)) {
            return migrateOldCode()
        }
        val available = Social.isAvailable(this)
        val joined = available && Social.isJoined(this, store)
        unavailable.visibility = if (available) View.GONE else View.VISIBLE
        joinSection.visibility = if (available && !joined) View.VISIBLE else View.GONE
        joinedSection.visibility = if (joined) View.VISIBLE else View.GONE
        if (!joined) return

        val code = store.friendCode ?: return
        codeText.text = code
        Social.requestSync(store)
        loadBoard()
    }

    /** Accounts from older versions had 8-character codes: clear them so the user can rejoin with a short one. */
    private fun migrateOldCode() {
        Social.leave(store) { ok ->
            if (ok) {
                showJoinError(getString(R.string.code_changed))
                refresh()
            } else {
                Toast.makeText(this, R.string.friend_network_error, Toast.LENGTH_SHORT).show()
                unavailable.visibility = View.GONE
                joinSection.visibility = View.GONE
                joinedSection.visibility = View.GONE
            }
        }
    }

    // --- Joining ---

    private fun join() {
        val nickname = validNicknameOrShowError() ?: return
        setBusy(true)
        Social.signInAnonymously { error ->
            if (error != null) finishJoin(error) else Social.join(store, nickname, ::finishJoin)
        }
    }

    private fun finishJoin(error: Exception?) {
        setBusy(false)
        if (error != null) showJoinError(getString(R.string.join_failed)) else refresh()
    }

    private fun validNicknameOrShowError(): String? {
        val nickname = Social.cleanNickname(nicknameInput.text.toString())
        if (nickname == null) showJoinError(getString(R.string.nickname_error)) else joinError.visibility = View.GONE
        return nickname
    }

    private fun showJoinError(message: String) {
        joinError.text = message
        joinError.visibility = View.VISIBLE
    }

    private fun setBusy(on: Boolean) {
        busy.visibility = if (on) View.VISIBLE else View.GONE
        findViewById<View>(R.id.join_button).isEnabled = !on
    }

    // --- Friend code ---

    private fun shareCode() {
        val code = store.friendCode ?: return
        val text = getString(R.string.share_text, code)
        startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
            getString(R.string.share_code),
        ))
    }

    private fun copyCode() {
        val code = store.friendCode ?: return
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DozyPal code", code))
        Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
    }

    private fun addFriend() {
        addStatus.visibility = View.VISIBLE
        addStatus.text = getString(R.string.adding_friend)
        Social.addFriend(this, store, friendCodeInput.text.toString()) { error ->
            addStatus.text = error ?: getString(R.string.friend_added)
            if (error == null) {
                friendCodeInput.text.clear()
                loadBoard()
            }
        }
    }

    // --- Leaderboard ---

    private fun loadBoard() {
        Social.loadLeaderboard(store) { result ->
            if (result == null) {
                Toast.makeText(this, R.string.friend_network_error, Toast.LENGTH_SHORT).show()
            } else {
                players = result
                renderBoard()
            }
        }
    }

    private fun score(player: Player): Int = board.app?.let { player.counts[it] ?: 0 } ?: player.total

    private fun renderBoard() {
        boardTabs.forEach { (b, view) -> view.isSelected = b == board }
        val app = board.app
        boardSubtitle.text = if (app == null) getString(R.string.board_battle_subtitle)
        else getString(R.string.board_app_subtitle, app.label)

        boardRows.removeAllViews()
        players.sortedWith(compareBy<Player>({ score(it) }, { it.nickname.lowercase() }))
            .forEachIndexed { index, player -> boardRows.addView(row(index, player)) }
        if (players.size <= 1) {
            boardRows.addView(TextView(this).apply {
                text = getString(R.string.board_empty)
                setTextColor(getColor(R.color.muted))
                setPadding(0, dp(12), 0, 0)
            })
        }
    }

    private fun row(index: Int, player: Player): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        if (player.isMe) setBackgroundResource(R.drawable.segment_bg)

        addView(TextView(context).apply {
            text = when (index) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${index + 1}." }
            textSize = 18f
            setTextColor(getColor(R.color.text))
        }, LinearLayout.LayoutParams(dp(40), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(TextView(context).apply {
            text = if (player.isMe) getString(R.string.board_you, player.nickname) else player.nickname
            textSize = 15f
            setTextColor(getColor(R.color.text))
            if (player.isMe) setTypeface(typeface, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(TextView(context).apply {
            text = score(player).toString()
            textSize = 16f
            setTextColor(getColor(R.color.purple))
            setTypeface(typeface, Typeface.BOLD)
        })

        if (!player.isMe) setOnLongClickListener { confirmRemove(player); true }
    }

    private fun confirmRemove(player: Player) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.remove_friend_title, player.nickname))
            .setPositiveButton(R.string.remove) { _, _ -> Social.removeFriend(player.uid) { loadBoard() } }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmLeave() {
        AlertDialog.Builder(this)
            .setTitle(R.string.leave_title)
            .setMessage(R.string.leave_message)
            .setPositiveButton(R.string.leave) { _, _ ->
                Social.leave(store) { ok ->
                    if (!ok) Toast.makeText(this, R.string.friend_network_error, Toast.LENGTH_SHORT).show()
                    refresh()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
