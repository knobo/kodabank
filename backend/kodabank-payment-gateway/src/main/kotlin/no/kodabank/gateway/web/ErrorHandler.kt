package no.kodabank.gateway.web

import no.kodabank.gateway.application.MerchantNotFoundException
import no.kodabank.gateway.application.PaymentOrderNotFoundException
import no.kodabank.gateway.application.SubscriptionNotFoundException
import no.kodabank.shared.client.ConcurrencyConflictException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ErrorHandler {

    @ExceptionHandler(MerchantNotFoundException::class)
    fun handleMerchantNotFound(e: MerchantNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "Merchant not found"))

    @ExceptionHandler(PaymentOrderNotFoundException::class)
    fun handlePaymentOrderNotFound(e: PaymentOrderNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "Payment order not found"))

    @ExceptionHandler(SubscriptionNotFoundException::class)
    fun handleSubscriptionNotFound(e: SubscriptionNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponse(e.message ?: "Subscription not found"))

    @ExceptionHandler(IllegalArgumentException::class, IllegalStateException::class)
    fun handleBadRequest(e: RuntimeException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(e.message ?: "Bad request"))

    @ExceptionHandler(ConcurrencyConflictException::class)
    fun handleConcurrencyConflict(e: ConcurrencyConflictException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(e.message ?: "Concurrency conflict"))
}

data class ErrorResponse(val error: String)
