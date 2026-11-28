# Common tasks. Everything runs through Docker Compose except `test`, which needs Java 21 + Docker.
COMPOSE := docker compose -f infra/docker-compose.yml
SENTINEL := http://localhost:8090

# eval: only count windows starting at/after this ISO instant (default: last hour), e.g.
#   make eval SINCE=2026-11-28T10:00:00Z
SINCE ?=

.PHONY: up down test train eval

up: ## build and start the whole stack, waiting until every service is healthy
	$(COMPOSE) up -d --build --wait

down: ## stop the stack (keeps the database and model volumes)
	$(COMPOSE) down

test: ## unit + integration tests (Testcontainers needs Docker)
	./mvnw verify

train: ## train the isolation forest on the windows sentinel has buffered (normal traffic only!)
	curl -fsS -X POST $(SENTINEL)/sentinel/train; echo

eval: ## precision / recall / FPR per attack type from recorded detections
	@curl -fsS "$(SENTINEL)/sentinel/eval$(if $(SINCE),?since=$(SINCE))" | python3 -m json.tool
