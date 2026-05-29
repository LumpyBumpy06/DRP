FROM python:3.14-slim

WORKDIR /code

COPY pyproject.toml .

#cheeky fix
RUN mkdir -p app && touch app/__init__.py

RUN pip install --no-cache-dir .

COPY app ./app

EXPOSE 80
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "80"]
