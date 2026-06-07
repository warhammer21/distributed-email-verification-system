package io.initialcapacity.emailverifier.registrationserver

import com.rabbitmq.client.ConnectionFactory
import io.initialcapacity.emailverifier.databasesupport.DatabaseTemplate
import io.initialcapacity.emailverifier.rabbitsupport.*
import io.initialcapacity.emailverifier.registration.RegistrationConfirmationService
import io.initialcapacity.emailverifier.registration.RegistrationDataGateway
import io.initialcapacity.emailverifier.registration.register
import io.initialcapacity.emailverifier.registrationrequest.RegistrationRequestDataGateway
import io.initialcapacity.emailverifier.registrationrequest.RegistrationRequestService
import io.initialcapacity.emailverifier.registrationrequest.UuidProvider
import io.initialcapacity.emailverifier.registrationrequest.registrationRequest
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.net.URI
import java.util.*

class App

private val logger = LoggerFactory.getLogger(App::class.java)

fun main(): Unit = runBlocking {
    val port = System.getenv("PORT")?.toInt() ?: 8081
    val rabbitUrl = System.getenv("RABBIT_URL")?.let(::URI)
        ?: throw RuntimeException("Please set the RABBIT_URL environment variable")
    val databaseUrl = System.getenv("DATABASE_URL")
        ?: throw RuntimeException("Please set the DATABASE_URL environment variable")

    val dbConfig = DatabaseConfiguration(databaseUrl)
    val dbTemplate = DatabaseTemplate(dbConfig.db)

    val connectionFactory = buildConnectionFactory(rabbitUrl)
    val registrationRequestGateway = RegistrationRequestDataGateway(dbTemplate)
    val registrationGateway = RegistrationDataGateway(dbTemplate)

    val registrationNotificationExchange = RabbitExchange(
        name = "registration-notification-exchange",
        type = "direct",
        routingKeyGenerator = { _: String -> "42" },
    )
    val registrationNotificationQueue = RabbitQueue("registration-notification")
    connectionFactory.declareAndBind(exchange = registrationNotificationExchange, queue = registrationNotificationQueue, routingKey = "42")

    val registrationRequestExchange = RabbitExchange(
        // (DONE)TODO - rename the request exchange (since you've already declared a direct exchange under the current name)
        name = "registration-request-consistent-hash-exchange",
        // (DONE)TODO - use a consistent hash exchange (x-consistent-hash)
        type = "x-consistent-hash",
        // (DONE)TODO - calculate a routing key based on message content
        routingKeyGenerator = @Suppress("UNUSED_ANONYMOUS_PARAMETER") {  message: String -> message },
    )
    // TODO (DONE) - read the queue name from the environment
    val registrationRequestQueue = RabbitQueue(
    System.getenv("REGISTRATION_REQUEST_QUEUE")
        ?: "registration-request"
    )
    // TODO (DONE) - read the routing key from the environment
    connectionFactory.declareAndBind(exchange = registrationRequestExchange, queue = registrationRequestQueue, routingKey =System.getenv("REGISTRATION_REQUEST_ROUTING_KEY") ?: "1")

    listenForRegistrationRequests(
        connectionFactory,
        registrationRequestGateway,
        registrationNotificationExchange,
        registrationRequestQueue
    )
    registrationServer(
        port,
        registrationRequestGateway,
        registrationGateway,
        connectionFactory,
        registrationRequestExchange
    ).start()
}

fun registrationServer(
    port: Int,
    registrationRequestGateway: RegistrationRequestDataGateway,
    registrationGateway: RegistrationDataGateway,
    connectionFactory: ConnectionFactory,
    registrationRequestExchange: RabbitExchange,
) = embeddedServer(
    factory = Jetty,
    port = port,
    module = { module(registrationRequestGateway, registrationGateway, connectionFactory, registrationRequestExchange) }
)

fun Application.module(
    registrationRequestGateway: RegistrationRequestDataGateway,
    registrationGateway: RegistrationDataGateway,
    connectionFactory: ConnectionFactory,
    registrationRequestExchange: RabbitExchange,
) {
    install(Resources)
    install(CallLogging)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json()
    }
// REQUEST STARTs HERE
    val publishRequest = publish(connectionFactory, registrationRequestExchange)

    install(Routing) {
        info()
        registrationRequest(publishRequest) // this underneet is sending the request to rabitmq - registrationRequestExchange
        register(RegistrationConfirmationService(registrationRequestGateway, registrationGateway)) // this is saving the incoming request
    }
} // at the end of this the message is in RabbitMQ waiting .....

fun CoroutineScope.listenForRegistrationRequests(
    connectionFactory: ConnectionFactory,
    registrationRequestDataGateway: RegistrationRequestDataGateway,
    registrationNotificationExchange: RabbitExchange,
    registrationRequestQueue: RabbitQueue,
    uuidProvider: UuidProvider = { UUID.randomUUID() },
) {
    val publishNotification = publish(connectionFactory, registrationNotificationExchange)
     // same its a container to public to rabbit mmq

    val registrationRequestService = RegistrationRequestService(
        gateway = registrationRequestDataGateway,
        publishNotification = publishNotification,
        uuidProvider = uuidProvider,
    ) // container for holding the dependencies to do the actual svae and publish 

    launch {
        logger.info("listening for registration requests")
        val channel = connectionFactory.newConnection().createChannel() //  this channel statys open till the app is running 
        // Push-based async messaging
        listen(queue = registrationRequestQueue, channel = channel) { email ->
            logger.debug("received registration request for {}", email)
            registrationRequestService.generateCodeAndPublish(email)
        }
    } // this is the actual lsitener it creates a channe and listen to the registrationRequestQueue 
    // once done it gets the generate code and save and publish messge to the registrationNotificationExchange next service 
}
