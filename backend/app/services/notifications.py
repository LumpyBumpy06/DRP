from app.services.firebase import send_push


def send_notification(token: str, message: str, message_type: str = "CHECKED_IN") -> None:
    if not token:
        return

    print(f"[NOTIFY] token={token} message={message}")

    send_push(
        token=token,
        title="QuietSignal",
        body=message,
        message_type=message_type,
    )
