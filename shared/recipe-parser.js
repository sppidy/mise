// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

const entityNames = {
  amp: '&',
  apos: "'",
  gt: '>',
  hellip: '…',
  ldquo: '“',
  lsquo: '‘',
  lt: '<',
  mdash: '—',
  nbsp: ' ',
  ndash: '–',
  quot: '"',
  rdquo: '”',
  rsquo: '’',
}

const decodeEntities = (value = '') => String(value).replace(/&(#(?:x[0-9a-f]+|\d+)|[a-z]+);/gi, (match, entity) => {
  if (entity[0] !== '#') return entityNames[entity.toLowerCase()] ?? match
  const hexadecimal = entity[1]?.toLowerCase() === 'x'
  const codePoint = Number.parseInt(entity.slice(hexadecimal ? 2 : 1), hexadecimal ? 16 : 10)
  try { return Number.isFinite(codePoint) ? String.fromCodePoint(codePoint) : match } catch { return match }
})

const text = (value = '') => decodeEntities(String(value)
  .replace(/<br\s*\/?\s*>/gi, ' ')
  .replace(/<[^>]+>/g, ' '))
  .replace(/\s+/g, ' ')
  .trim()

const structuredText = (value = '') => decodeEntities(String(value)
  .replace(/<br\s*\/?\s*>/gi, '\n')
  .replace(/<[^>]+>/g, ' '))
  .replace(/\r\n?/g, '\n')
  .replace(/[\t\f\v ]+/g, ' ')
  .replace(/ *\n */g, '\n')
  .replace(/\n{3,}/g, '\n\n')
  .trim()

const limit = (value, length) => {
  const clean = text(value)
  if (clean.length <= length) return clean
  return `${clean.slice(0, length - 1).replace(/\s+\S*$/, '')}…`
}

const attribute = (tag, name) => {
  const match = tag.match(new RegExp(`\\b${name}\\s*=\\s*(["'])([\\s\\S]*?)\\1`, 'i'))
  return match?.[2] || ''
}

const metaContent = (html, key) => {
  for (const tag of html.match(/<meta\b[^>]*>/gi) || []) {
    if ((attribute(tag, 'property') || attribute(tag, 'name') || attribute(tag, 'itemprop')).toLowerCase() === key.toLowerCase()) {
      return text(attribute(tag, 'content'))
    }
  }
  return ''
}

const metaStructuredContent = (html, key) => {
  for (const tag of html.match(/<meta\b[^>]*>/gi) || []) {
    if ((attribute(tag, 'property') || attribute(tag, 'name') || attribute(tag, 'itemprop')).toLowerCase() === key.toLowerCase()) {
      return structuredText(attribute(tag, 'content'))
    }
  }
  return ''
}

const jsonScriptValues = (html) => {
  const values = []
  for (const script of html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)) {
    const tag = `<script${script[1]}>`
    const type = attribute(tag, 'type').toLowerCase().split(';')[0].trim()
    if (type !== 'application/json' && type !== 'application/ld+json') continue
    const source = script[2].trim().replace(/^<!--\s*/, '').replace(/\s*-->$/, '')
    if (!source || source.length > 2_000_000) continue
    try { values.push(JSON.parse(source)) } catch { /* malformed or non-JSON script data */ }
  }
  return values
}

const recipeNode = (value) => {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = recipeNode(item)
      if (found) return found
    }
  }
  if (value && typeof value === 'object') {
    const type = value['@type']
    if (type === 'Recipe' || (Array.isArray(type) && type.includes('Recipe'))) return value
    for (const key of ['@graph', 'mainEntity', 'mainEntityOfPage']) {
      const found = recipeNode(value[key])
      if (found) return found
    }
  }
  return null
}

const durationMinutes = (value) => {
  if (!value) return 0
  const match = String(value).match(/P(?:([0-9]+)D)?T?(?:([0-9]+)H)?(?:([0-9]+)M)?/i)
  if (!match) return Number.parseInt(value, 10) || 0
  return (Number(match[1] || 0) * 1440) + (Number(match[2] || 0) * 60) + Number(match[3] || 0)
}

const imageValue = (value) => {
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return imageValue(value[0])
  return value?.url || value?.contentUrl || ''
}

const publicUrl = (value, base) => {
  try {
    if (!String(value || '').trim()) return undefined
    const url = new URL(value, base)
    return ['http:', 'https:'].includes(url.protocol) ? url.toString() : undefined
  } catch { return undefined }
}

const linkedImage = (html) => {
  for (const tag of html.match(/<link\b[^>]*>/gi) || []) {
    const rel = attribute(tag, 'rel').toLowerCase().split(/\s+/)
    if (rel.includes('image_src')) return attribute(tag, 'href')
  }
  return ''
}

const videoPoster = (html) => {
  for (const tag of html.match(/<video\b[^>]*>/gi) || []) {
    const poster = attribute(tag, 'poster')
    if (poster) return poster
  }
  return ''
}

const embeddedImage = (values, base) => {
  const candidates = new Map()
  const imageKeyPattern = /^(?:contenturl|cover|coverimage|coverurl|displayimage|displayurl|dynamiccover|image|imageurl|origincover|poster|posterurl|thumbnail|thumbnailsrc|thumbnailurl)$/
  const contextualUrlKeyPattern = /^(?:src|url)$/
  const unwantedContextPattern = /(?:avatar|badge|comment|emoji|icon|logo|placeholder|profile|sprite|watermark)/i
  const imageContextPattern = /(?:cover|display.?image|image.?versions|media|photo|picture|poster|recipe|thumbnail|video)/i

  const add = (value, object, path, priority) => {
    if (typeof value !== 'string' || unwantedContextPattern.test(path)) return
    const url = publicUrl(decodeEntities(value), base)
    if (!url) return
    const width = Number(object?.width || object?.image_width || object?.imageWidth || object?.widthPixels || 0)
    const height = Number(object?.height || object?.image_height || object?.imageHeight || object?.heightPixels || 0)
    const areaBonus = width > 0 && height > 0 ? Math.min(Math.log2(width * height), 24) : 0
    const contextBonus = /(?:media|post|recipe|video)/i.test(path) ? 8 : 0
    const score = priority + areaBonus + contextBonus
    if (score > (candidates.get(url) || 0)) candidates.set(url, score)
  }

  for (const root of values) {
    let visited = 0
    const seen = new WeakSet()
    const visit = (value, path = [], depth = 0) => {
      if (!value || typeof value !== 'object' || depth > 35 || visited >= 40_000 || seen.has(value)) return
      seen.add(value)
      visited += 1
      const pathLabel = path.join('.')

      if (!Array.isArray(value)) {
        for (const [key, child] of Object.entries(value)) {
          const normalized = String(key).replace(/[^a-z0-9]/gi, '').toLowerCase()
          if (imageKeyPattern.test(normalized)) add(child, value, `${pathLabel}.${key}`, 70)
          else if (contextualUrlKeyPattern.test(normalized) && imageContextPattern.test(pathLabel)) add(child, value, `${pathLabel}.${key}`, 55)
        }
      }

      for (const [key, child] of Object.entries(value)) visit(child, [...path.slice(-7), key], depth + 1)
    }
    visit(root)
  }

  return [...candidates.entries()].sort((left, right) => right[1] - left[1])[0]?.[0]
}

