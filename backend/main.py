from fastapi import FastAPI

app = FastAPI()


@app.get("/foo/bar")
async def root():
    return {"message": "Hello World"}