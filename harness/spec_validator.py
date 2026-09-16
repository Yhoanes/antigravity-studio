#!/usr/bin/env python3
"""
spec_validator.py - Validador de Especificaciones SDD (Spec-Driven Development)
Parte del Harness de Antigravity Mobile.

Verifica que cada archivo de especificación en `specs/` cumpla estrictamente
con los estándares SDD requeridos:
1. Cabecera con metadatos requeridos:
   - Identificador (SPEC-XXX)
   - Título
   - Autor
   - Estado (APPROVED FOR IMPLEMENTATION o DRAFT)
   - Dispositivo Objetivo (debe incluir Xiaomi Pad 6)
   - Runtime Target
2. Sección obligatoria de "Criterios de Aceptación Verificables" con identificadores tipo `AC-*-XXX`.
3. Sección de diagramas y/o contratos de interfaces (Mermaid, firmas C++, interfaces Kotlin, etc.).
"""

from __future__ import annotations

import argparse
import io
import json
import re
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional, Set

# Forzar UTF-8 en stdout/stderr para evitar problemas de codificación en Windows
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


@dataclass
class AcceptanceCriterion:
    id: str
    module: Optional[str] = None
    description: Optional[str] = None
    verification_method: Optional[str] = None
    line_number: Optional[int] = None


@dataclass
class ValidationError:
    category: str
    message: str
    line_number: Optional[int] = None
    suggestion: Optional[str] = None


@dataclass
class ValidationWarning:
    category: str
    message: str
    line_number: Optional[int] = None
    suggestion: Optional[str] = None


@dataclass
class SpecReport:
    file_path: str
    is_valid: bool
    metadata: Dict[str, str] = field(default_factory=dict)
    acceptance_criteria: List[AcceptanceCriterion] = field(default_factory=list)
    diagram_count: int = 0
    contract_count: int = 0
    errors: List[ValidationError] = field(default_factory=list)
    warnings: List[ValidationWarning] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "file_path": self.file_path,
            "is_valid": self.is_valid,
            "metadata": self.metadata,
            "acceptance_criteria": [asdict(ac) for ac in self.acceptance_criteria],
            "diagram_count": self.diagram_count,
            "contract_count": self.contract_count,
            "errors": [asdict(err) for err in self.errors],
            "warnings": [asdict(w) for w in self.warnings],
        }


def strip_outer_markdown(text: str) -> str:
    """Remueve marcas de formato markdown (` y **) solo si envuelven el texto por completo."""
    text = text.strip()
    # Si todo el string está entre comillas inversas: `texto`
    if text.startswith("`") and text.endswith("`") and len(text) >= 2:
        if text.count("`") == 2:
            text = text[1:-1].strip()
    # Si todo el string está en negrita: **texto**
    if text.startswith("**") and text.endswith("**") and len(text) >= 4:
        if text.count("**") == 2:
            text = text[2:-2].strip()
    return text


