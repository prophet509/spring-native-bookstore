# 🛡️ Security & 🚢 DevOps/ArgoCD Plan — spring-native-bookstore

> **Triết lý:** Chỉ liệt kê việc **đáng làm cho codebase này** ở trạng thái thực tế hiện tại. Không lý thuyết suông. Mỗi item phải có **why**, **what** và **verify**. Tham chiếu chéo với `docs/tasks/senior-roadmap.md` Giai đoạn 3 (Security) và Giai đoạn 5 (GitOps).

---

## 📋 Audit hiện trạng (sau session này)

### Security đã có
- [x] `edge-service` Gateway: OAuth2 Client (Authorization Code + OIDC) + Redis Session + `TokenRelay` filter + Rate Limiter + Circuit Breaker + OIDC Logout.
- [x] Resource servers (catalog/order/inventory/dispatcher/search): `oauth2ResourceServer.jwt(issuer-uri)` validate JWT.
- [x] `KeycloakJwtAuthoritiesConverter` ở **TẤT CẢ service** (catalog/order/inventory/dispatcher/search) — convert `roles` + `realm_access.roles` claim sang Spring Security `ROLE_*`.
  - **catalog-service**: converter nhận `clientId` param, extract thêm `resource_access.<client-id>.roles` (client-level roles).
  - **order/inventory/dispatcher/search**: converter extract `roles` (direct) + `realm_access.roles` (realm-level).
  - **Tests**: `KeycloakJwtAuthoritiesConverterTest` ở order/inventory/search.
- [x] **Per-endpoint role rules** đã implement:
  - catalog: `GET /books/**` permitAll, POST/PUT/DELETE `hasRole("employee")`, actuator `hasRole("ADMIN")` (health/info permitAll).
  - order: `GET /orders/**` authenticated, `POST /orders/**` `hasAnyRole("customer", "employee")`.
  - inventory: `GET /inventory/**` permitAll.
  - search: `GET /search/**` permitAll.
  - dispatcher: `anyExchange().authenticated()`.
- [x] **Method security**: `@EnableReactiveMethodSecurity` ở order/inventory/search, `@EnableMethodSecurity` ở catalog.
- [x] CSRF disabled cho stateless services (đúng).
- [x] Session: stateless cho resource servers, Redis cho edge.

### Bug security đã phát hiện trong session này
- [x] **Catalog actuator probe security** (đã fix `dfcc5ae` không phải ở đây — fix riêng): `EndpointRequest.toAnyEndpoint().hasRole("ADMIN")` chặn kubelet probe `/health/liveness` → 401 → CrashLoopBackOff. Đã thêm `EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class).permitAll()`.
- [x] **K8s manifest issuer-uri thiếu port**: `http://polar-keycloak/realms/...` (port 80) thay vì `:8080`. JWT validation timeout 30s → 500. Đã fix order/inventory/catalog/edge.
- [x] **Token issuer mismatch local Docker**: token từ host (`localhost:8080`) có `iss` khác với resource server (`polar-keycloak:8080`). Đã fix bằng cách load-test fetch token qua Docker network alias.

### Security còn thiếu / yếu
- ❌ **Method-level `@PreAuthorize`** chưa dùng rộng rãi — `@EnableMethodSecurity`/`@EnableReactiveMethodSecurity` đã bật nhưng chưa có `@PreAuthorize` thực tế trên domain methods.
- ❌ Không có **security headers** (CSP, HSTS, X-Frame-Options, Permissions-Policy) ở edge.
- ❌ **JWK cache** không được tune — mỗi resource server fetch JWK độc lập, có thể hammer Keycloak khi restart đồng loạt.
- ❌ Không có **PKCE verification test** — Spring Security 6+ tự bật nhưng chưa verify network trace.
- ❌ Không có **refresh token rotation** trong Keycloak realm config.
- ❌ Không có **per-user rate limiting** ở edge — `KeyResolver` mặc định là principal hoặc IP, chưa custom.
- ❌ **Secrets** đang ở plain text (`spring.r2dbc.password=password` trong env, ConfigMap, Git).
- ❌ Không có **vulnerability scanning** trong CI (deps + image).
- ❌ Không có **SBOM generation** + signing.
- ❌ Không có **mTLS** giữa services (mới chỉ HTTPS-less HTTP cluster-internal).
- ❌ Không có **audit log** ai đã `addBookToCatalog`, ai `submitOrder` ngoài log app thường.

### DevOps/CI/CD đã có
- [x] **GitHub Actions per service** (`.github/workflows/ci-*-pipeline.yml`): build + test.
- [x] **K8s manifest per service** (deployment.yml + service.yml + kustomization.yml + application.yml configmap + secret.yml). Dùng kustomize, không Helm.
- [x] **Tiltfile** cho dev local nhanh (mỗi service có 1 + Polar root).
- [x] **Skaffold** profile `kind` cho deploy nhanh vào kind cluster.
- [x] **Makefile k8s targets** (đã thêm trong session): `k8s-platform-up`, `k8s-services-up`, `k8s-up`, `k8s-down`.

