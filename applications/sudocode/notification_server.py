# =========================================================
# notification_server.py
# =========================================================

def main():

    # -----------------------------------------
    # read runtime configuration
    # -----------------------------------------

    rabbit_url = ENV["RABBIT_URL"]

    sendgrid_url = ENV["SENDGRID_URL"]

    sendgrid_api_key = ENV["SENDGRID_API_KEY"]

    from_address = ENV["FROM_ADDRESS"]

    database_url = ENV["DATABASE_URL"]

    # -----------------------------------------
    # infrastructure setup
    # -----------------------------------------

    rabbit_connection = RabbitConnection(rabbit_url)

    database = Database(database_url)

    # -----------------------------------------
    # define rabbitmq topology
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
    # create external service adapters
    # -----------------------------------------

    emailer = SendgridEmailer(
        sendgrid_url,
        sendgrid_api_key,
        from_address
    )

    notification_gateway = NotificationRepository(database)

    # -----------------------------------------
    # create business service
    # -----------------------------------------

    notifier = Notifier(
        gateway=notification_gateway,
        emailer=emailer
    )

    # -----------------------------------------
    # start background consumer
    # -----------------------------------------

    print("Listening for notification events...")

    listen_for_notification_requests(
        rabbit_connection,
        notifier,
        registration_notification_queue
    )


# =========================================================
# background worker loop
# =========================================================

def listen_for_notification_requests(
    rabbit_connection,
    notifier,
    queue
):

    channel = rabbit_connection.create_channel()

    # blocks forever waiting for messages
    while True:

        message = channel.consume(queue)

        # deserialize json message
        payload = parse_json(message)

        email = payload["email"]

        confirmation_code = payload["confirmationCode"]

        print(f"Received notification request for {email}")

        # business logic
        notifier.notify(
            email,
            confirmation_code
        )