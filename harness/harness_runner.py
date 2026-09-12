#!/usr/bin/env python3
"""
harness_runner.py - Ejecutor Central del Arnés de Evaluación de Antigravity Mobile.

Supervisa los bucles de evaluación cerrada (Closed-Loop Evaluation):
1. Validación SDD de especificaciones (spec_validator)
2. Análisis estático y linters de código (code_linter)
3. Ejecución de suites de prueba automatizadas (test_suite)

Genera reportes estructurados en JSON y consola (PASS/FAIL) con diagnósticos
y sugerencias de corrección para que los subagentes puedan iterar de forma autónoma.
"""

from __future__ import annotations

import argparse
import ast
import io
import json
import os
import sys
import time
import unittest
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Dict, List, Optional

# Asegurar codificación UTF-8 en Windows
if hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass
if hasattr(sys.stderr, "reconfigure"):
    try:
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    except Exception:
        pass

# Asegurar que el directorio raíz del proyecto esté en sys.path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

# Importar validador de especificaciones del arnés
try:
    from harness.spec_validator import SpecReport, SpecValidator
except ImportError:
    from spec_validator import SpecReport, SpecValidator


@dataclass
class DiagnosticItem:
    suite: str
    target: str
    category: str
    message: str
    line_number: Optional[int] = None
    suggestion: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)


@dataclass
class SuiteResult:
    name: str
    display_name: str
    status: str  # "PASS", "FAIL", "SKIPPED"
    duration_ms: float
    summary: str
    details: Dict[str, Any] = field(default_factory=dict)
    errors: List[str] = field(default_factory=list)
    warnings: List[str] = field(default_factory=list)
    diagnostics: List[DiagnosticItem] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "name": self.name,
            "display_name": self.display_name,
            "status": self.status,
            "duration_ms": round(self.duration_ms, 2),
            "summary": self.summary,
            "details": self.details,
            "errors": self.errors,
            "warnings": self.warnings,
            "diagnostics": [d.to_dict() for d in self.diagnostics],
        }


@dataclass
class HarnessReport:
    timestamp: str
    target_spec: Optional[str]
    overall_status: str  # "PASS" o "FAIL"
    summary: Dict[str, Any]
    suites: List[SuiteResult]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "timestamp": self.timestamp,
            "target_spec": self.target_spec,
            "overall_status": self.overall_status,
            "summary": self.summary,
            "suites": [s.to_dict() for s in self.suites],
        }


class SpecValidationSuite:
    """Suite de validación de especificaciones bajo metodología SDD."""

    name = "spec_validator"
    display_name = "SDD Spec Compliance Suite"

    def run(self, target_path: Optional[Path] = None) -> SuiteResult:
        start_time = time.perf_counter()
        target = target_path or Path("specs")
        validator = SpecValidator(specs_dir=target if target.is_dir() else target.parent)

        reports: List[SpecReport] = validator.validate_all(target_path=target)
        duration_ms = (time.perf_counter() - start_time) * 1000

        all_valid = len(reports) > 0 and all(r.is_valid for r in reports)
        total_acs = sum(len(r.acceptance_criteria) for r in reports)
        total_specs = len(reports)
        passed_specs = sum(1 for r in reports if r.is_valid)

        errors: List[str] = []
        warnings: List[str] = []
        diagnostics: List[DiagnosticItem] = []

        for r in reports:
            for w in r.warnings:
                warnings.append(f"{r.file_path}: {w.message}")
            for err in r.errors:
                err_msg = f"{r.file_path}: [{err.category}] {err.message}"
                if err.line_number:
                    err_msg += f" (Línea {err.line_number})"
                errors.append(err_msg)
                diagnostics.append(
                    DiagnosticItem(
                        suite=self.name,
                        target=r.file_path,
                        category=err.category,
                        message=err.message,
                        line_number=err.line_number,
                        suggestion=err.suggestion,
                    )
                )

        status = "PASS" if all_valid else "FAIL"
        summary = (
            f"{passed_specs}/{total_specs} especificaciones SDD válidas | "
            f"{total_acs} Criterios de Aceptación (AC) verificados"
        )

        details = {
            "total_specs": total_specs,
            "passed_specs": passed_specs,
            "failed_specs": total_specs - passed_specs,
            "total_acceptance_criteria": total_acs,
            "reports": [r.to_dict() for r in reports],
        }

        return SuiteResult(
            name=self.name,
            display_name=self.display_name,
            status=status,
            duration_ms=duration_ms,
            summary=summary,
            details=details,
            errors=errors,
            warnings=warnings,
            diagnostics=diagnostics,
        )


