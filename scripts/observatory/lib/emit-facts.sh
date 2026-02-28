#!/bin/bash
#
# Observatory fact emitters
# Each emit_*() function generates a specific JSON fact file
#

# Emit modules.json - list all modules with source directory info
emit_modules() {
    local root_dir="$1"
    local out="$2/modules.json"

    log_info "Emitting facts/modules.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET
from pathlib import Path

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

modules = []

# Parse root POM to find modules
tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

# Get module directories
module_dirs = root.findall('.//pom:module', ns)

for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')

    if os.path.exists(module_pom):
        # Parse module POM
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()

        # Get artifactId from direct project children (skip parent)
        art_id = None
        for child in mod_root:
            if child.tag.endswith('artifactId'):
                art_id = child
                break
        artifact_id = art_id.text if art_id is not None and art_id.text else module_name

        # Get source directory
        src_dir = mod_root.find('.//pom:sourceDirectory', ns)
        source_dir = src_dir.text if src_dir is not None else 'src/main/java'

        # Count source files
        src_path = os.path.join(root_dir, module_name, source_dir.replace('../', ''))
        if source_dir.startswith('../src'):
            src_path = os.path.join(root_dir, source_dir)

        src_count = 0
        if os.path.isdir(src_path):
            for dirpath, dirs, files in os.walk(src_path):
                src_count += sum(1 for f in files if f.endswith('.java'))

        # Count test files
        test_dir = mod_root.find('.//pom:testSourceDirectory', ns)
        test_source_dir = test_dir.text if test_dir is not None else 'src/test/java'

        test_path = os.path.join(root_dir, module_name, test_source_dir.replace('../', ''))
        if test_source_dir.startswith('../'):
            test_path = os.path.join(root_dir, test_source_dir)

        test_count = 0
        if os.path.isdir(test_path):
            for dirpath, dirs, files in os.walk(test_path):
                test_count += sum(1 for f in files if f.endswith('.java'))

        # Determine strategy
        if source_dir == '../src':
            strategy = 'full_shared'
        elif source_dir.startswith('../src/'):
            strategy = 'package_scoped'
        else:
            strategy = 'standard'

        modules.append({
            'name': artifact_id,
            'path': module_name,
            'has_pom': True,
            'src_files': src_count,
            'test_files': test_count,
            'source_dir': source_dir,
            'strategy': strategy
        })

output = {'modules': modules}
print(json.dumps(output, indent=2))
PYEOF
}

