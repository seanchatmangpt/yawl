import dspy

class SentimentAnalyzer(dspy.Module):
    """DSPy program for sentiment analysis."""
    def __init__(self):
        self.classify = dspy.ChainOfThought("text -> sentiment")
    
    def forward(self, text):
        return self.classify(text=text)

class WorkletSelector(dspy.Module):
    """DSPy program for worklet selection."""
    def __init__(self):
        self.select = dspy.ChainOfThought("task, case_data, available -> worklet")
    
    def forward(self, task, case_data, available):
        result = self.select(task=task, case_data=case_data, available=available)
        return {
            "worklet_id": result.worklet,
            "confidence": result.confidence,
            "rationale": result.rationale
        }
