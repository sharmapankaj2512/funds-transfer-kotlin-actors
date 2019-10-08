package com.spike.funds.domain

import arrow.core.Failure
import arrow.core.Success
import arrow.core.Try
import arrow.core.extensions.TryFunctor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.lang.IllegalArgumentException

class AccountActor(val openingBalance: Int = 0) {

    val self = this

    private val actor: SendChannel<Pair<AccountMessage, CompletableDeferred<Int>>> = GlobalScope.actor {
        var balance = openingBalance

        fun debit(amount: Int): Try<Int> {
            return Try {
                require(balance >= amount)
                balance -= amount
                balance
            }
        }

        fun credit(amount: Int): Try<Int> {
            return Try {
                require(amount > 0)
                balance += amount
                balance
            }
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
            is CreditAmount -> completes(credit(msg.amount), response)
            is DebitAmount -> completes(debit(msg.amount), response)
            is Transfer -> when (val result = debit(msg.amount)) {
                is Success -> msg.target.send(TransferCredit(self, msg.amount), response)
                is Failure -> response.completeExceptionally(result.exception)
            }
            is TransferCredit -> when (credit(msg.amount)) {
                is Success -> completes(response)
                is Failure -> msg.source.send(RevertDebit(-1 * msg.amount), response)
            }
            is RevertDebit -> debit(msg.amount).also {
                response.completeExceptionally(IllegalArgumentException())
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
class TransferCredit(val source: AccountActor, val amount: Int) : AccountMessage()
class RevertDebit(val amount: Int) : AccountMessage()