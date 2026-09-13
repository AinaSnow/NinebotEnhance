#!/usr/bin/env python3
"""Package verified build output; never include signing files or raw build command logs."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def git(*args):
    return subprocess.check_output(['git', *args], cwd=ROOT, text=True, encoding='utf-8').strip()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--kind', choices=['ci', 'release'], required=True)
    args = parser.parse_args()
    meta = json.loads((ROOT / 'dist/artifact-checks.json').read_text(encoding='utf-8'))
    version = meta['version']
    if not re.fullmatch(r'[0-9]+\.[0-9]+\.[0-9]+(?:-[a-zA-Z0-9.-]+)?', version):
        raise SystemExit('Invalid release version')
    if git('status', '--porcelain', '--untracked-files=no'):
        raise SystemExit('Tracked files differ from HEAD; refusing to archive mismatched source')
    commit = git('rev-parse', 'HEAD')
    apk = ROOT / 'dist' / f'NinebotEnhance-{version}.apk'
    if meta['apk'] != apk.name or sha256(apk) != meta['sha256']:
        raise SystemExit('APK differs from the verified build')
    if args.kind == 'release' and meta['signing_certificate_sha256'] != (ROOT / 'release-signing-certificate.txt').read_text().strip():
        raise SystemExit('Release certificate differs from the pinned certificate')
    folder = ROOT / 'dist' / args.kind
    folder.mkdir(exist_ok=True)
    if any(folder.iterdir()):
        raise SystemExit(f'{folder} must be empty to avoid mixing builds')
    packaged = folder / (f'NinebotEnhance-{version}-ci.apk' if args.kind == 'ci' else apk.name)
    shutil.copyfile(apk, packaged)
    if args.kind == 'release':
        source = folder / f'NinebotEnhance-{version}-source.zip'
        subprocess.run(['git', 'archive', '--format=zip', '--prefix=NinebotEnhance/', '--output=' + str(source), 'HEAD'], cwd=ROOT, check=True)
        tracked = set(git('ls-tree', '-r', '--name-only', 'HEAD').splitlines())
        with zipfile.ZipFile(source) as archive:
            included = {name.removeprefix('NinebotEnhance/') for name in archive.namelist() if not name.endswith('/')}
            if included != tracked:
                raise SystemExit('Source archive differs from the Git tree')
            for name in included:
                if set(Path(name).parts) & {'signing', 'build', 'dist', '.git', '.gradle', '.lody', 'archive'} or name.lower().endswith(('.jks', '.keystore', '.p12', 'password.txt', 'local.properties')):
                    raise SystemExit('Private or generated file found in source archive: ' + name)
    info = {key: meta[key] for key in ('version', 'version_code', 'package', 'host_assertions', 'signing_certificate_sha256')}
    info.update({'commit': commit, 'kind': args.kind, 'android_runtime_tested': False,
                 'files': {path.name: sha256(path) for path in sorted(folder.iterdir())}})
    (folder / 'BUILD-INFO.json').write_text(json.dumps(info, indent=2) + '\n', encoding='utf-8')
    hashes = ''.join(f'{sha256(path)}  {path.name}\n' for path in sorted(folder.iterdir()))
    (folder / 'SHA256SUMS.txt').write_text(hashes, encoding='utf-8')
    print(f'Packaged {args.kind} {version}, {info["host_assertions"]} assertions, commit {commit}')
    summary = os.environ.get('GITHUB_STEP_SUMMARY')
    if summary:
        with open(summary, 'a', encoding='utf-8') as output:
            output.write(f'## Ninebot Enhance {version}\n\n- Commit: `{commit}`\n- Host assertions: {info["host_assertions"]}\n')
            output.write('- CI APK uses a temporary test certificate and cannot update an official installation.\n' if args.kind == 'ci'
                         else '- APK certificate matches the pinned release certificate. New versions are published automatically from main.\n')

if __name__ == '__main__':
    main()
