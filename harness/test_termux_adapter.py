#!/usr/bin/env python3
"""
test_termux_adapter.py - Suite de validación para el adaptador de Termux en Antigravity Studio.

Verifica que scripts/setup_termux_antigravity.sh cumpla con todas las especificaciones:
- Instalación de dependencias mínimas (curl, jq, git, python, nano con pkg install -y).
- Configuración visual Cyber-Obsidian (~/.termux/colors.properties).
- Configuración de barra táctil para tablet (~/.termux/termux.properties con extra-keys).
- Detección y enlace del CLI Antigravity (agy) para ARM64 / Snapdragon 870.
- Configuración de entorno, proyectos y banner interactivo en ~/.bashrc.
- Sintaxis bash válida e idempotencia en ejecuciones sucesivas.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


class TestTermuxAdapter(unittest.TestCase):
    """Pruebas de validación estática y funcional para setup_termux_antigravity.sh."""

    @classmethod
    def setUpClass(cls):
        cls.project_root = Path(__file__).resolve().parent.parent
        cls.script_path = cls.project_root / "scripts" / "setup_termux_antigravity.sh"
        cls.relative_script = "scripts/setup_termux_antigravity.sh"

    def setUp(self):
        self.assertTrue(
            self.script_path.exists(),
            f"El script {self.script_path} debe existir",
        )
        self.script_content = self.script_path.read_text(encoding="utf-8")

    @classmethod
    def _to_wsl_or_posix_path(cls, path: Path) -> str:
        """Convierte una ruta de Windows a formato POSIX/WSL si es necesario."""
        drive = path.drive
        if drive:
            letter = drive[0].lower()
            rest = path.as_posix().split(":")[-1]
            return f"/mnt/{letter}{rest}"
        return path.as_posix()

    def test_script_file_exists_and_not_empty(self):
        """Verifica que setup_termux_antigravity.sh exista y no esté vacío."""
        self.assertGreater(
            len(self.script_content.strip()),
            100,
            "El script de instalación no debe estar vacío",
        )
        self.assertTrue(
            self.script_content.startswith("#!/usr/bin/env bash")
            or self.script_content.startswith("#!/bin/bash"),
            "El script debe comenzar con un shebang bash adecuado",
        )

    def test_bash_syntax_validation(self):
        """Valida la sintaxis bash del script usando 'bash -n' si bash está disponible."""
        bash_path = shutil.which("bash") or shutil.which("bash.exe")
        if not bash_path:
            self.skipTest("Binario bash no encontrado en el sistema; omitiendo prueba de sintaxis dinámica.")

        result = subprocess.run(
            [bash_path, "-n", self.relative_script],
            cwd=str(self.project_root),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
        self.assertEqual(
            result.returncode,
            0,
            f"Fallo de sintaxis bash en {self.script_path.name}:\nSTDERR: {result.stderr}\nSTDOUT: {result.stdout}",
        )

    def test_step1_minimum_dependencies_declared(self):
        """Paso 1: Valida que incluya verificación/instalación de curl, jq, git, python, nano con pkg install."""
        required_pkgs = ["curl", "jq", "git", "python", "nano"]
        for pkg in required_pkgs:
            self.assertIn(
                pkg,
                self.script_content,
                f"El paquete esencial '{pkg}' debe estar contemplado en el script",
            )
        self.assertIn(
            "pkg install",
            self.script_content,
            "Debe incluirse comando de instalación con 'pkg install'",
        )

    def test_step2_cyber_obsidian_colors_palette(self):
        """Paso 2: Valida la paleta Cyber-Obsidian oficial para ~/.termux/colors.properties."""
        expected_palette = {
            "background": "#0B0F19",
            "foreground": "#E2E8F0",
            "cursor": "#00F0FF",
            "color0": "#0B0F19",
            "color1": "#EF4444",
            "color2": "#10B981",
            "color3": "#F59E0B",
            "color4": "#3B82F6",
            "color5": "#8B5CF6",
            "color6": "#00F0FF",
            "color7": "#E2E8F0",
            "color8": "#1E293B",
            "color9": "#F87171",
            "color10": "#34D399",
            "color11": "#FBBF24",
            "color12": "#60A5FA",
            "color13": "#A78BFA",
            "color14": "#22D3EE",
            "color15": "#FFFFFF",
        }

        self.assertIn("colors.properties", self.script_content)
        for key, value in expected_palette.items():
            pattern = rf"{key}\s*=\s*{re.escape(value)}"
            self.assertTrue(
                re.search(pattern, self.script_content),
                f"Falta el valor exacto de color '{key}={value}' en colors.properties",
            )

    def test_step2_termux_properties_and_extra_keys(self):
        """Paso 2: Valida la barra táctil para tablet (extra-keys) en ~/.termux/termux.properties."""
        self.assertIn("termux.properties", self.script_content)
        self.assertIn("extra-keys", self.script_content)
        self.assertIn("extra-keys-style = arrows-only", self.script_content)

        required_buttons = [
            "ESC",
            "CTRL",
            "TAB",
            "✓ Aprobar",
            "⚡ Modelo",
            "⏹ Detener",
            "📁 Proyectos",
        ]
        for btn in required_buttons:
            self.assertIn(
                btn,
                self.script_content,
                f"El botón táctil '{btn}' debe estar presente en la barra de extra-keys",
            )

        # Verificar invocación condicional a termux-reload-settings
        self.assertIn("termux-reload-settings", self.script_content)

    def test_step3_agy_verification_and_linking(self):
        """Paso 3: Valida creación del lanzador 'agy' hacia proot-distro Ubuntu ARM64 y ausencia de menús mock."""
        self.assertIn("command -v proot-distro", self.script_content)
        self.assertIn("proot-distro login ubuntu", self.script_content)
        self.assertIn("INSTALL_BIN_DIR", self.script_content)
        self.assertIn("chmod +x", self.script_content)
        self.assertTrue(
            "Snapdragon 870" in self.script_content or "aarch64" in self.script_content or "ARM64" in self.script_content,
            "El soporte para Qualcomm Snapdragon 870 / ARM64 debe estar contemplado en el instalador",
        )
        # Validar que no existan las funciones mock previas
        self.assertNotIn("list_models()", self.script_content)
        self.assertNotIn("show_help()", self.script_content)

    def test_step4_bashrc_environment_and_banner(self):
        """Paso 4: Valida variables de entorno, carpeta ~/projects, banner ANSI, auto-arranque y wake-lock en ~/.bashrc."""
        self.assertIn(".bashrc", self.script_content)
        self.assertIn("projects", self.script_content)
        self.assertIn("Snapdragon 870", self.script_content)
        self.assertIn("Xiaomi Pad 6", self.script_content)
        self.assertIn("Antigravity", self.script_content)

        # Idempotencia: Verificar que use marcadores o lógica de reemplazo en .bashrc
        self.assertTrue(
            "# >>> ANTIGRAVITY STUDIO BOOTSTRAP >>>" in self.script_content
            and "# <<< ANTIGRAVITY STUDIO BOOTSTRAP <<<" in self.script_content,
            "El bloque en ~/.bashrc debe usar delimitadores de idempotencia",
        )

        # Auto-arranque interactivo directo y persistencia wake-lock contra HyperOS
        self.assertIn("AGY_LAUNCHED", self.script_content)
        self.assertIn("termux-wake-lock", self.script_content)

    def test_functional_execution_and_idempotency_in_mock_env(self):
        """Ejecuta el script en un entorno mock aislado para validar creación de archivos e idempotencia."""
        bash_path = shutil.which("bash") or shutil.which("bash.exe")
        if not bash_path:
            self.skipTest("Binario bash no encontrado; omitiendo ejecución funcional mock.")

        with tempfile.TemporaryDirectory() as tmp_dir:
            mock_home = Path(tmp_dir) / "mock_home"
            mock_home.mkdir(parents=True, exist_ok=True)

            posix_home = self._to_wsl_or_posix_path(mock_home)
            posix_prefix = f"{posix_home}/mock_prefix"

            bash_exec_cmd = (
                f'export HOME="{posix_home}"; '
                f'export PREFIX="{posix_prefix}"; '
                f'mkdir -p "{posix_prefix}/bin"; '
                f'bash "{self.relative_script}"'
            )

            # 1. Primera ejecución
            res1 = subprocess.run(
                [bash_path, "-c", bash_exec_cmd],
                cwd=str(self.project_root),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            self.assertEqual(
                res1.returncode,
                0,
                f"Fallo en la primera ejecución del instalador:\nSTDERR:\n{res1.stderr}\nSTDOUT:\n{res1.stdout}",
            )

            # Validar que los archivos fueron creados en mock_home
            termux_dir = mock_home / ".termux"
            colors_file = termux_dir / "colors.properties"
            props_file = termux_dir / "termux.properties"
            bashrc_file = mock_home / ".bashrc"
            projects_dir = mock_home / "projects"

            self.assertTrue(colors_file.exists(), "colors.properties debió ser creado")
            self.assertTrue(props_file.exists(), "termux.properties debió ser creado")
            self.assertTrue(bashrc_file.exists(), ".bashrc debió ser creado/modificado")
            self.assertTrue(projects_dir.exists(), "~/projects debió ser creado")

            # Validar contenido de colors.properties
            colors_text = colors_file.read_text(encoding="utf-8")
            self.assertIn("background=#0B0F19", colors_text)
            self.assertIn("cursor=#00F0FF", colors_text)
            self.assertIn("color15=#FFFFFF", colors_text)

            # Validar contenido de termux.properties
            props_text = props_file.read_text(encoding="utf-8")
            self.assertIn("extra-keys", props_text)
            self.assertIn("extra-keys-style = arrows-only", props_text)
            self.assertIn("✓ Aprobar", props_text)
            self.assertIn("⚡ Modelo", props_text)

            # Validar contenido de .bashrc
            bashrc_text = bashrc_file.read_text(encoding="utf-8")
            marker_start = "# >>> ANTIGRAVITY STUDIO BOOTSTRAP >>>"
            self.assertEqual(
                bashrc_text.count(marker_start),
                1,
                "Debe existir exactamente 1 bloque de bootstrap en .bashrc tras la primera ejecución",
            )

            # 2. Segunda ejecución (Prueba estricta de idempotencia)
            res2 = subprocess.run(
                [bash_path, "-c", bash_exec_cmd],
                cwd=str(self.project_root),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            self.assertEqual(
                res2.returncode,
                0,
                f"Fallo en la segunda ejecución (idempotencia):\nSTDERR:\n{res2.stderr}\nSTDOUT:\n{res2.stdout}",
            )

            # Validar que tras la segunda ejecución no se duplique el bloque en .bashrc
            bashrc_text_2 = bashrc_file.read_text(encoding="utf-8")
            self.assertEqual(
                bashrc_text_2.count(marker_start),
                1,
                "El bloque de bootstrap en .bashrc NO debe duplicarse tras múltiples ejecuciones (Idempotencia)",
            )

            # 3. Validar el binario lanzador 'agy' instalado y su delegación a proot-distro
            agy_bin = mock_home / "mock_prefix" / "bin" / "agy"
            self.assertTrue(agy_bin.exists(), "El binario agy debe existir en mock_prefix/bin/agy")

            agy_text = agy_bin.read_text(encoding="utf-8")
            self.assertIn("proot-distro login ubuntu", agy_text)
            self.assertIn("proot-distro no está instalado", agy_text)

            # Ejecución de agy sin proot-distro en PATH (debe retornar exit code 1 y advertencia)
            agy_cmd_nofallback = f'export HOME="{posix_home}"; bash "{posix_prefix}/bin/agy"'
            res_fail = subprocess.run(
                [bash_path, "-c", agy_cmd_nofallback],
                cwd=str(self.project_root),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            self.assertEqual(res_fail.returncode, 1, "Debe retornar código 1 si proot-distro no está instalado")
            self.assertIn("Error: proot-distro no está instalado", res_fail.stdout + res_fail.stderr)

            # Ejecución de agy con wrapper mock de proot-distro en PATH
            mock_proot = mock_home / "mock_prefix" / "bin" / "proot-distro"
            mock_proot.write_bytes(b'#!/bin/sh\necho "MOCK_PROOT_EXEC: $@"\nexit 0\n')
            mock_proot.chmod(0o755)

            agy_cmd_proot = (
                f'export HOME="{posix_home}"; '
                f'export PATH="{posix_prefix}/bin:$PATH"; '
                f'bash "{posix_prefix}/bin/agy" --version'
            )
            res_proot = subprocess.run(
                [bash_path, "-c", agy_cmd_proot],
                cwd=str(self.project_root),
                capture_output=True,
                text=True,
                encoding="utf-8",
                errors="replace",
            )
            self.assertEqual(res_proot.returncode, 0, f"Invocación con mock proot-distro falló: {res_proot.stderr}")
            self.assertIn("MOCK_PROOT_EXEC: login ubuntu", res_proot.stdout)

            # Validar que .bashrc contenga auto-arranque y persistencia wake-lock
            self.assertIn("AGY_LAUNCHED", bashrc_text)
            self.assertIn("termux-wake-lock", bashrc_text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
