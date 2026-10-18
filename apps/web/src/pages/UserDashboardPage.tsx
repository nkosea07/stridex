import { Link } from 'react-router-dom'
import { Activity, BarChart3, Trophy } from 'lucide-react'
import { Heart, Radio, Settings } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { useAuth } from '@/contexts/AuthContext'

export function UserDashboardPage() {
  const { user } = useAuth()
  const { t } = useTranslation()
  const isSpectator = user?.role === 'SPECTATOR'
  const cards = isSpectator
    ? [
        { title: t('nav:followedAthletes'), description: t('nav:followedAthletesDescription'), icon: Heart, to: '/app/stats' },
        { title: t('nav:publicTracking'), description: t('nav:publicTrackingDescription'), icon: Radio, to: '/race/demo' },
        { title: t('nav:notifications'), description: t('nav:spectatorNotificationsDescription'), icon: Activity, to: '/notifications' },
      ]
    : [
        { title: t('nav:myRegistrations'), description: t('nav:myRegistrationsDescription'), icon: Activity, to: '/app/stats' },
        { title: t('nav:personalResults'), description: t('nav:personalResultsDescription'), icon: Trophy, to: '/app/stats' },
        { title: t('nav:myPerformance'), description: t('nav:myPerformanceDescription'), icon: BarChart3, to: '/app/stats' },
      ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{t(isSpectator ? 'nav:spectatorDashboard' : 'nav:myDashboard')}</h1>
        <p className="text-muted-foreground">
          {t(isSpectator ? 'nav:spectatorDashboardDescription' : 'nav:athleteDashboardDescription', { name: user?.firstName })}
        </p>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {cards.map((card) => (
          <Card key={card.title}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">{card.title}</CardTitle>
            </CardHeader>
            <CardContent className="space-y-3 text-sm text-muted-foreground">
              <div className="flex items-center gap-2">
                <card.icon className="h-4 w-4" />
                <span>{card.description}</span>
              </div>
              <Button asChild variant="link" className="h-auto p-0">
                <Link to={card.to}>{t('nav:open')}</Link>
              </Button>
            </CardContent>
          </Card>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle>{t(isSpectator ? 'nav:spectatorNextStepTitle' : 'nav:athleteNextStepTitle')}</CardTitle>
          <CardDescription>{t(isSpectator ? 'nav:spectatorNextStepDescription' : 'nav:athleteNextStepDescription')}</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          <Button asChild>
            <Link to={isSpectator ? '/race/demo' : '/app/stats'}>
              {t(isSpectator ? 'nav:openPublicTracking' : 'nav:openMyPerformance')}
            </Link>
          </Button>
          <Button asChild variant="outline">
            <Link to="/app/settings">
              <Settings className="mr-2 h-4 w-4" />
              {t('nav:accountSettings')}
            </Link>
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
