package br.edu.ifsp.scl.persistence.statement

import br.edu.ifsp.scl.persistence.*
import br.edu.ifsp.scl.persistence.account.AccountEntity
import br.edu.ifsp.scl.persistence.transaction.TransactionData.Frequency.Daily
import br.edu.ifsp.scl.persistence.transaction.TransactionData.Frequency.Weekly
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.*

internal class AccountBalanceTest : DatabaseTest() {
    private lateinit var account: AccountEntity
    private val AccountEntity.currentTotalBalance get() = totalBalanceAt(defaultDate)
    private infix fun AccountEntity.totalBalanceAt(date: Date) = statementDao.balanceAt(this, date) getting { observedValue!! }

    @Before
    fun createAccount() {
        account = insertAccount()
    }
    
    @Test
    fun accountBalanceBeforeFirstTransactionShouldBeZero() {
        credit(at = date(2, 1, 2020), into = account)
        account totalBalanceAt date(1, 1, 2020) shouldBe 0.0
    }

    @Test
    fun accountBalanceShouldIncrementAfterCredit() {
        val before = account.currentTotalBalance
        credit(into = account)
        val after = account.currentTotalBalance
        assertTrue(after > before)
    }

    @Test
    fun accountBalanceShouldDecrementAfterDebit() {
        val before = account.currentTotalBalance
        debit(from = account)
        val after = account.currentTotalBalance
        assertTrue(after < before)
    }

    @Test
    fun accountBalanceShouldIncrementAfterReceivedTransference() {
        val before = account.currentTotalBalance
        transfer(from = insertAccount(), to = account)
        val after = account.currentTotalBalance
        assertTrue(after > before)
    }

    @Test
    fun accountBalanceShouldDecrementAfterSentTransference() {
        val before = account.currentTotalBalance
        transfer(from = account, to = insertAccount())
        val after = account.currentTotalBalance
        assertTrue(after < before)
    }

    @Test
    fun accountBalanceAtTransactionDateShouldConsiderIt() {
        credit(into = account)
        account.currentTotalBalance shouldBe defaultValue
    }

    @Test
    fun accountBalanceAfterTransactionDateShouldConsiderRepetitionAndFrequency() {
        credit(into = account, repeating = Daily, during = 5)
        account totalBalanceAt date(6, 1, 2020) shouldBe defaultValue * 5
    }

    @Test
    fun accountBalanceShouldConsiderRepeatingTransactionsWithDifferentFrequencies() {
        credit(into = account, repeating = Daily, during = 5)
        debit(from = account, repeating = Weekly, during = 3)
        account totalBalanceAt date(16, 1, 2020) shouldBe defaultValue * (5 - 3)
    }

    @Test
    fun accountBalanceShouldBeIndependentOfAnotherAccounts() {
        credit(20.0, into = account)
        credit(45.0, into = insertAccount())
        account.currentTotalBalance shouldBe 20.0
    }
}