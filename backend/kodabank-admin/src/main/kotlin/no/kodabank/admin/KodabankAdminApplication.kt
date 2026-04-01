package no.kodabank.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan(basePackages = ["no.kodabank.admin", "no.kodabank.shared"])
class KodabankAdminApplication

fun main(args: Array<String>) {
    runApplication<KodabankAdminApplication>(*args)
}
