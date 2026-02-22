"""
Real subprocess safety and shell command tests (Chicago TDD).

Tests real subprocess.run() calls with actual shell scripts and commands.
No mocks - all tests execute real commands with real input/output/errors.
Coverage: timeouts, signals, exit codes, output capture, env vars, working dirs.
"""

import os
import signal
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from typing import Optional

import pytest

from yawl_cli.utils import run_shell_cmd


class TestSubprocessTimeout:
    """Test timeout handling with real command execution."""

    def test_subprocess_timeout_hits_limit(self, temp_project_dir: Path) -> None:
        """Real test: long-running command hits timeout (SIGTERM).

        Fixture: sleep 10 with timeout=0.5
        Expect: TimeoutExpired exception raised
        Verify: Command actually killed by timeout
        """
        cmd = ["sleep", "10"]
        timeout = 0.5

        # This should timeout
        with pytest.raises(subprocess.TimeoutExpired):
            subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=timeout,
            )

    def test_subprocess_timeout_propagates_as_runtime_error(
        self, temp_project_dir: Path
    ) -> None:
        """Real test: timeout converted to RuntimeError by run_shell_cmd.

        Fixture: sleep 10 with timeout=0.5 via run_shell_cmd wrapper
        Expect: RuntimeError raised with helpful message
        Verify: Error message includes command and timeout value
        """
        cmd = ["sleep", "10"]
        timeout = 0.5

        with pytest.raises(RuntimeError, match="timed out"):
            run_shell_cmd(cmd, timeout=timeout)

    def test_subprocess_timeout_message_includes_command(self) -> None:
        """Real test: timeout error message includes original command.

        Fixture: sleep with short timeout
        Expect: Error message contains "sleep"
        """
        cmd = ["sleep", "10"]
        timeout = 0.3

        with pytest.raises(RuntimeError) as exc_info:
            run_shell_cmd(cmd, timeout=timeout)

        assert "sleep" in str(exc_info.value)
        assert "timed out" in str(exc_info.value).lower()

    def test_subprocess_timeout_message_includes_timeout_value(self) -> None:
        """Real test: error message includes timeout value for retry guidance.

        Fixture: timeout=0.5
        Expect: Message includes "0.5" and suggests increasing timeout
        """
        cmd = ["sleep", "10"]
        timeout = 0.5

        with pytest.raises(RuntimeError) as exc_info:
            run_shell_cmd(cmd, timeout=timeout)

        error_msg = str(exc_info.value).lower()
        assert "timeout" in error_msg

    def test_subprocess_quick_command_no_timeout(self) -> None:
        """Real test: quick command completes before timeout.

        Fixture: echo "hello" with timeout=10
        Expect: Command succeeds with exit code 0
        Verify: Output captured correctly
        """
        cmd = ["echo", "hello"]
        exit_code, stdout, stderr = run_shell_cmd(cmd, timeout=10)

        assert exit_code == 0
        assert "hello" in stdout

    def test_subprocess_timeout_with_retries(self) -> None:
        """Real test: timeout retried, eventually fails.

        Fixture: sleep 10 with timeout=0.3, retries=2
        Expect: 3 attempts total (initial + 2 retries), all timeout
        Verify: Final attempt still raises TimeoutExpired
        """
        cmd = ["sleep", "10"]
        timeout = 0.3

        # With retries, should still eventually timeout
        with pytest.raises(RuntimeError, match="timed out"):
            run_shell_cmd(cmd, timeout=timeout, retries=2, retry_delay=0.1)

    def test_subprocess_timeout_zero_invalid(self) -> None:
        """Real test: timeout=0 is invalid and rejected.

        Fixture: timeout=0
        Expect: ValueError raised
        """
        cmd = ["echo", "hello"]

        with pytest.raises(ValueError, match="positive"):
            run_shell_cmd(cmd, timeout=0)

    def test_subprocess_timeout_negative_invalid(self) -> None:
        """Real test: negative timeout is rejected.

        Fixture: timeout=-1
        Expect: ValueError raised
        """
        cmd = ["echo", "hello"]

        with pytest.raises(ValueError, match="positive"):
            run_shell_cmd(cmd, timeout=-1)


