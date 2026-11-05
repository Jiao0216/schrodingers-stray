import os
import sys
from pathlib import Path

# Import the agent modules from cat-agent/ with a dummy key — no test makes a real OpenAI call.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
os.environ.setdefault("OPENAI_API_KEY", "sk-test-not-real")
os.environ.setdefault("AGENT_LLM_ROUTING", "true")
