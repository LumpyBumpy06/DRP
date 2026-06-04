import logging

from app.services.firebase import send_push

logger = logging.getLogger(__name__)


def send_notification(token: str, message: str, message_type: str = "CHECKED_IN") -> None:
    if not token:
        return

    print(f"[NOTIFY] token={token} message={message}")

    try:
        send_push(
            token=token,
            title="QuietSignal",
            body=message,
            message_type=message_type,
        )
    except Exception:
        # Best-effort: a stale/invalid device token (e.g. FCM UnregisteredError)
        # must not fail the request that triggered the notification. Emergencies
        # also persist server-side, so the carer's poll still surfaces them even
        # when the push can't be delivered.
        logger.exception("Failed to send push notification (token may be stale)")
