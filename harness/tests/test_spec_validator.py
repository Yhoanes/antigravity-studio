#!/usr/bin/env python3
"""
test_spec_validator.py - Pruebas unitarias para el validador de especificaciones SDD.
"""

import sys
import tempfile
import unittest
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

try:
    from harness.spec_validator import SpecValidator
except ImportError:
    from spec_validator import SpecValidator


class TestSpecValidator(unittest.TestCase):
    def setUp(self):
        self.validator = SpecValidator()

    def test_spec_00_architecture_is_valid(self):
        """Verifica que specs/00-system-architecture.md pase todas las validaciones."""
        spec_path = Path("specs/00-system-architecture.md")
        self.assertTrue(spec_path.exists(), "specs/00-system-architecture.md debe existir")

        report = self.validator.validate_file(spec_path)
        self.assertTrue(
            report.is_valid,
            f"specs/00-system-architecture.md debe ser válida. Errores: {report.errors}",
        )
        self.assertEqual(report.metadata.get("id"), "SPEC-000")
        self.assertEqual(report.metadata.get("status"), "APPROVED FOR IMPLEMENTATION")
        self.assertIn("Xiaomi Pad 6", report.metadata.get("target_device", ""))
        self.assertGreaterEqual(len(report.acceptance_criteria), 12)
        self.assertGreaterEqual(report.diagram_count, 1)

    def test_valid_dummy_spec(self):
        """Verifica que una especificación dummy completa sea válida."""
        spec_content = """# SPEC-100: Especificación de Prueba

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-100` |
| **Título** | Test Spec |
| **Autor** | `qa_harness` |
| **Estado** | `APPROVED FOR IMPLEMENTATION` |
| **Dispositivo Objetivo** | Xiaomi Pad 6 |
| **Runtime Target** | Kotlin / NDK |

## 1. Diagrama de Arquitectura
```mermaid
graph TD
    A --> B
```

## 2. Criterios de Aceptación Verificables
| ID | Módulo | Criterio | Método |
| :--- | :--- | :--- | :--- |
| **`AC-TEST-001`** | Core | Debe procesar paquetes | Test unitario |
"""
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
            f.write(spec_content)
            temp_path = Path(f.name)

        try:
            report = self.validator.validate_file(temp_path)
            self.assertTrue(report.is_valid, f"Spec dummy debería ser válida. Errores: {report.errors}")
            self.assertEqual(len(report.acceptance_criteria), 1)
            self.assertEqual(report.acceptance_criteria[0].id, "AC-TEST-001")
            self.assertEqual(report.diagram_count, 1)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_missing_target_device(self):
        """Verifica que falle si no incluye Xiaomi Pad 6 como dispositivo objetivo."""
        spec_content = """# SPEC-101: Test Falta Dispositivo

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-101` |
| **Título** | Test Spec |
| **Autor** | `qa_harness` |
| **Estado** | `DRAFT` |
| **Dispositivo Objetivo** | Generic Android Device |
| **Runtime Target** | Kotlin |

## 1. Diagramas
```mermaid
graph TD
    A --> B
```

## 2. Criterios de Aceptación Verificables
| ID | Módulo | Criterio | Método |
| :--- | :--- | :--- | :--- |
| **`AC-TEST-002`** | Mod | Desc | Met |
"""
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
            f.write(spec_content)
            temp_path = Path(f.name)

        try:
            report = self.validator.validate_file(temp_path)
            self.assertFalse(report.is_valid)
            error_categories = [e.category for e in report.errors]
            self.assertIn("INVALID_TARGET_DEVICE", error_categories)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_invalid_status(self):
        """Verifica que falle si el estado no es APPROVED FOR IMPLEMENTATION o DRAFT."""
        spec_content = """# SPEC-102: Test Estado Inválido

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-102` |
| **Título** | Test Spec |
| **Autor** | `qa_harness` |
| **Estado** | `IN_REVIEW` |
| **Dispositivo Objetivo** | Xiaomi Pad 6 |
| **Runtime Target** | Kotlin |

## 1. Diagramas
```mermaid
graph TD
    A --> B
```

## 2. Criterios de Aceptación Verificables
| ID | Módulo | Criterio | Método |
| :--- | :--- | :--- | :--- |
| **`AC-TEST-003`** | Mod | Desc | Met |
"""
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
            f.write(spec_content)
            temp_path = Path(f.name)

        try:
            report = self.validator.validate_file(temp_path)
            self.assertFalse(report.is_valid)
            error_categories = [e.category for e in report.errors]
            self.assertIn("INVALID_METADATA_STATUS", error_categories)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_missing_acceptance_criteria_section(self):
        """Verifica que falle si falta la sección de Criterios de Aceptación."""
        spec_content = """# SPEC-103: Test Sin AC

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-103` |
| **Título** | Test Spec |
| **Autor** | `qa_harness` |
| **Estado** | `DRAFT` |
| **Dispositivo Objetivo** | Xiaomi Pad 6 |
| **Runtime Target** | Kotlin |

## 1. Diagramas
```mermaid
graph TD
    A --> B
```
"""
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
            f.write(spec_content)
            temp_path = Path(f.name)

        try:
            report = self.validator.validate_file(temp_path)
            self.assertFalse(report.is_valid)
            error_categories = [e.category for e in report.errors]
            self.assertIn("MISSING_AC_SECTION", error_categories)
        finally:
            temp_path.unlink(missing_ok=True)

    def test_duplicate_ac_id(self):
        """Verifica que detecte IDs de Criterios de Aceptación duplicados."""
        spec_content = """# SPEC-104: Test Duplicados

| Metadato | Valor |
| :--- | :--- |
| **Identificador** | `SPEC-104` |
| **Título** | Test Spec |
| **Autor** | `qa_harness` |
| **Estado** | `DRAFT` |
| **Dispositivo Objetivo** | Xiaomi Pad 6 |
| **Runtime Target** | Kotlin |

## 1. Diagramas
```mermaid
graph TD
    A --> B
```

## 2. Criterios de Aceptación Verificables
| ID | Módulo | Criterio | Método |
| :--- | :--- | :--- | :--- |
| **`AC-TEST-004`** | Mod1 | Criterio uno | Método |
| **`AC-TEST-004`** | Mod2 | Criterio duplicado | Método |
"""
        with tempfile.NamedTemporaryFile("w", suffix=".md", delete=False, encoding="utf-8") as f:
            f.write(spec_content)
            temp_path = Path(f.name)

        try:
            report = self.validator.validate_file(temp_path)
            self.assertFalse(report.is_valid)
            error_categories = [e.category for e in report.errors]
            self.assertIn("DUPLICATE_AC_ID", error_categories)
        finally:
            temp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    unittest.main()
