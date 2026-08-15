package com.bittv.iptv.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.bittv.iptv.R

/**
 * Simple "Tebak Kata" game: a category hint is shown, the word is masked
 * with underscores, and the player types the full word. Independent from
 * the TV/playlist code so it can't affect channel playback.
 */
class GameActivity : AppCompatActivity() {

    private data class Word(val category: String, val answer: String)

    private val wordBank = listOf(
        Word("TV", "REMOTE"),
        Word("TV", "SIARAN"),
        Word("TV", "ANTENA"),
        Word("TV", "CHANNEL"),
        Word("TV", "LAYAR"),
        Word("TV", "SATELIT"),
        Word("TV", "STASIUN"),
        Word("TV", "GAMBAR"),
        Word("HEWAN", "GAJAH"),
        Word("HEWAN", "JERAPAH"),
        Word("HEWAN", "KUCING"),
        Word("HEWAN", "BURUNG"),
        Word("MAKANAN", "RENDANG"),
        Word("MAKANAN", "SATE"),
        Word("MAKANAN", "BAKSO"),
        Word("MAKANAN", "NASGOR"),
        Word("NEGARA", "INDONESIA"),
        Word("NEGARA", "JEPANG"),
        Word("NEGARA", "MALAYSIA"),
        Word("OLAHRAGA", "SEPAKBOLA"),
        Word("OLAHRAGA", "BULUTANGKIS"),
        Word("OLAHRAGA", "RENANG")
    )

    private lateinit var categoryText: TextView
    private lateinit var blanksText: TextView
    private lateinit var feedbackText: TextView
    private lateinit var scoreText: TextView
    private lateinit var answerInput: EditText

    private var currentWord: Word = wordBank.first()
    private var score = 0
    private val usedIndexes = mutableSetOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val root = findViewById<android.view.View>(R.id.gameTopBar)
        val rootTopPadding = root.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = rootTopPadding + bars.top)
            insets
        }

        categoryText = findViewById(R.id.gameCategoryText)
        blanksText = findViewById(R.id.gameWordBlanksText)
        feedbackText = findViewById(R.id.gameFeedbackText)
        scoreText = findViewById(R.id.gameScoreText)
        answerInput = findViewById(R.id.gameAnswerInput)

        findViewById<Button>(R.id.gameBackButton).setOnClickListener { finish() }
        findViewById<Button>(R.id.gameSkipButton).setOnClickListener { nextWord(reveal = true) }
        findViewById<Button>(R.id.gameSubmitButton).setOnClickListener { checkAnswer() }

        answerInput.setOnEditorActionListener { _, actionId, event ->
            val isDone = actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
            if (isDone) checkAnswer()
            isDone
        }

        nextWord(reveal = false)
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }

    private fun checkAnswer() {
        val guess = answerInput.text.toString().trim()
        if (guess.isEmpty()) return

        if (guess.equals(currentWord.answer, ignoreCase = true)) {
            bumpScore()
            feedbackText.setTextColor(resources.getColor(com.bittv.iptv.R.color.live_dot, theme))
            feedbackText.text = "Benar! 🎉"
            popIn(feedbackText)
            popIn(blanksText)
            answerInput.postDelayed({ nextWord(reveal = false) }, 700)
        } else {
            feedbackText.setTextColor(resources.getColor(com.bittv.iptv.R.color.accent, theme))
            feedbackText.text = "Belum tepat, coba lagi"
            shake(answerInput)
        }
    }

    private fun bumpScore() {
        score += 1
        scoreText.text = "Skor: $score"
        popIn(scoreText, fromScale = 1.35f)
    }

    /** Efek "pop" ringan: elemen sedikit membesar lalu balik normal. */
    private fun popIn(view: View, fromScale: Float = 1.18f) {
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /** Efek "shake" ringan buat jawaban salah, biar keliatan responsif. */
    private fun shake(view: View) {
        val animator = ObjectAnimator.ofFloat(
            view, View.TRANSLATION_X,
            0f, -18f, 18f, -14f, 14f, -6f, 6f, 0f
        )
        animator.duration = 380
        animator.start()
    }

    /** Fade halus saat kata/kategori berganti, biar gak berubah mendadak. */
    private fun fadeSwap(view: View, update: () -> Unit) {
        view.animate()
            .alpha(0f)
            .setDuration(120)
            .withEndAction {
                update()
                view.alpha = 0f
                view.animate().alpha(1f).setDuration(180).start()
            }
            .start()
    }

    private fun nextWord(reveal: Boolean) {
        if (reveal) {
            feedbackText.setTextColor(resources.getColor(com.bittv.iptv.R.color.text_secondary, theme))
            feedbackText.text = "Jawabannya: ${currentWord.answer}"
        } else {
            feedbackText.text = ""
        }

        if (usedIndexes.size >= wordBank.size) usedIndexes.clear()
        var index: Int
        do {
            index = wordBank.indices.random()
        } while (index in usedIndexes && usedIndexes.size < wordBank.size)
        usedIndexes.add(index)

        currentWord = wordBank[index]

        fadeSwap(categoryText) { categoryText.text = "KATEGORI: ${currentWord.category}" }
        fadeSwap(blanksText) { blanksText.text = currentWord.answer.map { "_" }.joinToString(" ") }

        answerInput.setText("")
        answerInput.requestFocus()
    }
}
