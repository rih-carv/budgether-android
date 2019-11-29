package br.edu.ifsp.scl.persistence.transaction

import br.edu.ifsp.scl.persistence.*
import br.edu.ifsp.scl.persistence.account.Account
import br.edu.ifsp.scl.persistence.transaction.Transaction.*
import br.edu.ifsp.scl.persistence.transaction.Transaction.Transference.RelativeKind.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.*

class TransactionCRUDTest : DatabaseTest() {
    private val sampleDate = Date()
    private fun sampleTransactionData(
        account: Account,
        title: String = "Sample",
        category: String = "Sample",
        date: Date = sampleDate
    ) = sampleTransactionData(title, category, startDate = date, accountId = account.id)

    @Test
    fun insertedTransactionsShouldAppearInSelect() {
        val data = sampleTransactionData(insertAccount())

        val credit = insert(Credit(data))
        val debit = insert(Debit(data))
        val transference = insert(Transference(data, insertAccount().id))

        transactionDao.allTransactions().observedValue?.shouldContain(credit, debit, transference)
    }

    @Test
    fun insertedTransactionsShouldAppearOnlyInRespectiveAccountsSelect() {
        val account = insertAccount()
        val secondAccount = insertAccount()
        val data = sampleTransactionData(account)

        val credit = insert(Credit(data))
        val debit = insert(Debit(data))
        val transference = insert(Transference(data, secondAccount.id))

        transactionDao.allTransactionsOf(account).observedValue?.shouldContain(credit, debit, transference)
        transactionDao.allTransactionsOf(secondAccount).observedValue?.shouldNotContain(credit, debit)
        transactionDao.allTransactionsOf(secondAccount).observedValue?.shouldContain(transference)
    }

    @Test
    fun insertedTransactionsShouldBeDifferent() {
        val data = sampleTransactionData(insertAccount())

        insert(Credit(data))
        insert(Credit(data))

        insert(Debit(data))
        insert(Debit(data))

        insert(Transference(data, insertAccount().id))
        insert(Transference(data, insertAccount().id))

        transactionDao.allCreditTransactions().observedValue?.let {
            it.first() shouldBeDifferentFrom it.last()
        }

        transactionDao.allDebitTransactions().observedValue?.let {
            it.first() shouldBeDifferentFrom it.last()
        }

        transactionDao.allTransferenceTransactions().observedValue?.let {
            it.first() shouldBeDifferentFrom it.last()
        }
    }

    @Test
    fun nearestTransactionShouldBeTheOneWithCloserStartDate() {
        fun Int.daysFromNow() = Calendar.getInstance().also { it.add(Calendar.DATE, this) }.time

        val account = insertAccount()
        val beforeYesterdayTransaction = sampleTransactionData(account, date = (-2).daysFromNow())
        val yesterdayTransaction = sampleTransactionData(account, date = (-1).daysFromNow())
        val tomorrowTransaction = sampleTransactionData(account, date = 1.daysFromNow())
        val afterTomorrowTransaction = sampleTransactionData(account, date = 2.daysFromNow())

        insert(Credit(beforeYesterdayTransaction))
        val yesterdayCredit = insert(Credit(yesterdayTransaction))
        insert(Credit(afterTomorrowTransaction))

        insert(Debit(beforeYesterdayTransaction))
        val tomorrowDebit = insert(Debit(tomorrowTransaction))
        insert(Debit(afterTomorrowTransaction))

        insert(Transference(beforeYesterdayTransaction, insertAccount().id))
        val tomorrowSentTransference = insert(Transference(tomorrowTransaction, insertAccount().id))
        insert(Transference(afterTomorrowTransaction, insertAccount().id))

        transactionDao.nearestCreditTransactionOfAccount(account.id).observedValue?.let {
            it shouldBeEqualTo yesterdayCredit
        }

        transactionDao.nearestDebitTransactionOfAccount(account.id).observedValue?.let {
            it shouldBeEqualTo tomorrowDebit
        }

        transactionDao.nearestTransferenceTransactionOfAccount(account.id).observedValue?.let {
            it shouldBeEqualTo tomorrowSentTransference
        }

        val todayReceivedTransference = insert(Transference(sampleTransactionData(insertAccount(), date = Date()), account.id))

        transactionDao.nearestTransactionOf(account).observedValue?.let {
            it shouldBeEqualTo todayReceivedTransference
        }
    }

