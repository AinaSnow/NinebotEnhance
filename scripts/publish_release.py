#!/usr/bin/env python3
"""Publish verified files from push/manual runs on main. Published versions are never replaced."""
import json
import os
import re
import subprocess

from package_artifacts import ROOT, git, sha256

def main():
    if os.environ.get('GITHUB_EVENT_NAME') not in ('push', 'workflow_dispatch') or os.environ.get('GITHUB_REF') != 'refs/heads/main':
        raise SystemExit('Release publication requires a push or manual workflow on main')
    repository = os.environ.get('GITHUB_REPOSITORY', '')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise SystemExit('Missing valid repository identity')
    folder = ROOT / 'dist/release'
    info = json.loads((folder / 'BUILD-INFO.json').read_text(encoding='utf-8'))
    if info['kind'] != 'release' or info['commit'] != git('rev-parse', 'HEAD') or info['commit'] != os.environ.get('GITHUB_SHA'):
        raise SystemExit('Release files do not match the workflow commit')
    version = info['version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?', version):
        raise SystemExit('Invalid release version')
    tag = 'v' + version
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
    refs = json.loads(subprocess.check_output(['gh', 'api', f'repos/{repository}/git/matching-refs/tags/{tag}'], text=True, encoding='utf-8'))
    if any(ref['ref'] == 'refs/tags/' + tag for ref in refs):
        published = json.loads(subprocess.check_output(['gh', 'release', 'view', tag, '--repo', repository, '--json', 'isDraft,assets,url'], text=True, encoding='utf-8'))
        assets = {asset['name'] for asset in published['assets'] if asset['size'] > 0}
        if published['isDraft'] or not (expected | {'SHA256SUMS.txt'}) <= assets:
            raise SystemExit(f'{tag} exists without a complete published release; inspect it before retrying')
        message = f'{tag} is already published; keeping the existing release. Update version.properties and Protocol.java to publish a new version. {published["url"]}'
        print(message)
        summary = os.environ.get('GITHUB_STEP_SUMMARY')
        if summary:
            with open(summary, 'a', encoding='utf-8') as output:
                output.write('\n' + message + '\n')
        return
    notes = ROOT / 'build/release-notes.md'
    notes.write_text(f'Ninebot Enhance {version}\n\n安装包、对应源码和 SHA-256 校验值见附件。\n\n'
                     f'构建提交：`{info["commit"]}`\n\n通过 {info["host_assertions"]} 项主机断言；主机检查不代替设备实测。\n', encoding='utf-8')
    command = ['gh', 'release', 'create', tag, '--repo', repository, '--target', info['commit'], '--title', f'Ninebot Enhance {version}', '--notes-file', str(notes)]
    if '-' in version:
        command.append('--prerelease')
    command.extend(str(files[name]) for name in sorted(files))
    command.append(str(folder / 'SHA256SUMS.txt'))
    subprocess.run(command, cwd=ROOT, check=True)

if __name__ == '__main__':
    main()
