# =========================================================
# registration_server.py
# =========================================================

def main():

    # -----------------------------------------
    # read runtime configuration
    # -----------------------------------------

    port = ENV.get("PORT", 8081)

    rabbit_url = ENV["RABBIT_URL"]

    database_url = ENV["DATABASE_URL"]

    # -----------------------------------------
    # infrastructure setup
    # -----------------------------------------

    database = Database(database_url)

    rabbit_connection = RabbitConnection(rabbit_url)

    # -----------------------------------------
    # repositories / gateways
    # -----------------------------------------

    registration_request_gateway = RegistrationRequestRepository(database)

    registration_gateway = RegistrationRepository(database)

    # -----------------------------------------
    # rabbitmq topology
    # -----------------------------------------

    registration_notification_exchange = Exchange(
        name="registration-notification-exchange",
        type="direct",
        routing_key="42"
    )

    registration_notification_queue = Queue(
        "registration-notification"
    )

    rabbit_connection.bind_queue(
        exchange=registration_notification_exchange,
        queue=registration_notification_queue,
        routing_key="42"
    )

    # -----------------------------------------
    # exchange for registration requests
    # -----------------------------------------

    registration_request_exchange = Exchange(
        name="registration-request-exchange",
        type="direct",
        routing_key_generator=lambda msg: "42"
    )

    registration_request_queue = Queue(
        "registration-request"
    )

    rabbit_connection.bind_queue(
        exchange=registration_request_exchange,
        queue=registration_request_queue,
        routing_key="42"
    )

    # -----------------------------------------
    # start async background worker
    # -----------------------------------------

    listen_for_registration_requests(
        rabbit_connection,
        registration_request_gateway,
        registration_notification_exchange,
        registration_request_queue
    )

    # -----------------------------------------
    # start http server
    # -----------------------------------------

    start_http_server(
        port,
        registration_request_gateway,
        registration_gateway,
        rabbit_connection,
        registration_request_exchange
    )