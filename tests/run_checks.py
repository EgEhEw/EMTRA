#!/usr/bin/env python3
"""Run headless regressions with cached dependencies; no Gradle/network required."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--mtr-jar', type=Path)
parser.add_argument('--magic-jar', type=Path, help='Optional MAGIC 1.5.1 jar for compatibility bytecode checks')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'


def cached(group, artifact, version):
    jars = sorted((cache / group / artifact / version).glob('*/*.jar'))
    jars = [p for p in jars if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
    if not jars:
        raise SystemExit(f'Missing cached dependency: {group}:{artifact}:{version}')
    return jars[0]


mtr = args.mtr_jar or cached('maven.modrinth', 'minecraft-transit-railway', 'FABRIC-4.0.5+1.20.1')
jars = [mtr, cached('org.spongepowered', 'mixin', '0.8.5'),
        cached('com.google.code.findbugs', 'jsr305', '3.0.2'),
        cached('org.apache.logging.log4j', 'log4j-api', '2.20.0')]
jars += [cached('org.ow2.asm', artifact, '9.7.1') for artifact in ['asm', 'asm-tree', 'asm-analysis']]
# Client UI requires Minecraft mappings; the headless suite compiles all server/common code.
sources = [str(p) for p in (root / 'src/main/java').rglob('*.java') if p.name != 'DashboardScreenMixin.java']
with tempfile.TemporaryDirectory(prefix='emtra-checks-') as directory:
    classpath = ':'.join(map(str, jars))
    subprocess.run(['javac', '-proc:none', '--release', '17', '-cp', classpath, '-d', directory,
                    *sources, str(root / 'tests/RegressionChecks.java')], check=True)
    subprocess.run(['java', '-cp', directory + ':' + classpath, 'RegressionChecks',
                    *([str(args.magic_jar.resolve())] if args.magic_jar else [])], cwd=directory, check=True)
