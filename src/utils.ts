// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

export const makeId = () => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

export const extractSharedUrl = (value: string) => value.match(/https?:\/\/[^\s<>"']+/i)?.[0]?.replace(/[\])},.;!?]+$/, '') || ''