const instagramPostMedia = (jsonValues, url) => {
  let parsedUrl
  try { parsedUrl = new URL(url) } catch { return undefined }
  const shortcode = parsedUrl.pathname.match(/^\/(?:p|reels?|tv)\/([^/]+)/i)?.[1]
  if (!shortcode || !/(?:^|\.)instagram\.com$/i.test(parsedUrl.hostname)) return undefined
  const requestedIndex = Math.max(0, (Number.parseInt(parsedUrl.searchParams.get('img_index') || '1', 10) || 1) - 1)
  const matches = []

  for (const root of jsonValues) {
    let visited = 0
    const seen = new WeakSet()
    const visit = (value, depth = 0) => {
      if (!value || typeof value !== 'object' || depth > 35 || visited >= 40_000 || seen.has(value)) return
      seen.add(value)
      visited += 1
      if (!Array.isArray(value) && value.code === shortcode) matches.push(value)
      for (const child of Object.values(value)) visit(child, depth + 1)
    }
    visit(root)
  }

  const post = matches
    .flatMap((value) => [value.if_not_gated_logged_out, value])
    .filter(Boolean)
    .sort((left, right) => (Array.isArray(right.carousel_media) ? 2 : right.image_versions2 ? 1 : 0) - (Array.isArray(left.carousel_media) ? 2 : left.image_versions2 ? 1 : 0))[0]
  if (!post) return undefined
  const carousel = Array.isArray(post.carousel_media) && post.carousel_media.length ? post.carousel_media : null
  const media = carousel
    ? carousel[Math.min(requestedIndex, carousel.length - 1)]
    : post
  const mediaImage = (item) => publicUrl(item?.image_versions2?.candidates?.[0]?.url || item?.display_uri, url)
  const imageUrl = mediaImage(media) || mediaImage(post)
  if (!imageUrl) return undefined
  const imageUrls = (carousel || [post])
    .filter((item) => item?.media_type !== 2)
    .map(mediaImage)
    .filter((item, index, values) => item && values.indexOf(item) === index)
  return { imageUrl, imageUrls, kind: carousel || media?.media_type !== 2 ? 'photo' : 'video' }
}

const pageImage = (html, url, jsonValues, instagramMedia) => {
  if (instagramMedia?.imageUrl) return instagramMedia.imageUrl
  const declared = [
    metaContent(html, 'og:image:secure_url'),
    metaContent(html, 'og:image:url'),
    metaContent(html, 'og:image'),
    metaContent(html, 'twitter:image'),
    metaContent(html, 'twitter:image:src'),
    metaContent(html, 'thumbnailUrl'),
    metaContent(html, 'image'),
    videoPoster(html),
    linkedImage(html),
  ]
  for (const candidate of declared) {
    const resolved = publicUrl(candidate, url)
    if (resolved) return resolved
  }
  return embeddedImage(jsonValues, url)
}

