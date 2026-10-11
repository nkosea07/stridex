import { notificationsApi } from '@/lib/api'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY as string | undefined,
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN as string | undefined,
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID as string | undefined,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET as string | undefined,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID as string | undefined,
  appId: import.meta.env.VITE_FIREBASE_APP_ID as string | undefined,
}

const vapidKey = import.meta.env.VITE_FIREBASE_VAPID_KEY as string | undefined

export function isWebPushConfigured(): boolean {
  return Boolean(firebaseConfig.apiKey && firebaseConfig.projectId && firebaseConfig.messagingSenderId && vapidKey)
}

export async function enableWebPush(): Promise<boolean> {
  if (!isWebPushConfigured() || typeof window === 'undefined' || !('Notification' in window)) {
    return false
  }

  const permission = await Notification.requestPermission()
  if (permission !== 'granted') {
    return false
  }

  const { initializeApp, getApps, getApp } = await import('firebase/app')
  const { getMessaging, getToken, onMessage } = await import('firebase/messaging')

  const app = getApps().length > 0 ? getApp() : initializeApp({
    apiKey: firebaseConfig.apiKey,
    authDomain: firebaseConfig.authDomain,
    projectId: firebaseConfig.projectId,
    storageBucket: firebaseConfig.storageBucket,
    messagingSenderId: firebaseConfig.messagingSenderId,
    appId: firebaseConfig.appId,
  })

  const messaging = getMessaging(app)
  const token = await getToken(messaging, { vapidKey })
  if (!token) {
    return false
  }

  await notificationsApi.registerDevice(token, 'web')
  onMessage(messaging, () => undefined)
  return true
}
