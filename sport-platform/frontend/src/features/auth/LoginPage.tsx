import { FormEvent, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError } from '../../api/http'
import { useAuth } from '../../app/providers/AuthProvider'

type LoginLocationState = {
  from?: string
}

export function LoginPage() {
  const { isAuthenticated, login } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [errorMessage, setErrorMessage] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const state = location.state as LoginLocationState | null
  const returnTo = state?.from ?? '/my/registrations'

  // Если уже залогинен — делаем декларативный редирект, без вызова navigate() в рендере
  if (isAuthenticated) {
    return <Navigate to={returnTo} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setErrorMessage(null)
    setIsSubmitting(true)

    try {
      await login({
        email: email.trim(),
        password,
      })

      // navigate внутри обработчика события — это нормально
      navigate(returnTo, { replace: true })
    } catch (error) {
      const message =
        error instanceof ApiError && error.status === 401
          ? 'Проверьте email и пароль.'
          : error instanceof ApiError
          ? error.message
          : 'Не удалось выполнить вход. Попробуйте ещё раз.'

      setErrorMessage(message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="auth-section">
      <Link className="back-link" to="/">
        ← К событиям
      </Link>

      <div className="auth-card">
        <p className="eyebrow">Личный кабинет</p>

        <h1>Войти</h1>

        <p className="page-description">
          Войдите, чтобы подать заявку на дисциплину и посмотреть её статус.
        </p>

        <form className="auth-form" onSubmit={handleSubmit}>
          <label>
            <span>Email</span>
            <input
              autoComplete="email"
              disabled={isSubmitting}
              name="email"
              onChange={(event) => setEmail(event.target.value)}
              required
              type="email"
              value={email}
            />
          </label>

          <label>
            <span>Пароль</span>
            <input
              autoComplete="current-password"
              disabled={isSubmitting}
              name="password"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
          </label>

          {errorMessage ? (
            <p className="form-error" role="alert">
              {errorMessage}
            </p>
          ) : null}

          <button
            className="button button-primary"
            disabled={isSubmitting}
            type="submit"
          >
            {isSubmitting ? 'Входим…' : 'Войти'}
          </button>
        </form>
      </div>
    </section>
  )
}