const pagePostMedia = (html, url, jsonValues, coverImage, instagramMedia) => {
  const platform = socialPlatform(url)
  if (!platform) return undefined
  if (instagramMedia?.kind) return instagramMedia.kind
  if (platform === 'TikTok' || platform === 'YouTube') return 'video'
  const pathname = (() => { try { return new URL(url).pathname.toLowerCase() } catch { return '' } })()
  if ((platform === 'Instagram' && /^\/reels?\//.test(pathname))
    || (platform === 'Facebook' && /\/(?:reels?|videos?|watch)(?:\/|$)/.test(pathname))) return 'video'

  const declaredType = [
    metaContent(html, 'og:type'),
    metaContent(html, 'medium'),
    metaContent(html, 'twitter:card'),
  ].join(' ')
  const declaredVideo = [
    metaContent(html, 'og:video'),
    metaContent(html, 'og:video:url'),
    metaContent(html, 'og:video:secure_url'),
    metaContent(html, 'twitter:player'),
  ].some(Boolean)
  if (declaredVideo || /(?:^|[._\s-])video(?:$|[._\s-])|player/i.test(declaredType) || /<video\b/i.test(html)) return 'video'

  let photoSignal = false
  let videoSignal = false
  let visited = 0
  const seen = new WeakSet()
  const visit = (value, depth = 0) => {
    if (!value || typeof value !== 'object' || depth > 30 || visited >= 30_000 || seen.has(value) || videoSignal) return
    seen.add(value)
    visited += 1
    if (!Array.isArray(value)) {
      for (const [key, child] of Object.entries(value)) {
        const normalized = key.replace(/[^a-z0-9]/gi, '').toLowerCase()
        const stringValue = typeof child === 'string' ? child.toLowerCase() : ''
        if ((normalized === 'isvideo' && child === true)
          || (normalized === 'mediaproducttype' && /(?:clips?|reels?|video)/.test(stringValue))
          || (normalized === 'mediatype' && (child === 2 || /^(?:2|video)$/.test(stringValue)))
          || (normalized === 'typename' && /video/.test(stringValue))) videoSignal = true
        if ((normalized === 'isvideo' && child === false)
          || (normalized === 'mediatype' && (child === 1 || /^(?:1|image|photo)$/.test(stringValue)))
          || (normalized === 'typename' && /(?:image|photo)/.test(stringValue))) photoSignal = true
      }
    }
    for (const child of Object.values(value)) visit(child, depth + 1)
  }
  for (const value of jsonValues) visit(value)

  if (videoSignal) return 'video'
  if (photoSignal || coverImage) return 'photo'
  return undefined
}

const socialPlatformNames = {
  'facebook.com': 'Facebook',
  'fb.watch': 'Facebook',
  'instagram.com': 'Instagram',
  'lemon8-app.com': 'Lemon8',
  'pin.it': 'Pinterest',
  'pinterest.com': 'Pinterest',
  'redd.it': 'Reddit',
  'reddit.com': 'Reddit',
  'threads.net': 'Threads',
  'tiktok.com': 'TikTok',
  'twitter.com': 'X',
  'x.com': 'X',
  'youtu.be': 'YouTube',
  'youtube.com': 'YouTube',
}

const socialPlatform = (value) => {
  try {
    const host = new URL(value).hostname.toLowerCase().replace(/^www\./, '')
    const domain = Object.keys(socialPlatformNames).find((candidate) => host === candidate || host.endsWith(`.${candidate}`))
    return domain ? socialPlatformNames[domain] : ''
  } catch { return '' }
}

const normalizedPlatform = (value) => value.toLowerCase() === 'twitter' ? 'X' : value.replace(/^./, (letter) => letter.toUpperCase())
const socialEnvelopePattern = /^(.{1,80}?)\s+on\s+(Instagram|TikTok|YouTube|Facebook|Pinterest|Threads|X|Twitter|Reddit|Lemon8)\s*:\s*/i
const socialAuthorPattern = /^(.{1,80}?)\s+on\s+(?:Instagram|TikTok|YouTube|Facebook|Pinterest|Threads|X|Twitter|Reddit|Lemon8)(?:\s*:|\s*$)/i
const instagramEngagementEnvelopePattern = /^(?:[\d.,]+[kmb]?\s+likes?,\s+[\d.,]+[kmb]?\s+comments?\s+-\s+)?([a-z0-9._]+)\s+on\s+[a-z]+\s+\d{1,2},\s+\d{4}:\s*/i

const ingredientAmount = '(?:\\d+\\s+\\d+\\/\\d+|\\d+\\/\\d+|\\d+(?:[.,]\\d+)?|[\u00bc\u00bd\u00be\u2153\u2154\u215b\u215c\u215d\u215e]|half(?:\\s+(?:a|an))?|handful|pinch|a|an)'
const ingredientUnit = '(?:cups?|tablespoons?|tbsp|teaspoons?|tsp|grams?|kilograms?|g|kg|millilit(?:er|re)s?|ml|lit(?:er|re)s?|l|ounces?|oz|pounds?|lbs?|lb|cloves?|cans?|pinch(?:es)?|handfuls?|bunch(?:es)?|slices?|sprigs?|stalks?|heads?|fillets?|pieces?|packs?|packets?|whole|medium(?:-sized)?|small|large)'
const bulletPattern = '(?:[•●▪◦‣–—-]|✅|☑️?|✔️?|✓|🔸|🔹|▫️?|◾|◽|➡️?|➜)'
const numberedMarkerPattern = '(?:(?:step\\s*)?\\d{1,2}[.):]\\s+|[1-9]️?⃣\\s*)'
const bulletLinePattern = new RegExp(`^\\s*${bulletPattern}\\s*`, 'u')
const numberedLinePattern = new RegExp(`^\\s*${numberedMarkerPattern}`, 'i')
const numberedRowPattern = new RegExp(`(?:^|\\n|\\s)${numberedMarkerPattern}([\\s\\S]*?)(?=(?:\\n|\\s)${numberedMarkerPattern}|$)`, 'gi')

const parseIngredient = (value, index) => {
  const clean = text(value).replace(new RegExp(`^${bulletPattern}\\s*`, 'u'), '')
  const match = clean.match(new RegExp(`^(${ingredientAmount}(?:\\s*(?:-|to)\\s*${ingredientAmount})?(?:\\s*${ingredientUnit})?)(?:\\s+of)?\\s+(.+)$`, 'i'))
  return {
    id: `imported-${Date.now()}-${index}`,
    quantity: match?.[1]?.trim() || '',
    name: match?.[2]?.trim() || clean,
  }
}

const photoAmountPattern = new RegExp(`^(${ingredientAmount}(?:\\s*(?:-|to)\\s*${ingredientAmount})?(?:\\s*${ingredientUnit})?)(?:\\s*,\\s*(.+))?$`, 'i')
const photoNoisePattern = /^(?:ingredients?|directions?|instructions?|method|recipe|rise\s+with\b|save\b|follow\b|https?:\/\/|.*@\w|\d+(?:[.,]\d+)?(?:\s*-\s*\d+(?:[.,]\d+)?)?\s*(?:cal(?:ories)?|protein)\b|(?:makes?|serves?)\s+\d+)/i
const photoMarketingPattern = /^(?:healthy\b.*\bprotein|sweet\s*\+|bold\s*\+|light\s*\+|spicy\s*\+|creamy\s*\+|smoky\s*\+|fresh\s*\+|fiery\s*\+)/i
const photoCardTitlePattern = /\b(?:sauces?|dressing|dip|smoothie|salad|soup|curry|pasta|recipes?|maggi|noodles?|bowl|pakoda|bread|rice|chicken|paneer|cookies?|cake)\s*$/i

const normalizePhotoOcrLine = (value) => {
  const clean = text(value)
    .replace(/\b(?:Mago|Magg|Maqaqi)\b/gi, 'Maggi')
    .replace(/\bMaggi\)/gi, 'Maggi')
    .replace(/\bLavorites\b/g, 'Favorites')
    .replace(/\b(?:thsp|tosp|hsp)\b/gi, 'tbsp')
    .replace(/\bEtsp\b/gi, 'tsp')
    .replace(/\b(?:4sp|sp)\b/gi, 'tsp')
    .replace(/^[*.·•e°“”«»¢©]\s*/, '')
    .replace(/^@\s*(?=\d)/, '')
    .replace(/^[/*#¥£\\]+\s*(?=[A-Za-z¼½¾⅓⅔⅛⅜⅝⅞])/, '')
    .replace(/^[^A-Za-z0-9¼½¾⅓⅔⅛⅜⅝⅞"“]{1,5}\s*(?=(?:\d|[¼½¾⅓⅔⅛⅜⅝⅞"“]))/, '')
    .replace(/^[Jj]\s+(?=\d)/, '')
    .replace(/^[VY][£2]\s+(?=[A-Z][a-z])/, '')
    .replace(/^[YV]\s*%\s+(?=cups?\b)/i, '¼ ')
    .replace(/^Ya\s+(?=cups?\b)/i, '¼ ')
    .replace(/^%\.?\s+(?=(?:cups?|tsp)\b)/i, '½ ')
    .replace(/^["“]\s*(?=tsp\b)/i, '½ ')
    .replace(/^2d\s+(?=tbsp\b)/i, '2 ')
    .replace(/^(\d+(?:[.,]\d+)?)(?!(?:g|kg|ml|l|oz|lb)\b)(?=[a-z])/i, '$1 ')
    .replace(/^(?:c\s*)?a[l|i]\s+(?=tbsp\b)/i, '1 ')
    .replace(/^a\)\s+(?=tsp\b)/i, '1 ')
    .replace(/^(?:o[nli]|ol)\s+(?=tsp\b)/i, '1 ')
    .replace(/^(?:[a-z]{0,3}:?\s*)?\|+\s*(?=(?:t(?:b)?sp)\b)/i, '1 ')
    .replace(/^(?:[IOo])\s*(?=(?:t(?:b)?sp)\b)/i, '1 ')
    .replace(/^(?:Jd|oF|A)\s+(?=tbsp\b)/i, '2 ')
    .replace(/^(?=(?:tbsp|tsp)\b)/i, '1 ')
    .replace(/\s+/g, ' ')
    .trim()
  return /^(?:[A=]|\d{2,})$/i.test(clean) ? '' : clean
}

const photoLines = (value) => structuredText(value).split('\n').map(normalizePhotoOcrLine).filter(Boolean)

const normalizePhotoQuantity = (value) => text(value)
  .replace(/^1\s*\/\s*2\b/, '½')
  .replace(/^1\s*\/\s*4\b/, '¼')
  .replace(/^3\s*\/\s*4\b/, '¾')
  .replace(/([\d¼½¾⅓⅔⅛⅜⅝⅞])(?=(?:cups?|tbsp|tsp)\b)/i, '$1 ')
  .replace(/\b(?:cups?|tbsp|tsp)\b/gi, (unit) => unit.toUpperCase())

const photoAmount = (value) => {
  const clean = text(value)
    .replace(/\btbsf\b/i, 'tbsp')
    .replace(/^[vy]\s*½/i, '½')
    .replace(/^[vy](?:a|4)\s+(?=cups?\b)/i, '1/2 ')
    .replace(/^v2\s+(?=cups?\b)/i, '1/2 ')
    .replace(/^(?:h|ve)\s+(?=tsp\b)/i, '1/2 ')
  if (/^(?:a|an)$/i.test(clean)) return null
  if (/^to\s+taste$/i.test(clean)) return { quantity: 'to taste', note: '' }
  if (/^pinch$/i.test(clean)) return { quantity: 'pinch', note: '' }
  if (/^(?:cups?|tbsp|tsp)$/i.test(clean)) return { quantity: normalizePhotoQuantity(clean), note: '' }
  const match = clean.match(photoAmountPattern)
  return match ? { quantity: normalizePhotoQuantity(match[1]), note: match[2]?.trim() || '' } : null
}

const photoIngredientName = (value) => text(value)
  .replace(/\barlic\b/gi, 'garlic')
  .replace(/\bowder\b/gi, 'powder')
  .replace(/\blemoi\b/gi, 'lemon')
  .replace(/\bgroted\b/gi, 'grated')
  .replace(/\s+\d+$/, '')
  .replace(/\.\s+/g, ' ')
  .toLowerCase()

const photoInlineIngredient = (value) => {
  const clean = text(value).replace(/^[-•]\s*/, '')
  const match = clean.match(new RegExp(`^(${ingredientAmount}(?:\\s*(?:-|to)\\s*${ingredientAmount})?(?:\\s*${ingredientUnit})?)(?:\\s+of)?\\s+(.+)$`, 'i'))
  if (!match || !/[a-z]/i.test(match[2]) || photoNoisePattern.test(clean) || photoMarketingPattern.test(clean)) return null
  return { quantity: normalizePhotoQuantity(match[1]), name: match[2].trim() }
}

export const splitPhotoTextCards = (value) => {
  const lines = photoLines(value)
  if (lines.filter((line) => /^(?:step\s*)?\d{1,2}[.):]\s+.+/i.test(line)).length >= 2) return [lines.join('\n')]
  const ingredientIndexes = lines.map((line, index) => photoInlineIngredient(line) ? index : -1).filter((index) => index >= 0)
  if (ingredientIndexes.length < 4) return [lines.join('\n')]

  const unmeasuredIngredient = /^(?:splash\b|black\s+pepper\b|salt(?:\s*(?:&|and)\s*pepper)?\b)/i
  const heading = (line) => line.length >= 4 && line.length <= 60 && /[a-z]/i.test(line) &&
    !/\d/.test(line) && !photoAmount(line) && !photoInlineIngredient(line) &&
    !photoNoisePattern.test(line) && !photoMarketingPattern.test(line) && !unmeasuredIngredient.test(line)

  const starts = []
  let previousIngredient = -1
  ingredientIndexes.forEach((ingredientIndex) => {
    const candidates = []
    for (let index = previousIngredient + 1; index < ingredientIndex; index += 1) {
      if (heading(lines[index])) candidates.push(index)
    }
    const start = candidates.at(-1)
    if (start !== undefined && starts.at(-1) !== start) starts.push(start)
    previousIngredient = ingredientIndex
  })
  if (starts.length < 2) return [lines.join('\n')]
  return starts.map((start, index) => lines.slice(start, starts[index + 1] ?? lines.length).join('\n'))
}

