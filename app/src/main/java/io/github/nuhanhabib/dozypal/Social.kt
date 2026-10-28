package io.github.nuhanhabib.dozypal

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import java.security.SecureRandom
import java.time.LocalDate

data class Player(
    val uid: String,
    val nickname: String,
    val counts: Map<ReelApp, Int>,
    val isMe: Boolean,
) {
    val total: Int get() = counts.values.sum()
}

/**
 * Optional Scroll Battles: anonymous sign-in, the user's public profile (nickname + today's counts),
 * friends added by code, and the leaderboard. Everything is protected by firestore.rules.
 * Nothing here runs unless the user joins.
 */
object Social {

    private const val CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 5
    // The rules allow one save per 10 s; stay well above that.
    private const val SYNC_INTERVAL_MS = 30_000L
    private val NICKNAME_PATTERN = Regex("^[\\p{L}\\p{M}0-9 _.-]{3,20}$")

    private val random = SecureRandom()
    private val handler = Handler(Looper.getMainLooper())
    private var lastSyncAt = 0L
    private var syncScheduled = false

    private val auth get() = FirebaseAuth.getInstance()
    private val db get() = FirebaseFirestore.getInstance()
    private fun users() = db.collection("users")
    private fun friendsOf(uid: String) = users().document(uid).collection("friends")
    private fun codes() = db.collection("friendCodes")

    /** False when the APK was built without a Firebase config. */
    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    val uid: String? get() = auth.currentUser?.uid

    fun isJoined(context: Context, store: CountStore): Boolean =
        isAvailable(context) && uid != null && store.friendCode != null

    fun cleanNickname(raw: String): String? {
        val name = raw.trim().replace(Regex("\\s+"), " ")
        return name.takeIf { NICKNAME_PATTERN.matches(it) }
    }

    /** Accepts "k7m2q", "K7M-2Q" etc. Returns null if it can't be a valid code. */
    fun normalizeCode(raw: String): String? {
        val code = raw.uppercase().filter { it.isLetterOrDigit() }
        return code.takeIf { it.length == CODE_LENGTH && it.all { c -> c in CODE_ALPHABET } }
    }

    /** Codes from older versions were 8 characters; those accounts have to rejoin to get a short code. */
    fun hasOldCode(store: CountStore): Boolean = store.friendCode?.let { normalizeCode(it) == null } ?: false

    fun signInAnonymously(onDone: (Exception?) -> Unit) {
        if (uid != null) return onDone(null)
        auth.signInAnonymously()
            .addOnSuccessListener { onDone(null) }
            .addOnFailureListener { onDone(it) }
    }

    /** Creates the public profile and friend code, or reuses them if this account already has one. */
    fun join(store: CountStore, nickname: String, onDone: (Exception?) -> Unit) {
        val me = uid ?: return onDone(IllegalStateException("Not signed in"))
        users().document(me).get()
            .addOnSuccessListener { existing ->
                val existingCode = existing.getString("friendCode")
                if (existing.exists() && existingCode != null && normalizeCode(existingCode) != null) {
                    store.friendCode = existingCode
                    store.nickname = nickname
                    lastSyncAt = 0L
                    requestSync(store)
                    onDone(null)
                } else if (existing.exists()) {
                    // An old-format profile: remove it first, then start fresh with a short code.
                    wipe(me, existingCode) { ok ->
                        if (ok) createProfile(store, me, nickname, attempt = 0, onDone)
                        else onDone(IllegalStateException("Couldn't remove old profile"))
                    }
                } else {
                    createProfile(store, me, nickname, attempt = 0, onDone)
                }
            }
            .addOnFailureListener { onDone(it) }
    }

    private fun createProfile(store: CountStore, me: String, nickname: String, attempt: Int, onDone: (Exception?) -> Unit) {
        val code = newCode()
        db.batch()
            .set(codes().document(code), mapOf("uid" to me))
            .set(users().document(me), profile(store, nickname, code))
            .commit()
            .addOnSuccessListener {
                store.friendCode = code
                store.nickname = nickname
                lastSyncAt = SystemClock.elapsedRealtime()
                onDone(null)
            }
            .addOnFailureListener { e ->
                // A code collision makes the write an (unallowed) update; just try another code.
                if (attempt < 2) createProfile(store, me, nickname, attempt + 1, onDone) else onDone(e)
            }
    }

    /** Uploads today's counts, at most once every [SYNC_INTERVAL_MS]. Safe to call often. */
    fun requestSync(store: CountStore) {
        if (uid == null || store.friendCode == null || syncScheduled) return
        val wait = (SYNC_INTERVAL_MS - (SystemClock.elapsedRealtime() - lastSyncAt)).coerceAtLeast(0)
        syncScheduled = true
        handler.postDelayed({
            syncScheduled = false
            val me = uid ?: return@postDelayed
            val code = store.friendCode ?: return@postDelayed
            val nickname = store.nickname ?: return@postDelayed
            lastSyncAt = SystemClock.elapsedRealtime()
            users().document(me).set(profile(store, nickname, code))
        }, wait)
    }

