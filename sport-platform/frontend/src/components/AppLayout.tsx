// OPERATOR_WORKSPACE_NAV_V1
import { NavLink, Outlet, useNavigate } from 'react-router'

import { useAuth } from '../app/providers/AuthProvider'
import styles from './AppLayout.module.css'

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

  function navLinkClassName({ isActive }: { isActive: boolean }) {
    return isActive
      ? `${styles.navLink} ${styles.navLinkActive}`
      : styles.navLink
  }

  const canManageEvents = isPlatformAdmin || isOrganizer
  const canProcessPayments = isPlatformAdmin || isOperator

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <NavLink className={styles.brand} to="/">
          Sport Gate
        </NavLink>

        <nav
          className={styles.navigation}
          aria-label="Основная навигация"
        >
          <NavLink className={navLinkClassName} to="/" end>
            События
          </NavLink>

          {isAuthenticated ? (
            <NavLink
              className={navLinkClassName}
              to="/my/profile"
            >
              Мой профиль
            </NavLink>
          ) : null}

          {isAuthenticated && isParticipant ? (
            <NavLink
              className={navLinkClassName}
              to="/my/registrations"
            >
              Мои заявки на участие
            </NavLink>
          ) : null}

          <NavLink
            className={navLinkClassName}
            to="/my/organization-requests"
          >
            {isAuthenticated
              ? 'Мои заявки организаций'
              : 'Стать организатором'}
          </NavLink>

          {canManageEvents ? (
            <NavLink
              className={navLinkClassName}
              to="/organizer/events"
            >
              Мои события
            </NavLink>
          ) : null}

          {isPlatformAdmin ? (
            <NavLink
              className={navLinkClassName}
              to="/admin/organization-requests"
            >
              Заявки организаций
            </NavLink>
          ) : null}

          {isAuthenticated && isOperator ? (
            <NavLink className={navLinkClassName} to="/operator/events">
              Работа оператора
            </NavLink>
          ) : null}

          {canProcessPayments ? (
            <NavLink
              className={navLinkClassName}
              to="/operator/payments"
            >
              Заявки и платежи
            </NavLink>
          ) : null}

          {isAuthenticated ? (
            <button
              className={`${styles.navLink} ${styles.navButton}`}
              onClick={handleLogout}
              type="button"
            >
              Выйти
            </button>
          ) : (
            <>
              <NavLink
                className={navLinkClassName}
                to="/login"
              >
                Войти
              </NavLink>

              <NavLink
                className={navLinkClassName}
                to="/register"
              >
                Создать аккаунт
              </NavLink>
            </>
          )}
        </nav>
      </header>

      <main className={styles.content}>
        <Outlet />
      </main>
    </div>
  )
}