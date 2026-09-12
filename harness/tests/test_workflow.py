#!/usr/bin/env python3
"""
test_workflow.py - Pruebas de validación sintáctica y estructural para workflows de GitHub Actions.
"""

import unittest
from pathlib import Path

try:
    import yaml
    HAS_YAML = True
except ImportError:
    HAS_YAML = False


class TestGitHubWorkflows(unittest.TestCase):
    def setUp(self):
        self.workflow_path = Path(".github/workflows/build-apk.yml")

    def test_workflow_file_exists(self):
        """Verifica que el archivo build-apk.yml exista en la ruta correcta."""
        self.assertTrue(
            self.workflow_path.exists(),
            f"El archivo {self.workflow_path} debe existir",
        )

    @unittest.skipUnless(HAS_YAML, "PyYAML no instalado")
    def test_workflow_yaml_syntax_and_structure(self):
        """Verifica la sintaxis YAML y la estructura del workflow de build y release."""
        content = self.workflow_path.read_text(encoding="utf-8")
        data = yaml.safe_load(content)

        self.assertIsInstance(data, dict, "El contenido del YAML debe ser un diccionario")
        self.assertIn("name", data)
        self.assertIn("on", data)
        self.assertIn("jobs", data)

        # Validar triggers: push a main y tags v*
        triggers = data["on"]
        self.assertIn("push", triggers)
        push_config = triggers["push"]
        self.assertIn("main", push_config.get("branches", []))
        self.assertIn("v*", push_config.get("tags", []))

        # Validar jobs
        jobs = data["jobs"]
        self.assertIn("build", jobs)
        build_job = jobs["build"]
        self.assertEqual(build_job.get("runs-on"), "ubuntu-latest")
        self.assertEqual(build_job.get("permissions", {}).get("contents"), "write")

        # Validar steps requeridos
        steps = build_job.get("steps", [])
        step_names = [s.get("name", "") for s in steps]

        # Verificar presencia de cada paso esencial
        self.assertTrue(any("checkout" in n.lower() for n in step_names), "Falta paso de checkout")
        self.assertTrue(any("jdk 17" in n.lower() or "java" in n.lower() for n in step_names), "Falta setup JDK 17")
        self.assertTrue(any("android sdk" in n.lower() for n in step_names), "Falta setup Android SDK")
        self.assertTrue(any("ndk" in n.lower() for n in step_names), "Falta setup NDK")
        self.assertTrue(any("gradlew" in n.lower() for n in step_names), "Falta permisos gradlew")
        self.assertTrue(any("assembledebug" in s.get("run", "").lower() for s in steps), "Falta ejecución assembleDebug")
        self.assertTrue(any("upload-artifact" in s.get("uses", "").lower() for s in steps), "Falta upload-artifact")
        self.assertTrue(any("action-gh-release" in s.get("uses", "").lower() for s in steps), "Falta action-gh-release")


if __name__ == "__main__":
    unittest.main()
