"""Run with Python. Updates only optional seedMetric reads in the step-6 validator."""
from pathlib import Path
import os
import shutil
import sys


def main():
    target = Path(__file__).resolve().with_name('validate.py')
    if not target.is_file():
        print(f'ERROR: validator not found: {target}', file=sys.stderr)
        return 1
    original = target.read_text(encoding='utf-8')
    old = "metric(r['seedMetric'])"
    new = "metric(r.get('seedMetric'))"
    old_count, new_count = original.count(old), original.count(new)
    if old_count == 0 and new_count == 3:
        print('NT fix is already applied. No changes.')
        return 0
    if old_count != 3 or new_count != 0:
        print(f'ERROR: unexpected validator version: old={old_count}, new={new_count}; expected old=3, new=0.', file=sys.stderr)
        print('No files changed. Send this message for review.', file=sys.stderr)
        return 1
    updated = original.replace(old, new)
    compile(updated, str(target), 'exec')
    backup = target.with_name('validate.py.before-nt-fix.bak')
    if backup.exists():
        print(f'ERROR: backup already exists: {backup}. No files changed.', file=sys.stderr)
        return 1
    shutil.copy2(target, backup)
    temporary = target.with_name('validate.py.nt-fix.tmp')
    try:
        with temporary.open('w', encoding='utf-8', newline='') as stream:
            stream.write(updated)
        os.replace(temporary, target)
    finally:
        if temporary.exists():
            temporary.unlink()
    print('NT fix applied: 3 optional seedMetric reads updated.')
    print('Backup: validate.py.before-nt-fix.bak')
    print('Java code and experiment exports were not changed.')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
