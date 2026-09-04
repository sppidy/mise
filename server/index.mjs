// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import express from 'express'
import { promises as fs } from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'
import sharp from 'sharp'
import { createWorker, PSM } from 'tesseract.js'
import { extractRecipe, extractRecipeFromPhotoText, splitPhotoTextCards } from '../shared/recipe-parser.js'
import { requestPublicUrl } from './public-network.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const dataDirectory = process.env.DATA_DIR || path.join(root, 'data')
const imageDirectory = path.join(dataDirectory, 'images')
const stateFile = path.join(dataDirectory, 'state.json')
const port = Number(process.env.PORT || 8787)
const ollamaUrl = (process.env.OLLAMA_URL || '').trim().replace(/\/+$/, '')
const ollamaModel = (process.env.OLLAMA_MODEL || 'qwen3.5:4b').trim()
const app = express()
const nativeOrigins = new Set(['http://localhost', 'https://localhost', 'capacitor://localhost'])
for (const origin of (process.env.CORS_ORIGIN || '').split(',').map((value) => value.trim()).filter(Boolean)) nativeOrigins.add(origin)

app.disable('x-powered-by')
app.use((request, response, next) => {
  const origin = request.headers.origin
  if (origin && nativeOrigins.has(origin)) {
    response.setHeader('Access-Control-Allow-Origin', origin)
    response.setHeader('Vary', 'Origin')
    response.setHeader('Access-Control-Allow-Headers', 'content-type')
    response.setHeader('Access-Control-Allow-Methods', 'GET, PUT, POST, OPTIONS')
  }
  if (request.method === 'OPTIONS') return response.status(204).end()
  next()
})
app.use(express.json({ limit: '2mb' }))

const fetchPublicPage = async (value) => {
  let url = new URL(value)
  for (let redirect = 0; redirect < 4; redirect += 1) {
    const response = await requestPublicUrl(url, {
      limit: 2_000_000,
      timeout: 10_000,
      tooLargeMessage: 'That page is too large to import safely',
      headers: { 'user-agent': 'MiseRecipeBox/0.1 (+self-hosted recipe importer)', accept: 'text/html' },
    })
    if (response.status >= 300 && response.status < 400 && response.headers.get('location')) {
      url = new URL(response.headers.get('location'), url)
      continue
    }
    if (response.status < 200 || response.status >= 300) throw new Error(`The source returned HTTP ${response.status}`)
    const type = response.headers.get('content-type') || ''
    if (!type.includes('text/html')) throw new Error('That link does not point to a web page')
    return { html: response.body.toString('utf8'), finalUrl: url }
  }
  throw new Error('Too many redirects')
}

const cachePublicImage = async (value) => {
  let url = new URL(value)
  for (let redirect = 0; redirect < 4; redirect += 1) {
    const response = await requestPublicUrl(url, {
      limit: 5_000_000,
      timeout: 10_000,
      tooLargeMessage: 'The recipe image is too large to save',
      headers: { 'user-agent': 'MiseRecipeBox/0.1 (+self-hosted recipe importer)', accept: 'image/avif,image/webp,image/png,image/jpeg' },
    })
    if (response.status >= 300 && response.status < 400 && response.headers.get('location')) {
      url = new URL(response.headers.get('location'), url)
      continue
    }
    if (response.status < 200 || response.status >= 300) throw new Error(`The recipe image returned HTTP ${response.status}`)
    const type = (response.headers.get('content-type') || '').split(';')[0].toLowerCase()
    const extensions = { 'image/avif': '.avif', 'image/jpeg': '.jpg', 'image/png': '.png', 'image/webp': '.webp' }
    const extension = extensions[type]
    if (!extension) throw new Error('The recipe image format is not supported')
    const content = response.body
    const filename = `${createHash('sha256').update(content).digest('hex')}${extension}`
    await fs.mkdir(imageDirectory, { recursive: true })
    await fs.writeFile(path.join(imageDirectory, filename), content, { flag: 'wx' }).catch((error) => {
      if (error.code !== 'EEXIST') throw error
    })
    return `/api/images/${filename}`
  }
  throw new Error('Too many recipe image redirects')
}

const cachedImagePath = (value) => {
  if (typeof value !== 'string' || !value.startsWith('/api/images/')) return null
  const filename = path.basename(value)
  return filename === value.slice('/api/images/'.length) ? path.join(imageDirectory, filename) : null
}

