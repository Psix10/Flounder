// SWIM_LABELS_V1
import { formatHeatLabel } from '../../lib/heat-labels.mjs'
import { useState } from 'react'
import type { Standings } from '../../api/standings.api'
import { categoryOptions, filterStandings } from '../../lib/standings-ui.mjs'
import styles from './Standings.module.css'
const statuses:Record<string,string>={VALID:'Учтён',DID_NOT_START:'DNS — не стартовал',DID_NOT_FINISH:'DNF — не финишировал',DISQUALIFIED:'DSQ — дисквалифицирован'}
export function StandingsTable({data}:{data:Standings}) {
  const [selected,setSelected]=useState('')
  const options=categoryOptions(data.entries)
  const category=selected==='__legacy__'||options.includes(selected)?selected:''
  const entries=filterStandings(data.entries,category)
  return <section className={styles.card}>
    <p>
      Результат: {data.resultType === 'TIME' ? 'время' : data.resultType === 'POINTS' ? 'очки' : 'тип не распознан'}
      {' · Порядок: '}
      {data.rankingStrategy === 'ASC' ? 'меньшее значение — лучше' : data.rankingStrategy === 'DESC' ? 'большее значение — лучше' : 'порядок не распознан'}
      {' · Равные результаты: '}
      {data.tiePolicy === 'COMPETITION' ? 'одинаковое место' : 'правило не распознано'}
    </p>
    <p className={styles.hint}>Место в заплыве, общем зачёте и категории — разные значения. Фильтр не пересчитывает места.</p>
    <label className={styles.field}>Категория
      <select value={category} onChange={e=>setSelected(e.target.value)}>
        <option value="">Все категории</option>
        {options.map(code=><option key={code} value={code}>{code}</option>)}
        {data.entries.some(e=>e.categoryCode==null)?<option value="__legacy__">Без категории</option>:null}
      </select>
    </label>
    {entries.length===0?<p>Опубликованных строк для выбранного просмотра нет.</p>:<div className={styles.tableWrap}>
      <table className={styles.table}><caption>Показано {entries.length} из {data.entries.length} строк</caption>
        <thead><tr><th>Общее место</th><th>Место в категории</th><th>Категория</th><th>Участник</th><th>Клуб</th><th>Заплыв</th><th>Место в заплыве</th><th>Группа посева</th><th>Результат</th><th>Статус</th></tr></thead>
        <tbody>{entries.map((e,index)=><tr key={`${e.competitionUnitId}:${index}`}>
          <td>{e.overallPlace??'—'}</td><td>{e.categoryPlace??'—'}</td><td>{e.categoryCode??'—'}</td>
          <td>{e.participantName}</td><td>{e.clubName??'—'}</td><td>{formatHeatLabel(e.competitionUnitLabel)}</td>
          <td>{e.unitPlace??'—'}</td><td>{e.seedingGroupCode??'—'}</td>
          <td>{e.status==='VALID'?(e.rawValue??'—'):'—'}</td><td>{statuses[e.status]??e.status}</td>
        </tr>)}</tbody>
      </table>
    </div>}
  </section>
}
