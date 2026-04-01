package no.kodabank.core

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["no.kodabank.core", "no.kodabank.shared"])
class KodabankCoreApplication

fun main(args: Array<String>) {
    runApplication<KodabankCoreApplication>(*args)
}
