package no.kodabank.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands

class KodabankCli : CliktCommand(name = "kodabank") {
    override fun help(context: Context) = "KodaBank CLI - Demo banking administration tool"
    override fun run() {
        currentContext.findOrSetObject { AdminApiClient() }
    }
}

fun main(args: Array<String>) {
    val cli = KodabankCli().subcommands(
        ImportGroup().subcommands(
            ImportBankCommand()
        ),
        TenantsGroup().subcommands(
            TenantsListCommand(),
            TenantsStatusCommand()
        ),
        SetupCommand(),
        DemoGroup().subcommands(
            DemoGenerateCommand()
        )
    )

    try {
        cli.main(args)
    } catch (e: SystemExitException) {
        kotlin.system.exitProcess(e.code)
    }
}