const prepareRecipeCardImage = async (filename, fullFrame = false) => {
  const image = sharp(filename, { failOn: 'error' }).rotate()
  const metadata = await image.metadata()
  const width = metadata.width || 0
  const height = metadata.height || 0
  if (!width || !height || width * height > 20_000_000) throw new Error('The recipe image is too large to scan')

  // Most social recipe cards place text in a panel on the left. Keep the full
  // panel: trimming its margin can remove quantities and the first title word.
  const cropWidth = !fullFrame && width >= height * .65 ? Math.max(1, Math.round(width * .55)) : width
  const left = 0
  return sharp(filename, { failOn: 'error' })
    .rotate()
    .extract({ left, top: 0, width: Math.min(cropWidth, width - left), height })
    .resize({ width: 1200, withoutEnlargement: false })
    .grayscale()
    .normalize()
    .sharpen()
    .png()
    .toBuffer()
}

const scannedRecipeScore = (recipe) => ((recipe?.ingredients?.length || 0) * 4) + ((recipe?.steps?.length || 0) * 5)

const prepareVisionIngredientImage = async (filename, fullFrame = false) => {
  const image = sharp(filename, { failOn: 'error' }).rotate()
  const metadata = await image.metadata()
  const width = metadata.width || 0
  const height = metadata.height || 0
  if (!width || !height || width * height > 20_000_000) throw new Error('The recipe image is too large to scan')
  const cropWidth = !fullFrame && width >= height * .65 ? Math.max(1, Math.round(width * .55)) : width
  return sharp(filename, { failOn: 'error' })
    .rotate()
    .extract({ left: 0, top: 0, width: cropWidth, height: Math.max(1, Math.round(height * .58)) })
    .resize({ width: 900, withoutEnlargement: false })
    .jpeg({ quality: 86 })
    .toBuffer()
}

const visionIdentity = (value) => String(value || '').normalize('NFKD').replace(/[^a-z0-9]+/gi, ' ').trim().toLowerCase()

const modelFractionQuantity = (value) => {
  const match = String(value || '').trim().match(/^(1\s*\/\s*2|1\s*\/\s*4|3\s*\/\s*4|1\s*\/\s*3|2\s*\/\s*3|[¼½¾⅓⅔⅛⅜⅝⅞])\s*(cups?|tbsp|tsp|tablespoons?|teaspoons?)$/i)
  if (!match) return ''
  const fractions = { '1/2': '½', '1/4': '¼', '3/4': '¾', '1/3': '⅓', '2/3': '⅔' }
  const fraction = fractions[match[1].replace(/\s/g, '')] || match[1]
  const units = { cup: 'CUP', cups: 'CUPS', tablespoon: 'TBSP', tablespoons: 'TBSP', teaspoon: 'TSP', teaspoons: 'TSP' }
  return `${fraction} ${units[match[2].toLowerCase()] || match[2].toUpperCase()}`
}

const refinePhotoFractions = (recipe, modelResult) => {
  const corrections = []
  for (const card of modelResult?.cards || []) {
    for (const item of card?.fractions || []) {
      const quantity = modelFractionQuantity(item?.fraction_quantity)
      if (!quantity) continue
      corrections.push({ group: visionIdentity(card.title), name: visionIdentity(item.ingredient_name), quantity })
    }
  }
  if (!corrections.length) return recipe

  return {
    ...recipe,
    ingredients: recipe.ingredients.map((ingredient) => {
      const currentQuantity = String(ingredient.quantity || '').trim()
      if (!/^[¼½¾⅓⅔⅛⅜⅝⅞]/.test(currentQuantity) && !/^2\s+(?:CUP|TBSP|TSP)$/i.test(currentQuantity)) return ingredient
      const name = visionIdentity(ingredient.name)
      const group = visionIdentity(ingredient.group)
      const exact = corrections.find((item) => item.name === name && item.group === group)
      const nameMatches = corrections.filter((item) => item.name === name)
      const correction = exact || (nameMatches.length === 1 ? nameMatches[0] : null)
      return correction ? { ...ingredient, quantity: correction.quantity } : ingredient
    }),
  }
}

