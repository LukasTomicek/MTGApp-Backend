package mtg.app

import io.ktor.server.application.Application
import mtg.app.app.configureApp

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureApp()
}
