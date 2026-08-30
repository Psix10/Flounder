import { NavLink, Outlet, useNavigate } from 'react-router'
import { useAuth } from '../app/providers/AuthProvider'

export function AppLayout() {
  const {
    isAuthenticated,
    isPlatformAdmin,
    isOrganizer,
    isOperator,
    isParticipant,
    logout,
  } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    logout()
    navigate('/', { replace: true })
  }

  const navLinkClassName = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'nav-link nav-link-active' : 'nav-link'

  return (
    <div className="app-shell">
      <header className="site-header">
        <NavLink className="brand" to="/">
          Flounder
        </NavLink>

        <nav className="main-nav" aria-label="Основная навигация">
          <NavLink className={navLinkClassName} to="/" end>
            События
          </NavLink>

          {/* Участник: свои заявки */}
          {isAuthenticated && isParticipant ? (
            <NavLink className={navLinkClassName} to="/my/registrations">
              Мои заявки
            </NavLink>
          ) : null}

          {/* Организатор и Админ платформы: управление событиями */}
          {isPlatformAdmin || isOrganizer ? (
            <NavLink className={navLinkClassName} to="/organizer/events">
              Мои события
            </NavLink>
          ) : null}

          {/* Организатор и Админ платформы: рассмотрение заявок */}
          {isPlatformAdmin || isOrganizer ? (
            <NavLink
              className={navLinkClassName}
              to="/admin/registrations/review"
            >
              Заявки на рассмотрение
            </NavLink>
          ) : null}

          {/* Только Админ платформы: справочники */}
          {isPlatformAdmin ? (
            <NavLink className={navLinkClassName} to="/admin/sports">
              Виды спорта
            </NavLink>
          ) : null}

          {/* Оператор: обработка платежей */}
          {isPlatformAdmin || isOperator ? (
            <NavLink className={navLinkClassName} to="/operator/payments">
              Платежи
            </NavLink>
          ) : null}

          {isAuthenticated ? (
            <button
              className="nav-link nav-button"
              onClick={handleLogout}
              type="button"
            >
              Выйти
            </button>
          ) : (
            <NavLink className={navLinkClassName} to="/login">
              Войти
            </NavLink>
          )}
        </nav>
      </header>

      <main className="page-content">
        <Outlet />
      </main>
    </div>
  )
}