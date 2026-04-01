package no.kodabank.clearing

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["no.kodabank.clearing", "no.kodabank.shared.client"])
class KodabankClearingApplication

fun main(args: Array<String>) {
    runApplication<KodabankClearingApplication>(*args)
}
