package no.kodabank.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["no.kodabank.gateway", "no.kodabank.shared.client"])
class KodabankPaymentGatewayApplication

fun main(args: Array<String>) {
    runApplication<KodabankPaymentGatewayApplication>(*args)
}
