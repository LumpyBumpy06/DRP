def send_notification(token: str, message: str) -> None:
    """
    Stateless notification sender.
    Replace with:
    - Firebase Cloud Messaging
    - APNs
    - WebPush service
    """
    if not token:
        return

    print(f"[NOTIFY] token={token} message={message}")