const photoIngredientQuantity = (quantity, rawName) => /^2\s+TSP$/i.test(quantity) && /\barlic\b.*\bowder\b/i.test(rawName)
  ? '½ TSP'
  : quantity

const plausiblePhotoIngredient = ({ name }) => {
  const clean = text(name)
  return clean.length >= 3 && clean.length <= 100 && /[a-z]{3,}/i.test(clean) && !/\d|[\[\]{}<>]/.test(clean)
}

const plausiblePhotoTitle = (value) => text(value).length >= 4 && /[a-z]{3,}/i.test(value)

const completePhotoTitle = (value) => value.replace(/\bsauc$/i, (match) => match === match.toUpperCase() ? 'SAUCE' : 'sauce')

const photoTitle = (value) => {
  const lines = photoLines(value)
  const collection = lines.find((line) => /^\d{1,2}\s+.{3,60}\brecipes?\b/i.test(line))
  if (collection) return completePhotoTitle(collection)
  const firstIngredient = lines.findIndex((line, index) => index > 0 && (photoAmount(line) || photoInlineIngredient(line)))
  const headerLines = lines.slice(0, firstIngredient < 0 ? 4 : firstIngredient)
  const numbered = headerLines.find((line) => /^\d{1,2}[.)]\s*[a-z]/i.test(line))
  if (numbered) return completePhotoTitle(numbered.replace(/^\d{1,2}[.)]\s*/, '').trim())
  const candidates = headerLines
    .filter((line, index) => /[a-z]/i.test(line) && !photoNoisePattern.test(line) && !photoMarketingPattern.test(line) && !photoAmount(line) && (index === 0 || !photoInlineIngredient(line)))
    .slice(0, 2)
  if (!candidates.length) return ''
  const title = candidates.length > 1 && (photoCardTitlePattern.test(candidates[1]) || /^\d/.test(candidates[0]))
    ? `${candidates[0]} ${candidates[1]}`
    : candidates[0]
  return completePhotoTitle(title)
}

const photoCard = (value, cardIndex) => {
  const lines = photoLines(value)
  const title = photoTitle(value)
  const firstLikelyIngredient = lines.findIndex((line) => photoAmount(line) || photoInlineIngredient(line))
  const stepsStart = lines.findIndex((line, index) => index > firstLikelyIngredient && /^(?:step\s*)?\d{1,2}[.):]\s+.+/i.test(line))
  const ingredientLines = lines.slice(0, stepsStart < 0 ? lines.length : stepsStart)
  const nameFirst = []
  for (let index = 0; index < ingredientLines.length; index += 1) {
    const name = ingredientLines[index].replace(/^[-•]\s*/, '').trim()
    const amount = photoAmount(ingredientLines[index + 1])
    if (!amount || !/[a-z]/i.test(name) || photoNoisePattern.test(name) || photoMarketingPattern.test(name) || name === title) continue
    nameFirst.push({
      id: `photo-${Date.now()}-${cardIndex}-name-${nameFirst.length}`,
      quantity: amount.quantity,
      name: photoIngredientName(amount.note ? `${name}, ${amount.note}` : name),
      group: title || undefined,
    })
    index += 1
  }

  const amountFirst = []
  for (let index = 0; index < ingredientLines.length; index += 1) {
    const amount = photoAmount(ingredientLines[index])
    const inline = amount || ingredientLines[index] === title ? null : photoInlineIngredient(ingredientLines[index])
    if (inline) {
      const names = inline.name ? [inline.name] : []
      let cursor = index + 1
      if (inline.name && !new RegExp(`${ingredientUnit}$`, 'i').test(inline.quantity) && new RegExp(`^${ingredientUnit}$`, 'i').test(ingredientLines[cursor] || '')) {
        names.push(ingredientLines[cursor])
        cursor += 1
      } else if (!inline.name) {
        while (cursor < ingredientLines.length && names.length < 2 && !photoAmount(ingredientLines[cursor]) && !photoInlineIngredient(ingredientLines[cursor])) {
          const part = ingredientLines[cursor].replace(/^[-•]\s*/, '').trim()
          if (photoNoisePattern.test(part) || photoMarketingPattern.test(part)) break
          if (/[a-z]/i.test(part)) names.push(part)
          cursor += 1
        }
      }
      const name = names.join(' ')
      amountFirst.push({
        id: `photo-${Date.now()}-${cardIndex}-inline-${amountFirst.length}`,
        quantity: photoIngredientQuantity(inline.quantity, name),
        name: photoIngredientName(name),
        group: title || undefined,
      })
      index = cursor - 1
      continue
    }

    if (photoMarketingPattern.test(ingredientLines[index])) {
      const names = []
      let cursor = index + 1
      while (cursor < ingredientLines.length && names.length < 3 && !photoAmount(ingredientLines[cursor]) && !photoInlineIngredient(ingredientLines[cursor])) {
        const part = ingredientLines[cursor].replace(/^[-•]\s*/, '').trim()
        if (photoNoisePattern.test(part) || photoCardTitlePattern.test(part)) break
        if (/[a-z]/i.test(part) && !photoMarketingPattern.test(part)) names.push(part)
        cursor += 1
      }
      const name = names.join(' ').replace(/\s+/g, ' ').trim()
      if (/^(?:lemon|lime|orange)\s+zest$/i.test(name)) {
        amountFirst.push({
          id: `photo-${Date.now()}-${cardIndex}-unmeasured-${amountFirst.length}`,
          quantity: '',
          name: photoIngredientName(name),
          group: title || undefined,
        })
      }
      index = cursor - 1
      continue
    }

    if (!amount) continue
    const names = []
    let cursor = index + 1
    while (cursor < ingredientLines.length && names.length < 3 && !photoAmount(ingredientLines[cursor]) && !photoInlineIngredient(ingredientLines[cursor])) {
      const part = ingredientLines[cursor].replace(/^[-•]\s*/, '').trim()
      if (photoNoisePattern.test(part) || photoMarketingPattern.test(part)) break
      if (/[a-z]/i.test(part)) names.push(part)
      cursor += 1
    }
    if (!names.length) continue
    let name = names.join(' ').replace(/\s+/g, ' ').trim()
    let unmeasured = ''
    const splitUnmeasured = name.match(/^(.+?)\s+((?:lemon|lime|orange)\s+zest)$/i)
    if (splitUnmeasured) {
      name = splitUnmeasured[1]
      unmeasured = splitUnmeasured[2]
    }
    amountFirst.push({
      id: `photo-${Date.now()}-${cardIndex}-amount-${amountFirst.length}`,
      quantity: photoIngredientQuantity(amount.quantity, name),
      name: photoIngredientName(amount.note ? `${name}, ${amount.note}` : name),
      group: title || undefined,
    })
    if (unmeasured) {
      amountFirst.push({
        id: `photo-${Date.now()}-${cardIndex}-unmeasured-${amountFirst.length}`,
        quantity: '',
        name: photoIngredientName(unmeasured),
        group: title || undefined,
      })
    }
    index = cursor - 1
  }

  const ingredients = (amountFirst.length > nameFirst.length ? amountFirst : nameFirst).filter(plausiblePhotoIngredient)
  const firstIngredient = ingredientLines.findIndex((line) => photoAmount(line) || photoInlineIngredient(line))
  if (firstIngredient >= 0 && stepsStart >= 0) {
    const known = new Set(ingredients.map((ingredient) => ingredient.name.toLowerCase()))
    ingredientLines.slice(firstIngredient + 1).forEach((raw) => {
      if (photoAmount(raw) || photoInlineIngredient(raw)) return
      const name = photoIngredientName(raw.replace(/^[-•]\s*/, ''))
      const ingredient = {
        id: `photo-${Date.now()}-${cardIndex}-unmeasured-${ingredients.length}`,
        quantity: '',
        name,
        group: title || undefined,
      }
      if (!plausiblePhotoIngredient(ingredient) || known.has(name) || name === title.toLowerCase() ||
          photoNoisePattern.test(name) || photoMarketingPattern.test(name) || /[.!?]$/.test(name)) return
      ingredients.push(ingredient)
      known.add(name)
    })
  }

  const steps = []
  let current = ''
  for (const line of lines.slice(stepsStart < 0 ? lines.length : stepsStart)) {
    const numbered = line.match(/^(?:step\s*)?\d{1,2}[.):]\s*(.+)$/i)
    if (numbered) {
      if (current) steps.push(current)
      current = numbered[1].trim()
    } else if (current && !photoNoisePattern.test(line)) {
      current = `${current} ${line}`.replace(/\s+/g, ' ').trim()
    }
  }
  if (current) steps.push(current)

  const measuredWithUnits = ingredients.filter((ingredient) => new RegExp(`\\b${ingredientUnit}\\b`, 'i').test(ingredient.quantity)).length
  const allMeasured = ingredients.length >= 4 && ingredients.every((ingredient) => ingredient.quantity)
  const accepted = ingredients.length >= 2 && (steps.length >= 2 || measuredWithUnits >= 2 || allMeasured)
  return accepted ? { title, ingredients, steps } : null
}

