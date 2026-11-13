#!/usr/bin/env python3
"""Build a Forge 1.20.1 test jar from cached dependencies without Gradle sockets.

All mixins use remap=false. Refuse to package if direct Minecraft member calls
are introduced, since those would require the normal ForgeGradle reobfuscation.
"""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
from zipfile import ZipFile, ZIP_DEFLATED

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--jdk', type=Path, required=True, help='Java 17 JDK directory')
parser.add_argument('--mod-version', default='1.0.4-test1')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
cache = Path.home() / '.gradle/caches'
modules = cache / 'modules-2/files-2.1'
mc = '1.20.1'
forge = '47.3.0'
mtr = '4.0.5'
version = f'{args.mod_version}+{mc}'


def cached(group, artifact, number):
    paths = sorted((modules / group / artifact / number).glob('*/*.jar'))
    paths = [p for p in paths if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if not paths:
        raise SystemExit(f'Missing cached dependency: {group}:{artifact}:{number}')
    return paths[0]


classpath = [
    cache / f'forge_gradle/deobf_dependencies/maven/modrinth/minecraft-transit-railway/FORGE-{mtr}+{mc}_mapped_official_{mc}/minecraft-transit-railway-FORGE-{mtr}+{mc}_mapped_official_{mc}.jar',
    cache / f'forge_gradle/minecraft_user_repo/net/minecraftforge/forge/{mc}-{forge}_mapped_official_{mc}/forge-{mc}-{forge}_mapped_official_{mc}.jar',
    cached('net.minecraftforge', 'javafmllanguage', f'{mc}-{forge}'),
    cached('net.minecraftforge', 'fmlcore', f'{mc}-{forge}'),
    cached('net.minecraftforge', 'mergetool-api', '1.0'),
    cached('org.spongepowered', 'mixin', '0.8.5'),
    cached('com.google.code.findbugs', 'jsr305', '3.0.2'),
    cached('org.apache.logging.log4j', 'log4j-api', '2.20.0'),
    cached('org.ow2.asm', 'asm', '9.7.1'),
    cached('org.ow2.asm', 'asm-tree', '9.7.1'),
]
for path in classpath:
    if not path.is_file():
        raise SystemExit(f'Missing cached dependency: {path}')

sources = sorted((root / 'src/main/java').rglob('*.java')) + sorted((root / 'forge/src/main/java').rglob('*.java'))
for path in (root / 'src/main/java/org/mtr/altplatforms/mixin').glob('*.java'):
    if 'remap = false' not in path.read_text():
        raise SystemExit(f'Normal Gradle build required: {path.name} may need a refmap')

output = root / f'build/release/EMTRA-forge-{version}.jar'
output.parent.mkdir(parents=True, exist_ok=True)
with tempfile.TemporaryDirectory(prefix='emtra-forge-') as temporary:
    classes = Path(temporary) / 'classes'
    classes.mkdir()
    subprocess.run([str(args.jdk / 'bin/javac'), '-proc:none', '-encoding', 'UTF-8', '--release', '17',
                    '-cp', ':'.join(map(str, classpath)), '-d', str(classes), *map(str, sources)], check=True)
    for path in classes.rglob('*.class'):
        name = '.'.join(path.relative_to(classes).with_suffix('').parts)
        bytecode = subprocess.check_output([str(args.jdk / 'bin/javap'), '-c', '-p', '-classpath', str(classes), name], text=True)
        if re.search(r'// (?:InterfaceMethod|Method|Field) net/minecraft/', bytecode):
            raise SystemExit(f'Normal ForgeGradle reobfuscation required: {name}')

    staging = Path(temporary) / output.name
    with ZipFile(staging, 'w', ZIP_DEFLATED) as jar:
        jar.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\r\nMixinConfigs: mtr_alt_platforms.mixins.json\r\n\r\n')
        jar.writestr('mtr_alt_platforms.refmap.json', '{"mappings": {}, "data": {}}\n')
        for directory in [root / 'src/main/resources', root / 'forge/src/main/resources', classes]:
            for path in sorted(directory.rglob('*')):
                if not path.is_file():
                    continue
                name = path.relative_to(directory).as_posix()
                content = path.read_bytes()
                if name == 'META-INF/mods.toml':
                    content = content.decode().replace('${version}', version).replace('${minecraft_version}', mc).encode()
                jar.writestr(name, content)
    shutil.copy2(staging, output)
print(f'Built {output} ({output.stat().st_size} bytes)')
