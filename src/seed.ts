// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import type { AppState, Recipe } from './types'
import { makeId } from './utils'

const recipe = (recipe: Omit<Recipe, 'id' | 'createdAt'>): Recipe => ({
  ...recipe,
  id: makeId(),
  createdAt: new Date().toISOString(),
})

export const seedState: AppState = {
  collections: ['Weeknight wins', 'Slow Sundays', 'Baking shelf'],
  grocery: [
    { id: makeId(), name: 'Baby spinach', amount: '200 g', checked: false, category: 'Produce' },
    { id: makeId(), name: 'Cherry tomatoes', amount: '2 punnets', checked: false, category: 'Produce' },
    { id: makeId(), name: 'Coconut milk', amount: '1 can', checked: true, category: 'Pantry' },
    { id: makeId(), name: 'Salmon fillets', amount: '4', checked: false, category: 'Protein' },
  ],
  recipes: [
    recipe({
      title: 'Miso glazed salmon bowls',
      description: 'Sticky, savory salmon with steamed rice and crisp greens. A genuinely fast dinner that still feels special.',
      source: 'Mise kitchen', time: 28, servings: 4, difficulty: 'Easy', collection: 'Weeknight wins', favorite: true, imagePosition: 'tl', tags: ['Dinner', 'High protein'],
      ingredients: [
        { id: makeId(), quantity: '4', name: 'salmon fillets' },
        { id: makeId(), quantity: '3 tbsp', name: 'white miso' },
        { id: makeId(), quantity: '2 tbsp', name: 'soy sauce' },
        { id: makeId(), quantity: '2 cups', name: 'cooked jasmine rice' },
        { id: makeId(), quantity: '2 heads', name: 'baby bok choy' },
      ],
      steps: ['Whisk miso, soy sauce, a spoonful of warm water, and a pinch of sugar until glossy.', 'Brush the salmon generously and roast at 220°C for 10–12 minutes.', 'Sear the bok choy cut-side down until charred, then add a splash of water and cover for 2 minutes.', 'Divide rice between bowls, add salmon and greens, then spoon over the remaining glaze.'],
    }),
    recipe({
      title: 'Roasted tomato rigatoni',
      description: 'Blistered tomatoes folded into a silky sauce with basil and a little cream.',
      source: 'Imported from a blog', time: 35, servings: 4, difficulty: 'Easy', collection: 'Weeknight wins', favorite: false, imagePosition: 'tr', tags: ['Vegetarian', 'Pasta'],
      ingredients: [
        { id: makeId(), quantity: '400 g', name: 'rigatoni' },
        { id: makeId(), quantity: '500 g', name: 'cherry tomatoes' },
        { id: makeId(), quantity: '4 cloves', name: 'garlic' },
        { id: makeId(), quantity: '120 ml', name: 'double cream' },
        { id: makeId(), quantity: '1 handful', name: 'fresh basil' },
      ],
      steps: ['Roast tomatoes and garlic with olive oil at 220°C until blistered.', 'Boil rigatoni in well-salted water until just shy of al dente.', 'Crush the roasted tomatoes, stir in cream, and simmer for 3 minutes.', 'Toss pasta through the sauce with basil and enough pasta water to make it glossy.'],
    }),
    recipe({
      title: 'Brown butter banana bread',
      description: 'Deeply nutty, not too sweet, with a crisp demerara sugar top.',
      source: 'Family recipe', time: 70, servings: 8, difficulty: 'Medium', collection: 'Baking shelf', favorite: true, imagePosition: 'bl', tags: ['Baking', 'Make ahead'],
      ingredients: [
        { id: makeId(), quantity: '3', name: 'very ripe bananas' },
        { id: makeId(), quantity: '125 g', name: 'unsalted butter' },
        { id: makeId(), quantity: '180 g', name: 'plain flour' },
        { id: makeId(), quantity: '2', name: 'eggs' },
        { id: makeId(), quantity: '100 g', name: 'brown sugar' },
      ],
      steps: ['Brown the butter over medium heat and cool for 10 minutes.', 'Mash bananas, then whisk in sugar, eggs, and brown butter.', 'Fold in flour, baking soda, and salt only until no dry streaks remain.', 'Bake at 175°C for 50–55 minutes and cool completely before slicing.'],
    }),
    recipe({
      title: 'Coconut chickpea curry',
      description: 'A pantry-friendly curry with coconut, sweet potato, and lime.',
      source: 'Mise kitchen', time: 30, servings: 4, difficulty: 'Easy', collection: 'Slow Sundays', favorite: false, imagePosition: 'br', tags: ['Vegan', 'One pot'],
      ingredients: [
        { id: makeId(), quantity: '2 cans', name: 'chickpeas' },
        { id: makeId(), quantity: '1 can', name: 'coconut milk' },
        { id: makeId(), quantity: '1 large', name: 'sweet potato' },
        { id: makeId(), quantity: '2 tbsp', name: 'curry paste' },
        { id: makeId(), quantity: '1', name: 'lime' },
      ],
      steps: ['Fry curry paste in a little oil until fragrant.', 'Add diced sweet potato, chickpeas, coconut milk, and 200 ml water.', 'Simmer uncovered for 20 minutes until the potato is tender and the sauce thickens.', 'Season with lime juice and salt, then serve with rice or flatbread.'],
    }),
  ],
}
