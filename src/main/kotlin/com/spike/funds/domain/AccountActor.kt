package com.spike.funds.domain

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

class AccountActor(val openingBalance: Int = 0) {

    private val actor: SendChannel<Pair<AccountMessage, CompletableDeferred<Int>>> = GlobalScope.actor {
        var balance = openingBalance

        fun debit(amount: Int) {
            balance -= amount
        }

        fun credit(amount: Int) {
            balance += amount
        }

        fun completes(response: CompletableDeferred<Int>) {
            response.complete(balance)
        }

        for ((msg, response) in channel) when (msg) {
            is GetBalance -> completes(response)
            is CreditAmount -> credit(msg.amount).also { completes(response) }
            is DebitAmount -> debit(msg.amount).also { completes(response) }
            is Transfer -> {
                debit(msg.amount)
                msg.target.send(CreditAmount(msg.amount), response)
            }
        }
    }

    suspend fun send(
            message: AccountMessage,
            response: CompletableDeferred<Int> = CompletableDeferred())
            : CompletableDeferred<Int> {
        actor.send(Pair(message, response))
        return response
    }
}

sealed class AccountMessage
class CreditAmount(val amount: Int) : AccountMessage()
class DebitAmount(val amount: Int) : AccountMessage()
object GetBalance : AccountMessage()
class Transfer(val target: AccountActor, val amount: Int) : AccountMessage()
