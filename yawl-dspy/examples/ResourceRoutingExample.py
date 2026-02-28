import dspy

class ResourceRouter(dspy.Module):
    """DSPy program for resource allocation."""
    def __init__(self):
        self.predict = dspy.ChainOfThought(
            "task_type, required_capabilities, agent_scores -> best_agent"
        )
    
    def forward(self, task_type, required_capabilities, agent_scores):
        result = self.predict(
            task_type=task_type,
            required_capabilities=required_capabilities,
            agent_scores=agent_scores
        )
        return {
            "best_agent_id": result.best_agent,
            "confidence": result.confidence,
            "reasoning": result.reasoning
        }

class AnomalyForensics(dspy.Module):
    """DSPy program for anomaly analysis."""
    def __init__(self):
        self.analyze = dspy.ChainOfThought(
            "metric_name, duration_ms, deviation_factor, samples, cases -> root_cause"
        )
    
    def forward(self, metric_name, duration_ms, deviation_factor, samples, cases):
        result = self.analyze(
            metric_name=metric_name,
            duration_ms=duration_ms,
            deviation_factor=deviation_factor,
            samples=samples,
            cases=cases
        )
        return {
            "root_cause": result.root_cause,
            "confidence": result.confidence,
            "evidence_chain": [str(result.evidence)],
            "recommendation": result.recommendation
        }
