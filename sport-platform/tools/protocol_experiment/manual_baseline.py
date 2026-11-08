"""Manual initial-generation baseline. Standard library; uses installed validate.py."""
import argparse
import csv
from datetime import datetime, timezone
from decimal import Decimal
import io
import json
from pathlib import Path
import re
import sys
import time


def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'), parse_float=Decimal)


def write_json(path, data):
    Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2, default=str)+'\n', encoding='utf-8')


def csv_text(columns, rows):
    stream = io.StringIO(newline='')
    writer = csv.DictWriter(stream, fieldnames=columns, delimiter=';', lineterminator='\n')
    writer.writeheader()
    writer.writerows(rows)
    return stream.getvalue()


def prepare(run, output):
    run, output = Path(run).resolve(), Path(output).resolve()
    if output.exists():
        raise ValueError('Output directory already exists. Choose a new directory; no existing work was changed.')
    manifest = load(run/'manifest.json')
    report = load(run/'validation-report.json')
    if report.get('passed') is not True or report.get('violationCount') != 0:
        raise ValueError('Source run must have a successful independent validation report')
    matches = [s for s in manifest['scenarios'] if s['name'] == 'baseline']
    if len(matches) != 1:
        raise ValueError('Exactly one baseline scenario is required')
    source_path = run/matches[0]['input']
    source = load(source_path)
    if (source.get('capacity'), source.get('heatOrder'), source.get('partialHeat'), source.get('ntPlacement'),
            source.get('positionOrder')) != (8, 'SLOWEST_FIRST', 'FIRST', 'WEAKEST', [4,5,3,6,2,7,1,8]):
        raise ValueError('Unsupported manual task rules; this task is specifically configured for the step-6 experiment')
    eligible = [r for r in source['registrations'] if r['status'] == 'CONFIRMED']
    ids = [r['registrationId'] for r in eligible]
    if len(ids) != len(set(ids)) or not ids:
        raise ValueError('Invalid source registration identities')
    aliases = {f'P{i:04d}': r['registrationId'] for i, r in enumerate(eligible, 1)}
    participants = []
    for alias, row in zip(aliases, eligible):
        value = row.get('seedMetric')
        participants.append(dict(participant=alias, seed_seconds='NT' if value is None else str(value).replace('.', ',')))
    output.mkdir(parents=True)
    # Byte copy retains missing/null distinctions and the exact exported numeric representations.
    (output/'input.json').write_bytes(source_path.read_bytes())
    write_json(output/'identity-map.json', aliases)
    write_json(output/'task.json', dict(schemaVersion=1, scope='INITIAL_GENERATION_ONLY',
               label=manifest['label'], participantCount=len(eligible), sourceRun=str(run),
               sourceInput=matches[0]['input'], note='No automated assignments copied into task'))
    (output/'participants.csv').write_text(csv_text(['participant','seed_seconds'], participants), encoding='utf-8-sig')
    (output/'assignments.csv').write_text(csv_text(['participant','heat','lane'],
               [dict(participant=alias,heat='',lane='') for alias in aliases]), encoding='utf-8-sig')
    instructions = """РУЧНОЕ ФОРМИРОВАНИЕ ИСХОДНОГО ПРОТОКОЛА

Не открывайте готовые автоматические protocol-*.json и snapshot-*.json.
Не изучайте participants.csv и не распределяйте участников до запуска таймера.
Разрешены обычные инструменты Excel/LibreOffice: сортировка, фильтры, копирование,
обычные формулы. Не используйте макрос/скрипт распределения или готовый автопротокол.
Зафиксируйте фактически использованный способ параметром --method.

Заявки находятся в participants.csv, разделитель ;, кодировка UTF-8.
P0001 и т. п. — неизменяемые метки участников. NT — нет заявочного времени.

Правила:
1. Расположить числовые времена по возрастанию, NT после числовых.
2. Разделить отсортированный список на группы максимум по 8, начиная с сильнейших.
3. Нумеровать заплывы в обратном порядке групп: слабейший первым.
   Если есть неполная группа, она будет первым, самым слабым заплывом.
4. В каждом заплыве назначать дорожки от сильнейшего участника к слабейшему:
   4, 5, 3, 6, 2, 7, 1, 8. Неполный заплыв использует начало этого списка.
5. Для равных времен разрешен любой взаимный порядок.
6. Все подтвержденные участники должны быть назначены ровно один раз.

В assignments.csv заполнить heat и lane для каждого participant.
Можно менять порядок строк. Не менять метки участников и названия столбцов.
Сохранить CSV, не XLSX, и только после сохранения остановить таймер клавишей Enter.
Можно оставить CSV-шаблон открытым до старта, но не изучать входные времена заранее.

Первый прогон S-60 — пилот: он также знакомит оператора с заданием.
Для более серьезного сравнения отдельно фиксируйте опыт оператора и обучение.
Этот комплект измеряет исходное формирование, НЕ сценарии изменения/снятия.
"""
    (output/'TASK.txt').write_text(instructions, encoding='utf-8-sig')
    print(f'Prepared {len(eligible)} participants: {output}')
    print('Read TASK.txt. Do not inspect/sort the participant times before the timer starts.')
    return output


