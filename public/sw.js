// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

const cacheName = 'mise-shell-v1'
const shell = ['/', '/manifest.webmanifest', '/app-icon.svg', '/assets/recipe-sprite.png', '/assets/dm-sans-latin.woff2', '/assets/dm-serif-display-latin.woff2', '/assets/dm-serif-display-italic-latin.woff2']

self.addEventListener('install', (event) => event.waitUntil(caches.open(cacheName).then((cache) => cache.addAll(shell))))
self.addEventListener('activate', (event) => event.waitUntil(caches.keys().then((keys) => Promise.all(keys.filter((key) => key !== cacheName).map((key) => caches.delete(key))))))
self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET' || new URL(event.request.url).pathname.startsWith('/api/')) return
  event.respondWith(fetch(event.request).then((response) => {
    const copy = response.clone()
    caches.open(cacheName).then((cache) => cache.put(event.request, copy))
    return response
  }).catch(() => caches.match(event.request).then((cached) => cached || caches.match('/'))))
})
