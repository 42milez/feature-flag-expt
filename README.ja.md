# feature-flag-expt

[English](README.md) | 日本語

## 概要

[![CI](https://github.com/42milez/feature-flag-expt/actions/workflows/ci.yml/badge.svg)](https://github.com/42milez/feature-flag-expt/.github/workflows/ci.yml)

feature-flag-expt は、フィーチャーフラグを管理・評価するための Spring Boot
サービスです。フラグの作成、取得、更新、評価、プレビュー、ロールアウト変更案の
検証、監査を行う REST API を提供します。

フラグは、環境ごとのターゲティング、緊急停止用のキルスイッチ、テナント単位の
許可リスト、割合に基づく決定的なロールアウトで制御できます。割合ロールアウトでは、
フラグキーとテナントまたはユーザーの識別子から、安定したバケットを算出します。
更新内容は Spring Data JDBC を通じて PostgreSQL に永続化され、状態の変更は
監査イベントとして記録されます。ロールアウトポリシーの検証は更新時に必ず実行され、
危険な本番向けロールアウト変更を保存前に検出するため、事前に実行することも
できます。

本番用のフラグのドメイン、永続化の処理、監査の振る舞い、中核となる評価ロジックの
大部分は Java で実装されています。プレビュー API は Kotlin で実装されており、
変更案・サンプルごとの差分・サマリー出力を、変更の保存や監査イベントの書き込みを
行わずに返します。ロールアウトポリシーの API とサービスも Kotlin で変更案の
リクエスト処理を扱いますが、再利用されるポリシーのバリデーターは Java のままで、
本番の更新経路と共有されています。

## 継続的インテグレーション

GitHub Actions は 2 つのワークフローを使用します。

| ワークフロー | トリガー | 対象 |
|---|---|---|
| `CI` | プルリクエストと手動実行 | サービスのフォーマット、Error Prone によるコンパイル、ユニットテスト、Testcontainers を使った統合テスト、Kubernetes マニフェストのレンダリング検証、OpenAPI スナップショットの差分検出、Prometheus アラートルールの検証、プルリクエストでの Docker イメージのビルド確認、プルリクエストでの Trivy イメージスキャン |
| `Kind Smoke Test` | 毎日 18:00 UTC（03:00 JST）と手動実行 | kind クラスターでの起動をスケジュール実行と手動実行で検証し、デプロイのスモークチェックと Kubernetes の失敗診断の後に、ビルド済みイメージアーカイブを Trivy でスキャンします |

プルリクエストの CI では、Prometheus サーバーを起動せずに `promtool` で
アラートルールを検証します。また、サービスイメージをローカルでビルドし、
その同じイメージを Trivy でスキャンします。修正済みの High / Critical の
OS・ライブラリ脆弱性が見つかった場合は失敗し、未修正の検出結果は無視します。
スケジュール実行の脆弱性ゲートは、アプリケーションコードに変更がなくても、
新しい CVE が公開されたタイミングで赤くなることがあります。これは脆弱性ゲートとして
想定される挙動であり、未修正の検出結果を無視しても、その可能性が小さくなるだけで
完全になくなるわけではありません。

Docker Compose は、メインのローカル実行環境として意図的に提供していません。Docker
Compose では、Kubernetes マニフェスト、サービスディスカバリ、プローブ、デプロイ設定、
`kubectl apply` の一連の流れを検証できないためです。代わりに kind を使い、ローカルと
スケジュール実行のスモークテスト環境の両方で、Kubernetes へのデプロイ経路を検証します。
データベースを必要とする統合テストは Testcontainers で実行し、外部依存はテストコード側で
管理します。ローカルの Kubernetes スタックでは、開発者ひとり向けに最小・最軽量のローカル
Kubernetes 体験を追求するよりも、CI と共有できるシンプルな検証フローと、標準的な Kubernetes
に近い挙動とのバランスを優先しています。これはポートフォリオ作品として意図した選択であり、
ローカルのセットアップを小さく保ちつつ、CI/CD と Kubernetes へのデプロイの実践を示すための
ものです。ローカル Kubernetes に関する判断については
[ADR-0009](docs/decisions/0009-use-kind-for-local-kubernetes-development-and-ci-validation.md)
を参照してください。

Kubernetes の `base` レイヤーは、アプリケーションのワークロードとサービスの仕様を定義します。
`dev` オーバーレイは、ローカルの kind 向けの依存として、クラスター内 PostgreSQL、ローカルの
データベース設定、プレースホルダーの認証情報、`kind load` で使うローカルのイメージタグを追加します。

アプリケーションのワークロードは、Kubernetes Pod Security Standards の restricted プロファイルに
沿った、最小限のランタイムのハードニングを行っています。具体的には、非 root のユーザー/グループ、
Linux capabilities の全削除、サービスアカウントトークンのマウントの無効化、書き込み可能な `/tmp`
ボリュームのみを許可する読み取り専用ルートファイルシステム、RuntimeDefault の seccomp、リソース上限、
ヘルスプローブ、グレースフルシャットダウンを設定しています。kind と Kustomize のワークフローは、
宣言的なデプロイ経路と起動時の挙動をスモークテストで検証するためのものであり、本番クラスターの完全な
セキュリティモデルではありません。実際の本番トラフィックでは、Kubernetes によるエンドポイントの削除と
SIGTERM の送信が競合する可能性が残ります。そのため、プラットフォーム側がエンドポイントの伝播に追加の
時間を必要とする場合は、ロールアウトに短い `preStop` の遅延を追加できます。

## サービスの実行

### 前提条件

PostgreSQL のインスタンスが起動しており、接続できる必要があります。API へのアクセスには、
ローカル用の HTTP Basic ユーザーを 2 つ使います。reader は参照系の操作用、operator は
作成・更新の操作用です。Prometheus のメトリクスには、設定済みのローカルユーザーのいずれかが
必要です。Swagger UI と OpenAPI ドキュメントは、ポートフォリオをローカルで確認しやすいように
認証なしで公開しています。
監査イベントは、作成・更新の操作について、認証済みの HTTP Basic のプリンシパルを `actor` として
記録します。actor はサービスが Spring Security から導出するもので、リクエストのペイロードからは
受け取りません。

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
このリポジトリには、フィーチャーフラグの永続化用に PostgreSQL が含まれていますが、
ユーザーの認証情報は意図的にアプリケーションのデータベースの外に置いています。この
ポートフォリオの範囲では、ローカルの認証はデプロイの境界にとどめ、PostgreSQL はフラグの
状態、ロールアウト設定、検証の挙動、監査イベントのために使います。本番環境へのデプロイでは、
認証の境界を運用上の文脈とコンプライアンスの文脈に照らして検討する必要があります。起動時の
パスワードのエンコードは、インメモリのユーザーストア用であり、環境変数や Kubernetes Secret
そのものを保護するものではありません。

ルートと権限の対応も、この小規模なポートフォリオのサービスでは意図的に `SecurityConfig` に
直接記述しています。本番システムでは、エンドポイントのグルーピングの明確化、`flags:read`・
`flags:write`・`metrics:read` のような操作単位の権限、より複雑なチェックに対するメソッド
セキュリティ、あるいはポリシーの判断をアプリケーションの外で管理する必要がある場合の外部
認可レイヤーへと発展させる選択肢があります。このプロジェクトでは、余分な設定の間接化を
増やさず、セキュリティの境界を読み取りやすく保つために、対応をハードコードしています。

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
実行時のコンテキストに応じて機能を有効にするかどうかを判断できる点にあります。まず、
本番環境を対象とし、`tenant-a` を許可リストに含めたフラグを作成します。

```bash
curl -u featureflags-operator:featureflags-operator \
  -H 'Content-Type: application/json' \
  -d '{"flagKey":"checkout-redesign","status":"ENABLED","targetEnvironments":["production"],"killSwitchActive":false,"tenantAllowlist":["tenant-a"],"rolloutPercentage":25}' \
  http://localhost:8080/api/flags
```

次に、実行時コンテキストとして本番環境と `tenant-a` を渡してフラグを評価します。
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

Spring Boot の jar をビルドし、Docker イメージをビルドして kind にロードします。
Dockerfile は、サービスのビルド出力から、固定の jar 名
`feature-flag-platform.jar` をコピーします。

```bash
./gradlew kindLoadImage
# or: scripts/kind-load-image.sh
```

既存の kind クラスターがノード設定の変更前に作成されていた場合は、ワーカーノードと
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

`dev` オーバーレイは、ローカルの PostgreSQL、ローカルのデータベース URL、プレースホルダーの
データベース認証情報、ローカルのイメージタグを提供します。生成された ConfigMap、Secret、
リソース一式を確認したい場合は、レンダリングしたマニフェストをプレビューします。

```bash
./gradlew k8sRenderDev
./gradlew k8sApplyDev
# or: scripts/k8s-render-dev.sh
# or: scripts/k8s-apply-dev.sh
```

PostgreSQL とアプリケーションが Ready 状態になるまで待ちます。

```bash
./gradlew k8sWaitDev
# or: scripts/k8s-wait-dev.sh
```

アプリケーションと PostgreSQL の Pod がワーカーノードにスケジュールされていることを
確認します。

```bash
./gradlew k8sStatusDev
# or: scripts/k8s-status-dev.sh
```

アプリの Service をポートフォワードし、ヘルスエンドポイントを確認します。

```bash
./gradlew k8sPortForward
# or: scripts/k8s-port-forward.sh
```

ポートフォワードが有効な間に、別のターミナルでヘルスチェックを実行します。

```bash
./gradlew appHealth
# or: scripts/app-health.sh
```

ビルド、ロード、適用、待機、Pod ステータスの表示を 1 つのコマンドで実行します。

```bash
./gradlew devDeploy
# or: scripts/dev-deploy.sh
```

アプリの `dev` オーバーレイが起動した後、任意で適用できるローカルの Prometheus/Grafana
スタックを追加できます。

```bash
./gradlew k8sApplyObservabilityDev
./gradlew k8sWaitObservabilityDev
./gradlew k8sStatusObservabilityDev
```

確認したい場合は、ローカルのオブザーバビリティのサービスをポートフォワードします。

```bash
./gradlew k8sPortForwardPrometheus
./gradlew k8sPortForwardGrafana
```

Prometheus は `http://localhost:9090`、Grafana は `http://localhost:3000`
で利用できます。Grafana の開発専用のプレースホルダー認証情報は `admin` / `admin` です。
ローカルでの検証手順、サンプルのトラフィック送信コマンド、ルールやダッシュボードを変更した後の
手動リフレッシュの手順については [docs/observability.md](docs/observability.md) を参照してください。

ローカルのオブザーバビリティのオーバーレイは、このリポジトリをポートフォリオ作品として扱う
範囲に合わせ、標準出力・標準エラー出力のログと、小さな Prometheus/Grafana スタックまでを
意図的な対象範囲にしています。クラスター全体のログ収集ミドルウェアはインストールしません。
本番環境へのデプロイでは、対象のプラットフォームと運用要件に基づいて、ログの収集・ルーティング・
保持・アクセス制御のためのミドルウェアを選定する必要があります。

## 関連情報

### Swagger UI

サービスが起動したら、ブラウザで Swagger UI を開きます。

```
http://localhost:8080/swagger-ui.html
```

生の OpenAPI 仕様も以下で確認できます。

| 形式 | URL |
|---|---|
| JSON | `http://localhost:8080/v3/api-docs` |
| YAML | `http://localhost:8080/v3/api-docs.yaml` |

仕様の静的スナップショットは [docs/openapi.yaml](docs/openapi.yaml) にコミットされています。

### Observability

Actuator のヘルスエンドポイントはプローブ用に公開しており、Prometheus のメトリクスには、
設定済みのローカルユーザーのいずれかの HTTP Basic 認証情報が必要です。メトリクス名、構造化
ロギング、Prometheus と Grafana の成果物、アクセス制御の想定については
[docs/observability.md](docs/observability.md) を参照してください。

### 実装レビュー用にコードベースをパックする

[Repomix](https://repomix.com/ja/guide) を使って、ソース、テスト、API ドキュメント、
デプロイ用マニフェスト、選択した運用設定から、AI に渡しやすい単一の実装レビュー用パックを
生成します。

```bash
npx repomix@1.14.1 --config repomix.config.json
```

生成されたファイルは `build/repomix/feature-flag-expt-review.xml` に出力されます。
生成された Repomix の出力は Git から除外されます。Repomix はセキュリティチェックを実行しますが、
人による確認の代わりにはなりません。生成ファイルを外部の AI サービスに共有する前に、シークレット、
個人データ、内部 URL、認証情報、環境固有の設定が含まれていないことを確認してください。

パックの生成時にトークン数が大きい項目を確認するには、トークン数ツリーの閾値を指定して
同じコマンドを実行します。値 `1000` は「1000 トークン以上のファイル/ディレクトリを表示する」
という意味であり、トークン数の上限ではありません。

```bash
npx repomix@1.14.1 --config repomix.config.json --token-count-tree 1000
```

ローカルでの作業中の変更をレビュー対象に含める場合は、ワーキングツリーとステージ済みの差分を
明示的に含めます。

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
