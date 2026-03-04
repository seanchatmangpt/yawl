"""
DSPy Groq Configuration for YAWL Self-Play Loop.

Configures DSPy to use Groq's fast inference API with the gpt-oss-20b model.
This module provides a unified LLM interface for all YAWL DSPy operations.

Model naming convention:
- HTTP API: openai/gpt-oss-20b (OpenAI-compatible endpoint)
- DSPy: groq/gpt-oss-20b (DSPy's Groq integration)

Author: YAWL Foundation
Version: 6.0.0
"""

import os
import logging
from typing import Optional

logger = logging.getLogger(__name__)

# Try to import DSPy
try:
    import dspy
    DSPY_AVAILABLE = True
except ImportError:
    DSPY_AVAILABLE = False
    logger.warning("DSPy not available. Install dspy-ai to enable LLM features.")

# Groq configuration constants
GROQ_API_BASE = "https://api.groq.com/openai/v1"
GROQ_MODEL_DSPY = "groq/gpt-oss-20b"  # DSPy format: provider/model
GROQ_MODEL_HTTP = "openai/gpt-oss-20b"  # HTTP API format
GROQ_DEFAULT_MAX_TOKENS = 4096
GROQ_DEFAULT_TEMPERATURE = 0.7


class GroqDspyLM(dspy.LM):
    """
    Custom DSPy Language Model for Groq API.

    Uses the gpt-oss-20b model for fast inference.
    Implements the DSPy LM interface for seamless integration.
    """

    def __init__(
        self,
        api_key: Optional[str] = None,
        model: str = GROQ_MODEL_DSPY,
        temperature: float = GROQ_DEFAULT_TEMPERATURE,
        max_tokens: int = GROQ_DEFAULT_MAX_TOKENS,
        **kwargs
    ):
        """
        Initialize Groq DSPy LM.

        Args:
            api_key: Groq API key (defaults to GROQ_API_KEY env var)
            model: Model identifier in DSPy format (groq/model-name)
            temperature: Sampling temperature (0.0 to 2.0)
            max_tokens: Maximum tokens in response
            **kwargs: Additional arguments passed to dspy.LM
        """
        if not DSPY_AVAILABLE:
            raise ImportError("DSPy is required. Install with: pip install dspy-ai")

        self.api_key = api_key or os.getenv("GROQ_API_KEY")
        if not self.api_key:
            raise ValueError(
                "Groq API key required. Set GROQ_API_KEY environment variable "
                "or pass api_key parameter."
            )

        # Extract model name for Groq API (remove groq/ prefix if present)
        self.model_name = model.replace("groq/", "") if model.startswith("groq/") else model
        self.temperature = temperature
        self.max_tokens = max_tokens

        # Initialize parent dspy.LM with Groq configuration
        super().__init__(
            model=model,
            api_base=GROQ_API_BASE,
            api_key=self.api_key,
            temperature=temperature,
            max_tokens=max_tokens,
            **kwargs
        )

        logger.info(f"Initialized GroqDspyLM with model={model}, temp={temperature}")

    def __call__(self, prompt: str, **kwargs) -> str:
        """Execute a completion request."""
        return super().__call__(prompt, **kwargs)


def configure_dspy_for_groq(
    api_key: Optional[str] = None,
    model: str = GROQ_MODEL_DSPY,
    temperature: float = GROQ_DEFAULT_TEMPERATURE,
    max_tokens: int = GROQ_DEFAULT_MAX_TOKENS
) -> bool:
    """
    Configure DSPy to use Groq as the default LM.

    This should be called at application startup to route all DSPy
    calls through Groq's fast inference API.

    Args:
        api_key: Groq API key (defaults to GROQ_API_KEY env var)
        model: Model in DSPy format (groq/model-name)
        temperature: Sampling temperature
        max_tokens: Maximum response tokens

    Returns:
        True if configuration succeeded, False otherwise

    Example:
        >>> from yawl_dspy.groq_config import configure_dspy_for_groq
        >>> configure_dspy_for_groq()
        True
        >>> # Now all DSPy calls use Groq
        >>> import dspy
        >>> predictor = dspy.Predict("question -> answer")
        >>> result = predictor(question="What is YAWL?")
    """
    if not DSPY_AVAILABLE:
        logger.error("Cannot configure DSPy: dspy-ai not installed")
        return False

    api_key = api_key or os.getenv("GROQ_API_KEY")
    if not api_key:
        logger.error("Cannot configure DSPy: GROQ_API_KEY not set")
        return False

    try:
        # Configure DSPy with Groq LM
        lm = GroqDspyLM(
            api_key=api_key,
            model=model,
            temperature=temperature,
            max_tokens=max_tokens
        )
        dspy.configure(lm=lm)
        logger.info(f"DSPy configured with Groq: model={model}")
        return True
    except Exception as e:
        logger.error(f"Failed to configure DSPy for Groq: {e}")
        return False


