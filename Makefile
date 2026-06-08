SHELL := /bin/bash

SERVICES := config catalog order inventory dispatcher edge

MINIKUBE_PROFILE ?= polar
KUBE_CONTEXT ?= $(MINIKUBE_PROFILE)
K8S_PLATFORM_FILES := \
	polar-deployment/kubernetes/local/postgresql.yml \
	polar-deployment/kubernetes/local/postgresql-order.yml \
	polar-deployment/kubernetes/local/postgresql-inventory.yml \
	polar-deployment/kubernetes/local/keycloak.yml \
	polar-deployment/kubernetes/local/redis.yml \
	polar-deployment/kubernetes/local/kafka.yml \
	polar-deployment/kubernetes/local/observability.yml
EDGE_K8S_FILES := edge-service/k8s/deployment.yml edge-service/k8s/service.yml edge-service/k8s/ingress.yml
SKAFFOLD_FILE := skaffold.yml
.PHONY: help build test clean image image-publish spotless spotless-apply \
	build-% test-% clean-% run-% spotless-% spotless-apply-% \
	image-% image-publish-% \
	create-cluster deploy-platform \
	reset \
	tilt-up tilt-down \
	platform-up platform-down edge-up edge-down k8s-status \
	k8s-platform-up k8s-platform-down k8s-services-up k8s-services-down k8s-up k8s-down \
	cluster-create cluster-delete cluster-down \
	skaffold-dev skaffold-run skaffold-delete \
	infra-up infra-down wait-config services-up services-down frontend-up frontend-down compose-up compose-down outbox-cleanup

help: ## Show available targets
	@awk 'BEGIN {FS = ":.*##"; printf "\nAvailable targets:\n"} /^[a-zA-Z0-9_.-%-]+:.*##/ {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

build: $(addprefix build-,$(SERVICES)) ## Build all services

test: $(addprefix test-,$(SERVICES)) ## Test all services

clean: $(addprefix clean-,$(SERVICES)) ## Clean all services

image: $(addprefix image-,$(SERVICES)) ## Build OCI images for all services

image-publish: $(addprefix image-publish-,$(SERVICES)) ## Build and publish OCI images for all services

spotless: $(addprefix spotless-,$(SERVICES)) ## Run Spotless check for all services

spotless-apply: $(addprefix spotless-apply-,$(SERVICES)) ## Apply Spotless formatting for all services

build-%: ## Build one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew build

test-%: ## Test one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew test

clean-%: ## Clean one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew clean

PROFILE ?= default
run-%: ## Run one service locally (config|catalog|order|inventory|dispatcher|edge)
ifeq ($(PROFILE),prod)
	cd $*-service && KEYCLOAK_URL=http://localhost:8080 \
		./gradlew bootRun --args="--spring.profiles.active=prod"
else
	cd $*-service && ./gradlew bootRun
endif

image-publish-%: ## Build and publish OCI image for one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew bootBuildImage --publishImage

image-%: ## Build OCI image for one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew bootBuildImage

spotless-apply-%: ## Apply Spotless formatting for one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew spotlessApply

spotless-%: ## Run Spotless check for one service (config|catalog|order|inventory|dispatcher|edge)
	cd $*-service && ./gradlew spotlessCheck

cluster-create: ## Create minikube cluster and deploy platform dependencies
	./create-cluster.sh

create-cluster: cluster-create ## Alias for cluster-create

reset: ## Clean local platform and delete minikube profile
	-tilt down
	-minikube delete --profile $(MINIKUBE_PROFILE)

cluster-delete: ## Delete minikube cluster
	minikube delete --profile $(MINIKUBE_PROFILE)

cluster-down: cluster-delete ## Alias for cluster-delete

platform-up: tilt-up ## Start full local platform via Tilt

deploy-platform: ## Apply and wait for backing services only
	./deploy-platform.sh

platform-down: tilt-down ## Stop local platform managed by Tilt

edge-up: ## Apply edge-service Kubernetes manifests (Deployment/Service/Ingress)
	kubectl apply --context $(KUBE_CONTEXT) $(foreach f,$(EDGE_K8S_FILES),-f $(f))

edge-down: ## Delete edge-service Kubernetes manifests
	kubectl delete --context $(KUBE_CONTEXT) $(foreach f,$(EDGE_K8S_FILES),-f $(f))

k8s-status: ## Show deployments, services, ingress, and pods
	kubectl get --context $(KUBE_CONTEXT) deployments,services,ingress,pods

K8S_APP_SERVICES := config-service catalog-service order-service inventory-service edge-service