class CodeLinterSuite:
    """Suite de análisis estático y sintaxis para componentes de código."""

    name = "code_linter"
    display_name = "Static Analysis & Syntax Linter Suite"

    def run(self, project_root: Path = Path(".")) -> SuiteResult:
        start_time = time.perf_counter()
        errors: List[str] = []
        warnings: List[str] = []
        diagnostics: List[DiagnosticItem] = []

        scanned_files = 0
        python_files = list(project_root.glob("harness/**/*.py"))

        for py_file in python_files:
            scanned_files += 1
            try:
                content = py_file.read_text(encoding="utf-8")
                ast.parse(content, filename=str(py_file))
                compile(content, str(py_file), "exec")
            except SyntaxError as e:
                err_msg = f"{py_file.as_posix()}:{e.lineno}:{e.offset}: {e.msg}"
                errors.append(err_msg)
                diagnostics.append(
                    DiagnosticItem(
                        suite=self.name,
                        target=str(py_file.as_posix()),
                        category="SYNTAX_ERROR",
                        message=f"Error sintáctico: {e.msg}",
                        line_number=e.lineno,
                        suggestion="Corrija el error de sintaxis en el archivo especificado.",
                    )
                )
            except Exception as e:
                err_msg = f"{py_file.as_posix()}: {e}"
                errors.append(err_msg)
                diagnostics.append(
                    DiagnosticItem(
                        suite=self.name,
                        target=str(py_file.as_posix()),
                        category="COMPILE_ERROR",
                        message=str(e),
                        suggestion="Verifique la codificación y estructura del archivo.",
                    )
                )

        duration_ms = (time.perf_counter() - start_time) * 1000
        status = "PASS" if not errors else "FAIL"
        summary = (
            f"Análisis sintáctico completado sin errores en {scanned_files} archivos"
            if not errors
            else f"Fallo de análisis sintáctico en {len(errors)} archivos"
        )

        return SuiteResult(
            name=self.name,
            display_name=self.display_name,
            status=status,
            duration_ms=duration_ms,
            summary=summary,
            details={"scanned_files": scanned_files},
            errors=errors,
            warnings=warnings,
            diagnostics=diagnostics,
        )