### DevOps còn thiếu / yếu
- ❌ **CI không push image** — chỉ build + test. Không có image registry workflow.
- ❌ **Không có ArgoCD** setup.
- ❌ **Không có GitOps repo** — mọi `kubectl apply` đều thủ công hoặc qua Skaffold.
- ❌ **Image tag = `0.0.1-SNAPSHOT`** (cố định) — không immutable, không trace được commit nào deploy.
- ❌ **Không có staging/prod environment** tách biệt (chỉ có local kind).
- ❌ **Secrets trong Git** (file `secret.yml` plain text).
- ❌ **Không có HPA** — replicas cố định.
- ❌ **Không có PodDisruptionBudget**.
- ❌ **Không có canary/blue-green** strategy.
- ❌ **Migration DB** không có job riêng — Flyway chạy lúc app start (race condition giữa N replica).
- ❌ **Không có rollback playbook** documented.

---

## 🛡️ Phần A — Security Roadmap

### A1. Khắc phục những thứ "phải có" trước (Quick wins, cao ROI)

#### A1.1 Đồng bộ `KeycloakJwtAuthoritiesConverter` cho tất cả service ✅

**Why:** Hiện tại chỉ catalog có. Order/inventory/dispatcher khi authorize sẽ đối chiếu `realm_access.roles` Keycloak format mặc định, mà SecurityConfig dùng `hasRole("employee")` → có thể fail vì Spring expect prefix `ROLE_` và claim path khác.

**Status: COMPLETE.** `KeycloakJwtAuthoritiesConverter` đã có ở tất cả service (catalog/order/inventory/dispatcher/search). Tests đã có ở order/inventory/search.

**What (done):**
- [x] Trích `KeycloakJwtAuthoritiesConverter` thành class riêng trong mỗi service.
- [x] Áp `JwtAuthenticationConverter` (servlet) hoặc `ReactiveJwtAuthenticationConverterAdapter` (webflux) trỏ về converter này.
- [x] Thêm test verify: `KeycloakJwtAuthoritiesConverterTest` ở order/inventory/search.

**Verify:**
```bash
TOKEN=$(... fetch with bjorn/employee role ...)
curl -i -X POST http://localhost:9001/books -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{...}'
# expect 201 (employee role mapped)

TOKEN_CUSTOMER=$(... fetch with isabelle/customer role ...)
curl -i -X POST http://localhost:9001/books -H "Authorization: Bearer $TOKEN_CUSTOMER" -d '{...}'
# expect 403 (customer cannot create)
```

#### A1.2 Per-endpoint role rules ✅

**Why:** `anyRequest().hasRole("employee")` chặn cả `GET /books` (mà thực ra cho phép `permitAll`), nhưng không phân biệt được POST/PUT/DELETE (chỉ employee) và GET (cả customer).

**Status: COMPLETE.** Per-endpoint rules đã implement ở tất cả service.

**What (done):**
- [x] catalog: `GET /books/**` permitAll, POST/PUT/DELETE `hasRole("employee")`, actuator health/info permitAll, actuator khác `hasRole("ADMIN")`.
- [x] order: `GET /orders/**` authenticated, `POST /orders/**` `hasAnyRole("customer", "employee")`.
- [x] inventory: `GET /inventory/**` permitAll.
- [x] search: `GET /search/**` permitAll.
- [x] dispatcher: `anyExchange().authenticated()`.

**Verify:** Bộ test contract `*SecurityConfigTest` cho mỗi service:
```java
@ParameterizedTest
@CsvSource({
    "POST, /books, customer, 403",
    "POST, /books, employee, 201",
    "GET,  /books, anonymous, 200",
    "POST, /orders, customer, 201",
})
void securityRules(String method, String path, String role, int expectedStatus) { ... }
```

#### A1.3 Method-level `@PreAuthorize` cho domain rules

**Why:** Có những rule không thuộc URL pattern: chỉ cho phép user xem **đơn hàng của chính mình** (ngoại trừ employee được xem tất cả). `@EnableMethodSecurity`/`@EnableReactiveMethodSecurity` đã bật ở catalog/order/inventory/search nhưng chưa có `@PreAuthorize` thực tế.

**What:**
```java
// order-service ViewListBookUseCase / ViewOrderUseCase
@PreAuthorize("hasRole('employee') or #createdBy == authentication.name")
public Flux<Order> findByCreatedBy(String createdBy) { ... }

@PreAuthorize("hasRole('employee') or returnObject.createdBy == authentication.name")
public Mono<Order> findById(Long id) { ... }
```
- [ ] Bật `@EnableReactiveMethodSecurity` cho order/inventory (catalog đã có `@EnableMethodSecurity`).
- [ ] Test bằng `@WithMockUser(roles="customer")` cho method positive/negative.

#### A1.4 Security headers ở edge-service

**Why:** Browser cần CSP/HSTS/Frame-Options chống XSS, clickjacking, MIME-sniffing. Hiện chưa có ở edge.

**What:**
```java
// edge-service SecurityConfig (reactive)
http.headers(headers -> headers
    .frameOptions(frame -> frame.mode(Mode.DENY))
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
      + "img-src 'self' data:; connect-src 'self' https:; "
      + "frame-ancestors 'none'; form-action 'self'"))
    .referrerPolicy(ref -> ref.policy(ReferrerPolicy.STRICT_ORIGIN))
    .permissionsPolicy(perm -> perm.policy("camera=(), microphone=(), geolocation=()"))
    .hsts(hsts -> hsts.maxAge(Duration.ofDays(365)).includeSubdomains(true).preload(true))
    .xssProtection(xss -> xss.disable())  // CSP thay thế
);
```

