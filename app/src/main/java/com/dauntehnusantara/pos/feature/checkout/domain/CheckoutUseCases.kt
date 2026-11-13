package com.dauntehnusantara.pos.feature.checkout.domain

import javax.inject.Inject

class ObserveCheckout @Inject constructor(private val repository: CheckoutRepository) {
    operator fun invoke() = repository.observe()
}
class EditCart @Inject constructor(private val repository: CheckoutRepository) {
    suspend fun add(draft: Draft, line: CartLine) = repository.add(draft, line)
    suspend fun quantity(draft: Draft, lineId: String, quantity: Int) = repository.quantity(draft, lineId, quantity)
    suspend fun remove(draft: Draft, lineId: String) = repository.remove(draft, lineId)
}
class CompleteCheckout @Inject constructor(private val repository: CheckoutRepository) {
    suspend operator fun invoke(command: CheckoutCommand) = repository.complete(command)
    suspend fun dismiss(id: String) = repository.dismissReceipt(id)
    suspend fun receipts() = repository.receipts()
}