class AutomatedTestSuite:
    """Suite de pruebas unitarias e integración del arnés."""

    name = "test_suite"
    display_name = "Automated Unit & Regression Test Suite"

    def run(self, test_dir: Path = Path("harness/tests")) -> SuiteResult:
        start_time = time.perf_counter()
        errors: List[str] = []
        warnings: List[str] = []
        diagnostics: List[DiagnosticItem] = []

        if not test_dir.exists():
            duration_ms = (time.perf_counter() - start_time) * 1000
            return SuiteResult(
                name=self.name,
                display_name=self.display_name,
                status="SKIPPED",
                duration_ms=duration_ms,
                summary="Directorio de tests no encontrado (omitido)",
                details={"tests_run": 0},
            )

        loader = unittest.TestLoader()
        suite = loader.discover(
            start_dir=str(test_dir), pattern="test_*.py", top_level_dir="."
        )

        stream = io.StringIO()
        runner = unittest.TextTestRunner(stream=stream, verbosity=2)
        result = runner.run(suite)

        duration_ms = (time.perf_counter() - start_time) * 1000
        tests_run = result.testsRun

        if tests_run == 0:
            return SuiteResult(
                name=self.name,
                display_name=self.display_name,
                status="SKIPPED",
                duration_ms=duration_ms,
                summary="No se descubrieron pruebas unitarias",
                details={"tests_run": 0},
            )

        for failure in result.failures:
            test_case, tb = failure
            errors.append(f"FAIL: {test_case} - {tb.splitlines()[-1]}")
            diagnostics.append(
                DiagnosticItem(
                    suite=self.name,
                    target=str(test_case),
                    category="TEST_FAILURE",
                    message=tb.splitlines()[-1],
                    suggestion="Revise la aserción del test y corrija la lógica para cumplir el criterio.",
                )
            )

        for err in result.errors:
            test_case, tb = err
            errors.append(f"ERROR: {test_case} - {tb.splitlines()[-1]}")
            diagnostics.append(
                DiagnosticItem(
                    suite=self.name,
                    target=str(test_case),
                    category="TEST_ERROR",
                    message=tb.splitlines()[-1],
                    suggestion="Revise la traza de excepción para corregir el error en tiempo de ejecución.",
                )
            )

        is_success = result.wasSuccessful()
        status = "PASS" if is_success else "FAIL"
        summary = (
            f"{tests_run} pruebas ejecutadas con éxito (0 fallos, 0 errores)"
            if is_success
            else f"{len(result.failures)} fallos y {len(result.errors)} errores en {tests_run} pruebas"
        )

        return SuiteResult(
            name=self.name,
            display_name=self.display_name,
            status=status,
            duration_ms=duration_ms,
            summary=summary,
            details={
                "tests_run": tests_run,
                "failures": len(result.failures),
                "errors": len(result.errors),
            },
            errors=errors,
            warnings=warnings,
            diagnostics=diagnostics,
        )


class HarnessRunner:
    """Orquestador principal que ejecuta todas las suites y gestiona el bucle de evaluación."""

    def __init__(self, target_spec: Optional[Path] = None):
        self.target_spec = target_spec

    def execute(self, suites_to_run: Optional[List[str]] = None) -> HarnessReport:
        suites_map = {
            "spec_validator": SpecValidationSuite(),
            "code_linter": CodeLinterSuite(),
            "test_suite": AutomatedTestSuite(),
        }

        active_suites = (
            [suites_map[s] for s in suites_to_run if s in suites_map]
            if suites_to_run
            else list(suites_map.values())
        )

        suite_results: List[SuiteResult] = []
        overall_start = time.perf_counter()

        for suite in active_suites:
            if isinstance(suite, SpecValidationSuite):
                res = suite.run(target_path=self.target_spec)
            else:
                res = suite.run()
            suite_results.append(res)

        total_duration_ms = (time.perf_counter() - overall_start) * 1000

        total_suites = len(suite_results)
        passed_suites = sum(1 for s in suite_results if s.status == "PASS")
        failed_suites = sum(1 for s in suite_results if s.status == "FAIL")
        skipped_suites = sum(1 for s in suite_results if s.status == "SKIPPED")

        overall_status = "PASS" if failed_suites == 0 and passed_suites > 0 else "FAIL"

        timestamp_iso = datetime.now(timezone.utc).isoformat()

        summary = {
            "total_suites": total_suites,
            "passed_suites": passed_suites,
            "failed_suites": failed_suites,
            "skipped_suites": skipped_suites,
            "total_duration_ms": round(total_duration_ms, 2),
        }

        return HarnessReport(
            timestamp=timestamp_iso,
            target_spec=str(self.target_spec.as_posix()) if self.target_spec else None,
            overall_status=overall_status,
            summary=summary,
            suites=suite_results,
        )