**Verify:**
```bash
curl -I https://edge.bookstore.local/
# Expect: Content-Security-Policy, Strict-Transport-Security, X-Frame-Options: DENY, ...
# Tool: https://securityheaders.com/ — target rating A+
```

#### A1.5 JWK cache config

**Why:** Mỗi resource server fetch JWK set từ Keycloak per-startup. Nếu cache TTL ngắn, hàng nghìn request/giây có thể hammer Keycloak. Mặc định Spring 5 phút — đủ cho hot path nhưng nên explicit.

**What:** Spring Boot 4 dùng `nimbus-jose-jwt`; cấu hình:
```yaml
spring.security.oauth2.resourceserver.jwt:
  issuer-uri: ${KEYCLOAK_URL}/realms/PolarBookshop
  jwk-set-uri: ${KEYCLOAK_URL}/realms/PolarBookshop/protocol/openid-connect/certs
  cache-duration: 5m       # rotation interval
  jws-algorithms: [RS256]
```
- [ ] Verify trong code: `NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder` có set TTL.

### A2. Hardening (Quan trọng nhưng cần effort lớn hơn)

#### A2.1 Refresh token rotation

**Why:** Nếu refresh token bị leak, attacker có thể dùng nó nhiều lần để lấy access token mới. Rotation = mỗi lần dùng refresh → cấp refresh mới + revoke cái cũ. Reuse cái cũ → Keycloak detect tấn công, revoke cả session.

**What:**
- [ ] Trong Keycloak realm config (`realm-config.json`):
  ```json
  "revokeRefreshToken": true,
  "refreshTokenMaxReuse": 0
  ```
- [ ] Tăng `accessTokenLifespan` xuống 1-5 phút để refresh chạy thường xuyên hơn.
- [ ] Verify: trace network, mỗi lần Spring renew → response từ Keycloak có refresh_token mới.

#### A2.2 PKCE verification

**Why:** PKCE bảo vệ Authorization Code khỏi bị intercept (man-in-the-middle khi browser gửi code về). Spring Security 6+ tự bật cho public client, nhưng phải verify network trace.

**What:**
- [ ] Trace browser DevTools → request đến Keycloak `/auth` có `code_challenge` và `code_challenge_method=S256`.
- [ ] Test: Sửa code injection vào URL → reject vì PKCE mismatch.

#### A2.3 Per-user rate limiting

**Why:** Mặc định `RequestRateLimiter` bucket per IP — bypass dễ với NAT (nhiều user cùng 1 IP) hoặc DDoS từ nhiều IP. Per-user (sub claim) bảo vệ user account take-over.

**What:**
```java
// edge-service RateLimiterConfig
@Bean
KeyResolver userKeyResolver() {
    return exchange -> ReactiveSecurityContextHolder.getContext()
        .map(ctx -> ctx.getAuthentication().getName())
        .defaultIfEmpty("anonymous");
}
```
```yaml
# config/edge-service.yml
spring.cloud.gateway.routes:
  - id: order-route
    filters:
      - name: RequestRateLimiter
        args:
          redis-rate-limiter.replenishRate: 10
          redis-rate-limiter.burstCapacity: 20
          key-resolver: "#{@userKeyResolver}"
```

**Verify:** Script gửi 25 request liên tiếp với cùng token → request 21-25 nhận `429 Too Many Requests`.

#### A2.4 mTLS giữa services (advanced)

**Why:** Hiện cluster-internal traffic là HTTP plain. Bất kỳ ai compromise 1 pod đều có thể giả mạo service khác. mTLS đảm bảo cả 2 đầu xác thực.

**What:**
- [ ] **Service mesh** (Linkerd / Istio) — auto inject sidecar, transparent mTLS.
- [ ] HOẶC **manual** — dùng `cert-manager` cấp cert cho mỗi service, configure `WebClient`/`RestClient`/`R2DBC` với truststore.
- Khuyến nghị: Linkerd (nhẹ, tự động) cho bookstore size này.

#### A2.5 Secrets management

**Why:** `secret.yml` plain text trong Git là red flag. Bất kỳ ai clone repo đều có DB password. Production-grade: secrets phải mã hóa hoặc fetch từ vault.

**What:** Xem mục **B6 — SealedSecrets / External Secrets** ở phần DevOps.

### A3. Defense-in-depth (Khuyến khích cho production)

#### A3.1 Audit log

**Why:** Ai đã `DELETE /books/{isbn}` lúc 2h sáng? Hiện không có. Cần immutable log riêng cho compliance.

**What:**
- [ ] Logback appender riêng `audit.log` (file rolling), key: `userId`, `action`, `resource`, `timestamp`, `traceId`.
- [ ] Spring Security có sự kiện `AuthenticationSuccessEvent`, `AuthenticationFailureBadCredentialsEvent` — bind `@EventListener` ghi log audit.
- [ ] Domain events (POST /books, DELETE /orders) → ghi audit qua Aspect hoặc `@PostFilter`.

#### A3.2 Vulnerability scanning trong CI

