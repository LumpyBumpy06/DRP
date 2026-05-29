install:
	pip install -e ".[dev]"

format:
	ruff format app tests

format-check:
	ruff format --check app tests

lint:
	ruff check app tests --fix

lint-check:
	ruff check app tests

typecheck:
	mypy app

spell:
	codespell app tests

test:
	pytest -q --cov=app

all: format-check lint-check typecheck spell test