export const extractRecipeFromPhotoText = (pages, url = '') => {
  const cards = pages.map(photoCard).filter(Boolean)
  if (!cards.length) return null
  const platform = socialPlatform(url)
  const firstPageTitle = photoTitle(pages[0])
  const coverTitle = plausiblePhotoTitle(firstPageTitle) ? firstPageTitle : cards[0].title
  const title = cards.length > 1 ? coverTitle || `${cards.length} recipes from ${platform || 'photo post'}` : cards[0].title
  const ingredients = cards.flatMap((card) => card.ingredients.map((ingredient) => ({
    ...ingredient,
    group: cards.length > 1 ? card.title : undefined,
  })))
  const steps = cards.flatMap((card) => card.steps.map((step) => cards.length > 1 && card.title ? `${card.title} — ${step}` : step))
  const servings = Number.parseInt(pages.join('\n').match(/(?:serves?|makes?)\s*(\d+)/i)?.[1] || '', 10) || 4
  return {
    title: limit(title || 'Recipe from photo post', 100),
    description: cards.length > 1 ? `${cards.length} recipes scanned from a photo carousel.` : 'Recipe scanned from a photo.',
    source: platform || 'Photo post',
    sourceUrl: url || undefined,
    time: 10,
    servings,
    difficulty: 'Easy',
    ingredients,
    steps,
    tags: [platform || 'Photo import', 'Photo recipe'],
  }
}

const cleanSectionRows = (rows) => rows
  .map((item) => text(item.replace(/^\s*(?:👇|⬇️?)\s*/, '').replace(numberedLinePattern, '').replace(bulletLinePattern, '').replace(/^\s*\[[^\]]+\]\s*$/, '').replace(/^\s*(?:for|to make)\s+[^:]{1,60}:\s*$/i, ''))
    .replace(/(?:^|\s+)(?:toppings?|garnishes?|for serving|optional extras?)\s*:?\s*$/i, '')
    .replace(/\s+(?:(?:step\s*)?\d{1,2}[.):]|[1-9]️?⃣)\s*$/i, '')
    .trim())
  .filter((item) => Boolean(item) && !/^[.·•\s-]+$/.test(item))

const sectionHeadingPattern = /^(?:nutrition(?:al)?(?:\s+(?:facts?|info(?:rmation)?))?(?:\s*[-–—]\s*serves?\s+\d+)?|macros?(?:\s+per\s+serving)?|recipe\s+notes?|notes?|tips?|storage|substitutions?|serving\s+suggestions?|frequently\s+asked\s+questions?|faq|credits?)\s*:?[ \t]*$/i
const socialCaptionPattern = /^(?:[^\p{L}\p{N}\s]{1,4}\s*)?(?:https?:\/\/|send\s+(?:this|the)\b|save\s+(?:this|the)\b|share\s+(?:this|the)\s+(?:recipe|post)|follow\s+(?:me|us|@|for\b)|comment\s+(?:below|with\b)|like\s+(?:this|and\b)|subscribe\b|tag\s+(?:me|us|someone\b)|link\s+in\s+(?:my|the)\s+bio|(?:want|looking)\s+for\s+more\b|if\s+you\s+want\s+(?:weekly|more|recipes?|discounts?|updates?)\b|for\s+more\s+(?:recipes?|content)\b|check\s+out\s+(?:my|our|the)\b)/iu

const trimSectionContent = (value) => {
  const withoutHashtags = structuredText(value).split(/\s+#(?:[\p{L}\p{N}_])/u)[0].trim()
  if (!withoutHashtags) return ''

  const inlineBoundary = withoutHashtags.search(/\s+(?=(?:[^\p{L}\p{N}\s]{1,4}\s*)?(?:(?:step\s*)?\d{1,2}[.):]\s+)?(?:https?:\/\/|send\s+(?:this|the)\b|save\s+(?:this|the)\b|share\s+(?:this|the)\s+(?:recipe|post)|follow\s+(?:me|us|@|for\b)|comment\s+(?:below|with\b)|like\s+this\s+post|subscribe\b|tag\s+(?:me|us)|link\s+in\s+(?:my|the)\s+bio|for\s+more\s+recipes?)\b)/iu)
  const clean = inlineBoundary >= 0 ? withoutHashtags.slice(0, inlineBoundary).trim() : withoutHashtags
  const rows = clean.split('\n')
  const kept = []
  for (const row of rows) {
    const cleanRow = text(row)
    const withoutListMarker = cleanRow.replace(numberedLinePattern, '')
    if (kept.some((item) => text(item)) && (sectionHeadingPattern.test(cleanRow) || socialCaptionPattern.test(withoutListMarker))) break
    kept.push(row)
  }
  return kept.join('\n').trim()
}

const numberedRows = (value) => cleanSectionRows([...structuredText(value).matchAll(numberedRowPattern)]
  .map((match) => match[1]))

