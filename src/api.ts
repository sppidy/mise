// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import { extractRecipeFromPhotoText, extractRecipeFromText } from '../shared/recipe-parser.js'
import { seedState } from './seed'
import type { AppState, Recipe } from './types'

type OcrProgress = { progress?: number; status?: string }
type LoadResult = { state: AppState; connected: boolean }

class RecipeImportError extends Error {
  preview?: Partial<Recipe>

  constructor(message: string, preview?: Partial<Recipe>) {
    super(message)
    this.name = 'RecipeImportError'
    this.preview = preview
  }
}

const localKey = 'mise-state-v1'
const serverKey = 'mise-server-url'

const endpoint = (path: string) => `${localStorage.getItem(serverKey)?.trim().replace(/\/$/, '') || ''}${path}`

const hasRecipeDetails = (recipe: Partial<Recipe>) => Boolean(recipe.ingredients?.length || recipe.steps?.length)

const resolveImage = <T extends Partial<Recipe>>(recipe: T): T => {
  const serverUrl = localStorage.getItem(serverKey)?.trim().replace(/\/$/, '')
  if (!serverUrl) return recipe
  const resolve = (value: string) => value.startsWith('/') ? new URL(value, `${serverUrl}/`).toString() : value
  return {
    ...recipe,
    imageUrl: recipe.imageUrl ? resolve(recipe.imageUrl) : undefined,
    postImageUrls: recipe.postImageUrls?.map(resolve),
  }
}

const validateImport = (recipe: Partial<Recipe>) => {
  if (!hasRecipeDetails(recipe)) throw new Error('No ingredients or directions were found. Try a direct recipe page or add it manually.')
  return recipe
}

export const importPreviewFromError = (error: unknown) => error instanceof RecipeImportError ? error.preview : undefined

const validatePhotoText = (value = '') => {
  const clean = value.trim()
  if (clean.length < 20) throw new Error('Not enough text was found in that photo. Try a clearer, well-lit image.')
  return clean
}

const detailScore = (recipe: Partial<Recipe>) => ((recipe.ingredients?.length || 0) * 4) + ((recipe.steps?.length || 0) * 4)

const repairRecipe = (recipe: Recipe): Recipe => {
  if (recipe.postMedia === 'photo' && !recipe.ingredients.length && recipe.steps.length > 30) {
    const pages: string[] = []
    let current: string[] = []
    const amountRow = (value = '') => /^(?:[\d¼½¾⅓⅔⅛⅜⅝⅞]|[vy](?:½|2|4|a)\b|h\s+tsp\b|pinch\b|to\s+taste\b|cups?\b|tbsp\b|tsp\b)/i.test(value.trim())
    for (let index = 0; index < recipe.steps.length; index += 1) {
      const row = recipe.steps[index]
      const boundary = /\b(?:sauc(?:e)?|dressing|dip|smoothie|salad|soup|curry|pasta|recipe)\s*$/i.test(row) && !amountRow(recipe.steps[index - 1])
      if (boundary) {
        let heading = row
        const previous = current.at(-1)
        const beforePrevious = current.at(-2)
        if (current.length > 1 && previous?.includes(' ') && !amountRow(beforePrevious) && !/^(?:healthy\b|sweet\s*\+|bold\s*\+|light\s*\+|spicy\s*\+|creamy\s*\+|smoky\s*\+|fresh\s*\+|fiery\s*\+)/i.test(previous)) {
          heading = `${current.pop()} ${row}`
        }
        if (current.length) pages.push(current.join('\n'))
        current = [heading]
      } else if (current.length) current.push(row)
    }
    if (current.length) pages.push(current.join('\n'))
    const repairedPhoto = extractRecipeFromPhotoText(pages.length ? pages : [recipe.steps.join('\n')], recipe.sourceUrl)
    if (repairedPhoto?.ingredients?.length) {
      return {
        ...recipe,
        ingredients: repairedPhoto.ingredients,
        steps: repairedPhoto.steps || [],
        tags: [...new Set([...recipe.tags, ...(repairedPhoto.tags || [])])].slice(0, 4),
      }
    }
  }
  if (!recipe.sourceUrl || recipe.title.length < 180 || hasRecipeDetails(recipe)) return recipe
  const repaired = extractRecipeFromText(recipe.title, recipe.sourceUrl)
  return repaired && hasRecipeDetails(repaired) ? { ...recipe, ...repaired, id: recipe.id, collection: recipe.collection, favorite: recipe.favorite, createdAt: recipe.createdAt } : recipe
}

const recipeScore = (recipe: Recipe) => (recipe.ingredients.length * 4) + (recipe.steps.length * 4) + (recipe.imageUrl ? 3 : 0) + (recipe.title.length < 140 ? 1 : 0)

