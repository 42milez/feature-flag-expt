# feature-flag-expt

[English](README.md) | 日本語

[![CI](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yaml/badge.svg)](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yaml)
![Java](https://img.shields.io/badge/Java-25-orange)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791)
![Kubernetes](https://img.shields.io/badge/Kubernetes-kind-326CE5)
[![License](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

フィーチャーフラグ管理サービスを題材に、**JVM でのドメイン設計、Kubernetes デプロイ、オブザーバビリティ、CI 品質ゲート**を、1 つのレビューしやすいリポジトリで示すポートフォリオです。フラグは環境ごとのターゲティング、緊急停止用のキルスイッチ、テナント単位の許可リスト、フラグキーとテナント/ユーザー識別子から算出する決定的な割合バケットで制御でき、状態の変更はすべて監査イベントとして記録されます。

## 目次

- [このプロジェクトの重点領域](#このプロジェクトの重点領域)
- [開発アプローチ](#開発アプローチ)
- [アーキテクチャ](#アーキテクチャ)
- [技術スタック](#技術スタック)
- [クイックスタート](#クイックスタート)
- [API 一覧](#api-一覧)
- [設計上の意思決定](#設計上の意思決定)
- [デプロイと運用](#デプロイと運用)
- [オブザーバビリティ](#オブザーバビリティ)
- [開発](#開発)
- [リポジトリ構成](#リポジトリ構成)

## このプロジェクトの重点領域

- **JVM サービス設計** — 永続化されるフラグのドメイン、評価ロジック、Spring Data JDBC のトランザクションフロー、監査イベントの記録、Micrometer の計装、Spring Security の境界は Java が担当します。Kotlin は Spring Boot サービス内での Java/Kotlin 相互運用を示すため、読み取り中心の API 境界に限定して使っています。プレビュー API では変更案・サンプルごとの差分・集計を immutable な DTO で表し、ロールアウトポリシー API も同じリクエスト/レスポンス中心の形に置きます。ポリシーバリデーターは Java 実装を共有します。([ADR-0008](docs/decisions/0008-use-kotlin-for-evaluation-preview-api.md))
- **フェイルクローズなセキュリティ境界** — reader/operator ロールを伴うインメモリのユーザー管理（HTTP Basic 認証）。認証なしで公開されるのはヘルスチェック用エンドポイント（Kubernetes の liveness / readiness プローブ）と Swagger UI / OpenAPI ドキュメントだけで、既知の `/api/**` ルートはロール別に許可し、分類されていない `/api/**` ルートは認証済みでも既定で拒否します。([ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md))
- **Kubernetes デプロイ** — Kustomize の `base` と `dev` オーバーレイを kind にデプロイし、Pod Security Standards の [restricted](https://kubernetes.io/docs/concepts/security/pod-security-standards/#restricted) プロファイルに沿ってハードニングします。([ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md))
- **オブザーバビリティ** — Actuator/Micrometer のメトリクス、ECS JSON の構造化ログ、`promtool` のルールテスト付きでコミットした Prometheus アラートルール、Grafana ダッシュボード。([ADR-0011](docs/decisions/0011-keep-observability-stack-alerting-ready-but-local.md))
- **CI 品質ゲート** — フォーマット、Error Prone、全テストと Testcontainers テスト、Kubernetes マニフェストのレンダリング検証、OpenAPI スナップショットの差分検出、`promtool` チェック、Trivy のシークレット/イメージスキャンを、変更ごとに実行します。
- **AI エージェントを活用した開発ワークフロー** — 人間が主導する開発サイクルです。AI エージェントは計画、設計、実装、レビューを支援し、最終的なマージ判断は内容を精査した上でリポジトリオーナーが行います。

## 開発アプローチ

このリポジトリは、人間が主導する AI エージェント活用ワークフローで開発しています。プロダクトとしての意図を定義し、設計判断や実装内容をレビューし、マージを承認するのはオーナーです。AI エージェントは計画、設計、実装、レビューを支援する開発パートナーとして使用しています。

典型的な流れは次のとおりです。小さな機能や明確な修正では、ロードマップの作成を省略し、設計や実装から始めます。

1. オーナーが作りたい機能を伝え、AI エージェントがその内容を複数の実装フェーズに整理したロードマップ（Markdown）を作成します。
2. オーナーがロードマップを承認したら、AI エージェントがフェーズごとの設計書（Markdown）を作成します。
3. オーナーが設計を承認したら、その設計書をもとに AI エージェントが実装します。
4. オーナーが実装をレビューします。
5. 問題があれば AI エージェントに修正を依頼し、なければマージします。

1〜4 の各段階では、AI エージェント同士のピアレビューも行います。たとえば Codex が設計、実装を行い、Claude Code がレビューします。主なレビュー観点は、2026 年時点のモダンなプラクティスに沿っていること、設計と実装がセキュアであること、明らかなオーバーエンジニアリングの兆候がないことです。AI によるレビューは判断材料の一つであり、オーナーによる最終判断の代わりではありません。

## アーキテクチャ

フラグのドメイン、評価ロジック、永続化、監査の振る舞いは Java で実装しています。Kotlin は、Spring Boot サービス内での Java/Kotlin 相互運用を示すため、プレビューやロールアウトポリシー検証のような読み取り中心の API 境界に限定して使っています。プレビュー API では、変更案、サンプルごとの before/after 差分、集計を Kotlin のネストしたリクエスト/レスポンス DTO で表し、Java の `FeatureFlagEvaluator` を再利用します。ロールアウトポリシー検証 API は Kotlin のコントローラー/サービス層で現在のフラグと変更案を組み立て、Java の validator で検証します。検証結果のレスポンス DTO は、検証 API と PATCH 更新時のポリシー違反レスポンスで共有するため Java record としています。

```mermaid
flowchart LR
    Client([Client (e.g., curl)])

    subgraph Sec["Spring Security · HTTP Basic"]
        Auth{"reader / operator role"}
    end

    subgraph API["REST API · /api"]
        FlagCtl["Feature Flag &amp; Evaluate<br/><b>Java</b>"]
        PreviewCtl["Preview<br/><b>Kotlin</b>"]
        PolicyCtl["Rollout Policy<br/><b>Kotlin</b>"]
    end

    subgraph Core["Domain &amp; Services · Java"]
        Eval["Feature Flag Evaluator<br/>(shared)"]
        Svc["Feature Flag Service"]
        Policy["Rollout Policy Validator<br/>(shared)"]
        Audit["Audit Event Service"]
    end

    Repo[["Spring Data JDBC"]]
    DB[("PostgreSQL<br/>flags · audit_events")]

    Client --> Auth
    Auth --> FlagCtl & PreviewCtl & PolicyCtl
    FlagCtl --> Svc & Eval
    PreviewCtl --> Eval
    PolicyCtl --> Policy
    Svc --> Policy & Audit & Repo
    Audit --> Repo
    Repo --> DB
```

評価は次の順にチェックを適用し、最初に一致したものを結果の `reason` として返します。

```mermaid
flowchart TD
    Start([evaluate(flag, context)]) --> S{status == DISABLED?}
    S -- yes --> R1[/false · FLAG_DISABLED/]
    S -- no --> E{environment targeted?}
    E -- no --> R2[/false · ENVIRONMENT_NOT_TARGETED/]
    E -- yes --> K{kill switch active?}
    K -- yes --> R3[/false · KILL_SWITCH_ACTIVE/]
    K -- no --> A{tenant in allowlist?}
    A -- yes --> R4[/true · TENANT_ALLOWLIST_MATCH/]
    A -- no --> I{tenant or user id has text?}
    I -- no --> R5[/false · ROLLOUT_MISS/]
    I -- yes --> B{"bucket(flagKey, rolloutIdentity) &lt; rolloutPercentage?"}
    B -- yes --> R6[/true · ROLLOUT_MATCH/]
    B -- no --> R7[/false · ROLLOUT_MISS/]
```

> `bucket` は `floorMod(SHA-256(flagKey + ":" + rolloutIdentity), 100)` です。`rolloutIdentity` には tenant ID があればそれを使い、なければ user ID を使います。同じフラグキーと `rolloutIdentity` の組み合わせは常に同じバケットに入るため、リクエストごとにランダムになるのではなく、安定した決定的なロールアウトになります。

## 技術スタック

| 領域 | 技術 |
|---|---|
| 言語 | Java 25（ツールチェーン）、Kotlin 2.3 |
| フレームワーク | Spring Boot 4.0 — Web MVC、Security、Validation、Actuator |
| 永続化 | Spring Data JDBC + PostgreSQL、Flyway マイグレーション |
| API ドキュメント | springdoc-openapi 3.0（コードファースト）、コミット済み OpenAPI スナップショット |
| オブザーバビリティ | Micrometer + Prometheus、ECS JSON ログ、Grafana |
| ビルド | Gradle（convention plugin + version catalog）→ distroless `java25` イメージ |
| 品質 | Spotless（google-java-format、ktfmt）、Error Prone |
| テスト | JUnit、MockK、Testcontainers（PostgreSQL）、Spring Security Test |
| デプロイ | Docker（distroless、非 root）、Kubernetes + Kustomize、kind |
| CI | GitHub Actions、Trivy、promtool |

正確なパッチバージョンは [`gradle/libs.versions.toml`](gradle/libs.versions.toml) で管理しています。

## クイックスタート

3 ステップでフラグを作成・評価します。Docker と JDK 25 が必要です。

**1. PostgreSQL を起動する**

```bash
docker run --name feature-flags-postgres \
  -e POSTGRES_DB=featureflags -e POSTGRES_USER=featureflags \
  -e POSTGRES_PASSWORD=featureflags -p 5432:5432 -d postgres:16-alpine
```

コンテナはデータベースプロセスのみを起動します。マイグレーションファイルは `service/src/main/resources/db/migration` 以下にあり、このポートフォリオでは動作確認を簡潔にするため、アプリケーション起動時に Flyway が適用します。本番運用では、アプリケーションとデータベース変更のライフサイクルを分け、デプロイパイプラインや専用 Job でマイグレーションを実行する構成が望ましいです。データベース接続先や認証情報を変更する場合は [デプロイと運用](#デプロイと運用) を参照してください。

**2. サービスを起動する**

```bash
./gradlew :service:bootRun
```

`bootRun` はアプリケーションをフォアグラウンドで起動し続けるため、Gradle の進捗表示が `EXECUTING` のまま止まって見えます。`Started FeatureFlagApplication` が出たら起動完了です。終了するには `Ctrl+C` を押します。

**3. フラグを作成し、評価する**

```bash
# 作成: production を対象、tenant-a を許可リストに、25% ロールアウト（operator ロール）
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

```jsonc
// 201 Created
{ "flagKey": "checkout-redesign", "status": "ENABLED",
  "targetEnvironments": ["production"], "killSwitchActive": false,
  "tenantAllowlist": ["tenant-a"], "rolloutPercentage": 25 }
```

```bash
# production + tenant-a の文脈で評価（reader ロール）
curl -u featureflags-reader:featureflags-reader \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","environment":"production","tenantId":"tenant-a"}' \
  http://localhost:8080/api/evaluate
```

```jsonc
// 200 OK — tenant-a は許可リストに含まれるため、割合ロールアウトの手前で確定する。
// ロールアウトロジックに到達しないので bucket は null。
{ "flagKey": "checkout-redesign", "enabled": true,
  "reason": "TENANT_ALLOWLIST_MATCH", "bucket": null }
```

`enabled` と `reason` により、呼び出し側はフラグ設定の内部構造を知らずに機能を切り替えられます。全エンドポイントは **`http://localhost:8080/swagger-ui.html`** で対話的に確認できます。Kubernetes/kind の経路は [デプロイと運用](#デプロイと運用) を参照してください。

## API 一覧

| メソッド | パス | ロール | 用途 | 実装 |
|---|---|---|---|---|
| `POST` | `/api/flags` | operator | フラグの作成 | Java |
| `GET` | `/api/flags/{flagKey}` | reader / operator | フラグの取得 | Java |
| `PATCH` | `/api/flags/{flagKey}` | operator | フラグの更新（ロールアウトポリシー検証あり） | Java |
| `POST` | `/api/evaluate` | reader / operator | 文脈に基づくフラグの評価 | Java |
| `GET` | `/api/flags/{flagKey}/audit-events` | reader / operator | 監査イベントの一覧（古い順） | Java |
| `POST` | `/api/flags/{flagKey}/preview` | reader / operator | 変更案のプレビュー（差分、書き込みなし） | Kotlin |
| `POST` | `/api/flags/{flagKey}/validate-change` | reader / operator | 変更案のロールアウトポリシー検証 | Kotlin |

**運用エンドポイント**

| パス | アクセス |
|---|---|
| `/actuator/health`（`/liveness`、`/readiness`） | 公開（プローブ用） |
| `/actuator/prometheus` | 認証必須（任意のローカルユーザー） |
| `/swagger-ui.html`、`/v3/api-docs(.yaml)` | 公開 |
| その他の `/api/**` | 拒否（フェイルクローズ） |

生の OpenAPI 仕様は `/v3/api-docs`（JSON）と `/v3/api-docs.yaml`（YAML）で提供され、静的スナップショットを [docs/openapi.yaml](docs/openapi.yaml) にコミットしています。

## 設計上の意思決定

重要な意思決定は、MADR v4 形式の [Architecture Decision Records](docs/decisions/README.md) として記録しています。代表的なもの:

- [ADR-0002](docs/decisions/0002-use-spring-data-jdbc-instead-of-jpa.md) — JPA/Hibernate ではなく Spring Data JDBC を採用
- [ADR-0005](docs/decisions/0005-separate-domain-records-from-persistence-entities.md) — ドメインレコードと永続化エンティティを分離
- [ADR-0008](docs/decisions/0008-use-kotlin-for-evaluation-preview-api.md) — 評価プレビュー API に Kotlin を採用
- [ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md) — ローカル Kubernetes と CI 検証に kind を採用
- [ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md) — ローカルのセキュリティ境界に HTTP Basic を採用

すべての記録は[インデックス](docs/decisions/README.md)を参照してください。

## デプロイと運用

### 継続的インテグレーション

GitHub Actions は 3 つのワークフローを使用します。

| ワークフロー | トリガー | 対象 |
|---|---|---|
| `CI` | `main` への push、プルリクエスト、手動実行 | フォーマット、Error Prone コンパイル、ユニットテスト、Testcontainers 統合テスト、Kubernetes マニフェストのレンダリング検証、OpenAPI スナップショットの差分検出、Prometheus アラートルールの検証 |
| `Image Vulnerability Scan` | `main` への push、プルリクエスト、毎日 18:00 UTC（03:00 JST）、手動実行 | サービスイメージのビルド確認と Trivy イメージスキャン。テストやデプロイのシグナルとは分離 |
| `Kind Smoke Test` | 毎日 18:00 UTC（03:00 JST）、手動実行 | kind クラスターでの起動検証。デプロイ失敗時には Kubernetes の診断情報を収集 |

プルリクエストの CI では、Prometheus サーバーを起動せずに `promtool` でアラートルールを検証します。イメージのワークフローはサービスイメージをローカルでビルドし、その同じイメージを Trivy でスキャンします。

<details>
<summary>脆弱性ゲートの挙動</summary>

Trivy ゲートは、**修正済み**の High / Critical の OS・ライブラリ脆弱性で失敗し、未修正の検出結果は失敗条件から除外します。また、未修正の High / Critical を含む非ブロッキングのジョブサマリーを出力するため、ゲートを失敗させないリスクもレビュー時に確認できます。スケジュール実行は、新しい CVE の公開により、アプリケーションコードに変更がなくても失敗することがあります。

</details>

### 設定

サービスを起動するには PostgreSQL が必要です。クイックスタートの手順で PostgreSQL コンテナを起動した場合は、下記の既定値のままで接続できるため、追加設定は不要です。別のデータベースを使う場合やユーザー名・パスワードを変える場合は、対応する環境変数を上書きしてください。

| 変数 | ローカルでの値 |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |

### セキュリティ

API アクセスにはローカルの HTTP Basic ユーザーを 2 つ使います。参照系の **reader** と、作成・更新系の **operator** です。Prometheus メトリクスには任意の設定済みユーザーが必要で、Swagger UI と OpenAPI ドキュメントはローカルで確認しやすいよう公開のままにしています。監査イベントには認証済みプリンシパルを `actor` として記録します。

<details>
<summary>セキュリティモデルの範囲と発展</summary>

HTTP Basic はローカルのポートフォリオ用の最低限の認証です。ユーザーの認証情報は意図的にアプリケーションのデータベースの外に置き、PostgreSQL はフラグの状態、ロールアウト設定、検証の挙動、監査イベントのために使います。ルートと権限の対応は `SecurityConfig` に直接記述し、余分な間接化なしにセキュリティ境界を読み取りやすく保っています。ローカルのステートレスな JSON API 向けに CSRF トークン処理は無効化しており、ブラウザクライアントでのトレードオフや、本番では Basic を OIDC など組織で管理するアイデンティティプロバイダーへ置き換える方針は [ADR-0010](docs/decisions/0010-use-http-basic-for-local-portfolio-security-boundary.md) に記録しています。

</details>

### kind で実行する

kind ワークフローは Gradle タスクと、`scripts/` 配下の対応するシェルスクリプトから利用できます。Dockerfile は、サービスのビルド出力から固定の jar 名 `feature-flag-platform.jar` をコピーします。

```bash
./gradlew kindCreate          # ローカルクラスターを作成（または kindRecreate / kindDelete）
./gradlew kindLoadImage       # jar とイメージをビルドし kind にロード
./gradlew k8sRenderDev        # dev のレンダリング済みマニフェストをプレビュー
./gradlew devDeploy           # ビルド・ロード・適用・待機・Pod ステータス表示を一括実行
./gradlew k8sPortForward      # アプリの Service をポートフォワード（続けて ./gradlew appHealth）
```

`dev` オーバーレイは `base` の上にローカル kind 用の依存を追加します。クラスター内 PostgreSQL、ローカルのデータベース設定、プレースホルダーの認証情報、`kind load` で使うローカルのイメージタグです。各 Gradle タスクには、より小さなシェルのみのコマンドとして `scripts/*.sh` の同等物があります。

アプリのオーバーレイ起動後に、任意で適用できるローカルの Prometheus/Grafana スタックを追加できます。

```bash
./gradlew k8sApplyObservabilityDev   # 続けて k8sWaitObservabilityDev / k8sStatusObservabilityDev
./gradlew k8sPortForwardPrometheus   # http://localhost:9090
./gradlew k8sPortForwardGrafana      # http://localhost:3000（開発専用 admin / admin）
```

Docker Compose は意図的に**提供していません**。マニフェスト、サービスディスカバリ、プローブ、`kubectl apply` といった、Compose では検証できない実際の Kubernetes デプロイ経路を kind で検証するためです。データベースを必要とする統合テストは代わりに Testcontainers で実行します。[ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md) を参照してください。

### ランタイムのハードニング

ワークロードは Pod Security Standards の [restricted](https://kubernetes.io/docs/concepts/security/pod-security-standards/#restricted) プロファイルに沿っています。

- 非 root のユーザー/グループ、サービスアカウントトークンのマウント無効化
- 書き込み可能な `/tmp` ボリュームのみを許可する読み取り専用ルートファイルシステム
- Linux capabilities の全削除、RuntimeDefault seccomp
- リソース上限、ヘルスプローブ、グレースフルシャットダウン

<details>
<summary>ハードニングの範囲と本番での注意点</summary>

kind と Kustomize のワークフローは、宣言的なデプロイ経路と起動時の挙動をスモークテストで検証するためのものであり、本番クラスターの完全なセキュリティモデルではありません。実際の本番トラフィックでは、Kubernetes によるエンドポイントの削除と SIGTERM の送信が競合する可能性が残ります。そのため、プラットフォーム側がエンドポイントの伝播に追加の時間を必要とする場合は、ロールアウトに短い `preStop` の遅延を追加できます。

</details>

<details>
<summary>なぜ単一リポジトリなのか</summary>

アプリケーションコード、マニフェスト、オブザーバビリティスタック、CI を 1 つのリポジトリにまとめているのは、検証の流れを端から端まで、別リポジトリに依存せずレビューできるようにするためです。本番システムでは、デプロイ設定を別のコンフィグリポジトリに分け、Argo CD や Flux のような GitOps コントローラーで同期させるのが一般的です。そうすることで、デプロイの頻度をアプリケーション開発から独立させ、クラスターに影響する変更へのアクセス制御を厳密にし、クラスターへの書き込み権限を持つ認証情報をアプリケーションの CI から分離できます。このポートフォリオの範囲では、そうしたリリース境界の分離よりも、全体像を一望できるコンパクトな構成を優先しています。

</details>

## オブザーバビリティ

Actuator のヘルスエンドポイントはプローブ用に公開し、Prometheus メトリクスには任意の設定済みユーザーの HTTP Basic 認証情報が必要です。メトリクス名、構造化ロギング、Prometheus と Grafana の成果物、サンプルトラフィックの送信コマンド、ルールやダッシュボードを変更した後の手動リフレッシュ手順については [docs/observability.md](docs/observability.md) を参照してください。

コミット済みのアラートルールとテストは、Alertmanager・PagerDuty・Grafana の本番プロビジョニングではなく、ローカルで alerting-ready な成果物として意図的に扱っています（[ADR-0011](docs/decisions/0011-keep-observability-stack-alerting-ready-but-local.md)）。オーバーレイは標準出力・標準エラー出力のログと、小さな Prometheus/Grafana スタックまでを対象とし、クラスター全体のログ収集ミドルウェアはインストールしません。本番環境へのデプロイでは、対象プラットフォームに応じてログの収集・ルーティング・保持・アクセス制御のミドルウェアを選定する必要があります。

## 開発

### 静的解析

```bash
./gradlew :service:spotlessCheck    # フォーマットを確認
./gradlew :service:spotlessApply    # フォーマットを自動修正
./gradlew :service:compileJava      # Error Prone はコンパイル時に実行される
```

### テスト

```bash
./gradlew :service:test             # すべてのテスト

# 特定のクラス・メソッド
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```

### レビュー用にコードベースをパックする

[Repomix](https://repomix.com/ja/guide) を使って、ソース、テスト、API ドキュメント、デプロイ用マニフェスト、選択した運用設定から、AI に渡しやすい単一の実装レビュー用パックを生成します。

```bash
npx repomix@1.14.1 --config repomix.config.json                       # → build/repomix/feature-flag-expt-review.xml
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000   # 1000 トークン以上のファイル/ディレクトリを表示
npx repomix@1.14.1 --config repomix.config.json --include-diffs        # ワーキングツリー + ステージ済みの差分を含める
```

生成ファイルは Git から除外されます。Repomix はセキュリティチェックを実行しますが、人による確認の代わりにはなりません。生成ファイルを外部の AI サービスに共有する前に、シークレット、個人データ、内部 URL、認証情報、環境固有の設定が含まれていないことを確認してください。

## リポジトリ構成

```text
.
├── service/                       # Spring Boot サービス（Java + Kotlin）
│   └── src/main/.../featureflags/
│       ├── flags/                 # フラグドメイン・評価・永続化       （Java）
│       ├── audit/                 # 監査イベント                       （Java）
│       ├── policy/                # ロールアウトポリシー: validator は Java、API/service は Kotlin
│       ├── preview/               # プレビュー API                     （Kotlin）
│       └── SecurityConfig, OpenApiConfig, ...
├── deploy/
│   ├── k8s/base/                  # アプリの Deployment + Service
│   ├── k8s/overlays/dev/          # kind: クラスター内 PostgreSQL、ローカル設定
│   ├── k8s/overlays/dev-observability/   # Prometheus + Grafana + アラートルール
│   └── kind/cluster.yaml
├── docs/
│   ├── decisions/                 # ADR（MADR v4）
│   ├── observability.md
│   └── openapi.yaml               # コミット済み OpenAPI スナップショット
├── scripts/                       # kind/k8s Gradle タスクのシェル版
├── .github/workflows/             # CI · イメージスキャン · kind スモークテスト
└── build-logic/                   # Gradle convention plugin
```
