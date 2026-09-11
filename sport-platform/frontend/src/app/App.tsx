import { Navigate, Route, Routes } from 'react-router'
import { AppLayout } from '../components/AppLayout'
import { ProtectedRoute } from '../components/ProtectedRoute'
import { RequireRole } from '../components/RequireRole'
import { EventDetailsPage } from '../features/events/EventDetailsPage'
import { EventsPage } from '../features/events/EventsPage'
import { LoginPage } from '../features/auth/LoginPage'
import { MyRegistrationsPage } from '../features/registrations/MyRegistrationsPage'
import { PaymentPage } from '../features/payments/PaymentPage'
import { RegistrationPage } from '../features/registrations/RegistrationPage'
import { RegistrationReviewPage } from '../features/admin/RegistrationReviewPage'
import { SportsAdminPage } from '../features/admin/SportsAdminPage'
import { OrganizerEventsPage } from '../features/organizer/OrganizerEventsPage'
import { OperatorPaymentsPage } from '../features/payments/OperatorPaymentsPage'
import { JudgePanelPage } from '../features/results/JudgePanelPage'
import { OperatorRegistrationPage } from '../features/payments/OperatorRegistrationPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        {/* Публичные */}
        <Route index element={<EventsPage />} />
        <Route path="events/:eventCode" element={<EventDetailsPage />} />
        <Route path="login" element={<LoginPage />} />

        {/* Участник (PARTICIPANT) */}
        <Route element={<RequireRole roles={['participant']} />}>
          <Route
            path="events/:eventCode/disciplines/:disciplineId/register"
            element={<RegistrationPage />}
          />
          <Route path="my/registrations" element={<MyRegistrationsPage />} />
          <Route
            path="my/registrations/:registrationId/payment"
            element={<PaymentPage />}
          />
          <Route path="payment-return" element={<PaymentPage />} />
        </Route>

        {/* Организатор + Админ платформы: события и заявки */}
        <Route
          element={
            <RequireRole roles={['platform_admin', 'organizer']} />
          }
        >
          <Route
            path="organizer/events"
            element={<OrganizerEventsPage />}
          />
          <Route
            path="admin/registrations/review"
            element={<RegistrationReviewPage />}
          />
        </Route>

        {/* Только Админ платформы: справочники */}
        <Route element={<RequireRole roles={['platform_admin']} />}>
          <Route path="admin/sports" element={<SportsAdminPage />} />
        </Route>

        {/* Оператор (например, ручная обработка платежей + судейская панель) */}
        <Route
          element={
            <RequireRole roles={['platform_admin', 'operator']} />
          }
        >
          <Route path="operator/payments" element={<OperatorPaymentsPage />} />

          <Route
            path="operator/registrations/:registrationId"
            element={<OperatorRegistrationPage />}
          />

          <Route path="operator/units/:unitId" element={<JudgePanelPage />} />
        </Route>

        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}