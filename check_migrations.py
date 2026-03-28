import os
import re

def check_migrations(directory):
    files = os.listdir(directory)
    versions = []
    for f in files:
        match = re.match(r'V(\d+)__', f)
        if match:
            versions.append(int(match.group(1)))
    
    versions.sort()
    
    duplicates = set([x for x in versions if versions.count(x) > 1])
    gaps = []
    for i in range(1, max(versions) + 1):
        if i not in versions:
            gaps.append(i)
            
    print(f"Max version: {max(versions)}")
    print(f"Total files: {len(versions)}")
    print(f"Duplicates: {duplicates}")
    print(f"Gaps: {gaps}")

check_migrations('backend/src/main/resources/db/migration')
