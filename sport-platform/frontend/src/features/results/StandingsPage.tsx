// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { getEventById, getEventDisciplines, getPublicEventDetails } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { confirmFinal, getFinalHistory, getFinalStandings, getStandings, revokeFinal } from '../../api/standings.api'
import type { ConfirmFinalRequest, FinalStandings, Standings } from '../../api/standings.api'
import { useAuth } from '../../app/providers/AuthProvider'
import { StandingsTable } from './StandingsTable'
import { failureMessage, formatTimestamp, isMissingFinal, keepAttemptAfterFailure } from '../../lib/standings-ui.mjs'
import styles from './Standings.module.css'

type Resource<T> = {kind:'loading'} | {kind:'ready';data:T} | {kind:'error';error:unknown}
type Metadata = {eventId:string;disciplineId:string;eventTitle:string;disciplineName:string;publicSlug:string;key:string}
async function capture<T>(work:()=>Promise<T>):Promise<Resource<T>> {
  try { return {kind:'ready',data:await work()} }
  catch(error) { return {kind:'error',error} }
}
function Failure({error}:{error:unknown}) {
  return <div className={styles.error} role="alert">
    <p>{failureMessage(error)}</p>
    {error instanceof ApiError && error.code?<p>Код: <code>{error.code}</code></p>:null}
    {error instanceof ApiError && error.violations.length>0?<ul>{error.violations.map((v,i)=><li key={i}><code>{v.field}</code>: {v.message}</li>)}</ul>:null}
  </div>
}
function Snapshot({data}:{data:FinalStandings}) {
  return <section className={styles.card}>
    <h2>Сохранённая ревизия № {data.revision} · {data.status==='CONFIRMED'?'Подтверждена':'Снята'}</h2>
    <p>Подтверждение: {formatTimestamp(data.confirmedAt)}. Автор: <code>{data.confirmedBy}</code></p>
    {data.confirmationNote?<p>Примечание: {data.confirmationNote}</p>:null}
    {data.revokedAt?<p>Снятие: {formatTimestamp(data.revokedAt)}. Причина: {data.revocationReason??'—'}</p>:null}
    <p className={styles.hint}>Время показано в часовом поясе браузера. Это сохранённые данные, а не новый расчёт.</p>
    <StandingsTable data={data.standings}/>
  </section>
}
function StandingsWorkspace({eventId,disciplineId,accessToken}:{eventId:string;disciplineId:string;accessToken?:string}) {
  const [live,setLive]=useState<Resource<Standings>>({kind:'loading'})
  const [final,setFinal]=useState<Resource<FinalStandings|null>>({kind:'loading'})
  const [history,setHistory]=useState<Resource<FinalStandings[]>>({kind:'loading'})
  const [tab,setTab]=useState<'current'|'final'>('current')
  const [busy,setBusy]=useState(false)
  const busyRef=useRef(false)
  const mounted=useRef(true)
  const generation=useRef(0)
  const [message,setMessage]=useState<string|null>(null)
  const [actionError,setActionError]=useState<unknown>(null)
  const [complete,setComplete]=useState(false)
  const [note,setNote]=useState('')
  const [reason,setReason]=useState('')
  const [confirmAttempt,setConfirmAttempt]=useState<ConfirmFinalRequest|null>(null)
  const [revokeAttempt,setRevokeAttempt]=useState<{id:string;reason:string}|null>(null)
  const reload=useCallback(async()=>{
    const ticket=++generation.current
    if(mounted.current) {
      setLive({kind:'loading'});setFinal({kind:'loading'})
      if(accessToken) setHistory({kind:'loading'})
    }
    const [nextLive,nextFinal,nextHistory]=await Promise.all([
      capture(()=>getStandings(eventId,disciplineId)),
      capture(async()=>{
        try {return await getFinalStandings(eventId,disciplineId)}
        catch(error) {if(isMissingFinal(error)) return null;throw error}
      }),
      accessToken?capture(()=>getFinalHistory(eventId,disciplineId,accessToken)):Promise.resolve<Resource<FinalStandings[]>>({kind:'ready',data:[]}),
    ])
    if(!mounted.current||ticket!==generation.current) return
    setLive(nextLive);setFinal(nextFinal);setHistory(nextHistory)
    if(nextFinal.kind==='ready'&&nextFinal.data) setConfirmAttempt(null)
  },[eventId,disciplineId,accessToken])
  useEffect(()=>{
    mounted.current=true
    void reload()
    return ()=>{mounted.current=false;generation.current++}
  },[reload])
  const current=final.kind==='ready'?final.data:null
  // A successful PRIVATE history request establishes access, not the role label alone.
  const canManage=Boolean(accessToken)&&history.kind==='ready'
  const loading=live.kind==='loading'||final.kind==='loading'||Boolean(accessToken&&history.kind==='loading')
  const canConfirm=canManage&&!loading&&!busy&&final.kind==='ready'&&!current&&live.kind==='ready'&&live.data.entries.length>0&&(complete||Boolean(confirmAttempt))
  async function handleConfirm(event:FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if(!accessToken||!canConfirm||busyRef.current) return
    let attempt=confirmAttempt
    if(!attempt) {
      if(typeof globalThis.crypto?.randomUUID!=='function') {setActionError(new Error('Для создания requestId нужен защищённый контекст браузера (HTTPS или localhost).'));return}
      attempt={requestId:globalThis.crypto.randomUUID(),programmeComplete:true,note:note.trim()}
      setConfirmAttempt(attempt)
    }
    busyRef.current=true;setBusy(true);setMessage(null);setActionError(null)
    try {
      const saved=await confirmFinal(eventId,disciplineId,attempt,accessToken)
      if(!mounted.current) return
      setConfirmAttempt(null);setComplete(false);setNote('')
      setMessage(saved.status==='CONFIRMED'?`Подтверждена ревизия № ${saved.revision}.`:'Эта попытка относится к уже снятой ревизии. Нового подтверждения не создано.')
      if(saved.status==='CONFIRMED') {setFinal({kind:'ready',data:saved});setTab('final')}
      await reload()
    } catch(error) {
      if(!mounted.current) return
      setActionError(error)
      if(!keepAttemptAfterFailure(error)) setConfirmAttempt(null)
      await reload()
    } finally {
      busyRef.current=false;if(mounted.current) setBusy(false)
    }
  }
  async function handleRevoke(event:FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if(!accessToken||!canManage||busyRef.current||loading||(!current&&!revokeAttempt)) return
    const attempt=revokeAttempt??{id:current!.id,reason:reason.trim()}
    if(!attempt.reason||attempt.reason.length>1000) return
    setRevokeAttempt(attempt);busyRef.current=true;setBusy(true);setMessage(null);setActionError(null)
    try {
      const saved=await revokeFinal(eventId,disciplineId,attempt.id,attempt.reason,accessToken)
      if(!mounted.current) return
      setRevokeAttempt(null);setReason('')
      setMessage(saved.status==='REVOKED'?`Финализация ревизии № ${saved.revision} снята. Снимок остался в истории.`:'Сервер не подтвердил снятие. Проверьте актуальный статус.')
      await reload()
    } catch(error) {
      if(!mounted.current) return
      setActionError(error)
      if(!keepAttemptAfterFailure(error)) setRevokeAttempt(null)
      await reload()
    } finally {busyRef.current=false;if(mounted.current) setBusy(false)}
  }
  return <div className={styles.page}>
    <div className={styles.actions}>
      <button className={styles.button} type="button" aria-pressed={tab==='current'} onClick={()=>setTab('current')}>Текущий зачёт</button>
      <button className={styles.button} type="button" aria-pressed={tab==='final'} onClick={()=>setTab('final')}>Подтверждённый итог</button>
      <button className={styles.button} type="button" disabled={busy||loading} onClick={()=>void reload()}>Обновить</button>
    </div>
    {final.kind==='loading'?<p role="status">Проверяем состояние итогов…</p>:final.kind==='error'?<><p>Статус финализации неизвестен. Действия заблокированы до успешной проверки.</p><Failure error={final.error}/></>:
      <p className={styles.notice}>{current?`Итог подтверждён: ревизия № ${current.revision}. Изменения защищены сервером.`:'Подтверждённого итога сейчас нет. Ранее снятые подтверждения можно посмотреть в истории.'}</p>}
    {tab==='current'?<>
      <p className={styles.hint}>Зачёт только по опубликованным заплывам. Пока опубликована не вся программа, это не окончательные итоги.</p>
      {live.kind==='loading'?<p role="status">Загружаем текущий зачёт…</p>:live.kind==='error'?<Failure error={live.error}/>:<StandingsTable data={live.data}/>}
    </>:final.kind==='ready'&&current?<Snapshot data={current}/>:final.kind==='ready'?<section className={styles.card}>Сохранённого активного итога пока нет. Текущая таблица не подставляется вместо него.</section>:null}
    {message?<p className={styles.notice} role="status">{message}</p>:null}
    {actionError?<Failure error={actionError}/>:null}
    {accessToken?<>
      <section className={styles.card}>
        <h2>Управление итогами дисциплины</h2>
        <p className={styles.hint}>Сервер проверит все заплывы и назначения. Подтверждение итогов дисциплины не меняет статус всего мероприятия.</p>
        {history.kind==='loading'?<p role="status">Проверяем доступ и историю…</p>:history.kind==='error'?<Failure error={history.error}/>:null}
        {canManage&&final.kind==='ready'&&!current?<form onSubmit={handleConfirm}>
          <label className={styles.checkbox}><input type="checkbox" checked={complete} onChange={e=>setComplete(e.target.checked)} disabled={busy||Boolean(confirmAttempt)} required={!confirmAttempt}/>
            <span>Подтверждаю, что вся программа этой дисциплины заведена и завершена, все заплывы и результаты проверены.</span></label>
          <label className={styles.field}>Примечание (необязательно)<textarea maxLength={500} rows={3} value={note} onChange={e=>setNote(e.target.value)} disabled={busy||Boolean(confirmAttempt)}/></label>
          {confirmAttempt?<p className={styles.notice}>Есть попытка с неопределённым исходом. При повторе сохраняются прежние requestId и примечание. Неизвестный исход не считается успехом.</p>:null}
          <button className={styles.button} type="submit" disabled={!canConfirm}>{busy?'Выполняем…':confirmAttempt?'Повторить ту же попытку':'Подтвердить итог дисциплины'}</button>
        </form>:null}
        {canManage&&(current||revokeAttempt)?<form onSubmit={handleRevoke}>
          <p>Снятие относится только к снимку <code>{revokeAttempt?.id??current?.id}</code>. История сохраняется.</p>
          <label className={styles.field}>Обязательная причина<textarea required maxLength={1000} rows={3} value={revokeAttempt?.reason??reason} onChange={e=>setReason(e.target.value)} disabled={busy||Boolean(revokeAttempt)}/></label>
          {revokeAttempt?<p className={styles.hint}>Повтор адресуется прежнему снимку, даже если другой пользователь уже создал новую ревизию.</p>:null}
          <button className={`${styles.button} ${styles.danger}`} type="submit" disabled={busy||loading||!(revokeAttempt?.reason??reason).trim()}>{busy?'Выполняем…':revokeAttempt?'Повторить снятие того же снимка':'Снять финализацию для исправлений'}</button>
        </form>:null}
      </section>
      <section className={styles.card}><h2>История подтверждений</h2>
        {history.kind==='ready'?history.data.length===0?<p>История подтверждений пока пуста.</p>:<div className={styles.history}>
          {[...history.data].sort((a,b)=>b.revision-a.revision).map(item=><details key={item.id}>
            <summary>Ревизия № {item.revision} · {item.status==='CONFIRMED'?'Подтверждена':'Снята'} · {formatTimestamp(item.confirmedAt)}</summary>
            <Snapshot data={item}/>
          </details>)}
        </div>:null}
      </section>
    </>:null}
  </div>
}
function StandingsPage({manage=false}:{manage?:boolean}) {
  const {eventId,publicSlug,disciplineId}=useParams<{eventId:string;publicSlug:string;disciplineId:string}>()
  const {session}=useAuth()
  const token=manage?session?.accessToken:undefined
  const key=`${manage?'manage':'public'}:${eventId??publicSlug??''}:${disciplineId??''}:${manage?session?.expiresAt??'none':''}`
  const [metadata,setMetadata]=useState<Resource<Metadata>>({kind:'loading'})
  const [retry,setRetry]=useState(0)
  useEffect(()=>{
    let active=true
    setMetadata({kind:'loading'})
    async function load() {
      if(!disciplineId||manage&&(!eventId||!token)||!manage&&!publicSlug) throw new Error('Недостаточно данных для открытия дисциплины.')
      if(manage) {
        const [event,disciplines]=await Promise.all([getEventById(eventId!,token),getEventDisciplines(eventId!,token!)])
        const discipline=disciplines.find(d=>d.id===disciplineId)
        if(!discipline) throw new Error('Дисциплина не принадлежит выбранному мероприятию или недоступна.')
        return {eventId:event.id,disciplineId,eventTitle:event.title,disciplineName:discipline.name,publicSlug:event.publicSlug,key}
      }
      const event=await getPublicEventDetails(publicSlug!)
      const discipline=event.disciplines.find(d=>d.id===disciplineId)
      if(!discipline) throw new Error('Опубликованная дисциплина не найдена.')
      return {eventId:event.id,disciplineId,eventTitle:event.title,disciplineName:discipline.name,publicSlug:event.publicSlug,key}
    }
    void capture(load).then(result=>{if(active) setMetadata(result)})
    return ()=>{active=false}
  },[eventId,publicSlug,disciplineId,manage,token,key,retry])
  const context=metadata.kind==='ready'&&metadata.data.key===key?metadata.data:null
  return <section className={styles.page}>
    <Link to={manage?`/organizer/events/${encodeURIComponent(eventId??'')}`:`/events/${encodeURIComponent(publicSlug??'')}`}>← К мероприятию</Link>
    <h1>{context?`Зачёт: ${formatHeatLabel(context.disciplineName)}`:'Зачёт дисциплины'}</h1>
    {context?<>
      <p>{context.eventTitle}</p>
      {context.publicSlug?<Link to={`/events/${encodeURIComponent(context.publicSlug)}/disciplines/${encodeURIComponent(context.disciplineId)}/results`}>Опубликованные результаты по заплывам</Link>:null}
      <StandingsWorkspace key={key} eventId={context.eventId} disciplineId={context.disciplineId} accessToken={token}/>
    </>:metadata.kind==='error'?<><Failure error={metadata.error}/><button className={styles.button} type="button" onClick={()=>setRetry(v=>v+1)}>Повторить загрузку</button></>:<p role="status">Загружаем дисциплину…</p>}
  </section>
}
export function PublicStandingsPage() {return <StandingsPage/>}
export function OrganizerStandingsPage() {return <StandingsPage manage/>}