    /** Adds a friend by code on both sides. Calls back with a user-facing error message, or null on success. */
    fun addFriend(context: Context, store: CountStore, rawCode: String, onDone: (String?) -> Unit) {
        val code = normalizeCode(rawCode) ?: return onDone(context.getString(R.string.friend_bad_code))
        if (code == store.friendCode) return onDone(context.getString(R.string.friend_own_code))
        val me = uid ?: return onDone(context.getString(R.string.friend_network_error))

        codes().document(code).get()
            .addOnSuccessListener { codeDoc ->
                val friend = codeDoc.getString("uid") ?: return@addOnSuccessListener onDone(context.getString(R.string.friend_not_found))
                friendsOf(me).document(friend).get()
                    .addOnSuccessListener { already ->
                        if (already.exists()) return@addOnSuccessListener onDone(context.getString(R.string.friend_already))
                        val link = mapOf("code" to code, "addedAt" to FieldValue.serverTimestamp())
                        db.batch()
                            .set(friendsOf(me).document(friend), link)
                            .set(friendsOf(friend).document(me), link)
                            .commit()
                            .addOnSuccessListener { onDone(null) }
                            .addOnFailureListener { onDone(context.getString(R.string.friend_network_error)) }
                    }
                    .addOnFailureListener { onDone(context.getString(R.string.friend_network_error)) }
            }
            .addOnFailureListener { onDone(context.getString(R.string.friend_network_error)) }
    }

    /** You plus all friends, with today's counts (friends who haven't scrolled today show 0). */
    fun loadLeaderboard(store: CountStore, onDone: (List<Player>?) -> Unit) {
        val me = uid ?: return onDone(null)
        friendsOf(me).get()
            .addOnSuccessListener { friends ->
                val reads = friends.documents.map { users().document(it.id).get() }
                Tasks.whenAllComplete(reads).addOnCompleteListener {
                    val today = LocalDate.now().toString()
                    val others = reads.mapNotNull { task ->
                        if (task.isSuccessful) task.result?.takeIf { it.exists() }?.let { toPlayer(it, today) } else null
                    }
                    val mine = Player(
                        uid = me,
                        nickname = store.nickname ?: Nudges.DEFAULT_NAME,
                        counts = ReelApp.entries.associateWith { store.count(it) },
                        isMe = true,
                    )
                    onDone(others + mine)
                }
            }
            .addOnFailureListener { onDone(null) }
    }

    fun removeFriend(friend: String, onDone: (Boolean) -> Unit) {
        val me = uid ?: return onDone(false)
        db.batch()
            .delete(friendsOf(me).document(friend))
            .delete(friendsOf(friend).document(me))
            .commit()
            .addOnCompleteListener { onDone(it.isSuccessful) }
    }

    /** Deletes the profile, friend code and all friendships, then signs out. */
    fun leave(store: CountStore, onDone: (Boolean) -> Unit) {
        val me = uid ?: return onDone(false)
        wipe(me, store.friendCode) { ok ->
            if (ok) {
                store.friendCode = null
                store.nickname = null
                val user = auth.currentUser
                if (user != null && user.isAnonymous) user.delete()
                auth.signOut()
            }
            onDone(ok)
        }
    }

    private fun wipe(me: String, code: String?, onDone: (Boolean) -> Unit) {
        friendsOf(me).get()
            .addOnSuccessListener { friends ->
                val batch = db.batch()
                friends.documents.forEach {
                    batch.delete(friendsOf(me).document(it.id))
                    batch.delete(friendsOf(it.id).document(me))
                }
                if (code != null) batch.delete(codes().document(code))
                batch.delete(users().document(me))
                batch.commit().addOnCompleteListener { onDone(it.isSuccessful) }
            }
            .addOnFailureListener { onDone(false) }
    }

    private fun profile(store: CountStore, nickname: String, code: String): Map<String, Any> = mapOf(
        "nickname" to nickname,
        "friendCode" to code,
        "avatar" to if (store.isFemale) CountStore.GENDER_FEMALE else CountStore.GENDER_MALE,
        "day" to LocalDate.now().toString(),
        "counts" to ReelApp.entries.associate { it.name to store.count(it) },
        "total" to store.total(),
        "updatedAt" to FieldValue.serverTimestamp(),
    )

    private fun toPlayer(doc: DocumentSnapshot, today: String): Player {
        val isToday = doc.getString("day") == today
        val counts = doc.get("counts") as? Map<*, *> ?: emptyMap<String, Any>()
        return Player(
            uid = doc.id,
            nickname = doc.getString("nickname") ?: "?",
            counts = ReelApp.entries.associateWith { app ->
                if (isToday) (counts[app.name] as? Number)?.toInt() ?: 0 else 0
            },
            isMe = false,
        )
    }

    private fun newCode(): String =
        (1..CODE_LENGTH).map { CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)] }.joinToString("")
}
