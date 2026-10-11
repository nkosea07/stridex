/* eslint-env serviceworker */
/* global firebase */
// Firebase Cloud Messaging background service worker.
// Configuration is pushed from the app after registration so no secrets are committed here.

importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.2/firebase-messaging-compat.js');

let messaging = null;

function initializeMessaging(config) {
  if (!config || !config.apiKey || !config.projectId || !config.messagingSenderId || !config.appId) {
    return;
  }
  if (!firebase.apps.length) {
    firebase.initializeApp(config);
  }
  messaging = firebase.messaging();
  messaging.onBackgroundMessage((payload) => {
    const notification = payload.notification || {};
    self.registration.showNotification(notification.title || 'Race Tracker', {
      body: notification.body || '',
      data: payload.data || {},
    });
  });
}

self.addEventListener('message', (event) => {
  if (event.data && event.data.type === 'FIREBASE_CONFIG') {
    initializeMessaging(event.data.config);
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((clients) => {
      const url = (event.notification.data && event.notification.data.url) || '/';
      for (const client of clients) {
        if ('focus' in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    })
  );
});
