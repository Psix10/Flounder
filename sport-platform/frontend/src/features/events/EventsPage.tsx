import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getPublicEvents } from '../../api/events.api'
import { ApiError } from '../../api/http'
import { ErrorState } from '../../components/ErrorState'
import { LoadingState } from '../../components/LoadingState'
import type { PublicEvent } from './event.types'

function formatDate(value: string | null) {
    if (!value) {
        return 'Не указано'
    }

    return new Intl.DateTimeFormat('ru-RU', {
        dateStyle: 'long',
        timeStyle: 'short',
        timeZone: 'Europe/Moscow',
    }).format(new Date(value))
}

function getStatusLabel(status: PublicEvent['status']) {
    const labels: Record<PublicEvent['status'], string> = {
        DRAFT: 'Черновик',
        PUBLISHED: 'Опубликовано',
        REGISTRATION_OPEN: 'Регистрация открыта',
        REGISTRATION_CLOSED: 'Регистрация закрыта',
        FINISHED: 'Завершено',
        CANCELLED: 'Отменено',
    }

    return labels[status]
}

export function EventsPage() {
    const [events, setEvents] = useState<PublicEvent[]>([])
    const [isLoading, setIsLoading] = useState(true)
    const [errorMessage, setErrorMessage] = useState<string | null>(null)

    async function loadEvents() {
        setIsLoading(true)
        setErrorMessage(null)

        try {
        const response = await getPublicEvents()
        setEvents(response)
        } catch (error) {
        const message =
            error instanceof ApiError
            ? error.message
            : 'Проверьте, что backend запущен и CORS разрешает http://localhost:5173.'

        setErrorMessage(message)
        } finally {
        setIsLoading(false)
        }
    }

    useEffect(() => {
        void loadEvents()
    }, [])

    return (
        <section>
        <p className="eyebrow">Flounder sport platform</p>

        <h1>Спортивные события</h1>

        <p className="page-description">
            Выберите соревнование, чтобы посмотреть дисциплины и подать заявку.
        </p>

        {isLoading ? <LoadingState message="Загружаем события…" /> : null}

        {!isLoading && errorMessage ? (
            <ErrorState message={errorMessage} onRetry={() => void loadEvents()} />
        ) : null}

        {!isLoading && !errorMessage && events.length === 0 ? (
            <div className="state-card">
            Сейчас нет опубликованных событий.
            </div>
        ) : null}

        {!isLoading && !errorMessage && events.length > 0 ? (
            <div className="event-grid">
            {events.map((event) => (
                <article className="event-card" key={event.id}>
                <div className="event-card-header">
                    <span className="status-badge">{getStatusLabel(event.status)}</span>
                </div>

                <h2>{event.title}</h2>

                {event.description ? (
                    <p className="event-description">{event.description}</p>
                ) : (
                    <p className="event-description">Описание пока не добавлено.</p>
                )}

                <dl className="event-meta">
                    <div>
                    <dt>Регистрация</dt>
                    <dd>
                        {formatDate(event.registrationOpenAt)} —{' '}
                        {formatDate(event.registrationCloseAt)}
                    </dd>
                    </div>

                    <div>
                    <dt>Дата события</dt>
                    <dd>
                        {formatDate(event.eventStartAt)} —{' '}
                        {formatDate(event.eventEndAt)}
                    </dd>
                    </div>
                </dl>

                <Link
                    className="button button-primary"
                    to={`/events/${event.publicSlug}`}
                >
                    Подробнее
                </Link>
                </article>
            ))}
            </div>
        ) : null}
        </section>
    )
}