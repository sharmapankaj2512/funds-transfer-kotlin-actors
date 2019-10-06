package com.spike.funds.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.actor

class AccountActor(val openingBalance: Int = 0) {

    private val actor = GlobalScope.actor<AccountMessage> {
        var balance = openingBalance

        fun debit(amount: Int) { balance -= amount }

        fun credit(amount: Int) { balance += amount }

        fun completes(response: CompletableDeferred<Int>) {
            response.complete(balance)
        }

        for (msg in channel) {
            when (msg) {
                is CreditAmount -> {
                    credit(msg.amount).also { completes(msg.response) }
                }
                is DebitAmount -> {
                    debit(msg.amount).also { completes(msg.response) }
                }
                is Transfer -> {
                    debit(msg.amount)
                    msg.account.credit(msg.amount, msg.response)
                }
                is GetBalance -> completes(msg.response)
            }
        }
    }

    suspend fun send(message: AccountMessage) = actor.send(message)
}

sealed class AccountMessage
class CreditAmount(val amount: Int, val response: CompletableDeferred<Int>) : AccountMessage()
class DebitAmount(val amount: Int, val response: CompletableDeferred<Int>) : AccountMessage()
class GetBalance(val response: CompletableDeferred<Int>) : AccountMessage()
class Transfer(val account: Account, val amount: Int, val response: CompletableDeferred<Int>) : AccountMessage()
