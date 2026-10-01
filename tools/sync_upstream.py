#!/usr/bin/env python3
"""Prepare a tested upstream merge without advancing the fork's main branch."""
import argparse
import json
import subprocess
import tempfile
from pathlib import Path


def git(repo, *args, check=True):
    return subprocess.run(["git", "-C", str(repo), *args], check=check,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")


def matches(path, rules):
    return any(path.startswith(rule) if rule.endswith("/") else path == rule for rule in rules)


def resolve_relocations(repo, paths, replacements):
    """Normalize known Android-only path moves, then repeat an ordinary 3-way merge."""
    resolved = []
    for path in paths:
        if Path(path).suffix not in (".c", ".h", ".py", ".md", ".json"):
            continue
        stages = [git(repo, "show", f":{stage}:{path}", check=False) for stage in (1, 2, 3)]
        if any(stage.returncode for stage in stages):
            continue
        base, ours, theirs = [stage.stdout for stage in stages]
        old_base, old_theirs = base, theirs
        for old, new in replacements.items():
            base = base.replace(old, new)
            theirs = theirs.replace(old, new)
        if (base, theirs) == (old_base, old_theirs):
            continue
        with tempfile.TemporaryDirectory() as directory:
            files = [Path(directory)/name for name in ("ours", "base", "theirs")]
            for file, text in zip(files, (ours, base, theirs)):
                file.write_text(text, encoding="utf-8", newline="\n")
            merged = git(repo, "merge-file", "--stdout", *map(str, files), check=False)
            if merged.returncode == 0:
                (Path(repo)/path).write_text(merged.stdout, encoding="utf-8", newline="\n")
                git(repo, "add", "--", path)
                resolved.append(path)
    return resolved


def prepare(repo, upstream, policy):
    repo = Path(repo).resolve()
    if git(repo, "status", "--porcelain", "--untracked-files=normal").stdout.strip():
        raise RuntimeError("Sync requires a clean checkout")
    base = git(repo, "rev-parse", "HEAD").stdout.strip()
    tip = git(repo, "rev-parse", upstream+"^{commit}").stdout.strip()
    report = {"base": base, "upstream": tip, "changed": False, "conflicts": []}
    if git(repo, "merge-base", "--is-ancestor", tip, base, check=False).returncode == 0:
        return report
    git(repo, "merge-base", base, tip)  # Reject unrelated history.
    git(repo, "checkout", "--detach", base)
    result = git(repo, "-c", "merge.directoryRenames=true", "merge", "--no-ff", "--no-commit", tip, check=False)
    # Real merge failures (not conflicts) must never be turned into a candidate.
    merge_head = git(repo, "rev-parse", "--verify", "MERGE_HEAD", check=False)
    if merge_head.returncode:
        raise RuntimeError(result.stderr or "Upstream merge did not start")
    original = set(git(repo, "ls-tree", "-r", "--name-only", base).stdout.splitlines())
    paths = set(git(repo, "ls-files").stdout.splitlines()) | original
    for path in sorted(paths):
        if matches(path, policy["removed"]):
            git(repo, "rm", "-f", "--ignore-unmatch", "--", path)
        elif matches(path, policy["protected"]):
            if path in original:
                git(repo, "restore", "--source="+base, "--staged", "--worktree", "--", path)
            else:
                git(repo, "rm", "-f", "--ignore-unmatch", "--", path)
    conflicts = sorted(set(git(repo, "diff", "--name-only", "--diff-filter=U").stdout.splitlines()))
    report["relocated"] = resolve_relocations(repo, conflicts, policy.get("text_relocations", {}))
    report["conflicts"] = sorted(set(git(repo, "diff", "--name-only", "--diff-filter=U").stdout.splitlines()))
    if report["conflicts"]:
        git(repo, "merge", "--abort")
        return report
    git(repo, "diff", "--cached", "--check")
    git(repo, "commit", "-m", "Sync cybersecurity/halo-ce-universal at "+tip[:12])
    report["changed"] = True
    report["candidate"] = git(repo, "rev-parse", "HEAD").stdout.strip()
    report["branch"] = "codex/upstream-sync-"+tip[:12]+"-"+base[:12]
    return report


def publish(repo, report):
    """Reuse identical candidates after failed builds; never force-update refs."""
    branch = report["branch"]
    ref = "refs/heads/"+branch
    found = git(repo, "ls-remote", "origin", ref).stdout.strip()
    if found:
        git(repo, "fetch", "--no-tags", "origin", ref)
        existing = git(repo, "rev-parse", "FETCH_HEAD").stdout.strip()
        candidate = report["candidate"]
        for spec in ("%T", "%P"):
            if git(repo, "show", "-s", "--format="+spec, existing).stdout != git(repo, "show", "-s", "--format="+spec, candidate).stdout:
                raise RuntimeError("Existing sync candidate differs; refusing to overwrite it")
        report["candidate"] = existing
    else:
        git(repo, "push", "origin", report["candidate"]+":"+ref)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", default=".")
    parser.add_argument("--upstream", default="refs/remotes/upstream/main")
    parser.add_argument("--policy", default=".github/upstream-sync-policy.json")
    parser.add_argument("--report", required=True)
    parser.add_argument("--publish", action="store_true")
    args = parser.parse_args()
    policy = json.loads(Path(args.policy).read_text(encoding="utf-8"))
    report = prepare(args.repo, args.upstream, policy)
    Path(args.report).write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    if report["conflicts"]:
        raise SystemExit("Upstream conflicts require resolution: "+", ".join(report["conflicts"]))
    if args.publish and report["changed"]:
        publish(args.repo, report)
        Path(args.report).write_text(json.dumps(report, indent=2)+"\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
