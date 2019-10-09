package com.spike.funds.domain

import arrow.core.Failure
import arrow.core.Success
import arrow.core.Try
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
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

        suspend fun transfer(target: AccountActor, amount: Int, response: CompletableDeferred<Int>) {
            target.send(TransferApproved(self, amount), response)
        }

        suspend fun revert(source: AccountActor, amount: Int, response: CompletableDeferred<Int>) {
            source.send(RevertDebit(-1 * amount), response)
        }

        fun abort(response: CompletableDeferred<Int>, throwable: Throwable) {
            response.completeExceptionally(throwable)
        }

        for ((msg, response) in channel) when (msg) {
            is GetBalance -> completes(response)
            is CreditAmount -> completes(credit(msg.amount), response)
            is DebitAmount -> completes(debit(msg.amount), response)
            is InitiateTransfer -> when (val result = debit(msg.amount)) {
                is Success -> transfer(msg.target, msg.amount, response)
                is Failure -> abort(response, result.exception)
            }
            is TransferApproved -> when (credit(msg.amount)) {
                is Success -> completes(response)
                is Failure -> revert(msg.source, msg.amount, response)
            }
            is RevertDebit -> debit(msg.amount).also {
                abort(response, IllegalArgumentException())
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
class InitiateTransfer(val target: AccountActor, val amount: Int) : AccountMessage()
class TransferApproved(val source: AccountActor, val amount: Int) : AccountMessage()
class RevertDebit(val amount: Int) : AccountMessage()