    @Test
    fun updatedTransactionsShouldBeUpdatedInSelect() {
        val originalTransaction = insert(Credit(
            sampleTransactionData(insertAccount(), "Original")
        ))

        transactionDao.allTransactions().observedValue?.first()?.let {
            it shouldBeEqualTo originalTransaction
        }

        val updatedTransaction = originalTransaction.run { copy(data.copy("Updated")) }
        runBlocking { transactionDao.update(updatedTransaction) }
        transactionDao.allTransactions().observedValue?.first()?.let {
            it shouldBeDifferentFrom originalTransaction
            it shouldBeEqualTo updatedTransaction
        }
    }

    @Test
    fun transferenceRelativeKindShouldBeRight() {
        val account = insertAccount()
        val destinationAccount = insertAccount()
        val unrelatedAccount = insertAccount()

        val transference = insert(Transference(sampleTransactionData(account), destinationAccount.id))

        transference kindRelativeTo account shouldBeEqualTo Sent
        transference kindRelativeTo destinationAccount shouldBeEqualTo Received
        transference kindRelativeTo unrelatedAccount shouldBeEqualTo Unrelated
    }

    @Test
    fun insertedTransactionsShouldDisappearAfterDeletion() {
        val data = sampleTransactionData(insertAccount())
        val transaction = insert(Credit(data))
        runBlocking { transactionDao.delete(transaction) }
        assertTrue(transactionDao.allTransactions().observedValue?.isEmpty() ?: false)
    }

    @Test
    fun deletingAccountShouldDeleteAssociatedTransactions() {
        val data = sampleTransactionData(insertAccount())
        val credit = insert(Credit(data))
        val debit = insert(Debit(data))

        val destinationAccount = insertAccount()
        val transference = insert(Transference(data, destinationAccount.id))

        runBlocking { accountDao.delete(destinationAccount) }

        transactionDao.allTransactions().observedValue?.shouldContain(credit, debit)
        transactionDao.allTransactions().observedValue?.shouldNotContain(transference)
    }

    @Test
    fun insertedTitlesShouldAppearInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, "a")))
        insert(Credit(sampleTransactionData(account, "b")))

        transactionDao.allCreditTitles().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditTitles("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedTitlesShouldAppearSortedInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, "b")))
        insert(Credit(sampleTransactionData(account, "a")))

        transactionDao.allCreditTitles().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditTitles("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedTitlesShouldNotAppearDuplicatedInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, "a")))
        insert(Credit(sampleTransactionData(account, "a")))
        insert(Credit(sampleTransactionData(account, "b")))

        transactionDao.allCreditTitles().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditTitles("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedCategoriesShouldAppearInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, category = "a")))
        insert(Credit(sampleTransactionData(account, category = "b")))

        transactionDao.allCreditCategories().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditCategories("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedCategoriesShouldAppearSortedInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, category = "b")))
        insert(Credit(sampleTransactionData(account, category = "a")))

        transactionDao.allCreditCategories().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditCategories("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedCategoriesShouldNotAppearDuplicatedInSelect() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, category = "a")))
        insert(Credit(sampleTransactionData(account, category = "a")))
        insert(Credit(sampleTransactionData(account, category = "b")))

        transactionDao.allCreditCategories().observedValue?.shouldBeEqualTo(listOf("a", "b"))
        transactionDao.allCreditCategories("a").observedValue?.shouldBeEqualTo(listOf("a"))
    }

    @Test
    fun insertedCategoriesShouldAppearInSelectOfAssociatedAccounts() {
        val account = insertAccount()

        insert(Credit(sampleTransactionData(account, category = "a")))
        insert(Credit(sampleTransactionData(insertAccount(), category = "b")))

        transactionDao.allTransactionCategoriesOfAccount(account.id)
            .observedValue?.shouldBeEqualTo(listOf("a"))
    }
}