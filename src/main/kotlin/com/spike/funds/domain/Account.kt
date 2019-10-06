package com.spike.funds.domain

import kotlinx.coroutines.CompletableDeferred

class Account(balance: Int = 0) {
    private val actor = AccountActor(balance)
    
    suspend fun credit(amount: Int): CompletableDeferred<Int> {
        return actor.send(CreditAmount(amount))
    }

    suspend fun balance(): CompletableDeferred<Int> {
        return actor.send(GetBalance)
    }

    suspend fun debit(amount: Int): CompletableDeferred<Int> {
        return actor.send(DebitAmount(amount))
    }

    suspend fun transferTo(target: Account, amount: Int): CompletableDeferred<Int> {
        return actor.send(Transfer(target.actor, amount))
    }
}