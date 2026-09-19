import { Navigate, Route, Routes } from 'react-router'
import { AppLayout } from '../components/AppLayout'
import { RequireRole } from '../components/RequireRole'
import { LoginPage } from '../features/auth/LoginPage'
import { RegistrationReviewPage } from '../features/admin/RegistrationReviewPage'
import { SportsAdminPage } from '../features/admin/SportsAdminPage'
import { EventDetailsPage } from '../features/events/EventDetailsPage'
import { EventsPage } from '../features/events/EventsPage'
import { OrganizerEventsPage } from '../features/organizer/OrganizerEventsPage'
import { OperatorPaymentsPage } from '../features/payments/OperatorPaymentsPage'
import { OperatorRegistrationPage } from '../features/payments/OperatorRegistrationPage'
import { PaymentPage } from '../features/payments/PaymentPage'
import { CompetitionUnitsPage } from '../features/results/CompetitionUnitsPage'
import { JudgePanelPage } from '../features/results/JudgePanelPage'
import { PublicDisciplineResultsPage } from '../features/results/PublicDisciplineResultsPage'
import { MyRegistrationsPage } from '../features/registrations/MyRegistrationsPage'
import { RegistrationPage } from '../features/registrations/RegistrationPage'

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        {/* Публичные страницы */}
        <Route index element={<EventsPage />} />

        <Route
          path="events/:eventCode"
          element={<EventDetailsPage />}
        />

        <Route
          path="events/:eventId/disciplines/:disciplineId/results"
          element={<PublicDisciplineResultsPage />}
        />

        <Route path="login" element={<LoginPage />} />

        {/* Участник */}
        <Route element={<RequireRole roles={['participant']} />}>
          <Route
            path="events/:eventCode/disciplines/:disciplineId/register"
            element={<RegistrationPage />}
          />

          <Route
            path="my/registrations"
            element={<MyRegistrationsPage />}
          />

          <Route
            path="my/registrations/:registrationId/payment"
            element={<PaymentPage />}
          />

          <Route
            path="payment-return"
            element={<PaymentPage />}
          />
        </Route>

        {/* Организатор и администратор платформы */}
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

        {/* Только администратор платформы */}
        <Route element={<RequireRole roles={['platform_admin']} />}>
          <Route path="admin/sports" element={<SportsAdminPage />} />
        </Route>

        {/* Оператор и администратор: платежи, заявки и результаты */}
        <Route
          element={
            <RequireRole roles={['platform_admin', 'operator']} />
          }
        >
          <Route
            path="operator/payments"
            element={<OperatorPaymentsPage />}
          />

          <Route
            path="operator/registrations/:registrationId"
            element={<OperatorRegistrationPage />}
          />

          <Route
            path="operator/results"
            element={<CompetitionUnitsPage />}
          />

          <Route
            path="operator/competition-units/:unitId"
            element={<JudgePanelPage />}
          />
        </Route>

        {/* Неизвестный маршрут */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  )
}