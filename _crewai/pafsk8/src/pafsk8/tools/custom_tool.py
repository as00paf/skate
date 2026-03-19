import glob
from pathlib import Path
from typing import Type

from crewai.tools import BaseTool
from pydantic import BaseModel, Field


class FileSearchInput(BaseModel):
    """Input schema for FileSearchTool."""
    pattern: str = Field(..., description="Glob pattern to search for files (e.g., '**/*.kt', 'src/**/*.java')")
    base_path: str = Field(default=".", description="Base directory to search from (default: current directory)")


class FileSearchTool(BaseTool):
    name: str = "file_search"
    description: str = (
        "Searches for files matching a glob pattern in the project directory. "
        "Useful for finding Kotlin files, configuration files, or any files by name pattern. "
        "Returns a list of matching file paths."
    )
    args_schema: Type[BaseModel] = FileSearchInput

    def _run(self, pattern: str, base_path: str = ".") -> str:
        """
        Search for files matching the given glob pattern.

        Args:
            pattern: Glob pattern to match (e.g., '**/*.kt' for all Kotlin files)
            base_path: Base directory to search from

        Returns:
            Newline-separated list of matching file paths
        """
        # Navigate to crewai/pafsk8 root (3 levels up from tools/custom_tool.py)
        project_root = Path(__file__).resolve().parent.parent.parent

        # For this project, we want to search the main skate project
        # which is 3 levels up from crewai/pafsk8
        skate_project_root = project_root.parent.parent.parent

        search_path = skate_project_root / base_path if base_path != "." else skate_project_root

        # Perform glob search
        matches = glob.glob(str(search_path / pattern), recursive=True)

        if not matches:
            return f"No files found matching pattern '{pattern}' in {search_path}"

        # Return relative paths for readability
        relative_paths = [str(Path(m).relative_to(skate_project_root)) for m in matches]
        return "\n".join(relative_paths)