const parseIngredientRows = (value) => {
  const section = trimSectionContent(value)
  const clean = section.replace(/\s*\[[^\]]+\]\s*/g, '\n')
  if (!clean) return []

  const numbered = numberedRows(clean)
  if (numbered.length || numberedLinePattern.test(clean)) return numbered

  const bulletSeparator = section.includes('\n')
    ? new RegExp(`(?:^|\\n)\\s*${bulletPattern}\\s*`, 'gu')
    : new RegExp(`(?:^|\\s+)${bulletPattern}\\s+`, 'gu')
  const bulletRows = cleanSectionRows(clean.split(bulletSeparator))
  if (bulletRows.length > 1) return bulletRows

  const lineRows = cleanSectionRows(clean.split(/\n+/))
  if (lineRows.length > 1) return lineRows

  const semicolonRows = cleanSectionRows(clean.split(/\s*;\s*/))
  if (semicolonRows.length > 1) return semicolonRows

  const amountRows = cleanSectionRows(clean.split(new RegExp(`\\s+(?=${ingredientAmount}(?:\\s*${ingredientUnit})?\\s+)`, 'gi')))
  return amountRows.length > 1 ? amountRows : lineRows
}

const instructionLinePattern = /^(?:(?:now|next|then|finally|meanwhile)\b|to\s+(?:a|the)\b|(?:and\s+)?(?:your|the|it)\b.*\b(?:ready|done)\b|(?:and\s+)?(?:add|blend|boil|bake|chop|coat|combine|cook|dip|drain|fold|freeze|fry|garnish|grill|heat|keep|let|melt|mix|place|pour|preheat|roast|scoop|season|serve|set|simmer|spread|sprinkle|stir|take|top|transfer|whisk)\b|(?:once|before|after)\b.*\b(?:add|coat|dip|freeze|mix|serve|set|top)\b)/i

const embeddedIngredient = (value) => (text(value).match(/\badd\s+(.+?)(?=\s+(?:and\s+(?:set|blend|mix|cook|stir|pour|serve|keep|let)\b|then\b)|[.!]|$)/i)?.[1] || '')
  .replace(/^a\s+(?=pinch\b)/i, '')
const embeddedMeasuredIngredient = (value) => {
  const match = text(value).match(new RegExp(`\\b(?:into|in|with)\\s+(?:some\\s+)?(.{2,80}?)\\s*\\((${ingredientAmount}(?:\\s*${ingredientUnit})?)\\)`, 'i'))
  return match ? `${match[2]} ${match[1]}` : ''
}

const parseMixedRecipeSection = (value) => {
  const rows = cleanSectionRows(trimSectionContent(value).split(/\n+/))
  const ingredients = []
  const steps = []

  for (const row of rows) {
    if (instructionLinePattern.test(row)) {
      steps.push(row)
      for (const foundIngredient of [embeddedIngredient(row), embeddedMeasuredIngredient(row)].filter(Boolean)) {
        ingredients.push(foundIngredient)
      }
    } else {
      const parsed = parseIngredient(row, ingredients.length)
      if (!steps.length || parsed.quantity) ingredients.push(row)
      if (steps.length) steps[steps.length - 1] = `${steps.at(-1)} ${row}`
    }
  }

  return steps.length >= 2 ? { ingredients, steps } : null
}

const parseNumberedIngredientRows = (value) => {
  const groups = []
  let current = []
  const finishGroup = () => {
    if (current.length) groups.push(current)
    current = []
  }

  for (const row of structuredText(value).split('\n')) {
    if (numberedLinePattern.test(row)) {
      current.push(row)
    } else if (!text(row) || /^\s*(?:\[[^\]]+\]|(?:for|to make)\s+[^:]{1,60}:)\s*$/i.test(row)) {
      continue
    } else {
      finishGroup()
    }
  }
  finishGroup()

  if (groups.length) {
    return cleanSectionRows(groups.at(-1))
  }
  return numberedRows(value)
}

const parseUnheadedIngredientRows = (value) => {
  const groups = []
  let current = []
  const finishGroup = () => {
    if (current.length >= 2) groups.push(current)
    current = []
  }

  for (const row of structuredText(value).split('\n')) {
    const clean = text(row)
    const parsed = parseIngredient(clean, current.length)
    if (clean && parsed.quantity && !photoNoisePattern.test(clean)) {
      current.push(clean)
    } else {
      finishGroup()
    }
  }
  finishGroup()

  return cleanSectionRows(groups.sort((left, right) => right.length - left.length)[0] || [])
}

const parseDirectionRows = (value) => {
  const clean = trimSectionContent(value)
  if (!clean) return []

  const numbered = numberedRows(clean)
  if (numbered.length || numberedLinePattern.test(clean)) return numbered

  const bulletRows = cleanSectionRows(clean.split(new RegExp(`(?:^|\\s+)${bulletPattern}\\s+`, 'gu')))
  if (bulletRows.length > 1) return bulletRows

  const lineRows = cleanSectionRows(clean.split(/\n+/))
  if (lineRows.length > 1) return lineRows

  return cleanSectionRows(clean.split(/\s*;\s*/))
}

const parseSteps = (value) => {
  if (!value) return []
  if (typeof value === 'string') return text(value) ? [text(value)] : []
  if (Array.isArray(value)) return value.flatMap(parseSteps)
  if (value.text) return parseSteps(value.text)
  if (value.itemListElement) return parseSteps(value.itemListElement)
  return []
}

const humanizeHashtag = (value) => {
  const known = {
    airfryer: 'Air fryer',
    highprotein: 'High protein',
    instantpot: 'Instant Pot',
    mealprep: 'Meal prep',
    onepot: 'One pot',
    ricecooker: 'Rice cooker',
    slowcooker: 'Slow cooker',
  }
  return known[value.toLowerCase()] || value.replaceAll('_', ' ').replace(/([a-z])([A-Z])/g, '$1 $2').toLowerCase().replace(/^./, (letter) => letter.toUpperCase())
}

const titleCaseDish = (value) => text(value)
  .replace(/\b(?:side\s+dish|main\s+dish|dish|meal)\s*$/i, '')
  .toLocaleLowerCase('en')
  .replace(/(^|[\s/&+–—-])(\p{L})/gu, (match, prefix, letter) => `${prefix}${letter.toLocaleUpperCase('en')}`)
  .trim()

const callToActionTitle = (caption) => {
  const phrase = caption.match(/\b(?:send|save|share)\s+(?:this|the)\s+([^\n.!?]{2,120}?)\s+recipe\b/i)?.[1]
  return phrase ? titleCaseDish(phrase) : ''
}

const captionTime = (value) => {
  const matches = [...value.matchAll(/\b(\d+(?:\.\d+)?)\s*(hours?|hrs?|minutes?|mins?)\b/gi)]
  if (!matches.length) return 0
  return Math.round(matches.reduce((total, match) => total + Number(match[1]) * (/^h/i.test(match[2]) ? 60 : 1), 0))
}

const richText = (value, depth = 0) => {
  if (depth > 5 || value === null || value === undefined) return ''
  if (typeof value === 'string' || typeof value === 'number') return structuredText(value)
  if (Array.isArray(value)) return structuredText(value.map((item) => richText(item, depth + 1)).filter(Boolean).join(''))
  if (typeof value !== 'object') return ''
  for (const key of ['textOriginal', 'textDisplay', 'simpleText', 'text', 'content', 'body', 'message']) {
    if (typeof value[key] === 'string') return structuredText(value[key])
  }
  if (Array.isArray(value.runs)) return structuredText(value.runs.map((run) => richText(run, depth + 1)).join(''))
  return ''
}

const activeSignal = (value) => value === true || value === 1 || (typeof value === 'string' && /^(?:true|yes|pinned|creator|owner)$/i.test(value.trim())) || (value && typeof value === 'object')
const normalizedKey = (value) => String(value).replace(/[^a-z0-9]/gi, '').toLowerCase()

