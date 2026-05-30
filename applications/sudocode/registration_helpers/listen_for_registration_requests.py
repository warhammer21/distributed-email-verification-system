def listen_for_registration_requests(
    rabbit_connection,
    registration_request_gateway,
    registration_notification_exchange,
    registration_request_queue
):

    # helper function for publishing notifications
    publish_notification = build_publisher(
        rabbit_connection,
        registration_notification_exchange
    )

    # business service
    registration_request_service = RegistrationRequestService(
        gateway=registration_request_gateway,
        publish_notification=publish_notification,
        uuid_provider=generate_uuid
    )

    print("Listening for registration requests...")

    channel = rabbit_connection.create_channel()

    # infinite blocking consumer loop
    while True:

        email = channel.consume(
            registration_request_queue
        )

        print(f"Received registration request for {email}")

        # business workflow
        registration_request_service.generate_code_and_publish(
            email
        )