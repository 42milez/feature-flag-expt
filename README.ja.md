# feature-flag-expt

[English](README.md) | 日本語

## 概要

feature-flag-expt は、フィーチャーフラグを管理・評価するための Spring Boot
サービスです。フラグの作成、取得、更新、評価、プレビュー、監査を行う REST
API を提供します。

フラグは環境ごとのターゲティング、緊急停止用の kill switch、テナント単位の
allowlist、割合に基づく決定的なロールアウトで制御できます。割合ロールアウトでは、
フラグキーとテナントまたはユーザーの識別子から安定したバケットを算出します。
更新内容は Spring Data JDBC を通じて PostgreSQL に永続化され、状態変更は
監査イベントとして記録されます。

本番用のフラグドメイン、永続化フロー、監査の振る舞い、中核となる evaluator
の大部分は Java で実装されています。プレビュー API は Kotlin で実装されており、
提案された変更、サンプルごとの差分、サマリー出力を、変更の保存や監査イベントの
書き込みを行わずに表現します。

## サービスの実行

### 前提条件

PostgreSQL インスタンスが起動しており、接続できる必要があります。既定値は以下の認証情報に対応しています。

| 変数 | 既定値 |
|---|---|
| `FEATURE_FLAGS_DB_URL` | `jdbc:postgresql://localhost:5432/featureflags` |
| `FEATURE_FLAGS_DB_USERNAME` | `featureflags` |
| `FEATURE_FLAGS_DB_PASSWORD` | `featureflags` |

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

必要に応じてデータベース接続を上書きします。

```bash
FEATURE_FLAGS_DB_URL=jdbc:postgresql://localhost:5432/featureflags \
FEATURE_FLAGS_DB_USERNAME=featureflags \
FEATURE_FLAGS_DB_PASSWORD=featureflags \
./gradlew :service:bootRun
```

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

フラグを部分更新する場合、コレクションフィールドを省略するか `null` として送信すると、現在の値が保持されます。空の `targetEnvironments` または `tenantAllowlist` 配列を送信すると、そのコレクションは意図的にクリアされます。

```json
{
  "targetEnvironments": null,
  "tenantAllowlist": null
}
```

```json
{
  "targetEnvironments": [],
  "tenantAllowlist": []
}
```

## プレビュー API

プレビューエンドポイントは、提案されたフィーチャーフラグの変更を保存せず、監査イベントも書き込まずに評価します。

```http
POST /api/flags/{flagKey}/preview
```

プレビューは Kotlin で実装されています。不変データクラスが、ネストしたリクエスト/レスポンスモデル、サンプルごとの差分、集計サマリーを簡潔に表現するのに適しているためです。永続化されるドメインモデル、リポジトリフロー、監査の振る舞い、中核となる `FeatureFlagEvaluator` は Java のままにしているため、本番用の評価経路は共有されたままです。

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
