def start_http_server(
    port,
    registration_request_gateway,
    registration_gateway,
    rabbit_connection,
    registration_request_exchange
):

    app = WebApp()

    # create publisher helper
    publish_request = build_publisher(
        rabbit_connection,
        registration_request_exchange
    )

    # -------------------------------------------------
    # POST /request-registration
    # -------------------------------------------------

    @app.post("/request-registration")
    def request_registration(request):

        email = request.json["email"]

        # publish async event
        publish_request(email)

        return {
            "status": "request accepted"
        }

    # -------------------------------------------------
    # POST /register
    # -------------------------------------------------

    @app.post("/register")
    def register(request):

        email = request.json["email"]

        confirmation_code = request.json["confirmationCode"]

        confirmation_service = RegistrationConfirmationService(
            registration_request_gateway,
            registration_gateway
        )

        success = confirmation_service.confirm(
            email,
            confirmation_code
        )

        return {
            "success": success
        }

    app.run(port=port)