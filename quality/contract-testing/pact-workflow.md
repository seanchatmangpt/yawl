# YAWL v6.0.0 Pact Contract Testing Workflow

## Architecture

```
Consumer (test class)              Pact Broker                  Provider (engine)
        |                              |                               |
        |-- generates pact JSON -----> |                               |
        |   target/pacts/*.json        |                               |
        |                              |<-- provider fetches pacts ----|
        |                              |    mvn -P contract-verify     |
        |                              |--- publishes results -------> |
        |                              |                               |
```

## Consumer Side: Generate Pact Files

Run the consumer Pact tests to generate JSON pact contracts:

```bash
mvn -P contract clean test \
    -Dtest="EngineApiConsumerContractTest,StatelessApiConsumerContractTest,IntegrationApiConsumerContractTest"
```

Pact files are written to: `target/pacts/`

## Publish Pacts to Broker

```bash
mvn -P contract pact:publish \
    -Dpact.broker.url=http://pact-broker:9292 \
    -Dpact.broker.token=${PACT_BROKER_TOKEN} \
    -Dpact.consumer.version=${project.version} \
    -Dpact.consumer.tags=main,v6.0.0
```

## Provider Verification

The provider runs verification against pacts fetched from the broker:

```bash
mvn -P contract-verify clean test \
    -Dtest=EngineApiProviderContractTest \
    -Dpact.broker.url=http://pact-broker:9292 \
    -Dpact.broker.token=${PACT_BROKER_TOKEN} \
    -Dpact.provider.version=${project.version} \
    -Dpact.provider.tags=main
```

## Can-I-Deploy Gate (CI/CD)

Before deploying a consumer or provider, verify compatibility:

```bash
pact-broker can-i-deploy \
    --pacticipant YawlEngineApi \
    --version ${DEPLOY_VERSION} \
    --to-environment production \
    --broker-base-url http://pact-broker:9292 \
    --broker-token ${PACT_BROKER_TOKEN}
```

This command fails the pipeline if any contract is broken.

## Contract Tags

| Tag         | Meaning                                           |
|-------------|---------------------------------------------------|
| main        | Latest commit on main branch                      |
| v6.0.0      | Release version                                   |
| feature-*   | Feature branch in development                     |
| prod        | Currently deployed to production                  |

## Contract Files in This Repository

Consumer pacts (checked in for offline use):
- `quality/contract-testing/pact/YawlControlPanel-YawlEngineApi.json`
- `quality/contract-testing/pact/YawlBatchProcessor-YawlStatelessApi.json`
- `quality/contract-testing/pact/YawlExternalAgent-YawlIntegrationApi.json`

Provider verification results are NOT checked in; they are published to the broker.
