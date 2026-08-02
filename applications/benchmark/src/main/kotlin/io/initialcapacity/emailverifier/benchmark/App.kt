package io.initialcapacity.emailverifier.benchmark

import io.initialcapacity.emailverifier.fakesendgridendpoints.fakeSendgridRoutes
import io.ktor.client.*
import io.ktor.client.engine.java.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import kotlinx.coroutines.runBlocking
import java.util.*

data class Confirmation(
    val email: String,
    val code: UUID,
)

fun main(): Unit = runBlocking {
    val port = getEnvInt("PORT", 9090)

    val registrationCount = getEnvInt("REGISTRATION_COUNT", 5_000)

    val benchmark = Benchmark(
        registrationUrl = System.getenv("REGISTRATION_URL") ?: "http://localhost:8081",
        registrationCount = registrationCount,
        requestWorkerCount = getEnvInt("REQUEST_WORKER_COUNT", 4),
        registrationWorkerCount = getEnvInt("REGISTRATION_WORKER_COUNT", 4),
        client = HttpClient(Java) {
            expectSuccess = false
        }
    )

    val fakeEmailServer = fakeEmailServer(port, benchmark).apply { start() }

    val duration = benchmark.start(this)

    val registrationsPerSecond =
        registrationCount.toDouble() / duration.inWholeMilliseconds * 1000

    if (registrationsPerSecond < 50) {
        System.err.println(
            "ERROR: Throughput $registrationsPerSecond registrations/sec is below the required 50 registrations/sec."
        )
        throw RuntimeException("Business requirement not met")
    }

    fakeEmailServer.stop()
}

private fun getEnvInt(name: String, default: Int): Int =
    System.getenv(name)?.toInt() ?: default

private fun fakeEmailServer(
    port: Int,
    benchmark: Benchmark
) = embeddedServer(
    factory = Jetty,
    port = port,
    module = { fakeSendgridRoutes("super-secret") { benchmark.processConfirmation(it) } }
)
