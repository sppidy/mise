// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import type { Recipe } from '../src/types'

export function extractRecipe(html: string, url: string): Partial<Recipe>
export function extractRecipeFromText(value: string, url?: string, authorHint?: string): Partial<Recipe> | null
export function extractRecipeFromPhotoText(pages: string[], url?: string): Partial<Recipe> | null
export function splitPhotoTextCards(value: string): string[]
