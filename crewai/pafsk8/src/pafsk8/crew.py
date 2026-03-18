# crew.py
# Production-ready CrewAI configuration for the Skate Engine project

import os
from typing import List
from crewai import Agent, Crew, Process, Task
from crewai.project import CrewBase, agent, crew, task
from crewai.agents.agent_builder.base_agent import BaseAgent
import yaml

# ---------------- Paths from .env ----------------
PROJECT_ROOT = os.getenv("PROJECT_ROOT", "../../../../")  # relative to crew folder
KNOWLEDGE_BASE = os.path.join(PROJECT_ROOT, os.getenv("KNOWLEDGE_BASE", "docs/crew_knowledge_base"))

CONFIG_FOLDER = os.path.join(PROJECT_ROOT, "crewai/pafsk8/src/pafsk8/config")
AGENTS_FILE = os.path.join(CONFIG_FOLDER, "agents.yaml")
TASKS_FILE = os.path.join(CONFIG_FOLDER, "tasks.yaml")
SUBTASKS_FILE = os.path.join(CONFIG_FOLDER, "subtasks.yaml")

# ---------------- Load YAML Configs ----------------
def load_yaml(path):
    with open(path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)

agents_config = load_yaml(AGENTS_FILE)
tasks_config = load_yaml(TASKS_FILE)
subtasks_config = load_yaml(SUBTASKS_FILE)

# ---------------- Crew Definition ----------------
@CrewBase
class SkateEngineCrew:
    """Crew configuration for Skate Engine"""

    agents: List[BaseAgent]
    tasks: List[Task]

    # ---------------- Agents ----------------
    @agent
    def tech_lead(self) -> Agent:
        return Agent(config=agents_config['tech_lead'], verbose=True)

    @agent
    def software_engineer(self) -> Agent:
        return Agent(config=agents_config['software_engineer'], verbose=True)

    @agent
    def physics_engineer(self) -> Agent:
        return Agent(config=agents_config['physics_engineer'], verbose=True)

    @agent
    def reviewer(self) -> Agent:
        return Agent(config=agents_config['reviewer'], verbose=True)

    @agent
    def qa_engineer(self) -> Agent:
        return Agent(config=agents_config['qa_engineer'], verbose=True)

    @agent
    def documentation_engineer(self) -> Agent:
        return Agent(config=agents_config['documentation_engineer'], verbose=True)

    @agent
    def ui_ux_designer(self) -> Agent:
        return Agent(config=agents_config['ui_ux_designer'], verbose=True)

    @agent
    def project_manager(self) -> Agent:
        return Agent(config=agents_config['project_manager'], verbose=True)

    # ---------------- Tasks ----------------
    @task
    def codebase_review(self) -> Task:
        return Task(config=tasks_config['codebase_review'], subtasks=subtasks_config['codebase_review'])

    @task
    def roadmap_definition(self) -> Task:
        return Task(config=tasks_config['roadmap_definition'], subtasks=subtasks_config['roadmap_definition'])

    @task
    def feature_development(self) -> Task:
        return Task(config=tasks_config['feature_development'], subtasks=subtasks_config['feature_development'])

    @task
    def refactoring(self) -> Task:
        return Task(config=tasks_config['refactoring'], subtasks=subtasks_config['refactoring'])

    @task
    def code_review(self) -> Task:
        return Task(config=tasks_config['code_review'], subtasks=subtasks_config['code_review'])

    @task
    def testing(self) -> Task:
        return Task(config=tasks_config['testing'], subtasks=subtasks_config['testing'])

    @task
    def documentation(self) -> Task:
        return Task(config=tasks_config['documentation'], subtasks=subtasks_config['documentation'])

    @task
    def ui_ux_design(self) -> Task:
        return Task(config=tasks_config['ui_ux_design'], subtasks=subtasks_config['ui_ux_design'])

    @task
    def integration_and_release(self) -> Task:
        return Task(config=tasks_config['integration_and_release'], subtasks=subtasks_config['integration_and_release'])

    # ---------------- Crew ----------------
    @crew
    def crew(self) -> Crew:
        """Creates the SkateEngine crew"""
        return Crew(
            agents=self.agents,  # created by @agent
            tasks=self.tasks,    # created by @task
            process=Process.sequential,
            verbose=True
        )

    # ---------------- Run Method ----------------
    def run(self):
        """
        Start the crew workflow:
        - Load agents and tasks
        - Start the crew
        - Apply policies for knowledge sharing, testing, documentation, and code review
        """
        crew_instance = self.crew()

        # Apply crew-wide configuration
        crew_instance.config.update({
            "autonomy_level": "semi-autonomous",
            "collaboration": True,
            "respect_experts": True,
            "error_reporting": True,
            "parallel_execution": True,
            "max_task_duration_hours": int(os.getenv("MAX_TASK_DURATION_HOURS", 4)),
            "po_contact_required": [
                "major architectural decisions",
                "critical refactoring",
                "dependency changes",
                "feature approvals",
                "final releases"
            ],
            "knowledge_sharing": {
                "enabled": True,
                "shared_location": KNOWLEDGE_BASE
            },
            "testing_policy": {
                "focus_on_critical": True,
                "physics_and_complex_logic": True,
                "avoid_trivial_tests": True
            },
            "documentation_policy": {
                "only_stable_features": True,
                "include_ui_ux_guidelines": True,
                "divide_by_type": ["project", "engine", "workflow"]
            },
            "code_review_policy": {
                "enforce_solid_clean_architecture": True,
                "escalate_disagreements_to_tech_lead": True,
                "further_escalation_to_po": True
            }
        })

        print("Initializing SkateEngine CrewAI...")
        crew_instance.start_workflow()
        print("Crew workflow started. Monitoring tasks and approvals...")

# ---------------- Main Script ----------------
if __name__ == "__main__":
    skate_crew = SkateEngineCrew()
    skate_crew.run()