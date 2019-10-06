package com.spike.funds.domain

import kotlinx.coroutines.CompletableDeferred

class Account(balance: Int = 0) {
    suspend fun credit(amount: Int, response: CompletableDeferred<Int> = CompletableDeferred()): CompletableDeferred<Int> {
        actor.send(CreditAmount(amount, response))
        return response
    }

    suspend fun balance(): CompletableDeferred<Int> {
        val response = CompletableDeferred<Int>()
        actor.send(GetBalance(response))
        return response
    }

    suspend fun debit(amount: Int): CompletableDeferred<Int> {
        val response = CompletableDeferred<Int>()
        actor.send(DebitAmount(amount, response))
        return response

    }

    suspend fun transferTo(target: Account, amount: Int): CompletableDeferred<Int> {
        val response = CompletableDeferred<Int>()
        actor.send(Transfer(target, amount, response))
        return response
    }

    private val actor = AccountActor(balance)
}