**Why:** Phát hiện CVE trong dep tree + base image trước khi merge. Hiện đang tin tưởng `paketo-buildpacks` chọn base image safe — nhưng vẫn cần scan custom layer.

**What:** Thêm vào `.github/workflows/ci-*-pipeline.yml`:

```yaml
- name: Trivy filesystem scan (deps)
  uses: aquasecurity/trivy-action@master
  with:
    scan-type: fs
    scanners: vuln
    severity: HIGH,CRITICAL
    exit-code: 1

- name: Trivy image scan (after bootBuildImage)
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: pxloc97/order-service:${{ github.sha }}
    severity: HIGH,CRITICAL
    exit-code: 1
```

- [ ] Bonus: **Snyk** (sâu hơn về license) hoặc **Grype** (alternative).

#### A3.3 SAST + DAST

**What:**
- [ ] **SonarQube** community edition + GitHub action `sonarsource/sonarqube-scan-action` — phát hiện code smell, security hotspot.
- [ ] **OWASP ZAP** baseline scan trong CI nightly — nhận URL deployed staging, attack với mức `passive`.

#### A3.4 SBOM + image signing

**Why:** Supply chain. Biết chính xác layer nào của image chứa lib gì + verify image deployed đúng image build từ source.

**What:**
```gradle
// build.gradle
plugins { id 'org.cyclonedx.bom' version '2.0.0' }
```
```yaml
# CI
- run: ./gradlew cyclonedxBom            # generate SBOM
- run: cosign sign --key ... pxloc97/...  # sign image
```
- [ ] Push SBOM lên registry attached với image (Docker Hub OCI artifact).
- [ ] Ở deploy: `cosign verify` trước `kubectl apply`.

---

## 🚢 Phần B — DevOps / ArgoCD Roadmap

### B0. Pipeline thực tế bookstore cần (3 môi trường)

```
┌─────────┐   git push    ┌──────────────┐   build+test   ┌──────────────┐
│ Dev     │──────────────►│ GitHub       │───────────────►│ Image        │
│ commit  │               │ Actions CI   │   image push   │ Registry     │
└─────────┘               └──────┬───────┘                │ (Docker Hub  │
                                 │                        │  / GHCR)     │
                                 │ bump tag               └──────┬───────┘
                                 ▼                               │
                          ┌──────────────┐                       │ pull
                          │ GitOps repo  │                       │
                          │ (manifests + │                       ▼
                          │  values.yml) │              ┌──────────────────┐
                          └──────┬───────┘              │ K8s cluster      │
                                 │                      │ ┌──────────────┐ │
                                 │ Argo CD watches      │ │ ArgoCD       │ │
                                 └─────────────────────►│ │ controller   │ │
                                                        │ └──────────────┘ │
                                                        │   sync apps      │
                                                        └──────────────────┘
```

**3 môi trường:**
| Env | Cluster | Sync policy | Trigger |
|---|---|---|---|
| **dev** | kind local | manual `make k8s-up` | dev tự test |
| **staging** | EKS staging hoặc kind on CI | auto-sync khi `main` mới | merge PR |
| **production** | EKS prod | manual approve | tag release `v*` |

### B1. Image registry + immutable tags

**Why:** Hiện tag `0.0.1-SNAPSHOT` cố định → không phân biệt được commit nào. Production phải tag bằng git SHA.

**What:**
- [ ] Đăng ký GHCR (free cho open repo) hoặc Docker Hub.
- [ ] Sửa CI workflow:
```yaml
# .github/workflows/ci-order-pipeline.yml
- name: Login GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- name: Build OCI image
  working-directory: order-service
  run: ./gradlew bootBuildImage --imageName=ghcr.io/${{ github.repository_owner }}/order-service:${{ github.sha }}

- name: Push image
  run: docker push ghcr.io/${{ github.repository_owner }}/order-service:${{ github.sha }}
```
- [ ] Mỗi commit = 1 image với tag = SHA. Tag `latest` = HEAD nhánh main.

### B2. GitOps repo

**Why:** App code repo (`spring-native-bookstore`) ≠ deployment manifest repo. Tách biệt giúp ArgoCD watch deployment changes mà không cần build.

**What:**
- [ ] Tạo repo `polar-bookstore-gitops` (hoặc folder `polar-deployment/gitops/` cùng repo, tradeoff dễ debug nhưng coupled hơn).
- [ ] Cấu trúc (Kustomize overlay style):
```
polar-bookstore-gitops/
├── argocd/
│   ├── applicationset.yml      ← một ApplicationSet generate Application cho tất cả service × env
│   └── projects/
│       └── bookstore.yml       ← AppProject scope + RBAC
├── platform/                   ← Postgres x3, Kafka, Redis, Keycloak, observability
│   ├── postgres-order.yml
│   └── ...
└── services/
    ├── order-service/
    │   ├── base/               ← deployment + service + configmap (giống hiện tại trong order-service/k8s/)
    │   │   ├── deployment.yml
    │   │   ├── service.yml
    │   │   └── kustomization.yml
    │   └── overlays/
    │       ├── staging/
    │       │   ├── kustomization.yml   ← namePrefix: staging-, image tag, replicas
    │       │   ├── replicas-patch.yml
    │       │   └── resources-patch.yml
    │       └── production/
    │           ├── kustomization.yml   ← namePrefix: prod-, image tag, replicas
    │           ├── replicas-patch.yml
    │           ├── hpa.yml
    │           ├── pdb.yml
    │           └── resources-patch.yml
    └── ...
```

