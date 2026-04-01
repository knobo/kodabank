package no.kodabank.bff

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

@SpringBootApplication
@ComponentScan(
    basePackages = ["no.kodabank.bff", "no.kodabank.shared"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [no.kodabank.shared.auth.SecurityConfig::class]
        )
    ]
)
class KodabankBffApplication

fun main(args: Array<String>) {
    runApplication<KodabankBffApplication>(*args)
}