k8s-platform-up: ## Apply backing services (Postgres x3, Kafka, Keycloak, Redis) to the cluster
	kubectl create configmap keycloak-realm-config --context $(KUBE_CONTEXT) \
		--from-file=realm-config.json=polar-deployment/docker/keycloak/realm-config.json \
		--dry-run=client -o yaml | kubectl apply --context $(KUBE_CONTEXT) -f -
	$(foreach f,$(K8S_PLATFORM_FILES),kubectl apply --context $(KUBE_CONTEXT) -f $(f);)
	kubectl wait --context $(KUBE_CONTEXT) --for=condition=available --timeout=180s \
		deploy/polar-postgres deploy/polar-postgres-order deploy/polar-postgres-inventory \
		deploy/polar-kafka deploy/polar-keycloak deploy/polar-redis

k8s-platform-down: ## Delete backing services from the cluster
	$(foreach f,$(K8S_PLATFORM_FILES),kubectl delete --context $(KUBE_CONTEXT) -f $(f) --ignore-not-found;)

k8s-services-up: ## Deploy application services (config/catalog/order/inventory/edge) via kustomize
	$(foreach s,$(K8S_APP_SERVICES),kubectl apply --context $(KUBE_CONTEXT) -k $(s)/k8s/;)

k8s-services-down: ## Delete application services from the cluster
	$(foreach s,$(K8S_APP_SERVICES),kubectl delete --context $(KUBE_CONTEXT) -k $(s)/k8s/ --ignore-not-found;)

k8s-up: k8s-platform-up k8s-services-up ## Deploy full stack (platform + services) to the cluster

k8s-down: k8s-services-down k8s-platform-down ## Tear down full stack from the cluster

skaffold-dev: ## Run Skaffold dev against Kubernetes cluster
	skaffold dev -f $(SKAFFOLD_FILE) -p kind

skaffold-run: ## Run Skaffold once against Kubernetes cluster
	skaffold run -f $(SKAFFOLD_FILE) -p kind

skaffold-delete: ## Delete resources managed by Skaffold
	skaffold delete -f $(SKAFFOLD_FILE) -p kind

tilt-up: ## Start Tilt using the root Tiltfile
	tilt up

tilt-down: ## Stop Tilt and clean up Tilt-managed resources
	tilt down

infra-up: ## Start infrastructure services via Docker Compose (Kafka, Postgres, Keycloak, etc.)
	cd polar-deployment/docker && docker compose -f docker-compose.yml up -d

infra-down: ## Stop infrastructure services via Docker Compose
	cd polar-deployment/docker && docker compose -f docker-compose.yml down

OUTBOX_RETENTION_DAYS ?= 7
OUTBOX_DBS := polar-postgres-order:polardb_order polar-postgres-inventory:polardb_inventory polar-postgres-catalog:polardb_catalog
outbox-cleanup: ## Delete outbox_event rows older than OUTBOX_RETENTION_DAYS (default 7) across all 3 DBs
	@for spec in $(OUTBOX_DBS); do \
		container=$${spec%%:*}; db=$${spec##*:}; \
		echo "[$$container] retention=$(OUTBOX_RETENTION_DAYS)d -> $$db"; \
		docker exec -i -e PGPASSWORD=password $$container \
			psql -v ON_ERROR_STOP=1 -v retention_days=$(OUTBOX_RETENTION_DAYS) -U user -d $$db \
			< polar-deployment/docker/postgres/outbox-retention.sql; \
	done

wait-config: ## Wait until Config Server is ready for application services
	@echo "Waiting for Config Server..."
	@for i in $$(seq 1 60); do \
		if curl -fsS http://user:password@localhost:8888/edge-service/prod >/dev/null; then \
			echo "Config Server is ready."; \
			exit 0; \
		fi; \
		sleep 2; \
	done; \
	echo "Config Server did not become ready in 120 seconds."; \
	exit 1

services-up: wait-config ## Start application services via Docker Compose
	cd polar-deployment/docker && docker compose -f service.yml up -d

services-down: ## Stop application services via Docker Compose
	cd polar-deployment/docker && docker compose -f service.yml down

frontend-up: ## Start frontend and observability via Docker Compose
	cd polar-deployment/docker && docker compose -f docker-compose.yml -f frontend.yml up -d

frontend-down: ## Stop frontend and observability via Docker Compose
	cd polar-deployment/docker && docker compose -f docker-compose.yml -f frontend.yml down

compose-up: infra-up services-up frontend-up ## Start infra, services, and frontend via Docker Compose

compose-down: frontend-down services-down infra-down ## Stop frontend, services, and infra via Docker Compose
