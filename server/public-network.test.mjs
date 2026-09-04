// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import assert from 'node:assert/strict'
import test from 'node:test'
import { privateAddress, resolvePublicUrl } from './public-network.mjs'

test('rejects non-public IPv4 and IPv6 address ranges', () => {
  const blocked = [
    '0.0.0.0',
    '10.0.0.1',
    '100.64.0.1',
    '127.0.0.1',
    '169.254.1.1',
    '172.16.0.1',
    '192.168.1.1',
    '224.0.0.1',
    '::',
    '::1',
    '::ffff:127.0.0.1',
    'fc00::1',
    'fd00::1',
    'fe80::1',
    'ff02::1',
  ]
  for (const address of blocked) assert.equal(privateAddress(address), true, address)
  assert.equal(privateAddress('8.8.8.8'), false)
  assert.equal(privateAddress('2606:4700:4700::1111'), false)
})

test('accepts only credential-free HTTP URLs resolving entirely to public addresses', async () => {
  const publicLookup = async () => [{ address: '93.184.216.34', family: 4 }]
  const mixedLookup = async () => [
    { address: '93.184.216.34', family: 4 },
    { address: '127.0.0.1', family: 4 },
  ]

  assert.equal((await resolvePublicUrl('https://example.com/recipe', publicLookup)).url.hostname, 'example.com')
  await assert.rejects(resolvePublicUrl('file:///etc/passwd', publicLookup), /Only http and https/)
  await assert.rejects(resolvePublicUrl('https://user:secret@example.com/', publicLookup), /credentials/)
  await assert.rejects(resolvePublicUrl('https://example.com/', mixedLookup), /Private network/)
  await assert.rejects(resolvePublicUrl('http://[::ffff:127.0.0.1]/', publicLookup), /Private network/)
  await assert.rejects(resolvePublicUrl('http://100.64.0.1/', publicLookup), /Private network/)
})
