#!/usr/bin/env node
// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import fs from 'node:fs'
import path from 'node:path'

const [, , reference, actual, diffPath = 'android/build/visual-regression/diff.png'] = process.argv
if (!reference || !actual) {
  console.error('Usage: compare-android-screenshots.mjs <reference.png> <actual.png> [diff.png]')
  process.exit(2)
}

const readPng = async (file) => {
  const { PNG } = await import('pngjs')
  return new Promise((resolve, reject) => {
    fs.createReadStream(file).pipe(new PNG()).on('parsed', function () { resolve(this) }).on('error', reject)
  })
}
const [left, right] = await Promise.all([readPng(reference), readPng(actual)])
if (left.width !== right.width || left.height !== right.height) {
  console.error(`dimension mismatch: ${left.width}x${left.height} vs ${right.width}x${right.height}`)
  process.exit(1)
}
const { PNG } = await import('pngjs')
const diff = new PNG({ width: left.width, height: left.height })
let mismatched = 0
for (let i = 0; i < left.data.length; i += 4) {
  const distance = Math.max(
    Math.abs(left.data[i] - right.data[i]),
    Math.abs(left.data[i + 1] - right.data[i + 1]),
    Math.abs(left.data[i + 2] - right.data[i + 2]),
    Math.abs(left.data[i + 3] - right.data[i + 3]),
  )
  const changed = distance > 25
  if (changed) mismatched += 1
  diff.data[i] = changed ? 255 : 0
  diff.data[i + 1] = changed ? 0 : 0
  diff.data[i + 2] = changed ? 0 : 0
  diff.data[i + 3] = changed ? 255 : 0
}
fs.mkdirSync(path.dirname(diffPath), { recursive: true })
diff.pack().pipe(fs.createWriteStream(diffPath))
const ratio = mismatched / (left.width * left.height)
console.log(JSON.stringify({ width: left.width, height: left.height, mismatched, ratio, threshold: 0.1, maxRatio: 0.005, pass: ratio <= 0.005 }, null, 2))
process.exitCode = ratio <= 0.005 ? 0 : 1