const commentSignal = (value, kind, depth = 0, seen = new WeakSet()) => {
  if (!value || typeof value !== 'object' || depth > 4 || seen.has(value)) return false
  seen.add(value)
  for (const [key, child] of Object.entries(value)) {
    const normalized = normalizedKey(key)
    if (kind === 'pinned' && normalized.includes('pinned') && activeSignal(child)) return true
    if (kind === 'creator' && /^(?:creator|owner|iscreator|isowner|authoriscreator|authorisowner|commenteriscreator|commenterisowner|isvideocreator|ismediacreator|ispostcreator|ischannelowner)$/.test(normalized) && activeSignal(child)) return true
    if (typeof child === 'string' && kind === 'pinned' && /\bpinned\s+by\b/i.test(child)) return true
    if (commentSignal(child, kind, depth + 1, seen)) return true
  }
  return false
}

const commentText = (value, depth = 0) => {
  if (!value || typeof value !== 'object' || depth > 3) return ''
  for (const key of ['textOriginal', 'textDisplay', 'commentText', 'comment_text', 'contentText', 'body', 'message', 'text']) {
    const found = richText(value[key])
    if (found) return found
  }
  for (const key of ['snippet', 'topLevelComment', 'commentRenderer', 'comment', 'node']) {
    const found = commentText(value[key], depth + 1)
    if (found) return found
  }
  return ''
}

const commentAuthor = (value, depth = 0) => {
  if (!value || typeof value !== 'object' || depth > 3) return ''
  for (const key of ['authorDisplayName', 'authorText', 'username', 'uniqueId', 'nickname']) {
    const found = richText(value[key])
    if (found) return text(found).replace(/^@/, '')
  }
  for (const key of ['author', 'user', 'owner', 'snippet', 'topLevelComment', 'commentRenderer', 'comment', 'node']) {
    const found = commentAuthor(value[key], depth + 1)
    if (found) return found
  }
  return ''
}

const normalizedIdentity = (value) => text(value).replace(/^@/, '').replace(/\s+on\s+.*$/i, '').toLowerCase()

const embeddedCommentRecipes = (values, url, authorHint = '') => {
  const candidates = []
  const creatorIdentity = normalizedIdentity(authorHint)

  for (const root of values) {
    let visited = 0
    const seen = new WeakSet()
    const visit = (value, path = [], depth = 0) => {
      if (!value || typeof value !== 'object' || depth > 35 || visited >= 30_000 || seen.has(value) || candidates.length >= 24) return
      seen.add(value)
      visited += 1

      if (!Array.isArray(value)) {
        const pathLabel = path.join('.')
        const keys = Object.keys(value).join('.')
        const looksLikeComment = /comment/i.test(pathLabel) || /(?:comment|textOriginal|textDisplay|contentText)/i.test(keys)
        if (looksLikeComment) {
          const pinned = /pinned/i.test(pathLabel) || commentSignal(value, 'pinned')
          const author = commentAuthor(value)
          const creator = commentSignal(value, 'creator') || (creatorIdentity && normalizedIdentity(author) === creatorIdentity)
          const content = commentText(value)
          if ((pinned || creator) && content && content.length >= 20 && !candidates.some((candidate) => candidate.content === content)) {
            candidates.push({ content, author: author || authorHint, pinned, creator })
          }
        }
      }

      for (const [key, child] of Object.entries(value)) visit(child, [...path.slice(-7), key], depth + 1)
    }
    visit(root)
  }

  return candidates
    .map((candidate) => {
      const recipe = extractRecipeFromText(candidate.content, url, candidate.author)
      if (!recipe) return null
      const label = candidate.pinned ? 'Pinned comment' : 'Creator comment'
      const platform = socialPlatform(url)
      const attribution = candidate.author ? ` by ${candidate.author}` : ''
      return {
        ...recipe,
        description: limit(`Recipe from a ${label.toLowerCase()}${attribution}${platform ? ` on ${platform}` : ''}. ${recipe.description || ''}`, 320),
        tags: [platform, label, ...(recipe.tags || [])].filter((tag, index, tags) => tag && tags.indexOf(tag) === index).slice(0, 4),
        _commentPriority: candidate.pinned ? 2 : 1,
      }
    })
    .filter(Boolean)
}

const recipeDetailScore = (recipe) => ((recipe.ingredients?.length || 0) * 4) + ((recipe.steps?.length || 0) * 5)