# Emit reactor.json - module dependency graph and build order
emit_reactor() {
    local root_dir="$1"
    local out="$2/reactor.json"

    log_info "Emitting facts/reactor.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

# Parse root POM
tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

# Build reactor order (modules in order they appear in POM)
reactor_order = ['yawl-parent']
module_dirs = root.findall('.//pom:module', ns)
for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')
    if os.path.exists(module_pom):
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()
        art_id = mod_root.find('.//pom:artifactId', ns)
        if art_id is not None:
            reactor_order.append(art_id.text)

# Extract dependencies
module_deps = []
for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')

    if os.path.exists(module_pom):
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()

        # Get module's own artifactId
        from_id = mod_root.find('.//pom:artifactId', ns)
        if from_id is None:
            continue

        from_name = from_id.text

        # Find internal dependencies
        for dep in mod_root.findall('.//pom:dependency', ns):
            to_id = dep.find('pom:artifactId', ns)
            if to_id is not None:
                to_name = to_id.text
                # Only include internal YAWL dependencies
                if to_name in reactor_order and to_name != from_name:
                    module_deps.append({
                        'from': from_name,
                        'to': to_name
                    })

output = {
    'reactor_order': reactor_order,
    'module_deps': module_deps
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit shared-src.json - source ownership mapping
emit_shared_src() {
    local root_dir="$1"
    local out="$2/shared-src.json"

    log_info "Emitting facts/shared-src.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

# Group modules by shared source directory
shared_roots_map = {}
module_dirs = root.findall('.//pom:module', ns)

for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')

    if os.path.exists(module_pom):
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()

        art_id = mod_root.find('.//pom:artifactId', ns)
        if art_id is None:
            continue

        src_dir = mod_root.find('.//pom:sourceDirectory', ns)
        source_dir = src_dir.text if src_dir is not None else 'src/main/java'

        # Get compiler includes/excludes
        includes = []
        excludes = []

        plugin = mod_root.find('.//pom:plugin[pom:artifactId="maven-compiler-plugin"]', ns)
        if plugin is not None:
            config = plugin.find('pom:configuration', ns)
            if config is not None:
                inc_elem = config.find('pom:includes', ns)
                if inc_elem is not None:
                    for inc in inc_elem.findall('pom:include', ns):
                        if inc.text:
                            includes.append(inc.text)

                exc_elem = config.find('pom:excludes', ns)
                if exc_elem is not None:
                    for exc in exc_elem.findall('pom:exclude', ns):
                        if exc.text:
                            excludes.append(exc.text)

        if source_dir not in shared_roots_map:
            shared_roots_map[source_dir] = []

        shared_roots_map[source_dir].append({
            'name': art_id.text,
            'includes': '|'.join(includes) if includes else '',
            'excludes': '|'.join(excludes) if excludes else ''
        })

# Convert to output format
shared_roots = []
for root_path, modules in sorted(shared_roots_map.items()):
    shared_roots.append({
        'root': root_path,
        'modules': modules
    })

output = {
    'shared_roots': shared_roots,
    'ownership_ambiguities': []
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit tests.json - test configuration and counts
emit_tests() {
    local root_dir="$1"
    local out="$2/tests.json"

    log_info "Emitting facts/tests.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

# Get Surefire config
surefire_modules = []
surefire_includes = []
surefire_excludes = []

surefire = root.find('.//pom:plugin[pom:artifactId="maven-surefire-plugin"]/pom:configuration', ns)
if surefire is not None:
    inc = surefire.find('pom:includes', ns)
    if inc is not None:
        for i in inc.findall('pom:include', ns):
            if i.text:
                surefire_includes.append(i.text)
    exc = surefire.find('pom:excludes', ns)
    if exc is not None:
        for e in exc.findall('pom:exclude', ns):
            if e.text:
                surefire_excludes.append(e.text)

# Get Failsafe config
failsafe_modules = []
failsafe_includes = []
failsafe_excludes = []

failsafe = root.find('.//pom:plugin[pom:artifactId="maven-failsafe-plugin"]/pom:configuration', ns)
if failsafe is not None:
    inc = failsafe.find('pom:includes', ns)
    if inc is not None:
        for i in inc.findall('pom:include', ns):
            if i.text:
                failsafe_includes.append(i.text)
    exc = failsafe.find('pom:excludes', ns)
    if exc is not None:
        for e in exc.findall('pom:exclude', ns):
            if e.text:
                failsafe_excludes.append(e.text)

# Count tests per module
module_detail = []
module_dirs = root.findall('.//pom:module', ns)

for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')

    if os.path.exists(module_pom):
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()

        art_id = mod_root.find('.//pom:artifactId', ns)
        if art_id is None:
            continue

        test_dir = mod_root.find('.//pom:testSourceDirectory', ns)
        test_source = test_dir.text if test_dir is not None else 'src/test/java'

        # Count test files
        test_path = os.path.join(root_dir, module_name, test_source.replace('../', ''))
        if test_source.startswith('../'):
            test_path = os.path.join(root_dir, test_source)

        scoped_tests = 0
        visible_tests = 0
        integration_tests = 0

        if os.path.isdir(test_path):
            for root, dirs, files in os.walk(test_path):
                for f in files:
                    if f.endswith('Test.java'):
                        scoped_tests += 1
                        visible_tests += 1
                    elif f.endswith('Tests.java') or f.endswith('TestSuite.java'):
                        visible_tests += 1
                    elif f.endswith('IT.java'):
                        integration_tests += 1

        module_detail.append({
            'module': art_id.text,
            'scoped_tests': scoped_tests,
            'visible_tests': visible_tests,
            'integration_tests': integration_tests,
            'test_source': test_source if test_source != 'src/test/java' else 'src/test/java'
        })

        if visible_tests > 0:
            surefire_modules.append(art_id.text)

output = {
    'surefire': {
        'modules': surefire_modules,
        'includes': surefire_includes if surefire_includes else ['**/*Test.java'],
        'excludes': surefire_excludes if surefire_excludes else []
    },
    'failsafe': {
        'modules': failsafe_modules,
        'includes': failsafe_includes if failsafe_includes else ['**/*IT.java'],
        'excludes': failsafe_excludes if failsafe_excludes else []
    },
    'module_detail': module_detail
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit maven-hazards.json - build hazards
emit_maven_hazards() {
    local root_dir="$1"
    local out="$2/maven-hazards.json"

    log_info "Emitting facts/maven-hazards.json ..."

    python3 << 'PYEOF'
import json
import os

root_dir = os.environ.get('ROOT_DIR', '.')
hazards = []

# Check for cached download failures
m2_cache = os.path.expanduser('~/.m2/repository/org/yawlfoundation')
if os.path.isdir(m2_cache):
    for root, dirs, files in os.walk(m2_cache):
        for f in files:
            if f.endswith('.lastUpdated'):
                hazards.append({
                    'code': 'H_MAVEN_CACHED_MISSING_ARTIFACT',
                    'artifact': os.path.basename(root),
                    'message': f'Cached download failure in {root}. Run mvn clean install -U to refresh.'
                })
                break

output = {'hazards': hazards}
print(json.dumps(output, indent=2))
PYEOF
}

# Emit deps-conflicts.json - dependency version management
emit_deps_conflicts() {
    local root_dir="$1"
    local out="$2/deps-conflicts.json"

    log_info "Emitting facts/deps-conflicts.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

# Count parent-managed versions
dep_mgmt = root.find('.//pom:dependencyManagement', ns)
parent_managed = 0
if dep_mgmt is not None:
    parent_managed = len(dep_mgmt.findall('.//pom:dependency', ns))

# Count explicit versions in child POMs
child_explicit = 0
module_dirs = root.findall('.//pom:module', ns)
for module_elem in module_dirs:
    module_name = module_elem.text.strip()
    module_pom = os.path.join(root_dir, module_name, 'pom.xml')

    if os.path.exists(module_pom):
        mod_tree = ET.parse(module_pom)
        mod_root = mod_tree.getroot()

        for dep in mod_root.findall('.//pom:dependency', ns):
            version = dep.find('pom:version', ns)
            if version is not None and version.text and not version.text.startswith('${'):
                child_explicit += 1

output = {
    'parent_managed_entries': parent_managed,
    'child_explicit_versions': child_explicit,
    'strategy': 'Parent POM dependencyManagement centralizes versions. Child POMs use property references.',
    'conflicts': []
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit gates.json - quality gates configuration
emit_gates() {
    local root_dir="$1"
    local out="$2/gates.json"

    log_info "Emitting facts/gates.json ..."

    python3 << 'PYEOF'
import json
import os
import xml.etree.ElementTree as ET

root_dir = os.environ.get('ROOT_DIR', '.')
root_pom = os.path.join(root_dir, 'pom.xml')

tree = ET.parse(root_pom)
root = tree.getroot()
ns = {'pom': 'http://maven.apache.org/POM/4.0.0'}

gates = []
skip_flags = []

# Known quality gate plugins
quality_plugins = [
    ('spotbugs-maven-plugin', 'spotbugs', 'verify'),
    ('maven-checkstyle-plugin', 'checkstyle', 'validate'),
    ('jacoco-maven-plugin', 'jacoco', 'verify'),
    ('dependency-check-maven', 'dependency-check', 'verify'),
    ('maven-enforcer-plugin', 'enforcer', 'validate'),
    ('maven-pmd-plugin', 'pmd', 'verify'),
]

build = root.find('.//pom:build', ns)
if build is not None:
    for plugin_id, gate_name, phase in quality_plugins:
        plugin = build.find(f'.//pom:plugin[pom:artifactId="{plugin_id}"]', ns)
        if plugin is not None:
            # Check if active
            executions = plugin.findall('.//pom:execution', ns)
            has_goals = any(e.find('.//pom:goal', ns) is not None for e in executions)

            skip_elem = plugin.find('.//pom:skip', ns)
            is_skipped = skip_elem is not None and skip_elem.text == 'true'

            activation = 'NOT_FOUND'
            if has_goals and not is_skipped:
                activation = 'ACTIVE'
            elif not has_goals and not is_skipped:
                activation = 'CONFIG_ONLY'
            elif is_skipped:
                activation = 'SKIP_DEFAULT'

            gates.append({
                'name': gate_name,
                'phase': phase,
                'default_active': activation == 'ACTIVE',
                'activation': activation,
                'profiles': [],
                'plugin': plugin_id
            })

# Get profiles
profiles = []
profiles_elem = root.find('.//pom:profiles', ns)
if profiles_elem is not None:
    for profile in profiles_elem.findall('pom:profile', ns):
        profile_id = profile.find('pom:id', ns)
        if profile_id is not None:
            profiles.append(profile_id.text)

output = {
    'gates': gates,
    'skip_flags': [
        {'flag': '-DskipTests=true', 'risk': 'RED', 'disables': 'surefire+failsafe'},
        {'flag': '-Dspotbugs.skip=true', 'risk': 'YELLOW', 'disables': 'spotbugs'},
        {'flag': '-Dpmd.skip=true', 'risk': 'YELLOW', 'disables': 'pmd'},
        {'flag': '-Dcheckstyle.skip=true', 'risk': 'YELLOW', 'disables': 'checkstyle'}
    ],
    'profiles': profiles
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit dual-family.json - stateful/stateless mirror pairs
emit_dual_family() {
    local root_dir="$1"
    local out="$2/dual-family.json"

    log_info "Emitting facts/dual-family.json ..."

    python3 << 'PYEOF'
import json
import os
from pathlib import Path

root_dir = os.environ.get('ROOT_DIR', '.')
src_dir = os.path.join(root_dir, 'src')

families = []

# Scan for stateful classes in org.yawlfoundation.yawl.*
stateful_classes = {}
for java_file in Path(src_dir).rglob('*.java'):
    rel_path = java_file.relative_to(src_dir)
    fqcn = str(rel_path).replace('/', '.').replace('.java', '')

    # Only track stateful YAWL classes
    if fqcn.startswith('org.yawlfoundation.yawl.') and not fqcn.startswith('org.yawlfoundation.yawl.stateless'):
        class_name = java_file.stem
        stateful_classes[class_name] = fqcn

# Scan for stateless mirrors
stateless_dir = os.path.join(src_dir, 'org/yawlfoundation/yawl/stateless')
if os.path.isdir(stateless_dir):
    for java_file in Path(stateless_dir).rglob('*.java'):
        class_name = java_file.stem

        if class_name in stateful_classes:
            stateful_fqcn = stateful_classes[class_name]
            rel_path = java_file.relative_to(src_dir)
            stateless_fqcn = str(rel_path).replace('/', '.').replace('.java', '')

            # Determine category
            category = 'engine'
            if '/elements/' in str(java_file):
                category = 'elements'
            elif '/time/' in str(java_file):
                category = 'time'

            families.append({
                'name': class_name,
                'category': category,
                'stateful_fqcn': stateful_fqcn,
                'stateless_fqcn': stateless_fqcn,
                'policy': 'MIRROR_REQUIRED'
            })

output = {
    'mirror_namespaces': [
        {
            'stateful_prefix': 'org.yawlfoundation.yawl.',
            'stateless_prefix': 'org.yawlfoundation.yawl.stateless.'
        }
    ],
    'family_count': len(families),
    'families': families
}

print(json.dumps(output, indent=2))
PYEOF
}

# Emit duplicates.json - detect duplicate classes
emit_duplicates() {
    local root_dir="$1"
    local out="$2/duplicates.json"

    log_info "Emitting facts/duplicates.json ..."

    python3 << 'PYEOF'
import json
import os
from pathlib import Path
from collections import defaultdict

root_dir = os.environ.get('ROOT_DIR', '.')
src_dir = os.path.join(root_dir, 'src')

fqcn_map = defaultdict(list)

# Map all FQCNs to file paths
for java_file in Path(src_dir).rglob('*.java'):
    rel_path = str(java_file.relative_to(src_dir)).replace('\\', '/')
    fqcn = rel_path.replace('/', '.').replace('.java', '')

    fqcn_map[fqcn].append(rel_path)

# Find within-artifact duplicates
within_artifact = []
for fqcn, paths in fqcn_map.items():
    if len(paths) > 1:
        # Ignore stateful/stateless pairs
        has_stateful = any('stateless' not in p for p in paths)
        has_stateless = any('stateless' in p for p in paths)

        if not (has_stateful and has_stateless):
            within_artifact.append({
                'artifact': 'shared-src',
                'fqcn': fqcn,
                'paths': sorted(paths)
            })

output = {
    'within_artifact': within_artifact,
    'cross_family_duplicates': []
}

print(json.dumps(output, indent=2))
PYEOF
}

# Main function to run all fact emitters
run_facts() {
    local root_dir="$1"
    local facts_dir="$2"

    export ROOT_DIR="$root_dir"

    emit_modules "$root_dir" "$facts_dir" > "$facts_dir/modules.json"
    emit_reactor "$root_dir" "$facts_dir" > "$facts_dir/reactor.json"
    emit_shared_src "$root_dir" "$facts_dir" > "$facts_dir/shared-src.json"
    emit_tests "$root_dir" "$facts_dir" > "$facts_dir/tests.json"
    emit_maven_hazards "$root_dir" "$facts_dir" > "$facts_dir/maven-hazards.json"
    emit_deps_conflicts "$root_dir" "$facts_dir" > "$facts_dir/deps-conflicts.json"
    emit_gates "$root_dir" "$facts_dir" > "$facts_dir/gates.json"
    emit_dual_family "$root_dir" "$facts_dir" > "$facts_dir/dual-family.json"
    emit_duplicates "$root_dir" "$facts_dir" > "$facts_dir/duplicates.json"
}