def get_groq_lm(
    model: str = GROQ_MODEL_DSPY,
    temperature: float = GROQ_DEFAULT_TEMPERATURE,
    max_tokens: int = GROQ_DEFAULT_MAX_TOKENS
) -> Optional[dspy.LM]:
    """
    Get a configured Groq LM instance without setting it as global default.

    Useful for multi-model scenarios where different components use different LMs.

    Args:
        model: Model in DSPy format (groq/model-name)
        temperature: Sampling temperature
        max_tokens: Maximum response tokens

    Returns:
        Configured GroqDspyLM instance, or None if configuration fails
    """
    if not DSPY_AVAILABLE:
        return None

    api_key = os.getenv("GROQ_API_KEY")
    if not api_key:
        return None

    try:
        return GroqDspyLM(
            api_key=api_key,
            model=model,
            temperature=temperature,
            max_tokens=max_tokens
        )
    except Exception as e:
        logger.error(f"Failed to create Groq LM: {e}")
        return None


def is_groq_configured() -> bool:
    """Check if DSPy is configured with a Groq LM."""
    if not DSPY_AVAILABLE:
        return False

    try:
        # Check if dspy.settings has an LM configured
        lm = getattr(dspy.settings, 'lm', None)
        if lm is None:
            return False

        # Check if it's a Groq LM or has Groq model
        model = getattr(lm, 'model', '')
        return 'groq' in model.lower() or 'gpt-oss' in model.lower()
    except Exception:
        return False


# Self-play specific DSPy signatures
class V7GapProposalSignature(dspy.Signature):
    """Generate a technical proposal for a YAWL v7 design gap."""

    gap_name: str = dspy.InputField(desc="Name of the V7 capability gap")
    gap_description: str = dspy.InputField(desc="Detailed description of the gap")
    design_state: str = dspy.InputField(desc="Current cumulative design state")

    proposal: str = dspy.OutputField(desc="Technical proposal to address the gap")
    backward_compat_score: float = dspy.OutputField(desc="Backward compatibility score 0.0-1.0")
    performance_gain: float = dspy.OutputField(desc="Estimated performance improvement 0.0-1.0")
    reasoning: str = dspy.OutputField(desc="Technical justification for the proposal")


class V7ChallengeSignature(dspy.Signature):
    """Adversarially review a V7 design proposal."""

    gap_name: str = dspy.InputField(desc="Name of the V7 capability gap")
    proposal: str = dspy.InputField(desc="The proposal to review")
    compat_score: float = dspy.InputField(desc="Proposed backward compatibility score")

    verdict: str = dspy.OutputField(desc="ACCEPTED, MODIFIED, or REJECTED")
    confidence: float = dspy.OutputField(desc="Confidence in verdict 0.0-1.0")
    reasoning: str = dspy.OutputField(desc="Justification for the verdict")


class V7GapProposalModule(dspy.Module):
    """DSPy module for generating V7 gap proposals."""

    def __init__(self):
        super().__init__()
        self.propose = dspy.ChainOfThought(V7GapProposalSignature)

    def forward(self, gap_name: str, gap_description: str, design_state: str):
        return self.propose(
            gap_name=gap_name,
            gap_description=gap_description,
            design_state=design_state
        )


class V7ChallengeModule(dspy.Module):
    """DSPy module for challenging V7 proposals."""

    def __init__(self):
        super().__init__()
        self.challenge = dspy.ChainOfThought(V7ChallengeSignature)

    def forward(self, gap_name: str, proposal: str, compat_score: float):
        return self.challenge(
            gap_name=gap_name,
            proposal=proposal,
            compat_score=compat_score
        )


# Convenience function for self-play loop
def create_self_play_modules() -> tuple:
    """
    Create DSPy modules for self-play loop.

    Returns:
        Tuple of (proposal_module, challenge_module)

    Raises:
        ImportError: If DSPy is not available
        ValueError: If Groq API key is not configured
    """
    if not DSPY_AVAILABLE:
        raise ImportError("DSPy is required for self-play modules")

    if not os.getenv("GROQ_API_KEY"):
        raise ValueError("GROQ_API_KEY environment variable must be set")

    # Ensure DSPy is configured for Groq
    if not is_groq_configured():
        configure_dspy_for_groq()

    return V7GapProposalModule(), V7ChallengeModule()