export const extractRecipeFromText = (value, url, authorHint = '') => {
  const raw = structuredText(value)
  let caption = raw
  let author = text(authorHint)
  let platform = socialPlatform(url)
  const socialEnvelope = raw.match(socialEnvelopePattern)
  if (socialEnvelope) {
    author ||= text(socialEnvelope[1])
    platform ||= normalizedPlatform(socialEnvelope[2])
    caption = raw.slice(socialEnvelope[0].length)
  }
  const instagramEnvelope = platform === 'Instagram' ? caption.match(instagramEngagementEnvelopePattern) : null
  if (instagramEnvelope) {
    author ||= text(instagramEnvelope[1])
    caption = caption.slice(instagramEnvelope[0].length)
  }
  caption = caption.replace(/^["\u201c]\s*/, '').replace(/["\u201d]\.?\s*$/, '')

  const ingredientMarker = caption.match(/\b(?:(?:recipe\s+)?ingredients?|(?:what\s+)?you(?:(?:'|’)ll|\s+will)\s+need|shopping\s+list)(?:[ \t]+(?:per|for)[ \t]+(?:(\d+|one)[ \t]+)?(?:servings?|people|cookies?|bites?|balls?|bars?|pieces?|portions?|muffins?|wraps?|rolls?|popsicles?))?(?:[ \t]*\([^\n)]{0,160}\))?[ \t]*(?:[:：][ \t]*|\n+[ \t]*|(?:👇|⬇️?)[ \t]*|[-–—][ \t]+)/i)
  const directionsMarker = caption.match(/\b(?:directions?|(?:cooking\s+)?instructions?|method|procedure|process|prep(?:aration)?(?:[ \t]+of[ \t]+(?:this[ \t]+)?recipe)?|steps?|how[ \t]+to[ \t]+(?:make|cook|prepare)(?:[ \t]+(?:it|this|them|the\s+recipe))?(?:[ \t]+yourself)?)[ \t]*(?:[:：][ \t]*|\n+[ \t]*|(?:👇|⬇️?)[ \t]*|[-–—][ \t]+)/i)
  const unheadedIngredientRows = parseUnheadedIngredientRows(caption)
  if ((!ingredientMarker || ingredientMarker.index === undefined) && (!directionsMarker || directionsMarker.index === undefined) && !unheadedIngredientRows.length) return null

  const ingredientStart = ingredientMarker && ingredientMarker.index !== undefined ? ingredientMarker.index + ingredientMarker[0].length : -1
  const directionStart = directionsMarker && directionsMarker.index !== undefined ? directionsMarker.index + directionsMarker[0].length : -1
  const ingredientText = ingredientStart < 0
    ? directionStart >= 0 ? caption.slice(0, directionsMarker.index) : ''
    : caption.slice(ingredientStart, directionStart > ingredientStart ? directionsMarker.index : undefined)
  const directionsText = directionStart < 0 ? '' : caption.slice(directionStart, ingredientStart > directionStart ? ingredientMarker.index : undefined)
  let ingredientRows = ingredientMarker
    ? parseIngredientRows(ingredientText)
    : directionsMarker
      ? parseNumberedIngredientRows(ingredientText)
      : unheadedIngredientRows
  let stepRows = parseDirectionRows(directionsText)
  if (ingredientMarker && !directionsMarker) {
    const mixedSection = parseMixedRecipeSection(ingredientText)
    if (mixedSection) {
      ingredientRows = mixedSection.ingredients
      stepRows = mixedSection.steps
    }
  }
  if (!ingredientRows.length && !stepRows.length) return null

  const hashtags = [...caption.matchAll(/#([a-z0-9_]+)/gi)].map((match) => match[1])
  const titleStopWords = /^(?:dietfriendly|food|foodie|healthy|healthyfood|healthyrecipe|healthyrecipes|highprotein|instafood|lowcalorie|lowcarb|nutrition|recipe|recipes|weightloss|weightlossrecipe|weightlossrecipes)$/i
  const dishTags = hashtags.filter((tag) => !titleStopWords.test(tag)).slice(0, 2).map(humanizeHashtag)
  const generatedTitle = dishTags.map((tag, index) => index ? tag.toLowerCase() : tag).join(' ').replace(/^./, (letter) => letter.toUpperCase())
  const macroMatch = caption.match(/\bMacros?\s+per\s+serving\s*:\s*([\s\S]*?)\s+Ingredients?\b/i)
  const macros = text(macroMatch?.[1] || '').replace(/\s+(?=(?:protein|carbs?|fat|calories?|fibre|fiber|sugar)\s*:)/gi, ' · ')
  const sourceHost = (() => {
    try { return new URL(url).hostname.replace(/^www\./, '') }
    catch { return 'Shared text' }
  })()
  const sourceLabel = platform || sourceHost
  const source = author ? `${author} · ${sourceLabel}` : sourceLabel
  const firstCaptionLine = text(caption.split('\n')[0]).replace(/\s*(?:👇|⬇️?)\s*$/, '')
  const captionTitle = firstCaptionLine.length > 100 || /^(?:[^\p{L}\p{N}\s]*\s*)?(?:full\s+recipe|(?:(?:recipe\s+)?ingredients?|(?:cooking\s+)?instructions?|directions?|method|procedure|process|prep(?:aration)?|steps?|shopping\s+list)\s*:?[ \t]*)/iu.test(firstCaptionLine) ? '' : firstCaptionLine
  const ctaTitle = callToActionTitle(caption)
  const markerYield = ingredientMarker?.[1]?.toLowerCase() === 'one' ? 1 : Number.parseInt(ingredientMarker?.[1] || '', 10)
  const singleServingYield = /\b(?:per|for)\s+(?:one\s+)?serving\b/i.test(ingredientMarker?.[0] || '') ? 1 : 0

  return {
    title: limit(ctaTitle || captionTitle || generatedTitle || (author ? `Recipe by ${author}` : url ? `Recipe from ${sourceHost}` : 'Pasted recipe'), 100),
    description: limit(macros ? `${macros} per serving. Shared from ${source}.` : `Shared from ${source}.`, 320),
    source,
    sourceUrl: url || undefined,
    time: captionTime(directionsText) || 30,
    servings: markerYield || singleServingYield || Number.parseInt(ingredientMarker?.[0]?.match(/(?:serves?|makes?)\s*(\d+)/i)?.[1] || '', 10) || 4,
    difficulty: 'Easy',
    ingredients: ingredientRows.map(parseIngredient),
    steps: stepRows,
    tags: [platform || 'Imported', ...hashtags.map(humanizeHashtag)].filter((tag, index, tags) => tags.indexOf(tag) === index).slice(0, 4),
  }
}

export const extractRecipe = (html, url) => {
  const titleTag = text(html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1] || '')
  const openGraphTitle = metaContent(html, 'og:title')
  const openGraphDescription = metaContent(html, 'og:description')
  const structuredOpenGraphTitle = metaStructuredContent(html, 'og:title')
  const structuredOpenGraphDescription = metaStructuredContent(html, 'og:description')
  const structuredTwitterTitle = metaStructuredContent(html, 'twitter:title')
  const structuredTwitterDescription = metaStructuredContent(html, 'twitter:description')
  const structuredDescription = metaStructuredContent(html, 'description')
  const authorHint = metaContent(html, 'author') || text((structuredOpenGraphTitle || structuredTwitterTitle).match(socialAuthorPattern)?.[1] || '')
  const jsonValues = jsonScriptValues(html)
  const instagramMedia = instagramPostMedia(jsonValues, url)
  const coverImage = pageImage(html, url, jsonValues, instagramMedia)
  const postMedia = pagePostMedia(html, url, jsonValues, coverImage, instagramMedia)
  const postImageUrls = instagramMedia?.imageUrls
  const socialRecipes = [structuredOpenGraphTitle, structuredOpenGraphDescription, structuredTwitterTitle, structuredTwitterDescription, structuredDescription]
    .filter((candidate, index, candidates) => candidate && candidates.indexOf(candidate) === index)
    .map((candidate) => extractRecipeFromText(candidate, url, authorHint))
    .filter(Boolean)
  const socialRecipe = socialRecipes.reduce((best, recipe) => {
    if (!best) return recipe
    return recipeDetailScore(recipe) > recipeDetailScore(best) ? recipe : best
  }, null)
  const commentRecipe = embeddedCommentRecipes(jsonValues, url, authorHint).reduce((best, recipe) => {
    if (!best) return recipe
    const score = (item) => recipeDetailScore(item) + ((item._commentPriority || 0) * 2)
    return score(recipe) > score(best) ? recipe : best
  }, null)
  const socialOrCommentRecipe = socialRecipe && (!commentRecipe || recipeDetailScore(socialRecipe) >= recipeDetailScore(commentRecipe))
    ? socialRecipe
    : commentRecipe
  if (socialOrCommentRecipe) {
    const { _commentPriority, ...recipe } = socialOrCommentRecipe
    return {
      ...recipe,
      imageUrl: coverImage,
      postImageUrls,
      postMedia,
    }
  }

  let node = null
  for (const value of jsonValues) {
    node = recipeNode(value)
    if (node) break
  }

  const sourceHost = new URL(url).hostname.replace(/^www\./, '')
  if (!node) {
    return {
      title: limit(openGraphTitle || titleTag || 'Imported recipe', 100),
      description: limit(openGraphDescription || metaContent(html, 'description') || 'Imported from the web.', 320),
      source: sourceHost,
      sourceUrl: url,
      imageUrl: coverImage,
      postImageUrls,
      postMedia,
      time: 30,
      servings: 4,
      difficulty: 'Easy',
      ingredients: [],
      steps: [],
      tags: ['Imported'],
    }
  }

  const yieldValue = Array.isArray(node.recipeYield) ? node.recipeYield[0] : node.recipeYield
  const describedRecipe = extractRecipeFromText(node.description, url, node.author?.name || node.author || '')
  const ingredients = (node.recipeIngredient || node.ingredients || []).map(parseIngredient)
  const steps = parseSteps(node.recipeInstructions)
  return {
    title: limit(node.name || openGraphTitle || titleTag || 'Imported recipe', 100),
    description: limit(node.description || openGraphDescription || 'A recipe saved from the web.', 320),
    source: limit(node.author?.name || node.author || node.publisher?.name || sourceHost, 80),
    sourceUrl: url,
    imageUrl: publicUrl(imageValue(node.image), url) || coverImage,
    postImageUrls,
    postMedia,
    time: durationMinutes(node.totalTime || node.cookTime || node.prepTime) || 30,
    servings: Number.parseInt(yieldValue, 10) || describedRecipe?.servings || 4,
    difficulty: 'Easy',
    ingredients: ingredients.length ? ingredients : describedRecipe?.ingredients || [],
    steps: steps.length ? steps : describedRecipe?.steps || [],
    tags: ['Imported', ...(Array.isArray(node.recipeCategory) ? node.recipeCategory : node.recipeCategory ? [node.recipeCategory] : [])].map(text).filter(Boolean).slice(0, 4),
  }
}