const visionFractionCorrections = async (cards) => {
  if (!ollamaUrl || !ollamaModel || !cards.length) return null
  const images = await Promise.all(cards.map(async ({ filename, fullFrame }) =>
    (await prepareVisionIngredientImage(filename, fullFrame)).toString('base64')))
  const ingredient = {
    type: 'object',
    properties: { ingredient_name: { type: 'string' }, fraction_quantity: { type: 'string' } },
    required: ['ingredient_name', 'fraction_quantity'],
  }
  const card = {
    type: 'object',
    properties: { title: { type: 'string' }, fractions: { type: 'array', items: ingredient } },
    required: ['title', 'fractions'],
  }
  const format = { type: 'object', properties: { cards: { type: 'array', items: card } }, required: ['cards'] }
  const response = await fetch(`${ollamaUrl}/api/chat`, {
    method: 'POST',
    signal: AbortSignal.timeout(90_000),
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({
      model: ollamaModel,
      stream: false,
      think: false,
      format,
      options: { temperature: 0, num_ctx: 24_576 },
      messages: [{
        role: 'user',
        content: 'These are separate recipe-card ingredient panels in order. Return only ingredients whose printed amount contains a fraction such as ¼ or ½. ingredient_name must be the full ingredient words after the quantity and unit; fraction_quantity must be only the fraction plus unit, such as "½ cup". Copy ¼ versus ½ exactly. Ignore whole-number and unmeasured ingredients.',
        images,
      }],
    }),
  })
  if (!response.ok) throw new Error(`Local vision model returned HTTP ${response.status}`)
  const body = await response.json()
  const content = body?.message?.content
  if (typeof content !== 'string' || content.length > 200_000) throw new Error('Local vision model returned an invalid response')
  return JSON.parse(content)
}

const scanCachedPhotoRecipe = async (recipe) => {
  if (recipe.postMedia !== 'photo') return null
  const candidates = [...new Set([...(recipe.postImageUrls || []), recipe.imageUrl])]
    .map(cachedImagePath)
    .filter(Boolean)
    .slice(0, 12)
  if (!candidates.length) return null

  const worker = await createWorker('eng')
  try {
    await worker.setParameters({ tessedit_pageseg_mode: PSM.SPARSE_TEXT, preserve_interword_spaces: '1' })
    const pages = []
    const recipeCards = []
    for (const filename of candidates) {
      const panelResult = await worker.recognize(await prepareRecipeCardImage(filename))
      const panelPages = panelResult.data.text.trim().length >= 20 ? splitPhotoTextCards(panelResult.data.text) : []
      const panelRecipe = panelPages.length ? extractRecipeFromPhotoText(panelPages, recipe.sourceUrl) : null
      let selectedPages = panelPages
      let selectedRecipe = panelRecipe
      let fullFrame = false

      // Covers and less common full-width/right-panel cards may contain no
      // useful text in the usual left crop. Fall back to a full-frame scan,
      // choosing it only when it exposes a richer recipe. When neither scan is
      // a recipe, retain the full frame so a cover title can still be used.
      if (!panelRecipe || scannedRecipeScore(panelRecipe) < 18) {
        const fullResult = await worker.recognize(await prepareRecipeCardImage(filename, true))
        const fullPages = fullResult.data.text.trim().length >= 20 ? splitPhotoTextCards(fullResult.data.text) : []
        const fullRecipe = fullPages.length ? extractRecipeFromPhotoText(fullPages, recipe.sourceUrl) : null
        if (scannedRecipeScore(fullRecipe) > scannedRecipeScore(panelRecipe) || (!fullRecipe && !panelRecipe)) {
          selectedPages = fullPages
          selectedRecipe = fullRecipe
          fullFrame = true
        }
      }
      pages.push(...selectedPages)
      if (selectedRecipe?.ingredients?.length) recipeCards.push({ filename, fullFrame })
    }
    const scanned = extractRecipeFromPhotoText(pages, recipe.sourceUrl)
    if (!scanned || !recipeCards.length || !ollamaUrl) return scanned
    const modelResult = await visionFractionCorrections(recipeCards).catch((error) => {
      console.warn('Local vision fraction refinement failed:', error instanceof Error ? error.message : error)
      return null
    })
    return modelResult ? refinePhotoFractions(scanned, modelResult) : scanned
  } finally {
    await worker.terminate()
  }
}

