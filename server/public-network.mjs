// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import dns from 'node:dns/promises'
import { Buffer } from 'node:buffer'
import ipaddr from 'ipaddr.js'
import { Agent, fetch } from 'undici'

const publicRange = (address) => {
  try {
    return ipaddr.process(address).range() === 'unicast'
  } catch {
    return false
  }
}

export const privateAddress = (address) => !publicRange(address)

export const resolvePublicUrl = async (value, lookup = dns.lookup) => {
  const url = new URL(value)
  if (!['http:', 'https:'].includes(url.protocol)) throw new Error('Only http and https links are supported')
  if (url.username || url.password) throw new Error('Links containing credentials are not supported')

  const hostname = url.hostname.replace(/^\[|\]$/g, '')
  let records
  if (ipaddr.isValid(hostname)) {
    records = [{ address: hostname, family: ipaddr.parse(hostname).kind() === 'ipv6' ? 6 : 4 }]
  } else {
    try {
      records = await lookup(hostname, { all: true, verbatim: true })
    } catch {
      throw new Error('That host could not be resolved')
    }
  }
  if (!records.length || records.some(({ address }) => privateAddress(address))) {
    throw new Error('Private network links are not supported')
  }
  return { url, records }
}

const pinnedDispatcher = (records) => new Agent({
  connect: {
    lookup(_hostname, options, callback) {
      const requestedFamily = Number(options?.family || 0)
      const candidates = records
        .map(({ address, family }) => ({ address, family: Number(family) }))
        .filter(({ family }) => !requestedFamily || family === requestedFamily)

      if (!candidates.length) {
        callback(new Error('No public address is available for that host'))
      } else if (options?.all) {
        callback(null, candidates)
      } else {
        callback(null, candidates[0].address, candidates[0].family)
      }
    },
  },
})

const readLimitedBody = async (response, limit, message) => {
  const declaredSize = Number(response.headers.get('content-length') || 0)
  if (declaredSize > limit) {
    await response.body?.cancel()
    throw new Error(message)
  }

  const chunks = []
  let size = 0
  for await (const chunk of response.body || []) {
    size += chunk.byteLength
    if (size > limit) {
      throw new Error(message)
    }
    chunks.push(Buffer.from(chunk))
  }
  return Buffer.concat(chunks, size)
}

export const requestPublicUrl = async (value, { headers, limit, timeout, tooLargeMessage }) => {
  const { url, records } = await resolvePublicUrl(value)
  const dispatcher = pinnedDispatcher(records)
  try {
    const response = await fetch(url, {
      dispatcher,
      headers,
      redirect: 'manual',
      signal: AbortSignal.timeout(timeout),
    })
    const redirect = response.status >= 300 && response.status < 400 && response.headers.get('location')
    const body = redirect ? (await response.body?.cancel(), Buffer.alloc(0)) : await readLimitedBody(response, limit, tooLargeMessage)
    return { body, headers: response.headers, status: response.status, url }
  } finally {
    await dispatcher.close()
  }
}
