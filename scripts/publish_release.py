#!/usr/bin/env python3
"""Publish verified files only from a manual Release run on main. Existing tags are never moved."""
import json
import os
from pathlib import Path
import re
import subprocess

from package_artifacts import ROOT, git, sha256

def main():
    if os.environ.get('GITHUB_EVENT_NAME') != 'workflow_dispatch' or os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise SystemExit('Release publication requires a manual workflow on main')
    repository = os.environ.get('GITHUB_REPOSITORY', '')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise SystemExit('Missing valid repository identity')
    folder = ROOT / 'dist/release'
    info = json.loads((folder / 'BUILD-INFO.json').read_text(encoding='utf-8'))
    if info['kind'] != 'release' or info['commit'] != git('rev-parse', 'HEAD') or info['commit'] != os.environ.get('GITHUB_SHA'):
        raise SystemExit('Release files do not match the manually selected commit')
    version = info['version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?', version):
        raise SystemExit('Invalid release version')
    tag = 'v' + version
    refs = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repository}/git/matching-refs/tags/{tag}'], text=True, encoding='utf-8'))
    if any(ref['ref'] == 'refs/tags/' + tag for ref in refs):
        raise SystemExit(f'{tag} already exists; update the version before publishing again')
    expected = {f'NinebotEnhance-{version}.apk', f'NinebotEnhance-{version}-source.zip', 'BUILD-INFO.json'}
    lines = (folder / 'SHA256SUMS.txt').read_text().splitlines()
    files = {}
    for line in lines:
        digest, name = line.split('  ', 1)
        if name not in expected or sha256(folder / name) != digest:
            raise SystemExit('Release checksum mismatch')
        files[name] = folder / name
    if set(files) != expected:
        raise SystemExit('Incomplete release files')
    notes = ROOT / 'build/release-notes.md'
    notes.write_text(f'Ninebot Enhance {version}\n\n安装包、对应源码和 SHA-256 校验值见附件。\n\n'
                     f'构建提交：`{info["commit"]}`\n\n通过 {info["host_assertions"]} 项主机断言；主机检查不代替设备实测。\n', encoding='utf-8')
    command = ['gh', 'release', 'create', tag, '--repo', repository, '--target', info['commit'], '--title', f'Ninebot Enhance {version}', '--notes-file', str(notes)]
    if os.environ.get('PRERELEASE') == 'true':
        command.append('--prerelease')
    command.extend(str(files[name]) for name in sorted(files))
    command.append(str(folder / 'SHA256SUMS.txt'))
    subprocess.run(command, cwd=ROOT, check=True)

if __name__ == '__main__':
    main()
