install:
	pip install -e ".[dev]"

format:
	ruff format app tests

format-check: lint format
	ruff format --check app tests

lint:
	ruff check app tests

typecheck:
	mypy app

spell:
	codespell app tests

test:
	pytest -q

all: format-check lint typecheck spell test