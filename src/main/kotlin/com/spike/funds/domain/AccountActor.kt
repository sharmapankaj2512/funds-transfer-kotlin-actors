package com.spike.funds.domain

import arrow.core.Either
import arrow.core.Either.Left
import arrow.core.Either.Right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor

class AccountActor(val openingBalance: Int = 0) {

    val self = this

    private val actor: SendChannel<Pair<AccountMessage, CompletableDeferred<Int>>> = GlobalScope.actor {
        var balance = openingBalance

        suspend fun debit(amount: Int): Either<Throwable, Int> {
            return Either.catch {
                require(balance >= amount)
                balance -= amount
                balance
            }
        }

        suspend fun credit(amount: Int): Either<Throwable, Int> {
            return Either.catch {
                require(amount > 0)
                balance += amount
                balance
            }
        }

        fun completes(result: Either<Throwable, Int>, response: CompletableDeferred<Int>) {
            when (result) {
                is Right -> response.complete(balance)
                is Left -> response.completeExceptionally(result.a)
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
                is Right -> transfer(msg.target, msg.amount, response)
                is Left -> abort(response, result.a)
            }
            is TransferApproved -> when (credit(msg.amount)) {
                is Right -> completes(response)
                is Left -> revert(msg.source, msg.amount, response)
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