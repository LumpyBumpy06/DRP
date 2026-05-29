from fastapi import FastAPI
from pydantic import BaseModel, ConfigDict


def camelize(s: str) -> str:
    parts = s.replace("-", "_").split("_")
    return parts[0] + "".join(p.capitalize() for p in parts[1:])


class CamelModel(BaseModel):
    model_config = ConfigDict(
        alias_generator=camelize,
        validate_by_name=True,
    )


class UserId(CamelModel):
    user_id: int


app = FastAPI()


@app.get("/")
def read_root() -> dict[str, str]:
    return {"Hello": "Dudus World"}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.get("/okay/{user_id}")
async def get_tapped_okay(user_id: int) -> bool:
    """Checks if elderly person has tapped they are ok yet"""
    return False


@app.post("/okay")
async def tap_okay(user_id: UserId) -> None:
    """Elderly person has tapping they are ok"""
    print(f"{user_id} is okay")
    pass


@app.post("/token")
async def update_user_notif_token(user_id: UserId, token: str) -> None:
    """Update database with new user's (firebase) device token for sending notifications"""
    print(f"{user_id} has new token: {token}")
    pass