- [ ] Move K8s manifests từ `<service>/k8s/` sang GitOps repo. App repo chỉ chứa code, GitOps repo chứa manifest.
- [ ] CI bump image tag: sau khi push image, CI clone GitOps repo → chạy `kustomize edit set image` trong overlay tương ứng → commit → push. Ví dụ:
  ```bash
  cd services/order-service/overlays/staging
  kustomize edit set image order-service=ghcr.io/${{ github.repository_owner }}/order-service:${{ github.sha }}
  ```
  Không dùng `yq` sửa raw `deployment.yml` vì dễ làm hỏng Kustomize overlay.

### B3. ArgoCD setup

#### B3.1 Cài đặt
```bash
kubectl create namespace argocd
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/v2.13.0/manifests/install.yaml
kubectl wait --for=condition=available deploy --all -n argocd --timeout=300s
# Get admin password
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d
```

#### B3.2 ApplicationSet — generate Apps tự động

**Why:** Có 6+ service × 2 env, không nên viết Application thủ công cho từng cái. Tách thành **hai ApplicationSet** vì ArgoCD chỉ bật auto-sync khi block `automated:` tồn tại; không thể dùng một template cho cả auto-sync và manual-sync.

**Staging — auto-sync:**
```yaml
# argocd/applicationset-staging.yml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: bookstore-staging
  namespace: argocd
spec:
  generators:
    - list:
        elements:
          - service: catalog-service
          - service: order-service
          - service: inventory-service
          - service: dispatcher-service
          - service: edge-service
          - service: search-service
  template:
    metadata:
      name: '{{service}}-staging'
    spec:
      project: bookstore
      source:
        repoURL: https://github.com/<you>/polar-bookstore-gitops
        targetRevision: main
        path: services/{{service}}/overlays/staging
      destination:
        server: https://kubernetes.default.svc
        namespace: bookstore-staging
      syncPolicy:
        automated:
          prune: true
          selfHeal: true
        syncOptions:
          - CreateNamespace=true
```

**Production — manual sync:**
```yaml
# argocd/applicationset-production.yml
apiVersion: argoproj.io/v1alpha1
kind: ApplicationSet
metadata:
  name: bookstore-production
  namespace: argocd
spec:
  generators:
    - list:
        elements:
          - service: catalog-service
          - service: order-service
          - service: inventory-service
          - service: dispatcher-service
          - service: edge-service
          - service: search-service
  template:
    metadata:
      name: '{{service}}-production'
    spec:
      project: bookstore
      source:
        repoURL: https://github.com/<you>/polar-bookstore-gitops
        targetRevision: main
        path: services/{{service}}/overlays/production
      destination:
        server: https://kubernetes.default.svc
        namespace: bookstore-production
      syncPolicy:
        # Không có block automated → ArgoCD chỉ detect drift, không tự sync
        syncOptions:
          - CreateNamespace=true
```

**Notes:**
- `path` trỏ đến **overlay** (`overlays/{{env}}`), không phải base.
- Không dùng `namePrefix` ở ApplicationSet; overlay đã tự quản lý prefix (nếu cần).

#### B3.3 Sync policy theo env

| Env | `automated.prune` | `automated.selfHeal` | Auto-sync | Manual approve |
|---|---|---|---|---|
| dev (kind) | n/a | n/a | false | n/a (không qua ArgoCD) |
| **staging** | **true** | **true** | **true** | không (auto-sync) |
| **production** | n/a | n/a | **false** | **có** (kỹ sư click Sync sau review) |

Production: dev push tag `v1.2.3` → CI bump GitOps repo → ArgoCD detect change nhưng KHÔNG sync → kỹ sư review + click "Sync" trên UI.

#### B3.4 AppProject + RBAC

**Why:** Không để Application dùng `project: default`. AppProject giới hạn source repo, destination cluster/namespace, và resource kinds được phép sync. Trong production cấm dùng initial admin account.

**What:**
```yaml
# argocd/projects/bookstore.yml
apiVersion: argoproj.io/v1alpha1
kind: AppProject
metadata:
  name: bookstore
  namespace: argocd
spec:
  description: Polar Bookstore services
  sourceRepos:
    - https://github.com/<you>/polar-bookstore-gitops
  destinations:
    - namespace: bookstore-staging
      server: https://kubernetes.default.svc
    - namespace: bookstore-production
      server: https://kubernetes.default.svc
  clusterResourceWhitelist: []   # không cho phép cluster-scoped resources
  namespaceResourceWhitelist:
    - group: apps
      kind: Deployment
    - group: ''
      kind: Service
    - group: ''
      kind: ConfigMap
    - group: ''
      kind: Secret
    - group: ''
      kind: ServiceAccount
    - group: autoscaling
      kind: HorizontalPodAutoscaler
    - group: policy
      kind: PodDisruptionBudget
    - group: batch
      kind: Job
    - group: argoproj.io          # nếu dùng Argo Rollouts
      kind: Rollout
    - group: argoproj.io
      kind: AnalysisTemplate
  orphanedResources:
    warn: true
```

