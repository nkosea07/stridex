import { Link } from 'react-router-dom'
import { ArrowLeft, ShieldCheck } from 'lucide-react'
import { API_CONTRACT } from '@race-tracker/contracts'
import { Button } from '@/components/ui/button'

const sections: Array<{ title: string; body: string[] }> = [
  {
    title: '1. Who we are',
    body: [
      'Race Tracker ("we") operates a race registration, timing, and live-tracking platform for organizers, athletes, and spectators.',
      'This policy explains what personal data we process, why, how long we keep it, and the choices available to you.',
    ],
  },
  {
    title: '2. Data we collect',
    body: [
      'Account data: name, email address, phone number, role, and authentication metadata.',
      'Registration data: race entries, category, bib number, emergency contact, and any medical information you choose to provide to organizers.',
      'Race data: GPS location traces, checkpoint and timing records, results, and derived statistics while you participate in a race.',
      'Technical data: device push tokens, session tokens, and security/audit records.',
      'Media: photos or videos you or organizers upload to a race gallery.',
    ],
  },
  {
    title: '3. How we use it',
    body: [
      'To provide registration, timing, results, and live tracking to organizers and spectators.',
      'To send transactional notifications (for example registration confirmations, timing alerts, and emergency alerts).',
      'To keep the platform secure and to meet legal obligations.',
    ],
  },
  {
    title: '4. Legal bases',
    body: [
      'We process data to perform our contract with you, to comply with legal obligations, and on the basis of legitimate interests for safety and platform integrity. Where required, we rely on your consent.',
    ],
  },
  {
    title: '5. Sharing',
    body: [
      'Race organizers receive the registration and timing data needed to run their events. Public spectator pages are designed to be PII-free and do not expose email, phone, emergency, medical, or audit data.',
      'We use infrastructure and messaging sub-processors (hosting, email, push, payments) that process data on our behalf under contract.',
    ],
  },
  {
    title: '6. Retention',
    body: [
      'We keep personal data only as long as needed for the purposes above. When you delete your account, we deactivate it immediately and erase related personal data after a grace period, except where we must retain records to meet legal or financial obligations or to preserve append-only security and safety records.',
    ],
  },
  {
    title: '7. Your rights',
    body: [
      'You can access, correct, export, and request deletion of your personal data from your account settings. Export and deletion are available directly in the app; you may also contact us.',
    ],
  },
  {
    title: '8. Security',
    body: [
      'We use access controls, hashed credentials, scoped sessions, and audit logging to protect your data. No method of transmission or storage is completely secure.',
    ],
  },
  {
    title: '9. Changes',
    body: [
      'We may update this policy. When we do, we publish a new version and update the version and date shown below.',
    ],
  },
]

export function PrivacyPolicyPage() {
  const version = API_CONTRACT.legal?.privacyPolicyVersion ?? '1.0'

  return (
    <div className="min-h-screen bg-background">
      <div className="mx-auto max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <div className="mb-6">
          <Button asChild variant="ghost" className="mb-6 -ml-2 text-muted-foreground">
            <Link to="/">
              <ArrowLeft className="mr-2 h-4 w-4" />
              Back
            </Link>
          </Button>
          <div className="flex items-center gap-3">
            <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary text-primary-foreground">
              <ShieldCheck className="h-6 w-6" />
            </span>
            <div>
              <h1 className="text-2xl font-bold text-foreground sm:text-3xl">Privacy Policy</h1>
              <p className="mt-1 text-sm text-muted-foreground">Version {version}</p>
            </div>
          </div>
        </div>

        <article className="space-y-7">
          {sections.map((section) => (
            <section key={section.title} className="space-y-2">
              <h2 className="text-lg font-semibold text-foreground">{section.title}</h2>
              {section.body.map((paragraph) => (
                <p key={paragraph} className="text-sm leading-6 text-muted-foreground">
                  {paragraph}
                </p>
              ))}
            </section>
          ))}
        </article>

        <p className="mt-10 border-t border-border pt-6 text-sm text-muted-foreground">
          See also our account settings for data export and account deletion, and contact the race organizer for
          event-specific questions.
        </p>
      </div>
    </div>
  )
}