class SpecValidator:
    """Valida archivos markdown de especificación conforme al estándar SDD."""

    # Estados permitidos en el estándar SDD
    ALLOWED_STATUSES = {
        "APPROVED FOR IMPLEMENTATION",
        "DRAFT",
    }

    # Dispositivo objetivo obligatorio
    REQUIRED_TARGET_DEVICE = "Xiaomi Pad 6"

    # Regex para identificadores de especificación
    SPEC_ID_REGEX = re.compile(r"^SPEC-[A-Za-z0-9_-]+$")

    # Regex para identificadores de criterios de aceptación (ej: AC-ARCH-001, AC-PTY-010)
    AC_ID_REGEX = re.compile(r"\bAC-[A-Za-z0-9_]+-[A-Za-z0-9_]+\b")

    def __init__(self, specs_dir: Optional[Path] = None):
        self.specs_dir = specs_dir or Path("specs")

    def validate_file(self, file_path: Path) -> SpecReport:
        """Analiza y valida un único archivo de especificación."""
        report = SpecReport(file_path=str(file_path.as_posix()), is_valid=True)

        if not file_path.exists():
            report.is_valid = False
            report.errors.append(
                ValidationError(
                    category="FILE_NOT_FOUND",
                    message=f"El archivo especificado no existe: {file_path}",
                    suggestion="Verifique que la ruta al archivo de especificación sea correcta.",
                )
            )
            return report

        try:
            content = file_path.read_text(encoding="utf-8")
        except Exception as ex:
            report.is_valid = False
            report.errors.append(
                ValidationError(
                    category="FILE_READ_ERROR",
                    message=f"Error al leer el archivo: {ex}",
                    suggestion="Asegúrese de que el archivo tenga codificación UTF-8 válida.",
                )
            )
            return report

        lines = content.splitlines()

        # 1. Validar metadatos de cabecera
        self._validate_metadata(content, lines, report)

        # 2. Validar Criterios de Aceptación Verificables
        self._validate_acceptance_criteria(content, lines, report)

        # 3. Validar Diagramas y/o Contratos de Interfaz
        self._validate_diagrams_and_contracts(content, lines, report)

        # Determinar validez final
        report.is_valid = len(report.errors) == 0
        return report

    def validate_all(self, target_path: Optional[Path] = None) -> List[SpecReport]:
        """Valida todos los archivos en specs_dir o la ruta especificada."""
        path = target_path or self.specs_dir
        reports: List[SpecReport] = []

        if path.is_file():
            reports.append(self.validate_file(path))
            return reports

        if not path.exists():
            report = SpecReport(file_path=str(path.as_posix()), is_valid=False)
            is_file_target = path.suffix == ".md"
            target_type = "archivo de especificación" if is_file_target else "directorio de especificaciones"
            cat = "FILE_NOT_FOUND" if is_file_target else "DIRECTORY_NOT_FOUND"
            report.errors.append(
                ValidationError(
                    category=cat,
                    message=f"El {target_type} no existe: {path}",
                    suggestion=f"Verifique que la ruta '{path}' sea correcta.",
                )
            )
            return [report]

        spec_files = sorted(
            [f for f in path.glob("*.md") if not f.name.startswith(".")]
        )

        if not spec_files:
            report = SpecReport(file_path=str(path.as_posix()), is_valid=False)
            report.errors.append(
                ValidationError(
                    category="NO_SPECS_FOUND",
                    message=f"No se encontraron archivos markdown (.md) en {path}",
                    suggestion="Asegúrese de agregar especificaciones SDD en la carpeta specs/.",
                )
            )
            return [report]

        for spec_file in spec_files:
            reports.append(self.validate_file(spec_file))

        return reports

    def _validate_metadata(
        self, content: str, lines: List[str], report: SpecReport
    ) -> None:
        """Extrae y comprueba los metadatos requeridos del archivo."""
        metadata: Dict[str, str] = {}
        metadata_lines: Dict[str, int] = {}

        key_aliases = {
            "identificador": "id",
            "id": "id",
            "identifier": "id",
            "título": "title",
            "titulo": "title",
            "title": "title",
            "autor": "author",
            "author": "author",
            "estado": "status",
            "status": "status",
            "dispositivo objetivo": "target_device",
            "dispositivo": "target_device",
            "target device": "target_device",
            "target_device": "target_device",
            "runtime target": "runtime_target",
            "target runtime": "runtime_target",
            "runtime_target": "runtime_target",
        }

        # 1. Extracción desde tabla Markdown (| Metadato | Valor |)
        table_row_regex = re.compile(r"^\|\s*([^|]+?)\s*\|\s*([^|]+?)\s*\|$")
        for idx, line in enumerate(lines, start=1):
            match = table_row_regex.match(line.strip())
            if match:
                raw_key, raw_val = match.groups()
                clean_key = strip_outer_markdown(raw_key).lower()
                clean_val = strip_outer_markdown(raw_val)

                if clean_key in key_aliases:
                    canonical_key = key_aliases[clean_key]
                    if canonical_key not in metadata:
                        metadata[canonical_key] = clean_val
                        metadata_lines[canonical_key] = idx

        # 2. Extracción alternativa desde Frontmatter o listas clave: valor
        if len(metadata) < 6:
            kv_regex = re.compile(r"^[-*]?\s*\**([A-Za-zÀ-ÿ0-9_\s]+)\**\s*:\s*(.+)$")
            for idx, line in enumerate(lines, start=1):
                match = kv_regex.match(line.strip())
                if match:
                    raw_key, raw_val = match.groups()
                    clean_key = strip_outer_markdown(raw_key).lower().strip()
                    clean_val = strip_outer_markdown(raw_val)
                    if clean_key in key_aliases:
                        canonical_key = key_aliases[clean_key]
                        if canonical_key not in metadata:
                            metadata[canonical_key] = clean_val
                            metadata_lines[canonical_key] = idx

        # Fallback: buscar SPEC-XXX en el primer encabezado H1
        if "id" not in metadata:
            for idx, line in enumerate(lines[:10], start=1):
                if line.strip().startswith("#"):
                    spec_id_match = re.search(r"SPEC-[A-Za-z0-9_-]+", line)
                    if spec_id_match:
                        metadata["id"] = spec_id_match.group(0)
                        metadata_lines["id"] = idx
                        break

        report.metadata = metadata

        # a) Identificador
        if "id" not in metadata:
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_ID",
                    message="Falta el metadato obligatorio 'Identificador' (formato esperado: SPEC-XXX).",
                    suggestion="Agregue la fila '| **Identificador** | `SPEC-XXX` |' en la cabecera.",
                )
            )
        elif not self.SPEC_ID_REGEX.match(metadata["id"]):
            report.errors.append(
                ValidationError(
                    category="INVALID_METADATA_ID",
                    message=f"El Identificador '{metadata['id']}' no cumple con el formato SPEC-XXX.",
                    line_number=metadata_lines.get("id"),
                    suggestion="Utilice una nomenclatura conforme al estándar SDD (ejemplo: SPEC-000, SPEC-001).",
                )
            )

        # b) Título
        if "title" not in metadata or len(metadata["title"].strip()) < 3:
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_TITLE",
                    message="Falta el metadato obligatorio 'Título' o es demasiado breve.",
                    suggestion="Agregue la fila '| **Título** | Nombre Descriptivo |' en la cabecera.",
                )
            )

        # c) Autor
        if "author" not in metadata or not metadata["author"].strip():
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_AUTHOR",
                    message="Falta el metadato obligatorio 'Autor'.",
                    suggestion="Agregue la fila '| **Autor** | `subagent_name` |' en la cabecera.",
                )
            )

        # d) Estado
        if "status" not in metadata:
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_STATUS",
                    message="Falta el metadato obligatorio 'Estado'.",
                    suggestion="Defina el Estado como 'APPROVED FOR IMPLEMENTATION' o 'DRAFT'.",
                )
            )
        else:
            status_val = metadata["status"].strip().upper()
            if status_val not in self.ALLOWED_STATUSES:
                report.errors.append(
                    ValidationError(
                        category="INVALID_METADATA_STATUS",
                        message=f"El Estado '{metadata['status']}' no es válido. Valores permitidos: {sorted(self.ALLOWED_STATUSES)}",
                        line_number=metadata_lines.get("status"),
                        suggestion="Establezca el estado en 'APPROVED FOR IMPLEMENTATION' o 'DRAFT'.",
                    )
                )

        # e) Dispositivo Objetivo
        if "target_device" not in metadata:
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_TARGET_DEVICE",
                    message="Falta el metadato obligatorio 'Dispositivo Objetivo'.",
                    suggestion=f"Especifique '{self.REQUIRED_TARGET_DEVICE}' en la cabecera.",
                )
            )
        elif self.REQUIRED_TARGET_DEVICE.lower() not in metadata["target_device"].lower():
            report.errors.append(
                ValidationError(
                    category="INVALID_TARGET_DEVICE",
                    message=f"El 'Dispositivo Objetivo' debe incluir expresamente '{self.REQUIRED_TARGET_DEVICE}'. Encontrado: '{metadata['target_device']}'",
                    line_number=metadata_lines.get("target_device"),
                    suggestion=f"Asegúrese de incluir '{self.REQUIRED_TARGET_DEVICE}' como hardware primario de destino.",
                )
            )

        # f) Runtime Target
        if "runtime_target" not in metadata or not metadata["runtime_target"].strip():
            report.errors.append(
                ValidationError(
                    category="MISSING_METADATA_RUNTIME_TARGET",
                    message="Falta el metadato obligatorio 'Runtime Target'.",
                    suggestion="Agregue la fila '| **Runtime Target** | Kotlin / NDK / PRoot Linux |' en la cabecera.",
                )
            )

    def _validate_acceptance_criteria(
        self, content: str, lines: List[str], report: SpecReport
    ) -> None:
        """Verifica la existencia y formato de la sección de Criterios de Aceptación."""
        ac_section_pattern = re.compile(
            r"^#{1,4}\s+.*(criterios\s+de\s+aceptaci[oó]n|acceptance\s+criteria).*",
            re.IGNORECASE,
        )

        section_found = False
        section_line = None

        for idx, line in enumerate(lines, start=1):
            if ac_section_pattern.match(line.strip()):
                section_found = True
                section_line = idx
                break

        if not section_found:
            report.errors.append(
                ValidationError(
                    category="MISSING_AC_SECTION",
                    message="No se encontró la sección obligatoria de 'Criterios de Aceptación Verificables' (Acceptance Criteria).",
                    suggestion="Agregue una sección con encabezado '## Criterios de Aceptación Verificables (Acceptance Criteria)'.",
                )
            )
            return

        seen_ids: Set[str] = set()
        ac_list: List[AcceptanceCriterion] = []

        # Buscar filas de tabla tipo: | **`AC-ARCH-001`** | Módulo | Criterio | Método |
        table_ac_regex = re.compile(
            r"^\|\s*[\*`]*(AC-[A-Za-z0-9_]+-[A-Za-z0-9_]+)[\*`]*\s*\|\s*([^|]+)\|\s*([^|]+)\|\s*([^|]+)\|"
        )

        for idx, line in enumerate(lines, start=1):
            table_match = table_ac_regex.match(line.strip())
            if table_match:
                ac_id, mod, desc, meth = table_match.groups()
                ac_id_clean = strip_outer_markdown(ac_id)
                if ac_id_clean in seen_ids:
                    report.errors.append(
                        ValidationError(
                            category="DUPLICATE_AC_ID",
                            message=f"El identificador de criterio de aceptación '{ac_id_clean}' está duplicado.",
                            line_number=idx,
                            suggestion="Asegúrese de que cada identificador AC-*-XXX sea único en la especificación.",
                        )
                    )
                seen_ids.add(ac_id_clean)
                ac_list.append(
                    AcceptanceCriterion(
                        id=ac_id_clean,
                        module=strip_outer_markdown(mod),
                        description=strip_outer_markdown(desc),
                        verification_method=strip_outer_markdown(meth),
                        line_number=idx,
                    )
                )
            else:
                for match in self.AC_ID_REGEX.finditer(line):
                    candidate_id = match.group(0)
                    if candidate_id not in seen_ids:
                        seen_ids.add(candidate_id)
                        ac_list.append(
                            AcceptanceCriterion(
                                id=candidate_id,
                                description=strip_outer_markdown(line),
                                line_number=idx,
                            )
                        )

        report.acceptance_criteria = ac_list

        if not ac_list:
            report.errors.append(
                ValidationError(
                    category="EMPTY_AC_SECTION",
                    message="La sección de Criterios de Aceptación no contiene ningún identificador con formato válido 'AC-*-XXX'.",
                    line_number=section_line,
                    suggestion="Defina criterios verificables con identificadores como 'AC-ARCH-001', 'AC-PTY-001', etc.",
                )
            )

    def _validate_diagrams_and_contracts(
        self, content: str, lines: List[str], report: SpecReport
    ) -> None:
        """Verifica la presencia de diagramas arquitectónicos y contratos de interfaces."""
        # 1. Contabilizar bloques de diagramas Mermaid
        mermaid_blocks = re.findall(r"```mermaid[\s\S]*?```", content)
        report.diagram_count = len(mermaid_blocks)

        # 2. Contabilizar bloques de contratos de interfaces / código técnico
        contract_code_languages = (
            "kotlin",
            "cpp",
            "c\\+\\+",
            "c",
            "protobuf",
            "proto",
            "json",
            "typescript",
            "java",
        )
        pattern_str = rf"```({'|'.join(contract_code_languages)})[\s\S]*?```"
        contract_blocks = re.findall(pattern_str, content, re.IGNORECASE)
        report.contract_count = len(contract_blocks)

        # 3. Comprobar secciones relacionadas con diagramas o contratos
        contract_section_regex = re.compile(
            r"^#{1,4}\s+.*(diagrama|contrato|interfaz|interface|descomposici[oó]n|arquitectura|máquina\s+de\s+estados|state\s+machine).*",
            re.IGNORECASE,
        )
        has_contract_section = any(
            contract_section_regex.match(line.strip()) for line in lines
        )

        has_visual_or_contract = (report.diagram_count > 0) or (
            report.contract_count > 0
        )

        if not has_contract_section and not has_visual_or_contract:
            report.errors.append(
                ValidationError(
                    category="MISSING_DIAGRAMS_OR_CONTRACTS",
                    message="La especificación carece de secciones de diagramas o contratos de interfaz formales.",
                    suggestion="Incluya diagramas de arquitectura en Mermaid (```mermaid) o definiciones de interfaces de código (Kotlin / C++ / Schemas).",
                )
            )
        elif not has_visual_or_contract:
            report.warnings.append(
                ValidationWarning(
                    category="NO_MERMAID_OR_CODE_CONTRACT",
                    message="Se detectó sección de arquitectura/contrato pero no incluye bloques de diagramas Mermaid ni bloques de interfaz formal.",
                    suggestion="Agregue bloques ```mermaid o contratos de interfaces para mayor precisión SDD.",
                )
            )


