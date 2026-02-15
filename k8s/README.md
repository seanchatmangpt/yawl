# Order Fulfillment Party Agents - Kubernetes

Deploy autonomous party agents for order fulfillment simulation.

## Prerequisites

- Kubernetes cluster
- YAWL engine running (or use engine URL in config)
- ZAI_API_KEY for agent reasoning

## Setup

1. Create secrets:

```bash
kubectl create secret generic yawl-credentials \
  --from-literal=username=admin \
  --from-literal=password=YAWL

kubectl create secret generic zai-secrets \
  --from-literal=api_key=YOUR_ZAI_API_KEY
```

2. Update `configmap.yaml` if engine URL differs.

3. Deploy agents:

```bash
kubectl apply -f configmap.yaml
kubectl apply -f ordering-agent-deployment.yaml
kubectl apply -f carrier-agent-deployment.yaml
kubectl apply -f freight-agent-deployment.yaml
kubectl apply -f payment-agent-deployment.yaml
kubectl apply -f delivered-agent-deployment.yaml
```

Or deploy all at once:

```bash
kubectl apply -f .
```

## Scaling

```bash
kubectl scale deployment ordering-agent --replicas=5
```