def print_console_report(report: HarnessReport, verbose: bool = False) -> None:
    """Renderiza el reporte en la consola de manera clara y profesional."""
    print("\n" + "=" * 78)
    print("      ANTIGRAVITY MOBILE - ARNÉS DE EVALUACIÓN Y VERIFICACIÓN QA")
    print("=" * 78)
    print(f"Timestamp: {report.timestamp}")
    if report.target_spec:
        print(f"Objetivo de Evaluación: {report.target_spec}")
    print("-" * 78)

    for suite in report.suites:
        status_badge = f"[{suite.status}]"
        print(f"{status_badge:<8} {suite.display_name:<44} ({suite.duration_ms:.1f} ms)")
        print(f"         └─ {suite.summary}")

        if suite.warnings:
            for w in suite.warnings:
                print(f"         [WARN] {w}")

        if suite.errors:
            for err in suite.errors:
                print(f"         * {err}")

    # Sección de Diagnóstico de Bucle de Corrección (si hay fallos)
    all_diagnostics = [d for s in report.suites for d in s.diagnostics]
    if all_diagnostics:
        print("\n" + "#" * 78)
        print("  BUCLE DE EVALUACIÓN CERRADO: DIAGNÓSTICO PARA AUTO-CORRECCIÓN")
        print("#" * 78)
        for d in all_diagnostics:
            line_str = f" (Línea {d.line_number})" if d.line_number else ""
            print(f"\n[SUITE: {d.suite}] Destino: {d.target}{line_str}")
            print(f"  Fallo: [{d.category}] {d.message}")
            if d.suggestion:
                print(f"  -> ACCIÓN CORRECTIVA: {d.suggestion}")
        print("\n" + "#" * 78)

    print("\n" + "=" * 78)
    summary = report.summary
    print(
        f"RESUMEN GLOBAL: {summary['passed_suites']}/{summary['total_suites']} suites pasadas | "
        f"{summary['failed_suites']} fallos | {summary['skipped_suites']} omitidas | "
        f"Tiempo total: {summary['total_duration_ms']:.1f} ms"
    )
    if report.overall_status == "PASS":
        print("ESTADO FINAL: EVALUACIÓN SATISFECHA AL 100% [PASS]")
    else:
        print("ESTADO FINAL: EVALUACIÓN INCOMPLETA / FALLIDA [FAIL]")
    print("=" * 78 + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Arnés Central de Evaluación de Antigravity Mobile"
    )
    parser.add_argument(
        "target",
        nargs="?",
        default=None,
        help="Archivo .md específico o directorio de especificaciones (por defecto: specs)",
    )
    parser.add_argument(
        "--spec",
        dest="spec_opt",
        default=None,
        help="Ruta a un archivo de especificación específico",
    )
    parser.add_argument(
        "--suite",
        choices=["all", "specs", "spec_validator", "lint", "code_linter", "tests", "test_suite"],
        default="all",
        help="Suite específica a ejecutar (por defecto: all)",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emitir el reporte completo en formato JSON por stdout",
    )
    parser.add_argument(
        "--output-json",
        default=None,
        help="Guardar el reporte JSON estructurado en la ruta especificada",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Modo detallado",
    )

    args = parser.parse_args()

    # Determinar objetivo de especificación
    target_spec_path = None
    raw_target = args.spec_opt or args.target
    if raw_target:
        target_spec_path = Path(raw_target)

    # Determinar suites
    suites_to_run = None
    if args.suite in ("specs", "spec_validator"):
        suites_to_run = ["spec_validator"]
    elif args.suite in ("lint", "code_linter"):
        suites_to_run = ["code_linter"]
    elif args.suite in ("tests", "test_suite"):
        suites_to_run = ["test_suite"]

    runner = HarnessRunner(target_spec=target_spec_path)
    report = runner.execute(suites_to_run=suites_to_run)

    # Guardar en archivo si se solicitó
    if args.output_json:
        out_path = Path(args.output_json)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        out_path.write_text(json.dumps(report.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")

    # Guardar siempre copia de último reporte en harness/latest_report.json
    latest_path = Path("harness/latest_report.json")
    try:
        latest_path.parent.mkdir(parents=True, exist_ok=True)
        latest_path.write_text(json.dumps(report.to_dict(), indent=2, ensure_ascii=False), encoding="utf-8")
    except Exception:
        pass

    if args.json:
        print(json.dumps(report.to_dict(), indent=2, ensure_ascii=False))
    else:
        print_console_report(report, verbose=args.verbose)

    return 0 if report.overall_status == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
