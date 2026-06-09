# feature-flag-expt

[English](README.md) | 日本語

## 概要

[![CI](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yml/badge.svg)](https://github.com/42milez/feature-flag-expt/.github/workflows/ci.yml)

feature-flag-expt は、フィーチャーフラグを管理・評価するための Spring Boot
サービスです。フラグの作成、取得、更新、評価、プレビュー、提案されたロールアウト
変更の検証、監査を行う REST API を提供します。

フラグは環境ごとのターゲティング、緊急停止用の kill switch、テナント単位の
allowlist、割合に基づく決定的なロールアウトで制御できます。割合ロールアウトでは、
フラグキーとテナントまたはユーザーの識別子から安定したバケットを算出します。
更新内容は Spring Data JDBC を通じて PostgreSQL に永続化され、状態変更は
監査イベントとして記録されます。ロールアウトポリシーの検証は更新時に強制され、
保存前に危険な production ロールアウト変更を検出するために事前実行することも
できます。

本番用のフラグドメイン、永続化フロー、監査の振る舞い、中核となる evaluator
の大部分は Java で実装されています。プレビュー API は Kotlin で実装されており、
提案された変更、サンプルごとの差分、サマリー出力を、変更の保存や監査イベントの
書き込みを行わずに表現します。ロールアウトポリシー API/service も Kotlin で
提案変更のリクエストフローを扱い、再利用されるポリシー validator は Java のまま
本番の更新経路と共有されています。

## 継続的インテグレーション

GitHub Actions は 2 つの workflow を使用します。

| Workflow | Trigger | Coverage |
|---|---|---|
| `CI` | Pull request と手動実行 | フォーマット、Error Prone によるコンパイル、unit test、Testcontainers を使った integration test |
| `Kind Smoke Test` | 毎日 18:00 UTC（03:00 JST）と手動実行 | Spring Boot jar の packaging、service Docker image の buildability、kind cluster での Kubernetes manifest 起動検証 |

Docker Compose は main local runtime として意図的に提供していません。Docker Compose
では Kubernetes manifest、service discovery、probe、deployment configuration、
`kubectl apply` workflow を検証できないためです。代わりに kind を使って、local および
scheduled smoke-test 環境で Kubernetes deployment path を検証します。データベースを
必要とする integration test は Testcontainers で実行し、外部依存関係を test code によって
管理します。local Kubernetes stack では、最小・最軽量の単一開発者向け local Kubernetes
体験よりも、CI と共有できるシンプルな検証フローと標準的な Kubernetes に近い挙動の
バランスを優先しています。この選択は portfolio project として意図したものであり、
local setup を小さく保ちながら CI/CD と Kubernetes deployment practices を示すための
ものです。local Kubernetes の判断については
[ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md)
を参照してください。

Kubernetes の `base` layer は application workload と service contract を定義します。
`dev` overlay は local kind 用の依存関係として、cluster 内 PostgreSQL、local database
configuration、placeholder credentials、`kind load` で使う local image tag を追加します。

application workload は、Kubernetes Pod Security Standards の restricted profile に沿った
最小限の runtime hardening を使用します。non-root user/group、Linux capabilities の全削除、
service account token mount の無効化、制限付き `/tmp` volume を伴う read-only root filesystem、
RuntimeDefault seccomp、resource bounds、health probes、graceful termination を設定しています。
kind と Kustomize の workflow は、宣言的な deployment path と startup behavior の smoke test
を検証するためのものであり、完全な production cluster security model ではありません。実際の
production traffic では、Kubernetes endpoint removal が SIGTERM delivery と競合する可能性が
残るため、platform が endpoint propagation のための追加時間を必要とする場合は、rollout で短い
`preStop` delay を追加できます。

## サービスの実行

### 前提条件

PostgreSQL インスタンスが起動しており、接続できる必要があります。API access では
local の HTTP Basic user を 2 つ使います。reader は read-style operation 用、operator は
create と update operation 用です。Prometheus metrics には、設定済み local user のいずれかが
必要です。Swagger UI と OpenAPI docs は、ポートフォリオをローカルで確認しやすいように
認証なしで公開されています。
audit event は、create と update operation について、認証済み HTTP Basic principal を
`actor` として記録します。actor は Spring Security から service が導出し、request payload
からは受け取りません。

| 変数 | ローカルでの値 |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |

HTTP Basic は、現段階のポートフォリオをローカルで動かすための最低限の認証方式です。
この repository には feature flag の永続化用に PostgreSQL が含まれていますが、
user credential は意図的に application database の外に置いています。この portfolio の
scope では、local authentication は deployment boundary にとどめ、PostgreSQL は
flag state、rollout configuration、validation behavior、audit event のために使います。
production deployment では、authentication boundary を operational context と
compliance context に照らして検討する必要があります。startup 時の password encoding は
in-memory user store 用であり、environment variable や Kubernetes Secret 自体を保護する
ものではありません。

route と authority の対応も、この小規模な portfolio service では意図的に
`SecurityConfig` に直接記述しています。production system では、endpoint grouping
の明確化、`flags:read`、`flags:write`、`metrics:read` のような operation-level authority、
より複雑な check に対する method security、または policy decision を application 外で
管理する必要がある場合の external authorization layer へ発展させる選択肢があります。
この project では、余分な設定の間接化を増やさず security boundary を読み取りやすくするため、
mapping を hardcoded にしています。

### Docker で PostgreSQL を起動する

ローカルで Swagger UI を確認する場合は、既定のデータベース設定で PostgreSQL コンテナを起動します。

```bash
docker run --name feature-flags-postgres \
  -e POSTGRES_DB=featureflags \
  -e POSTGRES_USER=featureflags \
  -e POSTGRES_PASSWORD=featureflags \
  -p 5432:5432 \
  -d postgres:16-alpine
```

コンテナが既に存在する場合は、再度起動します。

```bash
docker start feature-flags-postgres
```

PostgreSQL コンテナはデータベースプロセスのみを起動します。スキーママイグレーションは、Spring Boot アプリケーションの起動時に Flyway によって適用されます。マイグレーションには `service/src/main/resources/db/migration` 以下のファイルが使用されます。

Flyway CLI や `docker-entrypoint-initdb.d` などのツールを使って PostgreSQL コンテナの起動フローからマイグレーションを実行することもできますが、このプロジェクトでは Spring Boot の起動時に実行する方法を標準のマイグレーション経路としています。

### サービスを起動する

```bash
./gradlew :service:bootRun
```

フィーチャーフラグの価値は、設定を保存することだけではなく、アプリケーションが
実行時のコンテキストに応じて機能を有効にするか判断できることにあります。まず、
production 環境を対象とし、`tenant-a` を allowlist に含めたフラグを作成します。

```bash
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

次に、実行時コンテキストとして production 環境と `tenant-a` を渡してフラグを評価します。
レスポンスの `enabled` と `reason` により、呼び出し側はフラグ設定の内部構造を知らずに
機能を切り替えられます。

```bash
curl -u featureflags-reader:featureflags-reader \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","environment":"production","tenantId":"tenant-a"}' \
  http://localhost:8080/api/evaluate
```

### kind で実行する

kind ワークフローは Gradle タスクと対応するシェルスクリプトから利用できます。
標準のプロジェクトエントリポイントを使いたい場合は Gradle を使い、より小さな
シェルのみのコマンドを使いたい場合は `scripts/` 配下のスクリプトを直接実行します。

ローカル kind クラスターを作成します。

```bash
./gradlew kindCreate
# or: scripts/kind-create.sh
```

Spring Boot jar をビルドし、Docker イメージをビルドして kind にロードします。
Dockerfile は、service のビルド出力から固定 jar 名
`feature-flag-platform.jar` をコピーします。

```bash
./gradlew kindLoadImage
# or: scripts/kind-load-image.sh
```

既存の kind クラスターがノード設定の変更前に作成されていた場合は、worker ノードと
ラベルが適用されるように再作成します。

```bash
./gradlew kindRecreate
# or: scripts/kind-recreate.sh
```

不要になったローカル kind クラスターを削除します。

```bash
./gradlew kindDelete
# or: scripts/kind-delete.sh
```

dev overlay は local PostgreSQL、local database URL、placeholder database credentials、
local image tag を提供します。生成された ConfigMap、Secret、resource set を確認したい
場合は、レンダリングされた manifest をプレビューします。

```bash
./gradlew k8sRenderDev
./gradlew k8sApplyDev
# or: scripts/k8s-render-dev.sh
# or: scripts/k8s-apply-dev.sh
```

PostgreSQL とアプリケーションが ready になるまで待ちます。

```bash
./gradlew k8sWaitDev
# or: scripts/k8s-wait-dev.sh
```

アプリケーションと PostgreSQL の Pod が worker ノードにスケジュールされていることを
確認します。

```bash
./gradlew k8sStatusDev
# or: scripts/k8s-status-dev.sh
```

アプリの Service をポートフォワードし、health endpoint を確認します。

```bash
./gradlew k8sPortForward
# or: scripts/k8s-port-forward.sh
```

ポートフォワードが有効な間に、別のターミナルで health check を実行します。

```bash
./gradlew appHealth
# or: scripts/app-health.sh
```

ビルド、ロード、適用、待機、Pod ステータスの表示を 1 つのコマンドで実行します。

```bash
./gradlew devDeploy
# or: scripts/dev-deploy.sh
```

app dev overlay が起動した後、opt-in の local Prometheus/Grafana stack を適用できます。

```bash
./gradlew k8sApplyObservabilityDev
./gradlew k8sWaitObservabilityDev
./gradlew k8sStatusObservabilityDev
```

確認したい場合は local observability service を port-forward します。

```bash
./gradlew k8sPortForwardPrometheus
./gradlew k8sPortForwardGrafana
```

Prometheus は `http://localhost:9090`、Grafana は `http://localhost:3000`
で利用できます。Grafana の dev-only placeholder credentials は `admin` / `admin` です。
local verification workflow、sample traffic command、rule や dashboard 変更後の manual
refresh step については [docs/observability.md](docs/observability.md) を参照してください。

local observability overlay は、この repository を portfolio project として扱う scope のため、
stdout/stderr logs と小さな Prometheus/Grafana stack までを意図的な範囲にしています。
cluster-level log collection middleware はインストールしません。production deployment では、
target platform と operational requirements に基づいて、log collection、routing、retention、
access-control のための middleware を選定する必要があります。

## 関連情報

### Swagger UI

サービスが起動したら、ブラウザで Swagger UI を開きます。

```
http://localhost:8080/swagger-ui.html
```

未加工の OpenAPI 仕様も以下で確認できます。

| 形式 | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

仕様の静的スナップショットは [docs/openapi.yaml](docs/openapi.yaml) にコミットされています。

### Observability

Actuator health endpoints は probe 用に公開され、Prometheus metrics には設定済み local user
いずれかの HTTP Basic 認証情報が必要です。metric name、structured logging、Prometheus と
Grafana の artifact、access-control の期待値については
[docs/observability.md](docs/observability.md) を参照してください。

### 実装レビュー用にコードベースを pack する

[Repomix](https://repomix.com/ja/guide) を使って、source、test、API docs、
deployment manifest、選択された operational configuration から、AI に渡しやすい
単一の implementation-review pack を生成します。

```bash
npx repomix@1.14.1 --config repomix.config.json
```

生成されたファイルは `build/repomix/feature-flag-expt-review.xml` に出力されます。
生成された Repomix output は Git から除外されます。Repomix は security check を実行しますが、
人による確認の代わりにはなりません。生成ファイルを外部の AI service に共有する前に、
secret、personal data、internal URL、credential、environment-specific configuration が
含まれていないことを確認してください。

pack の生成時に token 数が大きい項目を確認するには、token-count tree の閾値を指定して
同じコマンドを実行します。値 `1000` は「1000 tokens 以上の file/directory を表示する」
という意味であり、token 数の上限ではありません。

```bash
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000
```

local の作業中変更をレビュー対象に含める場合は、working tree と staged diff を明示的に
含めます。

```bash
npx repomix@1.14.1 --config repomix.config.json --include-diffs
```

## 静的解析

### フォーマットを確認する（Spotless）

```bash
./gradlew :service:spotlessCheck
```

### フォーマットを自動修正する（Spotless）

```bash
./gradlew :service:spotlessApply
```

### 静的解析を実行する（Error Prone）

Error Prone はコンパイル時に自動的に実行されます。

```bash
./gradlew :service:compileJava
```

## テストの実行

### すべてのテストを実行する

```bash
./gradlew :service:test
```

### 特定のテストクラスを実行する

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
```

### 特定のテストメソッドを実行する

```bash
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```
