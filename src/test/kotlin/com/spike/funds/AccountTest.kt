package com.spike.funds

import arrow.core.Either
import com.spike.funds.domain.Account
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AccountTest {
    @Test
    fun shouldCredit() = runBlocking {
        val account = Account()

        account.credit(10).await()

        assertEquals(10, account.balance().await())
    }

    @Test
    fun shouldDebit() = runBlocking {
        val account = Account(10)

        account.debit(10).await()

        assertEquals(0, account.balance().await())
    }

    @Test(expected = IllegalArgumentException::class)
    fun shouldFailInvalidDebit() = runBlocking {
        val account = Account(0)

        val ignore = account.debit(10).await()
    }

    @Test
    fun shouldTransfer() = runBlocking {
        val source = Account(10)
        val target = Account(0)

        source.transferTo(target, 10).await()

        assertEquals(0, source.balance().await())
        assertEquals(10, target.balance().await())
    }

    @Test
    fun shouldFailTransferOnInvalidCredit() = runBlocking {
        val source = Account(10)
        val target = Account(0)

        val result = Either.catch { source.transferTo(target, -1).await() }

        assertTrue(result.isLeft())
        assertEquals(10, source.balance().await())
        assertEquals(0, target.balance().await())
    }

    @Test
    fun shouldFailTransferOnInvalidDebit() = runBlocking {
        val source = Account(10)
        val target = Account(0)

        val result = Either.catch { source.transferTo(target, 20).await() }

        assertTrue(result.isLeft())
        assertEquals(10, source.balance().await())
        assertEquals(0, target.balance().await())
    }

    @Test
    fun shouldMakeConcurrentTransfers() = runBlocking {
        val n = 100_000
        val source = Account(n)
        val target = Account(0)
        val transfers = mutableListOf<Job>()

        for (i in 1..n)
            transfers.add(launch { source.transferTo(target, 1) })

        joinAll(*transfers.toTypedArray())

        assertEquals(0, source.balance().await())
        assertEquals(n, target.balance().await())
    }
}

