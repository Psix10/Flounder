'''Sequential manual update tasks using saved experiment inputs and accepted manual results.'''
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
PREVIOUS={'addition':'baseline','withdrawal':'addition','seed_change':'withdrawal'}

def load(path):
    return json.loads(Path(path).read_text(encoding='utf-8'),parse_float=Decimal)

def save(path,data):
    Path(path).write_text(json.dumps(data,ensure_ascii=False,indent=2,default=str)+'\n',encoding='utf-8')

def check_protocol(source,protocol):
    from validate import validate_protocol
    return validate_protocol(source,protocol)

def rows(source):
    result={}
    for r in source['registrations']:
        rid=r['registrationId']
        if rid in result:raise ValueError('Duplicate input registration ID')
        value=r.get('seedMetric')
        result[rid]=(r['status'],None if value is None else Decimal(str(value)))
    return result

def write_csv(path,columns,data):
    with Path(path).open('w',encoding='utf-8-sig',newline='') as stream:
        writer=csv.DictWriter(stream,fieldnames=columns,delimiter=';')
        writer.writeheader();writer.writerows(data)

def read_csv(raw):
    text=raw.decode('utf-8-sig')
    dialect=csv.Sniffer().sniff(text[:8192],delimiters=';,')
    reader=csv.DictReader(io.StringIO(text,newline=''),dialect=dialect)
    if reader.fieldnames is None or set(reader.fieldnames)!={'participant','heat','lane'}:
        raise ValueError('CSV must have exactly participant, heat, lane columns')
    data=[]
    for line,r in enumerate(reader,2):
        if None in r or any(r.get(k) is None for k in ('participant','heat','lane')):
            raise ValueError(f'Invalid CSV column count at line {line}')
        data.append({k:r[k].strip() for k in ('participant','heat','lane')})
    return data

def convert(raw,aliases):
    units={};count=0
    for r in read_csv(raw):
        if r['participant'] not in aliases:raise ValueError('Unknown participant: '+r['participant'])
        if any(re.fullmatch(r'[0-9]+',r[k]) is None for k in ('heat','lane')):
            raise ValueError('All heat/lane fields must be integers: '+r['participant'])
        heat,lane=int(r['heat']),int(r['lane'])
        if heat<1:raise ValueError('Heat number must be positive')
        units.setdefault(heat,[]).append(dict(registrationId=aliases[r['participant']],laneOrPosition=lane));count+=1
    return dict(unitCount=len(units),assignmentCount=count,units=[
        dict(sequenceNumber=h,assignments=a) for h,a in sorted(units.items())])

def display(value):return 'NT' if value is None else str(value).replace('.',',')

