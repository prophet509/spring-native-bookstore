#!/bin/sh

set -eu

KIND_CLUSTER="${KIND_CLUSTER:-bookstore}"
KUBE_CONTEXT="kind-${KIND_CLUSTER}"

printf "\nApplying platform manifests...\n"
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

kubectl apply --context "$KUBE_CONTEXT" \
  -f polar-deployment/kubernetes/local/postgresql.yml \
  -f polar-deployment/kubernetes/local/postgresql-order.yml \
  -f polar-deployment/kubernetes/local/kafka.yml \
  -f polar-deployment/kubernetes/local/keycloak.yml \
  -f polar-deployment/kubernetes/local/redis.yml \
  -f polar-deployment/kubernetes/local/observability.yml

printf "\nWaiting for platform deployments...\n"
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/polar-postgres --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/polar-postgres-order --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/polar-kafka --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/polar-keycloak --timeout=300s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/polar-redis --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/loki --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/tempo --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/mimir --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/grafana-alloy --timeout=180s
kubectl wait --context "$KUBE_CONTEXT" --for=condition=available deployment/grafana --timeout=180s

printf "\nPlatform is ready on context %s\n" "$KUBE_CONTEXT"
