"""Exercise real Git merges, policy resolution and candidate publication."""
import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("sync_upstream", ROOT / "tools/sync_upstream.py")
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
POLICY = json.loads((ROOT / ".github/upstream-sync-policy.json").read_text())


class SyncTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / "work"
        self.repo.mkdir()
        self.git("init", "-b", "main")
        self.git("config", "user.name", "Sync test")
        self.git("config", "user.email", "sync@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        self.git("config", "core.autocrlf", "false")
        self.write("source.c", "".join(f"line {i}\n" for i in range(100)))
        self.write("README.md", "upstream readme\n")
        self.write(".github/workflows/build.yml", "old build\n")
        self.write("port/windows/deleted.c", "old desktop code\n")
        self.git("add", ".")
        self.git("commit", "-m", "base")
        self.base = self.head()
        self.git("branch", "upstream")

    def git(self, *args):
        return sync.git(self.repo, *args).stdout.strip()

    def head(self):
        return self.git("rev-parse", "HEAD")

    def write(self, path, text):
        target = self.repo / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8", newline="\n")

    def commit(self, text):
        self.git("add", "-A")
        self.git("commit", "-m", text)
        return self.head()

    def upstream_edit(self, path, text):
        self.git("checkout", "upstream")
        self.write(path, text)
        tip = self.commit("upstream change")
        self.git("checkout", "main")
        return tip

    def test_no_change(self):
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertFalse(report["changed"])
        self.assertEqual(self.head(), self.base)

    def test_merge_keeps_both_source_changes_and_main_unchanged(self):
        text = (self.repo / "source.c").read_text()
        self.write("source.c", text.replace("line 5\n", "touch controls\n"))
        fork = self.commit("fork feature")
        tip = self.upstream_edit("source.c", text.replace("line 80\n", "upstream fix\n"))
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertTrue(report["changed"])
        self.assertIn("touch controls", (self.repo / "source.c").read_text())
        self.assertIn("upstream fix", (self.repo / "source.c").read_text())
        self.assertEqual(self.git("rev-parse", "main"), fork)
        self.assertEqual(self.git("show", "-s", "--format=%P", report["candidate"]), fork+" "+tip)
        self.assertEqual(self.git("tag"), "")

    def test_protected_workflows_and_deleted_platforms(self):
        self.write("README.md", "FulGer Android\n")
        self.write(".github/workflows/build.yml", "manual releases only\n")
        self.git("rm", "port/windows/deleted.c")
        self.commit("Android-only fork")
        self.git("checkout", "upstream")
        self.write("README.md", "new upstream documentation\n")
        self.write(".github/workflows/build.yml", "automatic upstream releases\n")
        self.write(".github/workflows/extra.yml", "extra upstream workflow\n")
        self.write("port/windows/deleted.c", "new desktop code\n")
        self.write("port/windows/added.c", "new desktop file\n")
        self.commit("upstream changes")
        self.git("checkout", "main")
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertTrue(report["changed"])
        self.assertEqual((self.repo / "README.md").read_text(), "FulGer Android\n")
        self.assertEqual((self.repo / ".github/workflows/build.yml").read_text(), "manual releases only\n")
        self.assertFalse((self.repo / ".github/workflows/extra.yml").exists())
        self.assertFalse((self.repo / "port/windows/added.c").exists())
        self.assertFalse((self.repo / "port/windows/deleted.c").exists())

    def test_real_source_conflict_aborts_without_advancing_main(self):
        self.write("source.c", "fork controls\n")
        fork = self.commit("fork feature")
        self.upstream_edit("source.c", "different engine\n")
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertEqual(report["conflicts"], ["source.c"])
        self.assertFalse(report["changed"])
        self.assertEqual(self.head(), fork)
        self.assertEqual(self.git("rev-parse", "main"), fork)
        self.assertEqual(self.git("status", "--porcelain"), "")

    def test_dirty_checkout_rejected(self):
        self.write("source.c", "unsaved work\n")
        with self.assertRaises(RuntimeError):
            sync.prepare(self.repo, "upstream", POLICY)
        self.assertEqual((self.repo / "source.c").read_text(), "unsaved work\n")

    def test_directory_rename_carries_new_shared_files(self):
        self.write("port/linux/src/a.c", "a\n")
        self.write("port/linux/src/b.c", "b\n")
        self.commit("common Linux layer")
        self.git("branch", "-f", "upstream", "HEAD")
        self.git("mv", "port/linux", "port/shared")
        self.commit("rename shared layer")
        self.git("checkout", "upstream")
        self.write("port/linux/src/new.c", "shared improvement\n")
        self.commit("upstream adds file")
        self.git("checkout", "main")
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertTrue(report["changed"])
        self.assertEqual((self.repo / "port/shared/src/new.c").read_text(), "shared improvement\n")
        self.assertFalse((self.repo / "port/linux/src/new.c").exists())

    def test_path_move_conflict_keeps_new_upstream_text(self):
        self.write("relocated.c", "// port/linux/foo.c old HUD\n")
        self.commit("shared base")
        self.git("branch", "-f", "upstream", "HEAD")
        self.write("relocated.c", "// port/shared/foo.c old HUD\n")
        self.commit("Android path move")
        self.upstream_edit("relocated.c", "// port/linux/foo.c new HUD\n")
        report = sync.prepare(self.repo, "upstream", POLICY)
        self.assertTrue(report["changed"])
        self.assertEqual(report["relocated"], ["relocated.c"])
        self.assertEqual((self.repo/"relocated.c").read_text(), "// port/shared/foo.c new HUD\n")

    def test_failed_candidate_reused_without_force_push(self):
        origin = Path(self.temp.name)/"origin.git"
        subprocess.run(["git", "init", "--bare", str(origin)], check=True, capture_output=True)
        self.git("remote", "add", "origin", str(origin))
        self.upstream_edit("new.c", "new code\n")
        report = sync.prepare(self.repo, "upstream", POLICY)
        sync.publish(self.repo, report)
        existing = report["candidate"]
        self.git("checkout", "main")
        retry = sync.prepare(self.repo, "upstream", POLICY)
        sync.publish(self.repo, retry)
        self.assertEqual(retry["candidate"], existing)
        self.assertEqual(self.git("tag"), "")


if __name__ == "__main__":
    unittest.main()
