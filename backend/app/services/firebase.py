import json
import logging

import firebase_admin
from firebase_admin import credentials, messaging

from app.settings import Settings

logger = logging.getLogger(__name__)

_firebase_initialized = False


def init_firebase(settings: Settings) -> None:
    if firebase_admin._apps:
        return  # already initialized (extra safety)

    if settings.firebase_credentials_file:
        cred = credentials.Certificate(settings.firebase_credentials_file)

    elif settings.firebase_credentials_json:
        cred = credentials.Certificate(json.loads(settings.firebase_credentials_json))

    elif settings.firebase_optional:
        # Local dev without credentials: skip init. send_notification already
        # swallows the resulting send_push errors, so push simply no-ops.
        logger.warning("Firebase credentials missing; push disabled (FIREBASE_OPTIONAL=true).")
        return

    else:
        raise RuntimeError("Missing Firebase credentials")

    firebase_admin.initialize_app(cred)


def send_push(token: str, title: str, body: str, message_type: str | None = None) -> None:
    message = messaging.Message(
        notification=messaging.Notification(
            title=title,
            body=body,
        ),
        data={
            "type": message_type or "",
            "body": body,
        },
        token=token,
        android=messaging.AndroidConfig(
            priority="high",
        ),
    )

    messaging.send(message)