def convert_assignments(raw, aliases):
    text = raw.decode('utf-8-sig')
    try:
        dialect = csv.Sniffer().sniff(text[:8192], delimiters=';,')
    except csv.Error as ex:
        raise ValueError('Cannot detect CSV delimiter; use semicolon-separated UTF-8 CSV') from ex
    reader = csv.DictReader(io.StringIO(text, newline=''), dialect=dialect)
    if reader.fieldnames is None or set(reader.fieldnames) != {'participant','heat','lane'}:
        raise ValueError('CSV columns must be exactly participant;heat;lane')
    units, count = {}, 0
    for number, row in enumerate(reader, 2):
        if None in row or any(row.get(k) is None for k in ('participant','heat','lane')):
            raise ValueError(f'CSV row {number}: invalid number of columns')
        alias = row['participant'].strip()
        if alias not in aliases:
            raise ValueError(f'CSV row {number}: unknown participant {alias!r}')
        values = []
        for column in ('heat','lane'):
            value = row[column].strip()
            if re.fullmatch(r'[0-9]+', value) is None:
                raise ValueError(f'CSV row {number}: {column} must be an integer; got {value!r}')
            values.append(int(value))
        heat, lane = values
        if heat < 1:
            raise ValueError(f'CSV row {number}: heat must be >= 1')
        units.setdefault(heat, []).append(dict(registrationId=aliases[alias],laneOrPosition=lane))
        count += 1
    return dict(unitCount=len(units), assignmentCount=count, units=[
        dict(sequenceNumber=number, assignments=assigned) for number, assigned in sorted(units.items())])


def assess(work, attempt, raw, metadata, validator=None):
    if validator is None:
        from validate import validate_protocol
        validator = validate_protocol
    source, aliases = load(work/'input.json'), load(work/'identity-map.json')
    (attempt/'assignments.csv').write_bytes(raw)
    protocol = None
    try:
        protocol = convert_assignments(raw, aliases)
        violations = validator(source, protocol)
    except (ValueError, UnicodeError, csv.Error) as ex:
        violations = [str(ex)]
    report = dict(metadata, passed=not violations, violationCount=len(violations), violations=violations,
                  timingScope='Human reading, sorting, assigning, checking, saving CSV and confirming completion; validation excluded',
                  comparableTo='One initial-generation task only; not an end-to-end automated workflow comparison')
    if protocol is not None:
        write_json(attempt/'protocol.json',protocol)
    write_json(attempt/'report.json',report)
    return report


def measure(work, operator, method):
    work = Path(work).resolve()
    task = load(work/'task.json')
    if task.get('scope') != 'INITIAL_GENERATION_ONLY':
        raise ValueError('Unsupported task scope')
    for name in ('assignments.csv','input.json','identity-map.json','TASK.txt'):
        if not (work/name).is_file():
            raise ValueError(f'Missing task file: {name}')
    attempts = work/'attempts'; attempts.mkdir(exist_ok=True)
    number = 1
    while (attempts/f'attempt-{number:03d}').exists():
        number += 1
    attempt = attempts/f'attempt-{number:03d}'; attempt.mkdir()
    print(f'Task: {task["label"]}, {task["participantCount"]} participants; attempt {number}')
    print('Read TASK.txt. Do not read/sort seed times or fill assignments before START.')
    print('After START open participants.csv, fill assignments.csv, check and save CSV.')
    print(f'Work directory: {work}')
    try:
        input('Press Enter to START, or Ctrl+C to cancel: ')
    except KeyboardInterrupt:
        write_json(attempt/'report.json',dict(cancelled=True,started=False))
        return 130
    started = datetime.now(timezone.utc).isoformat()
    start = time.perf_counter_ns()
    try:
        input('START. After saving the finished assignments.csv, press Enter to STOP: ')
    except KeyboardInterrupt:
        elapsed = (time.perf_counter_ns()-start)/1_000_000_000
        write_json(attempt/'report.json',dict(cancelled=True,started=True,elapsedSeconds=elapsed,
                   operator=operator,method=method,startedAt=started))
        return 130
    elapsed_ns = time.perf_counter_ns()-start
    metadata = dict(schemaVersion=1,taskLabel=task['label'],scenario='baseline',attempt=number,
                    operator=operator,method=method,startedAt=started,
                    finishedAt=datetime.now(timezone.utc).isoformat(),elapsedNanoseconds=elapsed_ns,
                    elapsedSeconds=elapsed_ns/1_000_000_000, measured=True)
    # Persist timing before parsing/validation, and preserve exactly the submitted bytes.
    write_json(attempt/'timing.json',metadata)
    try:
        raw = (work/'assignments.csv').read_bytes()
    except OSError as ex:
        write_json(attempt/'report.json',dict(metadata,passed=False,violations=[str(ex)]))
        raise
    report = assess(work,attempt,raw,metadata)
    print(json.dumps(dict(passed=report['passed'],elapsedSeconds=report['elapsedSeconds'],
                         violationCount=report['violationCount'],report=str(attempt/'report.json')),ensure_ascii=False))
    if not report['passed']:
        print('Attempt preserved. Read report.json. Start a NEW timed attempt before making corrections.')
    else:
        print('Valid manual result preserved. This is an observed task duration, not a general speedup estimate.')
    return 0 if report['passed'] else 1


def main():
    parser = argparse.ArgumentParser()
    commands = parser.add_subparsers(dest='command',required=True)
    prep = commands.add_parser('prepare')
    prep.add_argument('--run',type=Path,required=True); prep.add_argument('--out',type=Path,required=True)
    timed = commands.add_parser('measure')
    timed.add_argument('--work',type=Path,required=True)
    timed.add_argument('--operator',required=True,help='Anonymous operator code, e.g. operator01')
    timed.add_argument('--method',required=True,help='Actual manual tool/method, e.g. Excel-sort-copy')
    args = parser.parse_args()
    try:
        if args.command == 'prepare':
            prepare(args.run,args.out)
            return 0
        return measure(args.work,args.operator,args.method)
    except (ValueError,KeyError,TypeError,OSError) as ex:
        print(f'ERROR: {ex}',file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
