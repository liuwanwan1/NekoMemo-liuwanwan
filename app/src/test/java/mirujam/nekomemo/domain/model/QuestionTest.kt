package mirujam.nekomemo.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionTest {

    private fun question(consecutiveCorrect: Int) = Question(
        id = 1,
        questionBankId = 1,
        text = "q",
        options = listOf("A", "B"),
        correctIndices = listOf(0),
        type = QuestionType.SINGLE_CHOICE,
        consecutiveCorrect = consecutiveCorrect
    )

    @Test
    fun isMastered_falseBelowThreshold() {
        assertFalse(question(consecutiveCorrect = 0).isMastered)
        assertFalse(question(consecutiveCorrect = Question.MASTERY_THRESHOLD - 1).isMastered)
    }

    @Test
    fun isMastered_trueAtOrAboveThreshold() {
        assertTrue(question(consecutiveCorrect = Question.MASTERY_THRESHOLD).isMastered)
        assertTrue(question(consecutiveCorrect = Question.MASTERY_THRESHOLD + 5).isMastered)
    }
}
