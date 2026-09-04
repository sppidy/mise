// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

export type View = 'home' | 'recipes' | 'favorites' | 'collections' | 'grocery'

export type Ingredient = {
  id: string
  quantity: string
  name: string
  group?: string
}

export type Recipe = {
  id: string
  title: string
  description: string
  source: string
  sourceUrl?: string
  imageUrl?: string
  postImageUrls?: string[]
  postMedia?: 'photo' | 'video'
  time: number
  servings: number
  difficulty: 'Easy' | 'Medium' | 'Project'
  collection: string
  favorite: boolean
  imagePosition: 'tl' | 'tr' | 'bl' | 'br'
  tags: string[]
  ingredients: Ingredient[]
  steps: string[]
  createdAt: string
}

export type GroceryItem = {
  id: string
  name: string
  amount: string
  checked: boolean
  category: string
}

export type AppState = {
  recipes: Recipe[]
  grocery: GroceryItem[]
  collections: string[]
}