- [ ] Cấu hình ArgoCD SSO (OIDC/Dex) thay vì dùng initial admin password.
- [ ] Tạo ArgoCD policy: `role:bookstore-admin` có quyền sync `bookstore` project; `role:bookstore-readonly` chỉ xem.
- [ ] Lưu admin password ban đầu vào secret manager và rotate ngay sau setup.
- [ ] Backup ArgoCD state: export Application + AppProject manifests định kỳ.

#### B3.5 Notifications

```yaml
# argocd-notifications-cm
triggers:
  - name: on-sync-failed
    condition: app.status.operationState.phase in ['Error', 'Failed']
subscriptions:
  - recipients: [slack:bookstore-deploy]
    triggers: [on-sync-failed, on-deployed, on-health-degraded]
```

### B4. Helm vs Kustomize

**Khuyến nghị: Kustomize** cho bookstore vì:
- Đã có `kustomization.yml` mỗi service.
- Đơn giản, no template engine.
- Helm mạnh hơn nhưng overhead lớn cho bookstore size này.

**Layout kustomize overlay:**
```
services/order-service/
├── base/
│   ├── deployment.yml
│   ├── service.yml
│   └── kustomization.yml
└── overlays/
    ├── staging/
    │   ├── kustomization.yml      ← namePrefix, image tag, replicas
    │   └── replicas-patch.yml
    └── production/
        ├── kustomization.yml
        ├── hpa.yml                ← chỉ prod
        ├── pdb.yml
        └── resources-patch.yml
```

### B5. HPA + PodDisruptionBudget

**Why:** Cluster scale theo tải. PDB đảm bảo upgrade rolling không downtime.

**What:**
```yaml
# overlays/production/hpa.yml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service   # phải khớp với tên Deployment sau khi overlay áp namePrefix (nếu có)
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
```

```yaml
# overlays/production/pdb.yml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service
spec:
  maxUnavailable: 1    # tốt hơn minAvailable: 1 khi replica count thấp
  selector:
    matchLabels:
      app: order-service
```

**Notes:**
- `minAvailable: 1` cho service 2 replicas vẫn có thể cho phép drain cả 2 pod nếu có surge. `maxUnavailable: 1` an toàn hơn.
- Nếu overlay dùng `namePrefix: prod-`, HPA `scaleTargetRef.name` phải là `prod-order-service`, và PDB selector cũng phải khớp label (không khớp theo tên).
- Cân nhắc thêm `topologySpreadConstraints` trong Deployment để pod nằm ở các node/AZ khác nhau.

### B6. Secrets — SealedSecrets

**Why:** `secret.yml` plain text trong Git = leak ngay khi clone. SealedSecrets mã hóa với public key của controller trong cluster, chỉ controller decrypt được.

**What:**
```bash
# Cài SealedSecrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.27.0/controller.yaml

# Tạo secret + seal
kubectl create secret generic catalog-db --from-literal=password=password \
  --dry-run=client -o yaml | kubeseal --controller-namespace kube-system -o yaml > services/catalog-service/base/sealed-secret.yml

# Commit sealed-secret.yml → safe to commit
```

**Bonus production:** External Secrets Operator + AWS Secrets Manager — secrets sống ở AWS, K8s pull on-demand. Phù hợp khi có nhiều cluster / multi-env.

### B7. Database migrations

**Why:** Hiện Flyway chạy lúc app start. Race condition khi 2+ replica boot đồng thời. Cách đúng: chạy migrations qua **Job** riêng trước khi rollout app.

**What:**

**Option A (khuyến nghị):** Build một migrations image nhỏ chứa SQL từ `src/main/resources/db/migration/`. Không dùng ConfigMap vì dễ vượt size limit và lạc version.

```yaml
# services/order-service/base/flyway-job.yml
apiVersion: batch/v1
kind: Job
metadata:
  name: flyway-order-${IMAGE_TAG}     # tag-suffixed = mỗi version mới run lại
  annotations:
    argocd.argoproj.io/sync-wave: "-1"   # chạy trước Deployment
spec:
  ttlSecondsAfterFinished: 600
  template:
    spec:
      restartPolicy: OnFailure
      containers:
        - name: flyway
          image: ghcr.io/<you>/order-service-migrations:${IMAGE_TAG}
          command: [flyway, migrate]
          args:
            - -url=jdbc:postgresql://polar-postgres-order:5432/polardb_order
            - -user=$(USER)
            - -password=$(PASSWORD)
            - -locations=filesystem:/flyway/sql
          envFrom:
            - secretRef: { name: order-db }
```

**Option B (đơn giản hơn cho local):** Dùng init container chạy từ application image nhưng chỉ cho phép 1 replica khởi động đầu tiên chạy migration. Không dùng cho production multi-replica vẫn có race risk.

- [ ] Argo CD `sync-wave` annotation (`argocd.argoproj.io/sync-wave: "-1"`) đảm bảo Job chạy trước Deployment.
- [ ] CI build và push `*-migrations` image cùng tag với app image.
- [ ] Không bao giờ rollback migration bằng cách delete Job; rollback = revert GitOps commit + run forward-fix migration (`V<next>__*.sql`).


### B8. Argo Rollouts (canary, optional)

