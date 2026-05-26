#!/bin/sh

set -eu

MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-polar}"
KUBE_CONTEXT="${KUBE_CONTEXT:-$MINIKUBE_PROFILE}"

printf "\n📦 Initializing Kubernetes cluster...\n\n"
minikube start --cpus 2 --memory 4g --driver docker --profile "$MINIKUBE_PROFILE"

printf "\n🔌 Enabling NGINX Ingress Controller...\n\n"
minikube addons enable ingress --profile "$MINIKUBE_PROFILE"

sleep 30

printf "\n📦 Creating Keycloak realm config...\n"
kubectl create configmap keycloak-realm-config \
  --context "$KUBE_CONTEXT" \
  --from-file=realm-config.json=polar-deployment/docker/keycloak/realm-config.json \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-tempo-config \
  --context "$KUBE_CONTEXT" \
  --from-file=tempo.yml=polar-deployment/docker/platform/tempo/tempo.yml \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-mimir-config \
  --context "$KUBE_CONTEXT" \
  --from-file=mimir.yml=polar-deployment/docker/platform/mimir/mimir.yml \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-alloy-config \
  --context "$KUBE_CONTEXT" \
  --from-file=config.alloy=polar-deployment/kubernetes/local/alloy-k8s.alloy \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-grafana-datasources \
  --context "$KUBE_CONTEXT" \
  --from-file=datasource.yml=polar-deployment/docker/platform/grafana/datasources/datasource.yml \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-grafana-dashboards \
  --context "$KUBE_CONTEXT" \
  --from-file=polar-deployment/docker/platform/grafana/dashboards \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

kubectl create configmap observability-grafana-config \
  --context "$KUBE_CONTEXT" \
  --from-file=grafana.ini=polar-deployment/docker/platform/grafana/grafana.ini \
  --dry-run=client -o yaml | kubectl apply --context "$KUBE_CONTEXT" -f -

printf "\n📦 Deploying Keycloak...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/keycloak.yml

printf "\n⌛ Waiting for Keycloak to be ready...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/polar-keycloak \
  --timeout=300s

printf "\n📦 Deploying PostgreSQL (catalog and order)...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/postgresql.yml
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/postgresql-order.yml

printf "\n⌛ Waiting for PostgreSQL deployments to be ready...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/polar-postgres \
  --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/polar-postgres-order \
  --timeout=180s

printf "\n📦 Deploying Redis...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/redis.yml

printf "\n⌛ Waiting for Redis to be ready...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/polar-redis \
  --timeout=180s

printf "\n📦 Deploying Elasticsearch...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/elasticsearch.yml

printf "\n⌛ Waiting for Elasticsearch to be ready...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/elasticsearch \
  --timeout=300s

printf "\n📦 Deploying Kafka...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/kafka.yml

printf "\n⌛ Waiting for Kafka to be ready...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/polar-kafka \
  --timeout=180s

printf "\n📦 Deploying observability stack...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/observability.yml

printf "\n⌛ Waiting for observability deployments...\n"
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/loki \
  --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/tempo \
  --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/mimir \
  --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/grafana-alloy \
  --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" \
  --for=condition=available deployment/grafana \
  --timeout=180s

printf "\n📦 Deploying Polar UI service...\n"
kubectl apply --context "$KUBE_CONTEXT" -f polar-deployment/kubernetes/local/polar-ui.yml

printf "\n⛵ Happy Sailing!\n\n"