const normalizeState = (state: AppState): AppState => {
  const recipes: Recipe[] = []
  const sourceIndexes = new Map<string, number>()
  for (const original of state.recipes) {
    const recipe = resolveImage(repairRecipe(original))
    const source = recipe.sourceUrl?.trim().replace(/\/$/, '')
    if (!source) {
      recipes.push(recipe)
      continue
    }
    const existingIndex = sourceIndexes.get(source)
    if (existingIndex === undefined) {
      sourceIndexes.set(source, recipes.length)
      recipes.push(recipe)
      continue
    }
    const existing = recipes[existingIndex]
    if (recipeScore(recipe) > recipeScore(existing)) {
      recipes[existingIndex] = { ...recipe, favorite: recipe.favorite || existing.favorite }
    } else if (recipe.favorite && !existing.favorite) {
      recipes[existingIndex] = { ...existing, favorite: true }
    }
  }
  return { ...state, recipes }
}

const localRead = (): AppState => {
  try {
    const state = normalizeState(JSON.parse(localStorage.getItem(localKey) || '') as AppState)
    localStorage.setItem(localKey, JSON.stringify(state))
    return state
  } catch {
    localStorage.setItem(localKey, JSON.stringify(seedState))
    return seedState
  }
}

export const api = {
  async load(): Promise<LoadResult> {
    try {
      const response = await fetch(endpoint('/api/state'))
      if (!response.ok) throw new Error('API unavailable')
      const state = normalizeState((await response.json()) as AppState)
      localStorage.setItem(localKey, JSON.stringify(state))
      return { state, connected: true }
    } catch {
      return { state: localRead(), connected: false }
    }
  },
  async save(state: AppState): Promise<boolean> {
    localStorage.setItem(localKey, JSON.stringify(state))
    try {
      const response = await fetch(endpoint('/api/state'), {
        method: 'PUT',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(state),
      })
      return response.ok
    } catch {
      // Local-first: the browser copy remains available when the server is offline.
      return false
    }
  },
  async connect(value: string, localState: AppState): Promise<AppState> {
    const previous = localStorage.getItem(serverKey)
    this.setServerUrl(value)
    try {
      const response = await fetch(endpoint('/api/state'))
      if (response.ok) {
        const state = normalizeState((await response.json()) as AppState)
        localStorage.setItem(localKey, JSON.stringify(state))
        return state
      }
      if (response.status === 404) {
        if (await this.save(localState)) return localState
        throw new Error('The server was reached, but the recipe box could not be initialized')
      }
      const body = await response.json().catch(() => ({}))
      throw new Error(body.error || `The server returned HTTP ${response.status}`)
    } catch (error) {
      if (previous === null) localStorage.removeItem(serverKey)
      else localStorage.setItem(serverKey, previous)
      throw error instanceof Error ? error : new Error('Could not connect to that server')
    }
  },
  async importRecipe(url: string): Promise<Partial<Recipe>> {
    const response = await fetch(endpoint('/api/import'), {
      method: 'POST',
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify({ url }),
    })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) throw new RecipeImportError(body.error || 'Could not import this page', body.preview ? resolveImage(body.preview) : undefined)
    return resolveImage(validateImport(body))
  },
  importSharedText(value: string, sourceUrl = ''): Partial<Recipe> {
    return validateImport(extractRecipeFromText(value, sourceUrl) || {})
  },
  async analyzePhoto(file: Blob, onProgress?: (progress: OcrProgress) => void): Promise<string> {
    if (file.size > 15_000_000) throw new Error('Choose a photo smaller than 15 MB.')

    const { createWorker } = await import('tesseract.js')
    const worker = await createWorker('eng', undefined, { logger: (message) => onProgress?.(message) })
    try {
      const result = await worker.recognize(file)
      return validatePhotoText(result.data.text)
    } finally {
      await worker.terminate()
    }
  },
  async analyzeSharedPhoto(path: string): Promise<string> {
    void path
    throw new Error('Shared-photo analysis is available in the Android app.')
  },
  async scanPhotoPost(recipe: Partial<Recipe>, sourceUrl: string): Promise<Partial<Recipe>> {
    void recipe
    void sourceUrl
    throw new Error('Photo-post analysis is available in the Android app.')
  },
  getServerUrl(): string {
    return localStorage.getItem(serverKey) || ''
  },
  setServerUrl(value: string): void {
    const normalized = value.trim().replace(/\/$/, '')
    if (normalized) localStorage.setItem(serverKey, normalized)
    else localStorage.removeItem(serverKey)
  },
}