**Why:** Standard `RollingUpdate` cập nhật toàn bộ pod theo % — nếu version mới crash, rollback bằng tay tốn thời gian. Argo Rollouts canary: deploy version mới nhận 10% traffic, đo metric, auto-promote hoặc rollback.

**What:** Áp dụng cho **catalog-service** trước (read-heavy, dễ rollback):
```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: catalog-service
spec:
  strategy:
    canary:
      steps:
        - setWeight: 10
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: success-rate
        - setWeight: 50
        - pause: { duration: 5m }
        - setWeight: 100
```

```yaml
# AnalysisTemplate
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
spec:
  metrics:
    - name: success-rate
      successCondition: result[0] >= 0.95
      provider:
        prometheus:
          query: |
            sum(rate(http_server_requests_seconds_count{outcome="SUCCESS"}[5m]))
            / sum(rate(http_server_requests_seconds_count[5m]))
```

### B9. Disaster Recovery + rollback playbook

**What:** Document trong `RUNBOOK.md`:
- [ ] Rollback ArgoCD: `argocd app rollback <app> <revision>` hoặc revert GitOps commit.
- [ ] Rollback DB migration: Flyway downgrade scripts (`U<n>__*.sql`) — opt-in, nhưng ưu tiên forward-fix migration.
- [ ] Postgres backup: cron job dump → S3 (production trên AWS RDS dùng auto-backup).
- [ ] Recovery time test: mỗi quý chạy "kill cluster → restore từ Git" — nếu < 30 phút thì pass.

### B10. Production readiness checklist

**Goal:** Đảm bảo ArgoCD + K8s setup không chỉ chạy được mà còn an toàn và operable.

- [ ] **Resource limits/requests** đã set cho tất cả containers.
- [ ] **ResourceQuota + LimitRange** đã tạo trong mỗi namespace (`bookstore-staging`, `bookstore-production`).
- [ ] **NetworkPolicies** hạn chế traffic giữa các service (chỉ cho phép những gì cần thiết).
- [ ] **Pod Security Standards** (restricted) được apply cho namespace.
- [ ] **Liveness, readiness, startup probes** đã thêm vào tất cả Deployments.
- [ ] **Topology spread constraints** đảm bảo pod phân bố qua node/AZ.
- [ ] **Image pull secrets** cho registry private (GHCR/ECR) đã tạo và mount.
- [ ] **Container image non-root user** và read-only root filesystem.
- [ ] **ArgoCD notifications** gửi Slack/email khi sync failed/health degraded.
- [ ] **Monitoring ArgoCD itself**: metrics `/metrics`, Grafana dashboard, alert when app is `OutOfSync` hoặc `Degraded`.
- [ ] **Backup ArgoCD**: lưu trữ AppProject, ApplicationSet, secret ngoài cluster.
- [ ] **Disaster recovery drill** đã chạy ít nhất 1 lần trước khi go-live.

---

## 🎯 Thứ tự thực hiện đề xuất (3-month sprint)

### Tháng 1 — Security baseline
| Tuần | Task | Done |
|---|---|---|
| 1 | A1.1 KeycloakJwtAuthoritiesConverter cho 3 service còn lại | ⬜ |
| 1 | A1.2 Per-endpoint role rules + tests | ⬜ |
| 2 | A1.4 Security headers ở edge | ⬜ |
| 2 | A1.5 JWK cache config | ⬜ |
| 3 | A1.3 Method-level @PreAuthorize cho order/inventory | ⬜ |
| 3 | A2.1 Refresh token rotation (Keycloak realm config) | ⬜ |
| 4 | A2.3 Per-user rate limiting + test | ⬜ |
| 4 | A2.2 PKCE verification + DevTools trace doc | ⬜ |

### Tháng 2 — DevOps GitOps
| Tuần | Task | Done |
|---|---|---|
| 1 | B1 Image registry GHCR + CI push immutable tag | ⬜ |
| 1 | B6 SealedSecrets — convert secret.yml | ⬜ |
| 2 | B2 GitOps repo + manifest migration | ⬜ |
| 3 | B3 ArgoCD install + ApplicationSet + AppProject | ⬜ |
| 3 | B3.5 ArgoCD Notifications → Slack | ⬜ |
| 3 | B7 Flyway Job + sync-wave | ⬜ |
| 4 | B5 HPA + PDB cho production overlay | ⬜ |

### Tháng 3 — Hardening + delivery patterns
| Tuần | Task | Done |
|---|---|---|
| 1 | A3.2 Trivy in CI cho deps + image | ⬜ |
| 1 | A3.4 SBOM + cosign sign | ⬜ |
| 2 | A3.3 SonarQube + ZAP baseline scan | ⬜ |
| 3 | B8 Argo Rollouts canary cho catalog | ⬜ |
| 3 | A3.1 Audit log + immutable storage | ⬜ |
| 4 | A2.4 mTLS via Linkerd | ⬜ |
| 4 | B9 + B10 Disaster recovery runbook + production readiness drill | ⬜ |

---

## 🔥 Boss Challenges

