package com.spike.funds.domain

import arrow.core.Failure
import arrow.core.Success
import arrow.core.Try
import arrow.core.extensions.TryFunctor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

class AccountActor(val openingBalance: Int = 0) {

    private val actor: SendChannel<Pair<AccountMessage, CompletableDeferred<Int>>> = GlobalScope.actor {
        var balance = openingBalance

        fun debit(amount: Int): Try<Int> {
            return Try {
                require(balance >= amount)
                balance -= amount
                balance
            }
        }

        fun credit(amount: Int) {
            balance += amount
        }

        fun completes(result: Try<Int>, response: CompletableDeferred<Int>) {
            when (result) {
                is Success -> response.complete(balance)
                is Failure -> response.completeExceptionally(result.exception)
            }
        }

        fun completes(response: CompletableDeferred<Int>) {
            response.complete(balance)
        }

        for ((msg, response) in channel) when (msg) {
            is GetBalance -> completes(response)
            is CreditAmount -> credit(msg.amount).let { completes(response) }
            is DebitAmount -> completes(debit(msg.amount), response)
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
