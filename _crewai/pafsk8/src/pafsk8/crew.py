# crew.py
# Production-ready CrewAI configuration for the Skate Engine project

import os
from pathlib import Path
from typing import List

import yaml
from crewai import Agent, Crew, Process, Task
from crewai.agents.agent_builder.base_agent import BaseAgent
from crewai.project import CrewBase, agent, crew, task
from crewai_tools import (
    FileReadTool,
    FileWriterTool,
    SerperDevTool,
    ScrapeWebsiteTool,
    CodeInterpreterTool,
    DirectoryReadTool,
)
# ---------------- Load .env file ----------------
from dotenv import load_dotenv

load_dotenv(Path(__file__).parent.parent / ".env")

# ---------------- Validate required environment variables ----------------
# Only validate variables that are actually required
required_vars = ["MODEL", "FAST_MODEL", "SMART_MODEL"]
for var in required_vars:
    if not os.getenv(var):
        raise ValueError(f"Required environment variable {var} not set. Check your .env file.")

# ---------------- Paths ----------------
# Get absolute path to crew project root
CREW_ROOT = Path(__file__).resolve().parent.parent
CONFIG_FOLDER = CREW_ROOT / "pafsk8" / "config"
AGENTS_FILE = CONFIG_FOLDER / "agents.yaml"
TASKS_FILE = CONFIG_FOLDER / "tasks.yaml"

# Skate project root (3 levels up from crewai/pafsk8)
SKATE_PROJECT_ROOT = CREW_ROOT.parent.parent.parent

# Knowledge base path from .env
KNOWLEDGE_BASE = os.getenv("KNOWLEDGE_BASE_PATH", str(CREW_ROOT / "knowledge"))

# ---------------- File Tools ----------------
# Configure file tools to work in the skate project root
file_read_tool = FileReadTool()
file_write_tool = FileWriterTool()

# ---------------- Search & Research Tools ----------------
serper_dev_tool = SerperDevTool()
scrape_website_tool = ScrapeWebsiteTool()
code_interpreter_tool = CodeInterpreterTool()
directory_read_tool = DirectoryReadTool()


# ---------------- Load YAML Configs with env var interpolation ----------------
def load_yaml(path):
    """Load YAML file with environment variable interpolation."""
    with open(path, 'r', encoding='utf-8') as f:
        content = f.read()
    # Interpolate environment variables (${VAR} syntax)
    content = os.path.expandvars(content)
    return yaml.safe_load(content)


# ---------------- Load YAML Configs ----------------
agents_config = load_yaml(AGENTS_FILE)
tasks_config = load_yaml(TASKS_FILE)


# ---------------- Crew Definition ----------------
@CrewBase
class SkateEngineCrew:
    """Crew configuration for Skate Engine"""

    agents: List[BaseAgent]
    tasks: List[Task]

    # ---------------- Agents ----------------
    @agent
    def tech_lead(self) -> Agent:
        return Agent(
            config=agents_config['tech_lead'],
            verbose=True,
            tools=[file_read_tool, directory_read_tool, serper_dev_tool, scrape_website_tool]
        )

    @agent
    def software_engineer(self) -> Agent:
        return Agent(
            config=agents_config['software_engineer'],
            verbose=True,
            tools=[file_read_tool, file_write_tool, directory_read_tool, scrape_website_tool, code_interpreter_tool]
        )

    @agent
    def physics_engineer(self) -> Agent:
        return Agent(
            config=agents_config['physics_engineer'],
            verbose=True,
            tools=[file_read_tool, file_write_tool, directory_read_tool, serper_dev_tool, code_interpreter_tool]
        )

    @agent
    def reviewer(self) -> Agent:
        return Agent(
            config=agents_config['reviewer'],
            verbose=True,
            tools=[file_read_tool]
        )

    @agent
    def qa_engineer(self) -> Agent:
        return Agent(
            config=agents_config['qa_engineer'],
            verbose=True,
            tools=[file_read_tool, file_write_tool, code_interpreter_tool]
        )

    @agent
    def documentation_engineer(self) -> Agent:
        return Agent(
            config=agents_config['documentation_engineer'],
            verbose=True,
            tools=[file_read_tool, file_write_tool, scrape_website_tool]
        )

    @agent
    def ui_ux_designer(self) -> Agent:
        return Agent(
            config=agents_config['ui_ux_designer'],
            verbose=True,
            tools=[file_read_tool, scrape_website_tool]
        )

    @agent
    def project_manager(self) -> Agent:
        return Agent(
            config=agents_config['project_manager'],
            verbose=True,
            tools=[file_read_tool, serper_dev_tool]
        )

    # ---------------- Tasks ----------------
    @task
    def codebase_review(self) -> Task:
        task_config = tasks_config['codebase_review']
        return Task(
            description=task_config['description'],
            expected_output=task_config['expected_output'],
            agent=self.tech_lead()
        )

    @task
    def roadmap_definition(self) -> Task:
        task_config = tasks_config['roadmap_definition']
        return Task(
            description=task_config['description'],
            expected_output=task_config['expected_output'],
            agent=self.project_manager()
        )

    # Note: Development tasks are in dev_tasks.yaml for separate execution
    # Uncomment these methods when implementing the development workflow crew

    # @task
    # def feature_development(self) -> Task:
    #     return Task(config=tasks_config['feature_development'])
    #
    # @task
    # def refactoring(self) -> Task:
    #     return Task(config=tasks_config['refactoring'])
    #
    # @task
    # def code_review(self) -> Task:
    #     return Task(config=tasks_config['code_review'])
    #
    # @task
    # def testing(self) -> Task:
    #     return Task(config=tasks_config['testing'])
    #
    # @task
    # def documentation(self) -> Task:
    #     return Task(config=tasks_config['documentation'])
    #
    # @task
    # def ui_ux_design(self) -> Task:
    #     return Task(config=tasks_config['ui_ux_design'])
    #
    # @task
    # def integration_and_release(self) -> Task:
    #     return Task(config=tasks_config['integration_and_release'])

    # ---------------- Crew ----------------
    @crew
    def crew(self) -> Crew:
        """Creates the SkateEngine crew"""
        return Crew(
            agents=self.agents,  # created by @agent
            tasks=self.tasks,  # created by @task
            process=Process.sequential,
            verbose=True
        )

    # ---------------- Run Method ----------------
    def run(self, inputs=None):
        """
        Start the crew workflow:
        - Load agents and tasks
        - Execute the crew with kickoff
        """
        crew_instance = self.crew()

        if inputs is None:
            inputs = {}

        print("Initializing SkateEngine CrewAI...")
        result = crew_instance.kickoff(inputs=inputs)
        print("Crew workflow completed.")
        return result


# ---------------- Main Script ----------------
if __name__ == "__main__":
    skate_crew = SkateEngineCrew()
    skate_crew.run()