const socialPhotoTitle = (recipe) => {
  const quoted = recipe.description?.match(/["“]([^"”]+)/)?.[1] || recipe.title?.match(/["“]([^"”]+)/)?.[1] || ''
  return quoted.match(/^([A-Z0-9][A-Z0-9 &'’+-]{6,80}?)(?=\s+[A-Z][a-z]|\s*$)/)?.[1]?.trim() || ''
}

app.use('/api/images', express.static(imageDirectory, { dotfiles: 'deny', fallthrough: false, immutable: true, maxAge: '1y' }))

app.get('/api/health', (_request, response) => response.json({ ok: true }))

const legalDocuments = new Map([
  ['LICENSE', 'LICENSE'],
  ['LICENSE-EXCEPTION.md', 'LICENSE-EXCEPTION.md'],
  ['NOTICE', 'NOTICE'],
  ['PRIVACY.md', 'PRIVACY.md'],
  ['THIRD_PARTY_NOTICES.md', 'THIRD_PARTY_NOTICES.md'],
])
app.use('/legal/THIRD_PARTY_LICENSES', express.static(path.join(root, 'THIRD_PARTY_LICENSES'), { dotfiles: 'deny', index: false }))
app.get('/legal/:document', (request, response) => {
  const filename = legalDocuments.get(request.params.document)
  if (!filename) return response.status(404).send('Not found')
  response.type(filename.endsWith('.md') ? 'text/markdown' : 'text/plain').sendFile(path.join(root, filename))
})

app.get('/api/state', async (_request, response) => {
  try {
    response.type('json').send(await fs.readFile(stateFile, 'utf8'))
  } catch (error) {
    if (error.code === 'ENOENT') return response.status(404).json({ error: 'No recipe box has been created yet' })
    response.status(500).json({ error: 'Could not read the recipe box' })
  }
})

app.put('/api/state', async (request, response) => {
  const state = request.body
  if (!state || !Array.isArray(state.recipes) || !Array.isArray(state.grocery) || !Array.isArray(state.collections)) {
    return response.status(400).json({ error: 'Invalid recipe box data' })
  }
  await fs.mkdir(dataDirectory, { recursive: true })
  const temporaryFile = `${stateFile}.next`
  await fs.writeFile(temporaryFile, JSON.stringify(state, null, 2))
  await fs.rename(temporaryFile, stateFile)
  response.status(204).end()
})

app.post('/api/import', async (request, response) => {
  try {
    const { html, finalUrl } = await fetchPublicPage(request.body?.url)
    const recipe = extractRecipe(html, finalUrl.toString())
    const sourceImages = [...new Set([recipe.imageUrl, ...(recipe.postImageUrls || [])].filter(Boolean))]
    const cachedImages = new Map()
    await Promise.all(sourceImages.map(async (imageUrl) => {
      try { cachedImages.set(imageUrl, await cachePublicImage(imageUrl)) } catch { /* Keep the source image when caching is unavailable. */ }
    }))
    if (recipe.imageUrl) recipe.imageUrl = cachedImages.get(recipe.imageUrl) || recipe.imageUrl
    if (recipe.postImageUrls?.length) {
      recipe.postImageUrls = recipe.postImageUrls.map((imageUrl) => cachedImages.get(imageUrl) || imageUrl)
    }
    if (!recipe.ingredients?.length && !recipe.steps?.length) {
      const scanned = await scanCachedPhotoRecipe(recipe).catch((error) => {
        console.warn('Photo recipe OCR failed:', error instanceof Error ? error.message : error)
        return null
      })
      if (scanned?.ingredients?.length || scanned?.steps?.length) {
        return response.json({
          ...recipe,
          ...scanned,
          title: socialPhotoTitle(recipe) || scanned.title,
          sourceUrl: recipe.sourceUrl,
          imageUrl: recipe.imageUrl,
          postImageUrls: recipe.postImageUrls,
          postMedia: recipe.postMedia,
          tags: [...new Set([...(recipe.tags || []), ...(scanned.tags || [])])].slice(0, 4),
        })
      }
      return response.status(422).json({
        error: 'No recipe was found in the caption or public pinned comments',
        preview: {
          title: recipe.title,
          description: recipe.description,
          source: recipe.source,
          sourceUrl: recipe.sourceUrl,
          imageUrl: recipe.imageUrl,
          postImageUrls: recipe.postImageUrls,
          postMedia: recipe.postMedia,
          tags: recipe.tags,
        },
      })
    }
    response.json(recipe)
  } catch (error) {
    response.status(422).json({ error: error instanceof Error ? error.message : 'Could not import that link' })
  }
})

const dist = path.join(root, 'dist')
app.use(express.static(dist))
app.use(async (request, response, next) => {
  if (request.path.startsWith('/api/')) return next()
  try {
    await fs.access(path.join(dist, 'index.html'))
    response.sendFile(path.join(dist, 'index.html'))
  } catch {
    response.status(404).send('Build the web app with `npm run build` first.')
  }
})

app.use((error, _request, response, _next) => {
  console.error(error)
  response.status(500).json({ error: 'Something went wrong' })
})

const server = app.listen(port, '0.0.0.0', () => console.log(`Mise is listening on http://0.0.0.0:${port}`))
const shutdown = () => server.close(() => process.exit(0))
process.on('SIGTERM', shutdown)
process.on('SIGINT', shutdown)