def print_cli_report(reports: List[SpecReport], verbose: bool = False) -> None:
    """Imprime el reporte en consola con formato claro PASS/FAIL."""
    all_valid = all(r.is_valid for r in reports)
    total_specs = len(reports)
    passed_specs = sum(1 for r in reports if r.is_valid)
    failed_specs = total_specs - passed_specs
    total_acs = sum(len(r.acceptance_criteria) for r in reports)

    print("\n" + "=" * 76)
    print("           ANTIGRAVITY MOBILE - REPORTE DE VALIDACIÓN SDD")
    print("=" * 76)

    for report in reports:
        status_tag = "[PASS]" if report.is_valid else "[FAIL]"
        spec_id = report.metadata.get("id", "SPEC-UNKNOWN")
        spec_title = report.metadata.get("title", "Sin Título")
        print(f"\n{status_tag} {spec_id}: {spec_title}")
        print(f"       Archivo: {report.file_path}")
        print(f"       Estado SDD: {report.metadata.get('status', 'NO DEFINIDO')}")
        print(f"       Hardware Objetivo: {report.metadata.get('target_device', 'NO DEFINIDO')}")
        print(f"       Criterios de Aceptación: {len(report.acceptance_criteria)} verificados")
        print(f"       Diagramas Mermaid: {report.diagram_count} | Contratos de Interfaz: {report.contract_count}")

        if report.acceptance_criteria and verbose:
            print("       Lista de ACs:")
            for ac in report.acceptance_criteria:
                mod_info = f" [{ac.module}]" if ac.module else ""
                print(f"         - {ac.id}{mod_info}: {ac.description or ''}")

        if report.warnings:
            for w in report.warnings:
                line_info = f" (Línea {w.line_number})" if w.line_number else ""
                print(f"       [WARN] {w.category}{line_info}: {w.message}")

        if report.errors:
            print("       [ERRORES DETECTADOS]:")
            for err in report.errors:
                line_info = f" (Línea {err.line_number})" if err.line_number else ""
                print(f"         * [{err.category}]{line_info}: {err.message}")
                if err.suggestion:
                    print(f"           -> Sugerencia: {err.suggestion}")

    print("\n" + "-" * 76)
    print(f"RESUMEN: {passed_specs}/{total_specs} especificaciones válidas | {total_acs} Criterios de Aceptación registrados")
    if all_valid:
        print("RESULTADO GLOBAL: 100% CUMPLIMIENTO SDD [PASS]")
    else:
        print(f"RESULTADO GLOBAL: {failed_specs} especificaciones requieren corrección [FAIL]")
    print("=" * 76 + "\n")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Validador de Especificaciones SDD de Antigravity Mobile"
    )
    parser.add_argument(
        "target",
        nargs="?",
        default="specs",
        help="Ruta a un archivo .md o directorio de especificaciones (por defecto: specs)",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emitir el reporte exclusivamente en formato JSON estructurado",
    )
    parser.add_argument(
        "-v",
        "--verbose",
        action="store_true",
        help="Mostrar detalles exhaustivos de cada criterio de aceptación",
    )

    args = parser.parse_args()
    target_path = Path(args.target)

    validator = SpecValidator(specs_dir=target_path if target_path.is_dir() else target_path.parent)
    reports = validator.validate_all(target_path=target_path)

    all_valid = all(r.is_valid for r in reports) and len(reports) > 0

    if args.json:
        data = {
            "status": "PASS" if all_valid else "FAIL",
            "total_specs": len(reports),
            "passed_specs": sum(1 for r in reports if r.is_valid),
            "failed_specs": sum(1 for r in reports if not r.is_valid),
            "reports": [r.to_dict() for r in reports],
        }
        print(json.dumps(data, indent=2, ensure_ascii=False))
    else:
        print_cli_report(reports, verbose=args.verbose)

    return 0 if all_valid else 1


if __name__ == "__main__":
    sys.exit(main())