- **Sec-1:** Token leak simulation — log Bearer token, attacker cố replay → refresh rotation phát hiện reuse → revoke session.
- **Sec-2:** OWASP Top 10 audit báo cáo: zero CRITICAL ở Trivy + Sonar.
- **Sec-3:** Customer login thử `DELETE /books/{isbn}` → 403 method-security; thử `GET /orders` của user khác → 403 `@PreAuthorize`.
- **Ops-1:** Push commit + 5 phút sau xem ArgoCD UI tự deploy lên staging.
- **Ops-2:** Inject 10% lỗi vào catalog-service → Argo Rollouts auto-rollback < 5 phút (Prometheus query cảnh báo).
- **Ops-3:** Disaster drill — `kubectl delete namespace bookstore-production` → recovery từ Git < 15 phút.
- **Sec+Ops-1:** Sealed secret rotation — đổi DB password trong AWS Secrets Manager → External Secrets sync → Pod auto-restart pickup → no downtime.

---

## 📚 Further Reading (bổ sung tasks/senior-roadmap.md)

### Security deep dive
- [OWASP Top 10 — 2021](https://owasp.org/Top10/) — checklist must-know
- [OWASP API Security Top 10 — 2023](https://owasp.org/API-Security/editions/2023/en/0x11-t10/)
- [Spring Security — Servlet Method Security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [Spring Security — `@PreAuthorize` SpEL](https://docs.spring.io/spring-security/reference/servlet/authorization/expression-based.html)
- [Keycloak — Token Mappers](https://www.keycloak.org/docs/latest/server_admin/#_protocol-mappers)
- [Keycloak — Refresh Token rotation](https://www.keycloak.org/docs/latest/server_admin/#_clients) (xem section "Tokens" tab)
- [Mozilla Observatory](https://observatory.mozilla.org/) — security headers checker
- [Trivy — Filesystem & Image scan](https://aquasecurity.github.io/trivy/latest/)
- [cosign — Image signing](https://docs.sigstore.dev/cosign/overview/)
- [CycloneDX Gradle plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin)
- [Linkerd — getting started](https://linkerd.io/2/getting-started/) — service mesh + mTLS

### DevOps / GitOps deep dive
- [OpenGitOps Principles](https://opengitops.dev/) — 4 nguyên tắc nền tảng
- [Argo CD — ApplicationSet generators](https://argo-cd.readthedocs.io/en/stable/operator-manual/applicationset/)
- [Argo CD — Sync Waves & Hooks](https://argo-cd.readthedocs.io/en/stable/user-guide/sync-waves/)
- [Argo CD — App of Apps pattern](https://argo-cd.readthedocs.io/en/stable/operator-manual/cluster-bootstrapping/)
- [Argo CD — Notifications catalog](https://argocd-notifications.readthedocs.io/en/stable/catalog/)
- [Argo Rollouts — Canary Strategy](https://argoproj.github.io/argo-rollouts/features/canary/)
- [Argo Rollouts — Analysis](https://argoproj.github.io/argo-rollouts/features/analysis/)
- [SealedSecrets — README](https://github.com/bitnami-labs/sealed-secrets#readme)
- [External Secrets Operator](https://external-secrets.io/latest/)
- [Kustomize — Reference](https://kubectl.docs.kubernetes.io/references/kustomize/)
- [Kubernetes — HPA v2 algorithm](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/#algorithm-details)
- [Kubernetes — PodDisruptionBudget](https://kubernetes.io/docs/tasks/run-application/configure-pdb/)
- [Flyway — In-Pod migration considerations](https://documentation.red-gate.com/fd/migrations-184127470.html)

### Books cụ thể
| Sách | Mục đích |
|---|---|
| **OAuth 2.0 in Action** — Justin Richer & Antonio Sanso | Deep dive OAuth flows + threat model |
| **Spring Security in Action** — Laurentiu Spilca | Practical Spring Security patterns |
| **GitOps and Kubernetes** — Yuen, Matyushentsev, Ekenstam, Suen | Argo CD bible |
| **Continuous Delivery** — Jez Humble, David Farley | Mindset đầu tiên trước khi đụng tool |
| **The DevOps Handbook** — Gene Kim et al. | 4 capabilities của high-performing org |
| **Container Security** — Liz Rice | Image security, namespace, capabilities |

---

## 🧭 Mapping với senior-roadmap.md

| Mục trong file này | Tham chiếu senior-roadmap.md |
|---|---|
| A1.1 — A1.5 (security baseline) | Giai đoạn 3.B (BFF, role-based, headers) |
| A2.1 — A2.5 (hardening) | Giai đoạn 3.C (testing) + Module 6 (mới) |
| A3.* (defense-in-depth) | Giai đoạn 4.7 (observability) + Giai đoạn 7.10 (Sonar) |
| B1 — B4 (image + GitOps + ArgoCD) | Giai đoạn 5 (toàn bộ) |
| B5 — B6 (HPA, secrets) | Giai đoạn 5.5 (secrets) + Giai đoạn 7 (HPA prod) |
| B7 (DB migrations) | (mới — chưa có trong senior roadmap, complementary) |
| B8 (Argo Rollouts) | Giai đoạn 5.6 (canary) |
| B9 (DR runbook) | (mới — production-readiness) |
| B10 (Production readiness checklist) | Giai đoạn 7 (AWS production hardening) |

> File này tập trung **hành động cụ thể với codebase hiện tại**, senior-roadmap tập trung **kỹ năng + giai đoạn học**. Đọc song song.
