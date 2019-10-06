package com.spike.funds

import com.spike.funds.domain.Account
import kotlinx.coroutines.*
import org.junit.Test
import kotlin.test.assertEquals

class AccountTest {
    @Test
    fun shouldCredit() = runBlocking {
        val account = Account()

        val (_, balance) = awaitAll(
                account.credit(10),
                account.balance())

        assertEquals(10, balance)
    }

    @Test
    fun shouldDebit() = runBlocking {
        val account = Account(10)

        val (_, balance) = awaitAll(
                account.debit(10),
                account.balance())

        assertEquals(0, balance)
    }

    @Test
    fun shouldTransfer() = runBlocking {
        val source = Account(10)
        val target = Account(0)

        val (_, sourceBalance, targetBalance) = awaitAll(
                source.transferTo(target, 10),
                source.balance(),
                target.balance())

        assertEquals(0, sourceBalance)
        assertEquals(10, targetBalance)
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

        val (sourceBalance, targetBalance) = awaitAll(
                source.balance(),
                target.balance())

        assertEquals(0, sourceBalance)
        assertEquals(n, targetBalance)
    }
}

