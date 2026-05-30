@app.post("/register")
def register():

    db.insert(...)

    send_email(...)

    analytics.track(...)

    fraud_check(...)

    return success