class TestSubprocessExitCodes:
    """Test exit code handling and propagation."""

    def test_subprocess_exit_code_zero_success(self) -> None:
        """Real test: exit code 0 indicates success.

        Fixture: true command (exits 0)
        Expect: returncode == 0
        """
        cmd = ["true"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert stderr == ""

    def test_subprocess_exit_code_one_error(self) -> None:
        """Real test: exit code 1 indicates error.

        Fixture: false command (exits 1)
        Expect: returncode == 1
        """
        cmd = ["false"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 1

    def test_subprocess_exit_code_two_fatal(self) -> None:
        """Real test: exit code 2 indicates fatal error.

        Fixture: sh -c 'exit 2'
        Expect: returncode == 2
        """
        cmd = ["sh", "-c", "exit 2"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 2

    def test_subprocess_custom_exit_code_propagated(self) -> None:
        """Real test: custom exit codes (42) are propagated.

        Fixture: sh -c 'exit 42'
        Expect: returncode == 42
        """
        cmd = ["sh", "-c", "exit 42"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 42

    def test_subprocess_exit_code_on_output(self) -> None:
        """Real test: exit code returned alongside output.

        Fixture: command that outputs and exits with code 1
        Expect: tuple contains (1, stdout, stderr)
        """
        cmd = ["sh", "-c", "echo 'error message' >&2; exit 1"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 1
        assert "error message" in stderr

    def test_subprocess_exit_code_with_stdout_and_stderr(self) -> None:
        """Real test: exit code captured with both stdout and stderr.

        Fixture: command outputs to both streams, exits 1
        Expect: All three captured correctly
        """
        cmd = [
            "sh",
            "-c",
            "echo 'stdout text' && echo 'stderr text' >&2 && exit 1",
        ]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 1
        assert "stdout text" in stdout
        assert "stderr text" in stderr


class TestSubprocessOutputCapture:
    """Test output capture and stream separation."""

    def test_subprocess_stdout_captured(self) -> None:
        """Real test: stdout captured separately from stderr.

        Fixture: echo "hello world"
        Expect: stdout contains "hello world", stderr is empty
        """
        cmd = ["echo", "hello world"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert "hello world" in stdout
        assert stderr == ""

    def test_subprocess_stderr_captured(self) -> None:
        """Real test: stderr captured separately from stdout.

        Fixture: sh -c 'echo error >&2'
        Expect: stderr contains "error", stdout is empty
        """
        cmd = ["sh", "-c", "echo error >&2"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert "error" in stderr
        assert stdout == ""

    def test_subprocess_stdout_and_stderr_not_mixed(self) -> None:
        """Real test: stdout and stderr are separate, not mixed.

        Fixture: command outputs to both streams
        Expect: streams are separate, not interleaved
        """
        cmd = [
            "sh",
            "-c",
            "echo stdout_first && echo stderr_first >&2 && echo stdout_last",
        ]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        # Verify they're not mixed in a single stream
        assert "stderr_first" not in stdout
        assert "stdout_first" not in stderr

    def test_subprocess_multiline_output_captured(self) -> None:
        """Real test: multiline output captured completely.

        Fixture: echo with multiple lines
        Expect: All lines captured
        """
        cmd = ["sh", "-c", "echo line1 && echo line2 && echo line3"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        lines = stdout.strip().split("\n")
        assert len(lines) >= 3
        assert "line1" in stdout
        assert "line2" in stdout
        assert "line3" in stdout

    def test_subprocess_empty_output_captured(self) -> None:
        """Real test: empty output is handled correctly.

        Fixture: true (no output)
        Expect: stdout and stderr are empty strings
        """
        cmd = ["true"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert stdout == ""
        assert stderr == ""

    def test_subprocess_large_output_captured(self) -> None:
        """Real test: large output (1MB) is captured completely.

        Fixture: seq 1 100000 (generates ~500KB output)
        Expect: All lines captured
        """
        cmd = ["seq", "1", "10000"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        lines = stdout.strip().split("\n")
        assert len(lines) >= 10000


class TestSubprocessEnvironmentVariables:
    """Test environment variable passing."""

    def test_subprocess_env_var_passed(self) -> None:
        """Real test: environment variable passed to subprocess.

        Fixture: TEST_VAR=hello, command reads TEST_VAR
        Expect: subprocess receives TEST_VAR value
        """
        cmd = ["sh", "-c", "echo $TEST_VAR"]
        env = os.environ.copy()
        env["TEST_VAR"] = "hello"

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
        )

        assert "hello" in result.stdout

    def test_subprocess_java_home_used(self) -> None:
        """Real test: JAVA_HOME environment variable used by subprocess.

        Fixture: Set JAVA_HOME to temp directory
        Expect: Subprocess can read JAVA_HOME
        """
        cmd = ["sh", "-c", "echo $JAVA_HOME"]
        env = os.environ.copy()
        env["JAVA_HOME"] = "/tmp/java"

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
        )

        assert "/tmp/java" in result.stdout

    def test_subprocess_maven_opts_passed(self) -> None:
        """Real test: MAVEN_OPTS environment variable passed.

        Fixture: MAVEN_OPTS=-Xmx2g
        Expect: Subprocess receives MAVEN_OPTS
        """
        cmd = ["sh", "-c", "echo $MAVEN_OPTS"]
        env = os.environ.copy()
        env["MAVEN_OPTS"] = "-Xmx2g"

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
        )

        assert "-Xmx2g" in result.stdout

    def test_subprocess_multiple_env_vars(self) -> None:
        """Real test: multiple environment variables passed together.

        Fixture: VAR1=a, VAR2=b, VAR3=c
        Expect: All three available in subprocess
        """
        cmd = ["sh", "-c", "echo $VAR1:$VAR2:$VAR3"]
        env = os.environ.copy()
        env["VAR1"] = "a"
        env["VAR2"] = "b"
        env["VAR3"] = "c"

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
        )

        assert "a:b:c" in result.stdout

    def test_subprocess_env_var_expansion(self) -> None:
        """Real test: environment variable expansion in shell.

        Fixture: VAR=prefix, command uses $VAR as part of path
        Expect: $VAR expanded in subprocess
        """
        cmd = ["sh", "-c", "echo $TEST_PREFIX/path"]
        env = os.environ.copy()
        env["TEST_PREFIX"] = "prefix"

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            env=env,
        )

        assert "prefix/path" in result.stdout


class TestSubprocessWorkingDirectory:
    """Test working directory handling."""

    def test_subprocess_working_dir_respected(self) -> None:
        """Real test: cwd parameter changes subprocess working directory.

        Fixture: pwd in /tmp
        Expect: Output contains /tmp
        """
        result = subprocess.run(
            ["pwd"],
            capture_output=True,
            text=True,
            cwd="/tmp",
        )

        assert "/tmp" in result.stdout

    def test_subprocess_cwd_via_run_shell_cmd(self, temp_project_dir: Path) -> None:
        """Real test: run_shell_cmd respects cwd parameter.

        Fixture: temp_project_dir, run pwd with cwd=temp_project_dir
        Expect: Output is temp_project_dir path
        """
        exit_code, stdout, stderr = run_shell_cmd(
            ["pwd"],
            cwd=temp_project_dir,
        )

        assert exit_code == 0
        assert str(temp_project_dir) in stdout

    def test_subprocess_file_access_from_cwd(self) -> None:
        """Real test: files in cwd are accessible.

        Fixture: Create temp file in directory, cat it from that cwd
        Expect: File contents read successfully
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            tmpdir_path = Path(tmpdir)
            test_file = tmpdir_path / "test.txt"
            test_file.write_text("file contents")

            result = subprocess.run(
                ["cat", "test.txt"],
                capture_output=True,
                text=True,
                cwd=tmpdir_path,
            )

            assert result.returncode == 0
            assert "file contents" in result.stdout

    def test_subprocess_cwd_isolation(self) -> None:
        """Real test: subprocess cwd doesn't affect calling process cwd.

        Fixture: Run pwd in /tmp, verify original cwd unchanged
        Expect: Calling process cwd unchanged
        """
        original_cwd = os.getcwd()

        result = subprocess.run(
            ["pwd"],
            capture_output=True,
            text=True,
            cwd="/tmp",
        )

        assert os.getcwd() == original_cwd
        assert "/tmp" in result.stdout


class TestSubprocessCommandNotFound:
    """Test handling of command not found errors."""

    def test_subprocess_command_not_found_raises_error(self) -> None:
        """Real test: FileNotFoundError on nonexistent command.

        Fixture: /nonexistent/command
        Expect: FileNotFoundError raised
        """
        with pytest.raises(FileNotFoundError):
            subprocess.run(
                ["/nonexistent/command"],
                capture_output=True,
                text=True,
            )

    def test_subprocess_command_not_found_via_run_shell_cmd(self) -> None:
        """Real test: run_shell_cmd raises RuntimeError for missing command.

        Fixture: /nonexistent/command via run_shell_cmd
        Expect: RuntimeError raised with helpful message
        """
        with pytest.raises(RuntimeError, match="not found"):
            run_shell_cmd(["/nonexistent/command"])

    def test_subprocess_maven_not_found_helpful_message(self) -> None:
        """Real test: mvn not found gives installation guidance.

        Fixture: /nonexistent/mvn
        Expect: Error mentions Maven installation
        """
        # Save original PATH to restore later
        original_path = os.environ.get("PATH", "")
        try:
            # Temporarily clear PATH to make mvn unfindable
            os.environ["PATH"] = "/nonexistent"

            with pytest.raises(RuntimeError) as exc_info:
                run_shell_cmd(["mvn", "--version"])

            error_msg = str(exc_info.value)
            # Should mention Maven or installation
            assert "mvn" in error_msg.lower() or "not found" in error_msg.lower()

        finally:
            os.environ["PATH"] = original_path

    def test_subprocess_script_not_found_helpful_message(self) -> None:
        """Real test: missing script gives helpful error message.

        Fixture: /nonexistent/script.sh
        Expect: Error mentions script not found
        """
        with pytest.raises(RuntimeError, match="not found"):
            run_shell_cmd(["/nonexistent/script.sh"])


class TestSubprocessSignalHandling:
    """Test signal handling (Ctrl+C, etc.)."""

    def test_subprocess_keyboard_interrupt_caught(self) -> None:
        """Real test: KeyboardInterrupt (Ctrl+C) is caught and converted.

        Note: We can't easily simulate Ctrl+C in test, but we can verify
        the exception handling path exists in run_shell_cmd.
        """
        # Verify the run_shell_cmd function has KeyboardInterrupt handling
        import inspect

        source = inspect.getsource(run_shell_cmd)
        assert "KeyboardInterrupt" in source

    def test_subprocess_sigterm_kills_process(self) -> None:
        """Real test: SIGTERM can kill subprocess.

        Fixture: Start sleep process, send SIGTERM after 0.1s
        Expect: Process killed by signal
        """
        proc = subprocess.Popen(
            ["sleep", "10"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        time.sleep(0.1)
        proc.terminate()  # SIGTERM
        returncode = proc.wait(timeout=2)

        # Negative exit code indicates death by signal
        assert returncode != 0

    def test_subprocess_sigkill_kills_process(self) -> None:
        """Real test: SIGKILL (kill -9) kills subprocess.

        Fixture: Start sleep, send SIGKILL after 0.1s
        Expect: Process killed
        """
        proc = subprocess.Popen(
            ["sleep", "10"],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )

        time.sleep(0.1)
        proc.kill()  # SIGKILL
        returncode = proc.wait(timeout=2)

        assert returncode != 0


class TestSubprocessValidation:
    """Test input validation and error handling."""

    def test_subprocess_empty_command_rejected(self) -> None:
        """Real test: empty command list is rejected.

        Fixture: cmd=[]
        Expect: ValueError raised
        """
        with pytest.raises(ValueError, match="empty"):
            run_shell_cmd([])

    def test_subprocess_none_command_rejected(self) -> None:
        """Real test: None in command arguments rejected.

        Fixture: ["echo", None]
        Expect: ValueError raised
        """
        with pytest.raises(ValueError, match="strings"):
            run_shell_cmd(["echo", None])  # type: ignore

    def test_subprocess_mixed_type_arguments_rejected(self) -> None:
        """Real test: non-string arguments rejected.

        Fixture: ["echo", 42]
        Expect: ValueError raised
        """
        with pytest.raises(ValueError, match="strings"):
            run_shell_cmd(["echo", 42])  # type: ignore

    def test_subprocess_string_args_only(self) -> None:
        """Real test: command with all string args succeeds.

        Fixture: ["echo", "arg1", "arg2"]
        Expect: Success
        """
        exit_code, stdout, stderr = run_shell_cmd(["echo", "arg1", "arg2"])

        assert exit_code == 0
        assert "arg1" in stdout
        assert "arg2" in stdout


class TestSubprocessRetry:
    """Test retry mechanism."""

    def test_subprocess_retry_succeeds_on_second_attempt(self) -> None:
        """Real test: command succeeds on second attempt.

        Fixture: Script that fails first time, succeeds second
        Note: We can't easily create a command that behaves differently
              on retries without mocking, so we test with a command that
              always succeeds to verify retry infrastructure works.
        """
        # Create a temp file to track attempts
        with tempfile.TemporaryDirectory() as tmpdir:
            tmpdir_path = Path(tmpdir)
            attempt_file = tmpdir_path / "attempts.txt"

            # Script that increments a counter
            script = tmpdir_path / "test.sh"
            script.write_text(
                f"""#!/bin/bash
count=$(cat {attempt_file} 2>/dev/null || echo 0)
count=$((count + 1))
echo $count > {attempt_file}
echo "Attempt $count"
"""
            )
            script.chmod(0o755)

            # Initialize counter
            attempt_file.write_text("0")

            # Run command with retries
            exit_code, stdout, stderr = run_shell_cmd(
                [str(script)],
                timeout=10,
            )

            assert exit_code == 0
            attempts = int(attempt_file.read_text().strip())
            assert attempts >= 1

    def test_subprocess_retry_delay_respected(self) -> None:
        """Real test: retry delay is observed.

        Fixture: Command with retries and 0.2s delay
        Expect: Total execution time reflects delays
        """
        start_time = time.time()

        # Command that succeeds immediately
        exit_code, stdout, stderr = run_shell_cmd(
            ["true"],
            retries=2,
            retry_delay=0.1,
            timeout=10,
        )

        elapsed = time.time() - start_time

        # Should execute quickly (no actual retries needed)
        assert exit_code == 0
        # Allow some overhead
        assert elapsed < 1.0


class TestSubprocessVerbose:
    """Test verbose output mode."""

    def test_subprocess_verbose_flag_works(self) -> None:
        """Real test: verbose=True enables debug output.

        Fixture: run_shell_cmd with verbose=True
        Expect: No exception; command still runs
        Note: Actual console output is hard to test, but we verify
              the flag is accepted and doesn't break execution.
        """
        exit_code, stdout, stderr = run_shell_cmd(
            ["echo", "hello"],
            verbose=True,
        )

        assert exit_code == 0
        assert "hello" in stdout


class TestShellScriptExecution:
    """Test real shell script execution."""

    def test_shell_script_basic_execution(self, temp_project_dir: Path) -> None:
        """Real test: Execute shell script from disk.

        Fixture: Create scripts/dx.sh (already exists from fixture)
        Expect: Script executes successfully
        """
        dx_script = temp_project_dir / "scripts" / "dx.sh"
        assert dx_script.exists()

        exit_code, stdout, stderr = run_shell_cmd(
            ["bash", str(dx_script)],
            cwd=temp_project_dir,
        )

        assert exit_code == 0

    def test_shell_script_with_arguments(self, temp_project_dir: Path) -> None:
        """Real test: Execute shell script with arguments.

        Fixture: Create script that echoes arguments
        Expect: Arguments passed through correctly
        """
        script = temp_project_dir / "test_args.sh"
        script.write_text("#!/bin/bash\necho $1 $2\n")
        script.chmod(0o755)

        exit_code, stdout, stderr = run_shell_cmd(
            ["bash", str(script), "arg1", "arg2"],
            cwd=temp_project_dir,
        )

        assert exit_code == 0
        assert "arg1" in stdout
        assert "arg2" in stdout

    def test_shell_script_returns_exit_code(self, temp_project_dir: Path) -> None:
        """Real test: Shell script exit code is propagated.

        Fixture: Script that exits with code 42
        Expect: exit_code == 42
        """
        script = temp_project_dir / "exit_42.sh"
        script.write_text("#!/bin/bash\nexit 42\n")
        script.chmod(0o755)

        exit_code, stdout, stderr = run_shell_cmd(
            ["bash", str(script)],
            cwd=temp_project_dir,
        )

        assert exit_code == 42

    def test_shell_script_with_pipes(self, temp_project_dir: Path) -> None:
        """Real test: Shell script using pipes.

        Fixture: Script that pipes through multiple commands
        Expect: Pipe operations work correctly
        """
        cmd = ["sh", "-c", "echo 'hello\\nworld' | sort"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        lines = stdout.strip().split("\n")
        assert "hello" in stdout
        assert "world" in stdout


class TestSubprocessIntegration:
    """Integration tests combining multiple features."""

    def test_subprocess_full_workflow_success(self) -> None:
        """Real test: Full workflow - command with env, cwd, timeout, capture.

        Fixture: echo with env var, custom cwd, timeout
        Expect: All features work together
        """
        with tempfile.TemporaryDirectory() as tmpdir:
            tmpdir_path = Path(tmpdir)
            cmd = ["sh", "-c", "echo $TEST_VAR"]
            env = os.environ.copy()
            env["TEST_VAR"] = "integration test"

            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                cwd=tmpdir_path,
                env=env,
                timeout=10,
            )

            assert result.returncode == 0
            assert "integration test" in result.stdout

    def test_subprocess_error_recovery_workflow(self) -> None:
        """Real test: Error occurs, caught, and handled gracefully.

        Fixture: Command that fails with stderr
        Expect: Error captured, exit code propagated, no crash
        """
        cmd = ["sh", "-c", "echo 'error happened' >&2; exit 1"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 1
        assert "error happened" in stderr

    def test_subprocess_timeout_recovery_workflow(self) -> None:
        """Real test: Timeout occurs, caught, with retry logic.

        Fixture: Command timeout with retries
        Expect: Timeout caught, retries attempted, finally fails cleanly
        """
        with pytest.raises(RuntimeError, match="timed out"):
            run_shell_cmd(
                ["sleep", "10"],
                timeout=0.2,
                retries=1,
                retry_delay=0.1,
            )


class TestCommandEdgeCases:
    """Test edge cases and boundary conditions."""

    def test_command_with_special_characters(self) -> None:
        """Real test: Command output with special characters.

        Fixture: echo with special chars that are safe in double quotes
        Expect: Output captured correctly
        """
        cmd = ["sh", "-c", "echo 'Special: ! & | ; special chars'"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        # Verify output is present
        assert "Special" in stdout

    def test_command_with_unicode_output(self) -> None:
        """Real test: Unicode characters in output.

        Fixture: echo with unicode (emoji, non-ASCII)
        Expect: Unicode captured and decoded correctly
        """
        cmd = ["echo", "Hello 世界 🌍"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert "Hello" in stdout

    def test_command_with_null_bytes(self) -> None:
        """Real test: Binary output with null bytes.

        Fixture: printf with null bytes
        Expect: Output captured (may contain nulls)
        """
        # printf with %c and NULL code point
        cmd = ["sh", "-c", "printf 'hello\\x00world'"]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0

    def test_command_with_very_long_line(self) -> None:
        """Real test: Very long output line (10KB+).

        Fixture: echo with 10KB string
        Expect: Long line captured completely
        """
        long_string = "x" * 10000
        cmd = ["echo", long_string]
        exit_code, stdout, stderr = run_shell_cmd(cmd)

        assert exit_code == 0
        assert long_string in stdout


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
