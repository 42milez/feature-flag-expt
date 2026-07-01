# 開発

[English](development.md) | 日本語

ローカルでの実行と開発に関する詳しい参照資料です。プロジェクト概要、アーキテクチャ、API、運用上の考え方は [README](../README.ja.md) を参照してください。

## 前提条件

実行したいワークフローに合わせて、必要最小限のセットアップ方法を選んでください。

### Docker のみのクイックスタート

`docker compose` コマンドを利用できる Docker 環境が必要です。macOS では [Docker Desktop](https://docs.docker.com/desktop/) や [OrbStack](https://docs.orbstack.dev/install) など、自分の環境に合うものを使ってください。Linux では Docker Engine と Compose プラグイン、または同等の構成を使います。

### ローカル kind / Kubernetes 検証

`docker` に加えて `kind` と `kubectl` が必要です。kind へのデプロイ手順と、ローカルの Prometheus/Grafana 検証コマンドはこれらのツールに依存します。公式の [kind インストール手順](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) と [Kubernetes tools](https://kubernetes.io/docs/tasks/tools/) のドキュメントを参照してください。

### ホスト上で JVM を直接実行する開発

JDK 25 が必要です。CI と揃えるため、[Eclipse Temurin 25](https://adoptium.net/temurin/releases/?version=25) を推奨します。この方法は、ホスト側でのテスト、`bootRun`、OpenAPI 生成、Gradle の補助タスクに必要です。一部の Gradle タスクがシェルスクリプトや Unix ツールを呼び出すため、このプロジェクトは macOS、Linux、または WSL 上の Windows を対象にしています。

## クイックスタート（フル）

3 ステップでフラグを作成し、評価します。この手順では [Docker のみのクイックスタート](#docker-のみのクイックスタート) の前提条件を使います。ホスト側の JDK は不要です。

**1. ローカルの Compose スタックを起動する**

```bash
docker compose up --build -d
```

Compose は Spring Boot の jar 生成を含めてサービスイメージをビルドし、アプリと PostgreSQL を起動します。アプリは `127.0.0.1:8080`、PostgreSQL は `127.0.0.1:5432` にバインドされるため、どちらのポートもローカルマシンからのみ到達できます。データベースには名前付きボリュームがなく、クイックスタート用の破棄可能な状態として扱います。ポート `8080` は `k8sPortForward` と競合し、ポート `5432` は同じ loopback ポートにバインドされた既存のローカル PostgreSQL と競合するため、Compose と kind のポートフォワード手順は別々に実行してください。

**2. フラグを作成し、評価する**

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

`enabled` と `reason` により、呼び出し側はフラグ設定の内部構造を知らずに機能を切り替えられます。全エンドポイントは **`http://localhost:8080/swagger-ui.html`** で対話的に確認できます。Kubernetes/kind の手順は [kind で実行する](#kind-で実行する) を参照してください。

**3. ローカルスタックを停止する**

```bash
docker compose down
```

## 承認ワークフローの手順

高リスク変更は、承認されるまで fail-closed で拒否されます。ここでは上で作成した `checkout-redesign` フラグ（production 対象、`tenant-a` 許可リスト、25% ロールアウト）を使うので、スタックは起動したままにしてください（停止手順の前に実行します）。本番ロールアウトを 25% から 80% へ引き上げる +55 ポイントの変更は、リスク分類器が高リスクと判定します。`approver` ユーザーの認証情報は [設定](#設定) にあります。

**1. 承認なしの高リスク変更は拒否される**

```bash
# operator が承認を付けずに直接ロールアウトを引き上げる
curl -u featureflags-operator:featureflags-operator -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80}' \
  http://localhost:8080/api/flags/checkout-redesign
```

```jsonc
// 422 Unprocessable Entity — 承認されるまで変更はブロックされる。
{ "flagKey": "checkout-redesign", "allowed": false,
  "violations": [ { "code": "HIGH_RISK_REQUIRES_APPROVAL",
                    "message": "High-risk changes require approval before rollout.",
                    "severity": "ERROR" } ] }
```

**2. operator が承認を依頼する**

```bash
# operator が同じ変更案を記述した承認リクエストを作成する
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80}' \
  http://localhost:8080/api/flags/checkout-redesign/approval-requests
```

```jsonc
// 201 Created — PENDING の依頼に、依頼者・リスク・before/after スナップショットが記録される。
{ "approvalId": "5f0a5f6e-7f24-4f4f-a426-bb534ee726bd",
  "flagKey": "checkout-redesign", "requester": "featureflags-operator",
  "approver": null, "status": "PENDING",
  "riskReasons": ["LARGE_PRODUCTION_ROLLOUT_INCREASE"],
  "currentSnapshot": { /* rolloutPercentage: 25, ... */ },
  "proposedSnapshot": { /* rolloutPercentage: 80, ... */ } }
```

**3. 別ユーザーの approver が承認する**

```bash
# approver が手順 2 の approvalId を承認する（依頼者は自分の依頼を承認できない）
curl -u featureflags-approver:featureflags-approver -X POST \
  http://localhost:8080/api/flags/checkout-redesign/approval-requests/5f0a5f6e-7f24-4f4f-a426-bb534ee726bd/approve
```

```jsonc
// 200 OK — 依頼は APPROVED になり、approver が記録される。
{ "approvalId": "5f0a5f6e-7f24-4f4f-a426-bb534ee726bd",
  "flagKey": "checkout-redesign", "requester": "featureflags-operator",
  "approver": "featureflags-approver", "status": "APPROVED", ... }
```

**4. operator が承認付きで変更を再適用する**

```bash
# 同じ operator・同じ変更案に、今度は approvalId を付与する
curl -u featureflags-operator:featureflags-operator -X PATCH \
  -H 'Content-Type: application/json' \
  -d '{"rolloutPercentage":80,"approvalId":"5f0a5f6e-7f24-4f4f-a426-bb534ee726bd"}' \
  http://localhost:8080/api/flags/checkout-redesign
```

```jsonc
// 200 OK — ロールアウトが適用され、承認は 1 回限りで消費される。
{ "flagKey": "checkout-redesign", "status": "ENABLED",
  "targetEnvironments": ["production"], "killSwitchActive": false,
  "tenantAllowlist": ["tenant-a"], "rolloutPercentage": 80 }
```

承認は 1 つの変更案と 1 人の依頼者に紐づきます。記録された before/after スナップショットと照合され、依頼者自身は承認できず、初回使用時に消費されます。このフラグの監査証跡には、さらに `APPROVAL_REQUESTED`、`APPROVAL_APPROVED`、`APPROVAL_USED`、`ROLLOUT_PERCENTAGE_CHANGED` が並びます。全エンドポイントの一覧は [README の API 一覧](../README.ja.md#api-一覧) を参照してください。

## 設定

サービスの起動には PostgreSQL が必要です。Compose クイックスタートでは、アプリコンテナが `postgres` サービスに自動的に接続されます。ホストから JVM を直接実行する場合、以下の既定値で `localhost:5432` の PostgreSQL に接続します。別のデータベースを使う場合やユーザー名・パスワードを変更する場合は、対応する環境変数を上書きしてください。

| 変数 | ローカル値 |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |
| `FEATURE_FLAGS_SECURITY_READER_USERNAME` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_READER_PASSWORD` | `featureflags-reader` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_USERNAME` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_OPERATOR_PASSWORD` | `featureflags-operator` |
| `FEATURE_FLAGS_SECURITY_APPROVER_USERNAME` | `featureflags-approver` |
| `FEATURE_FLAGS_SECURITY_APPROVER_PASSWORD` | `featureflags-approver` |

## kind で実行する

kind のワークフローを実行する前に、[ローカル kind / Kubernetes 検証](#ローカル-kind--kubernetes-検証) の前提条件を用意してください。

```bash
./gradlew kindCreate     # ローカルクラスタを作成する（または: kindRecreate）
./gradlew kindLoadImage  # イメージをビルドして kind に読み込む
./gradlew k8sRenderDev   # 必要に応じてレンダリング済み dev マニフェストを確認する
./gradlew devDeploy      # ビルド、読み込み、適用、待機、Pod 状態表示をまとめて実行する
./gradlew k8sPortForward # アプリサービスをポートフォワードする
./gradlew appHealth      # ローカルのヘルスエンドポイントを確認する
```

`dev` オーバーレイは `base` にローカル kind 用の依存関係を追加します。含まれるものは、クラスタ内 PostgreSQL、ローカルデータベース設定、プレースホルダー資格情報、`kind load` で使うローカルイメージタグです。

Docker Compose は、シンプルなローカルアプリケーション実行環境としてのみ用意しています。Kubernetes マニフェスト、サービスディスカバリ、プローブ、`kubectl apply` の検証には kind を使います。Compose はアプリを `127.0.0.1:8080` にバインドするため、`k8sPortForward` と競合します。Compose と kind によるアプリ公開手順を同時に使わず、どちらか一方を選んでください。データベースに依存する統合テストは、引き続き Testcontainers で実行します。詳しくは [ADR-0009](decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md) を参照してください。

## JVM の開発ループ

JVM をホスト上で直接実行する開発では、[ホスト上で JVM を直接実行する開発](#ホスト上で-jvm-を直接実行する開発) の前提条件を使います。ホスト側のツールチェーンをインストールしたら、ローカルの Compose データベースだけを起動し、ホストから `bootRun` でサービスを実行します。

```bash
docker compose up -d postgres
./gradlew :service:bootRun
```

`bootRun` はアプリケーションをフォアグラウンドで実行し続けるため、Gradle の進捗表示は `EXECUTING` のままになります。`Started FeatureFlagApplication` が表示されたらアプリケーションは起動済みです。停止するには `Ctrl+C` を押します。Compose が PostgreSQL を `127.0.0.1:5432` に公開するため、データベースは `localhost:5432` で到達できます。

Gradle の Compose タスクは、ホスト側の Java ツールチェーンをすでに持っているコントリビューター向けの Docker Compose ラッパーとして残しています。

```bash
./gradlew composeConfig # Compose 設定を検証する
./gradlew composeUp     # Gradle 経由で Compose スタックを起動する
./gradlew composeDown   # Compose スタックを停止して削除する
```

## 静的解析

```bash
./gradlew :service:spotlessCheck # フォーマットを確認する
./gradlew :service:spotlessApply # フォーマットを修正する
./gradlew :service:compileJava   # コンパイル時に Error Prone が実行される
```

## テスト

```bash
./gradlew :service:test             # 全テスト
./gradlew :service:jacocoTestReport # JaCoCo の XML/HTML カバレッジレポートを生成する

# 単一のクラスまたはメソッド
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest"
./gradlew :service:test --tests "com.github.milez42.featureflags.flags.FeatureFlagEvaluatorTest.fullRolloutEnablesFlag"
```

## レビュー用にコードベースをまとめる

[Repomix](https://repomix.com/guide) を使うと、ソース、テスト、API ドキュメント、デプロイ用マニフェスト、選択した運用設定を 1 つにまとめた、AI による実装レビュー向けのパックを生成できます。

```bash
npx repomix@1.14.1 --config repomix.config.json                         # -> build/repomix/feature-flag-expt-review.xml
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000 # 1000 トークン以上のファイル/ディレクトリを表示する
npx repomix@1.14.1 --config repomix.config.json --include-diffs         # working tree と staged diff を含める
```

これらのコマンドはリポジトリルートから実行してください。生成物は git-ignore されています。Repomix はセキュリティチェックを実行しますが、人間によるレビューの代わりにはなりません。外部の AI サービスとファイルを共有する前に、シークレット、個人データ、内部 URL、資格情報、環境固有の設定が含まれていないことを確認してください。
