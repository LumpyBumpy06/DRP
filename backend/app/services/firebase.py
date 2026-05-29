import json

import firebase_admin
from firebase_admin import credentials, messaging

from app.settings import Settings

_firebase_initialized = False


def init_firebase(settings: Settings) -> None:
    if firebase_admin._apps:
        return  # already initialized (extra safety)

    if not settings.firebase_credentials_json:
        raise RuntimeError("Missing Firebase credentials")

    cred_dict = json.loads(settings.firebase_credentials_json)

    cred = credentials.Certificate(cred_dict)
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
