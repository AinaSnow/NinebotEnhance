#!/usr/bin/env python3
"""Create a local release key once. Private material stays under the Git-ignored signing/ directory."""
from pathlib import Path
import argparse,hashlib,os,secrets,subprocess

ROOT=Path(__file__).resolve().parents[1]
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--jdk',required=True)
    parser.add_argument('--output-dir',type=Path,default=ROOT/'signing',help='Use a temporary directory for CI signing without touching the local release key')
    args=parser.parse_args()
    folder=args.output_dir;folder.mkdir(parents=True,exist_ok=True)
    key=folder/'release.jks';password=folder/'password.txt';certificate=folder/'certificate.der'
    if key.exists() or password.exists():raise SystemExit('Signing material already exists; refusing to overwrite it.')
    password.write_text(secrets.token_urlsafe(40)+'\n',encoding='utf-8');os.chmod(password,0o600)
    keytool=Path(args.jdk)/'bin'/('keytool.exe' if os.name=='nt' else 'keytool')
    subprocess.run([str(keytool),'-genkeypair','-keystore',str(key),'-storepass:file',str(password),'-keypass:file',str(password),'-alias','ninebot-enhance',
        '-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=Ninebot Enhance','-noprompt'],check=True)
    subprocess.run([str(keytool),'-exportcert','-keystore',str(key),'-storepass:file',str(password),'-alias','ninebot-enhance','-file',str(certificate)],check=True)
    digest=hashlib.sha256(certificate.read_bytes()).hexdigest();pin=ROOT/'release-signing-certificate.txt'
    if not pin.exists():pin.write_text(digest+'\n',encoding='utf-8')
    print('Created local release key. Back up signing/ privately. Certificate SHA-256: '+digest)
if __name__=='__main__':main()