def prepare(run,previous_attempt,scenario,output):
    run,previous_attempt,output=map(lambda p:Path(p).resolve(),(run,previous_attempt,output))
    if output.exists():raise ValueError('Output already exists; choose a new directory. Nothing overwritten.')
    if previous_attempt.parent.name!='attempts':raise ValueError('Use an attempts/attempt-XXX directory')
    work=previous_attempt.parent.parent
    task=load(work/'task.json');report=load(previous_attempt/'report.json')
    if report.get('passed') is not True:raise ValueError('Previous manual submission must have passed validation')
    prior=task.get('scenario','baseline' if task.get('scope')=='INITIAL_GENERATION_ONLY' else None)
    if prior!=PREVIOUS[scenario]:raise ValueError(f'{scenario} requires previous scenario {PREVIOUS[scenario]}')
    manifest=load(run/'manifest.json');approval=load(run/'validation-report.json')
    if approval.get('passed') is not True or approval.get('violationCount')!=0:
        raise ValueError('Automated source run must have passed validation')
    paths={s['name']:s['input'] for s in manifest['scenarios']}
    previous=load(work/'input.json')
    if previous!=load(run/paths[PREVIOUS[scenario]]):raise ValueError('Previous input differs from required scenario input')
    aliases=load(work/'identity-map.json')
    if len(set(aliases.values()))!=len(aliases):raise ValueError('Invalid identity map')
    raw=(previous_attempt/'assignments.csv').read_bytes()
    errors=check_protocol(previous,convert(raw,aliases))
    if errors:raise ValueError('Previous saved CSV is invalid: '+str(errors))
    target_path=run/paths[scenario];target=load(target_path)
    if {k:v for k,v in previous.items() if k!='registrations'}!={k:v for k,v in target.items() if k!='registrations'}:
        raise ValueError('Rule configuration changed')
    before,after=rows(previous),rows(target)
    added=[rid for rid in after if rid not in before];removed=set(before)-set(after)
    changed=[rid for rid in before if rid in after and before[rid]!=after[rid]]
    if scenario=='addition':
        if removed or changed or len(added)!=max(3,manifest['baseSize']//20) or any(after[r][0]!='CONFIRMED' for r in added):
            raise ValueError('Unexpected addition delta')
    elif scenario=='withdrawal':
        if added or removed or len(changed)!=2 or any(before[r][0]!='CONFIRMED' or after[r][0]!='REJECTED'
                or before[r][1]!=after[r][1] for r in changed):raise ValueError('Unexpected withdrawal delta')
    elif added or removed or len(changed)!=1 or any(before[r][0]!='CONFIRMED' or after[r][0]!='CONFIRMED'
            or after[r][1]!=Decimal('99.99') for r in changed):raise ValueError('Unexpected seed-change delta')
    reverse={rid:a for a,rid in aliases.items()}
    next_number=max([int(a[1:]) for a in aliases if re.fullmatch(r'P[0-9]+',a)]+[0])+1
    for rid in after:
        if rid not in reverse:
            alias=f'P{next_number:04d}';next_number+=1
            while alias in aliases:alias=f'P{next_number:04d}';next_number+=1
            aliases[alias]=rid;reverse[rid]=alias
    changes=[]
    for rid in added+changed:
        old=before.get(rid)
        changes.append(dict(change='ADDED' if old is None else 'EXCLUDED' if old[0]!=after[rid][0] else 'SEED_CHANGED',
            participant=reverse[rid],old_status='' if old is None else old[0],new_status=after[rid][0],
            old_seed_seconds='' if old is None else display(old[1]),new_seed_seconds=display(after[rid][1])))
    assigned=read_csv(raw)+[dict(participant=reverse[rid],heat='',lane='') for rid in added]
    output.mkdir(parents=True)
    (output/'input.json').write_bytes(target_path.read_bytes())
    (output/'previous-input.json').write_bytes((work/'input.json').read_bytes())
    (output/'previous-assignments.csv').write_bytes(raw)
    save(output/'identity-map.json',aliases)
    count=sum(s=='CONFIRMED' for s,_ in after.values())
    save(output/'task.json',dict(schemaVersion=1,scope='MANUAL_UPDATE',scenario=scenario,label=manifest['label'],
        participantCount=count,sourceRun=str(run),previousAttempt=str(previous_attempt),changeCount=len(changes)))
    write_csv(output/'changes.csv',['change','participant','old_status','new_status','old_seed_seconds','new_seed_seconds'],changes)
    write_csv(output/'participants.csv',['participant','status','seed_seconds'],[
        dict(participant=reverse[rid],status=s,seed_seconds=display(v)) for rid,(s,v) in after.items() if s=='CONFIRMED'])
    write_csv(output/'assignments.csv',['participant','heat','lane'],assigned)
    instructions='''РУЧНОЕ ОБНОВЛЕНИЕ СУЩЕСТВУЮЩЕГО ПРОТОКОЛА
До старта прочитать только эту инструкцию. changes.csv и новые времена изучать после START.
assignments.csv содержит предыдущие назначения, НЕ решение новой задачи.
ADDED: новые участники добавлены строками с пустыми heat/lane; назначить вручную.
EXCLUDED: удалить указанных участников из сдаваемого assignments.csv.
SEED_CHANGED: учесть новое время при перераспределении.
participants.csv содержит актуальные подтвержденные заявки и времена.
Обновить все затронутые назначения; сохранять прежнюю дорожку необязательно.
Правила: времена ASC, NT после чисел; группы по 8 начиная с сильнейших;
затем группы в обратном порядке (слабейшая первой, неполная первой).
Внутри группы от сильнейшего к слабейшему дорожки 4,5,3,6,2,7,1,8.
Для равных времен допустим любой взаимный порядок.
Разрешены обычная сортировка/копирование/формулы Excel; не скрипт/макрос расстановки.
Не смотреть автоматические protocol-*.json/snapshot-*.json.
Сохранить assignments.csv: ровно participant;heat;lane, без лишних столбцов.
Шаблон и список изменений заранее подготовлены скриптом: их создание не измеряется.
Первое Enter запускает таймер. Второе — после проверки и сохранения CSV.
При невалидной сдаче сохранить попытку. До исправлений начать новую измеряемую попытку.
'''
    (output/'TASK.txt').write_text(instructions,encoding='utf-8-sig')
    print(f'Prepared update {scenario}: {len(changes)} changes, {count} eligible participants')
    print(f'Work directory: {output}')
    return output

def measure(work,operator,method):
    work=Path(work).resolve();task=load(work/'task.json')
    if task.get('scope')!='MANUAL_UPDATE':raise ValueError('This command requires a MANUAL_UPDATE task')
    for name in ('input.json','identity-map.json','assignments.csv','changes.csv'):
        if not (work/name).is_file():raise ValueError('Missing task file: '+name)
    attempts=work/'attempts';attempts.mkdir(exist_ok=True);number=1
    while (attempts/f'attempt-{number:03d}').exists():number+=1
    attempt=attempts/f'attempt-{number:03d}';attempt.mkdir()
    print(f'{task["label"]}: {task["scenario"]}, attempt {number}, {task["participantCount"]} eligible participants')
    print(f'Work directory: {work}')
    print('Read TASK.txt before START. Read changes.csv and update assignments.csv AFTER START.')
    try:input('Enter to START, Ctrl+C to cancel: ')
    except KeyboardInterrupt:
        save(attempt/'report.json',dict(cancelled=True,started=False));return 130
    started=datetime.now(timezone.utc).isoformat();start=time.perf_counter_ns()
    try:input('START. After updating, checking and saving assignments.csv, Enter to STOP: ')
    except KeyboardInterrupt:
        save(attempt/'report.json',dict(cancelled=True,started=True,elapsedSeconds=(time.perf_counter_ns()-start)/1e9));return 130
    duration=time.perf_counter_ns()-start
    metadata=dict(schemaVersion=1,taskLabel=task['label'],scenario=task['scenario'],attempt=number,
        operator=operator,method=method,startedAt=started,finishedAt=datetime.now(timezone.utc).isoformat(),
        elapsedNanoseconds=duration,elapsedSeconds=duration/1e9,measured=True,previousAttempt=task['previousAttempt'],
        timingScope='Read prepared changes, manually update/check/save CSV; template setup and automatic validation excluded')
    save(attempt/'timing.json',metadata)
    raw=(work/'assignments.csv').read_bytes();(attempt/'assignments.csv').write_bytes(raw)
    try:
        protocol=convert(raw,load(work/'identity-map.json'));save(attempt/'protocol.json',protocol)
        errors=check_protocol(load(work/'input.json'),protocol)
    except (ValueError,UnicodeError,csv.Error) as ex:errors=[str(ex)]
    report=dict(metadata,passed=not errors,violationCount=len(errors),violations=errors)
    save(attempt/'report.json',report)
    print(json.dumps(dict(passed=report['passed'],elapsedSeconds=report['elapsedSeconds'],violationCount=len(errors),
        report=str(attempt/'report.json')),ensure_ascii=False))
    return 0 if not errors else 1

def main():
    parser=argparse.ArgumentParser();commands=parser.add_subparsers(dest='command',required=True)
    p=commands.add_parser('prepare');p.add_argument('--run',type=Path,required=True)
    p.add_argument('--previous-attempt',type=Path,required=True);p.add_argument('--scenario',choices=PREVIOUS,required=True)
    p.add_argument('--out',type=Path,required=True)
    m=commands.add_parser('measure');m.add_argument('--work',type=Path,required=True)
    m.add_argument('--operator',required=True);m.add_argument('--method',required=True)
    args=parser.parse_args()
    try:
        if args.command=='prepare':prepare(args.run,args.previous_attempt,args.scenario,args.out);return 0
        return measure(args.work,args.operator,args.method)
    except (OSError,ValueError,KeyError,TypeError,csv.Error) as ex:
        print('ERROR: '+str(ex),file=sys.stderr);return 2

if __name__=='__main__':raise SystemExit(main())
