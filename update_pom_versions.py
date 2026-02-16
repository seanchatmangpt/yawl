#!/usr/bin/env python3
"""Update Maven dependency versions in pom.xml for Java 25 compatibility"""

import re

VERSION_UPDATES = {
    # Framework & BOM
    'spring-boot.version': ('3.2.5', '3.4.2'),
    'jakarta-ee.version': ('10.0.0', '11.0.0'),
    'opentelemetry.version': ('1.40.0', '1.46.0'),
    'opentelemetry-instrumentation.version': ('2.6.0', '2.10.0'),
    'resilience4j.version': ('2.2.0', '2.2.0'),  # Already latest
    'testcontainers.version': ('1.19.7', '1.20.4'),
    'jacoco.version': ('0.8.11', '0.8.12'),

    # Jakarta EE
    'jakarta.servlet.version': ('6.0.0', '6.1.0'),
    'jakarta.persistence.version': ('3.0.0', '3.2.0'),
    'jakarta.xml.bind.version': ('3.0.1', '4.0.2'),
    'jakarta.mail.version': ('2.1.0', '2.1.3'),
    'angus.mail.version': ('2.0.0', '2.0.3'),
    'jakarta.activation.version': ('2.1.0', '2.1.3'),
    'angus.activation.version': ('1.0.0', '2.0.2'),
    'jakarta.faces.version': ('3.0.0', '4.1.2'),
    'jakarta.cdi.version': ('3.0.0', '4.1.0'),
    'jakarta.ws.rs.version': ('3.1.0', '4.0.0'),

    # ORM & Database
    'hibernate.version': ('6.5.1.Final', '6.6.6.Final'),
    'h2.version': ('2.2.224', '2.3.232'),
    'postgresql.version': ('42.7.2', '42.7.5'),
    'mysql.version': ('8.0.36', '9.1.0'),
    'hikaricp.version': ('5.1.0', '6.2.1'),

    # Core Libraries
    'commons.lang3.version': ('3.14.0', '3.17.0'),
    'commons.io.version': ('2.15.1', '2.18.0'),
    'commons.codec.version': ('1.16.0', '1.17.1'),
    'commons.collections4.version': ('4.4', '4.5.0'),
    'commons.dbcp2.version': ('2.10.0', '2.12.0'),
    'commons.pool2.version': ('2.12.0', '2.12.0'),  # Already latest
    'commons.text.version': ('1.11.0', '1.13.0'),

    # Logging
    'log4j.version': ('2.23.1', '2.24.3'),
    'slf4j.version': ('2.0.9', '2.0.16'),
    'jboss.logging.version': ('3.5.3.Final', '3.6.1.Final'),

    # JSON
    'gson.version': ('2.10.1', '2.11.0'),

    # XML
    'jaxb.version': ('2.3.1', '4.0.5'),
    'jaxen.version': ('1.2.0', '2.0.0'),

    # Testing
    'junit.jupiter.version': ('5.10.1', '5.11.4'),
    'junit.platform.version': ('1.10.1', '1.11.4'),
    'xmlunit.version': ('1.3', '2.10.0'),

    # Other
    'jandex.version': ('3.1.7', '3.2.3'),
    'byte.buddy.version': ('1.12.23', '1.15.11'),
    'classmate.version': ('1.5.1', '1.7.0'),
    'istack.version': ('4.1.1', '4.2.0'),
    'saxon.version': ('12.4', '12.5'),
    'jersey.version': ('3.1.5', '3.1.9'),
    'microsoft.graph.version': ('5.81.0', '6.24.0'),
    'azure.identity.version': ('1.11.4', '1.14.2'),
    'micrometer.version': ('1.12.4', '1.14.2'),
}

def update_pom(filename='pom.xml'):
    with open(filename, 'r') as f:
        content = f.read()

    updated = content
    changes = []

    for prop, (old_ver, new_ver) in VERSION_UPDATES.items():
        pattern = f'<{prop}>{re.escape(old_ver)}</{prop}>'
        replacement = f'<{prop}>{new_ver}</{prop}>'

        if re.search(pattern, updated):
            updated = re.sub(pattern, replacement, updated)
            changes.append(f'{prop}: {old_ver} → {new_ver}')

    with open(filename, 'w') as f:
        f.write(updated)

    return changes

if __name__ == '__main__':
    changes = update_pom('/home/user/yawl/pom.xml')
    print(f"Updated {len(changes)} versions:")
    for change in changes:
        print(f"  {change}")
