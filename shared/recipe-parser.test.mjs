// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import assert from 'node:assert/strict'
import test from 'node:test'
import { extractRecipe, extractRecipeFromPhotoText, extractRecipeFromText, splitPhotoTextCards } from './recipe-parser.js'

// These captions are anonymized, adapted parser fixtures. Handles and post IDs are deliberately fictitious.

test('turns an Instagram caption into a usable recipe', () => {
  const caption = 'Sample Cook on Instagram: &quot;Full Recipe ⇩; Macros per serving: Protein: 49g Carbs: 60g Fat: 14g Calories: 572 Ingredients per 2 servings (the entire rice cooker): - 2/3 cup jasmine rice, uncooked - 3 cups chicken bone broth - 10 oz boneless, skinless chicken thigh - 1/2 tbsp minced garlic [Topping] - 1 tsp sesame seeds How to make it yourself: 1. Wash your raw rice. 2. Add the broth and chicken. 3. Cook for 1 hour and serve. #highprotein #ricecooker #porridge&quot;'
  const html = `<meta property="og:title" content="${caption}"><meta content="https://cdn.example/porridge.jpg" property="og:image">`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/example/')

  assert.equal(recipe.title, 'Rice cooker porridge')
  assert.equal(recipe.servings, 2)
  assert.equal(recipe.time, 60)
  assert.equal(recipe.ingredients.length, 5)
  assert.deepEqual(recipe.ingredients[0], {
    id: recipe.ingredients[0].id,
    quantity: '2/3 cup',
    name: 'jasmine rice, uncooked',
  })
  assert.equal(recipe.ingredients[3].name, 'minced garlic')
  assert.equal(recipe.steps.length, 3)
  assert.match(recipe.description, /Protein: 49g · Carbs: 60g/)
  assert.equal(recipe.imageUrl, 'https://cdn.example/porridge.jpg')
})

test('parses an Instagram reel caption with an ingredient yield in cookies', () => {
  const caption = [
    'Chocolate Peanut Butter Yogurt Bites',
    'Recipe by @samplecook',
    'Ingredients for 8 cookies:',
    'To a bowl add',
    '240g high protein yogurt',
    '120g unsweetened peanut butter',
    '100g honey',
    'Mix it well',
    'Scoop the mixture into 8 small bites',
    'And freeze for at least 1 hour',
    'Once they’re fully frozen, dip each one into melted dark chocolate (150g) and watch it instantly coat into a smooth glossy shell.',
    'Before it sets, add a pinch of salt',
    'and some crunchy peanuts on top',
    'And it’s ready to serve',
    'Nutrition - serves 8',
    'Calories per serve - 270',
  ].join('&#10;')
  const html = `<meta property="og:description" content="${caption}"><meta property="og:image" content="https://cdn.example/yogurt-bites.jpg">`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/sample-yogurt-bites/')

  assert.equal(recipe.title, 'Chocolate Peanut Butter Yogurt Bites')
  assert.equal(recipe.servings, 8)
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '240g', name: 'high protein yogurt' },
    { quantity: '120g', name: 'unsweetened peanut butter' },
    { quantity: '100g', name: 'honey' },
    { quantity: '150g', name: 'melted dark chocolate' },
    { quantity: 'pinch', name: 'salt' },
  ])
  assert.ok(recipe.steps.length >= 6)
  assert.doesNotMatch(recipe.steps.join(' '), /Calories per serve/)
  assert.equal(recipe.imageUrl, 'https://cdn.example/yogurt-bites.jpg')
})

test('uses the dish named in an Instagram send-this CTA instead of generic hashtags', () => {
  const caption = [
    'Sample Cook on Instagram: "⇩ Full Recipe 🥒 ⇩',
    '',
    'Macros per serving:',
    'Protein: 3g',
    'Carbs: 12g',
    'Fat: 4g',
    'Calories: 85',
    '',
    'Ingredients per 4 servings:',
    '- 7 Persian cucumbers',
    '- 1/2 tbsp salt',
    '- 2 tbsp gochugaru',
    '',
    'How to make it yourself:',
    '1. Slice the cucumbers.',
    '2. Mix with the seasoning.',
    '📩 Send this spicy Korean cucumber kimchi side dish recipe to make for later',
    '#lowcalorie #dietfriendly #nutrition #weightlossrecipe #koreanfood"',
  ].join('\n')

  const recipe = extractRecipeFromText(caption, 'https://www.instagram.com/reel/sample-cucumber-kimchi/')

  assert.equal(recipe.title, 'Spicy Korean Cucumber Kimchi')
  assert.equal(recipe.servings, 4)
  assert.equal(recipe.ingredients.length, 3)
  assert.deepEqual(recipe.steps, ['Slice the cucumbers.', 'Mix with the seasoning.'])
})

test('parses singular-serving Instagram ingredients and excludes the toppings heading', () => {
  const caption = [
    'Sample Cook on Instagram: "⇩ Full Recipe 🍜 ⇩',
    '',
    'Ingredients per serving:',
    '- 6oz kelp noodles',
    '- 1.5 tbsp lemon juice',
    '- 2/3 tbsp baking soda',
    '- 1 tsp chicken bouillon powder',
    '- 2 cups chicken bone broth',
    '- 3 tbsp soy sauce',
    '',
    'Toppings:',
    '- 5oz boneless skinless chicken thigh, raw',
    '- 1/3 second spray oil',
    '- 1 tsp salt',
    '- 1 tsp paprika',
    '- 2 tsp minced garlic',
    '- 1 tsp black pepper',
    '- 1 egg (soft boiled)',
    '- 1 green onion',
    '- 1 bok choy',
    '- 1 tsp chili oil',
    '',
    'How to make it yourself:',
    '1. Prepare the kelp noodles.',
    '2. Assemble the ramen.',
    '📩 Save this kelp noodle japanese ramen recipe to make for later',
    '#highprotein #lowcalorie #weightlossrecipes #healthyrecipes #lowcarb"',
  ].join('\n')

  const recipe = extractRecipeFromText(caption, 'https://www.instagram.com/reel/sample-kelp-ramen/')

  assert.equal(recipe.title, 'Kelp Noodle Japanese Ramen')
  assert.equal(recipe.servings, 1)
  assert.equal(recipe.ingredients.length, 16)
  assert.equal(recipe.ingredients.some(({ name }) => /toppings/i.test(name)), false)
  assert.deepEqual(recipe.steps, ['Prepare the kelp noodles.', 'Assemble the ramen.'])
})

test('parses a contiguous ingredient block without an ingredients heading', () => {
  const caption = [
    '263 likes, 0 comments - samplecook on September 3, 2026: &quot;3-Ingredients Vegan Chocolate Spread.',
    '',
    'Like Nutella, but so much healthier!',
    'Made with ROASTED chickpeas!',
    '',
    'Vegan Chocolate Spread',
    '75 grams roasted chickpeas',
    '6 Medjool dates, pitted (I recommend to use 100 grams)',
    '25grams cocoa powder',
    '240 ml hot water',
    '',
    'Recipe by @samplecook',
    '',
    'Macros for the whole batch:',
    '🔥 ~604 kcal',
    '💪 ~22g protein&quot;',
  ].join('&#10;')
  const html = `<meta property="og:description" content="${caption}"><meta property="og:image" content="https://cdn.example/chocolate-spread.jpg">`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/sample-chocolate-spread/')

  assert.equal(recipe.title, '3-Ingredients Vegan Chocolate Spread.')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '75 grams', name: 'roasted chickpeas' },
    { quantity: '6', name: 'Medjool dates, pitted (I recommend to use 100 grams)' },
    { quantity: '25grams', name: 'cocoa powder' },
    { quantity: '240 ml', name: 'hot water' },
  ])
  assert.equal(recipe.steps.length, 0)
  assert.equal(recipe.imageUrl, 'https://cdn.example/chocolate-spread.jpg')
})

test('selects the largest embedded Instagram media image and ignores comment avatars', () => {
  const caption = 'Paneer bowl&#10;Ingredients:&#10;200g paneer&#10;1 tomato&#10;Directions:&#10;1. Cook the paneer.&#10;2. Add the tomato.'
  const html = [
    `<meta property="og:description" content="${caption}">`,
    `<script type="application/json">${JSON.stringify({
      media: {
        image_versions2: {
          candidates: [
            { url: 'https://cdn.example/paneer-small.jpg', width: 320, height: 400 },
            { url: 'https://cdn.example/paneer-large.jpg', width: 1080, height: 1350 },
          ],
        },
        comments: [{ user: { profile_pic_url: 'https://cdn.example/avatar.jpg' } }],
      },
    })}</script>`,
  ].join('')
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/paneer-bowl/')

  assert.equal(recipe.imageUrl, 'https://cdn.example/paneer-large.jpg')
})

test('marks a shared social image as a photo post that can be scanned', () => {
  const html = [
    '<meta property="og:type" content="article">',
    '<meta property="og:title" content="Five-minute lunch on Instagram">',
    '<meta property="og:image" content="https://cdn.example/lunch-card.jpg">',
  ].join('')

  const recipe = extractRecipe(html, 'https://www.instagram.com/p/lunch-card/')

  assert.equal(recipe.postMedia, 'photo')
  assert.equal(recipe.imageUrl, 'https://cdn.example/lunch-card.jpg')
})

test('selects and downloads the requested image from an Instagram carousel', () => {
  const html = [
    '<meta property="og:type" content="article">',
    '<meta property="og:image" content="https://cdn.example/cropped-preview.jpg">',
    `<script type="application/json">${JSON.stringify({
      data: {
        xig_polaris_media: {
          code: 'CAROUSEL1',
          if_not_gated_logged_out: {
            code: 'CAROUSEL1',
            media_type: 8,
            carousel_media: [
              { media_type: 1, image_versions2: { candidates: [{ url: 'https://cdn.example/full-first.jpg' }] } },
              { media_type: 1, image_versions2: { candidates: [{ url: 'https://cdn.example/full-second.jpg' }] } },
            ],
          },
        },
      },
    })}</script>`,
  ].join('')

  const recipe = extractRecipe(html, 'https://www.instagram.com/p/CAROUSEL1/?img_index=2')

  assert.equal(recipe.postMedia, 'photo')
  assert.equal(recipe.imageUrl, 'https://cdn.example/full-second.jpg')
  assert.deepEqual(recipe.postImageUrls, [
    'https://cdn.example/full-first.jpg',
    'https://cdn.example/full-second.jpg',
  ])
})

test('parses label-and-quantity recipe cards from a photo carousel', () => {
  const recipe = extractRecipeFromPhotoText([
    '8 high protein\nsauces\nwithout blowing your calories\n36-49 cal · 5-6g protein · serves 4',
    '1. tzatziki\ngreek yoghurt\n200g\ngrated cucumber\n1/2 cup\ngarlic\n1 clove, minced\nlemon juice\n1 tbsp\ndill\n1 tbsp, chopped\nsalt & pepper\nto taste\n36 cal · 5g protein · serves 4\nRISE WITH TEAGAN',
    '2. creamy dijon\ngreek yoghurt\n200g\ndijon mustard\n1 tbsp\nlemon juice\n1 tbsp\nsalt & pepper\nto taste',
  ], 'https://www.instagram.com/p/CAROUSEL1/')

  assert.equal(recipe.title, '8 high protein sauces')
  assert.equal(recipe.servings, 4)
  assert.equal(recipe.ingredients.length, 10)
  assert.deepEqual(recipe.ingredients[0], {
    id: recipe.ingredients[0].id,
    quantity: '200g',
    name: 'greek yoghurt',
    group: 'tzatziki',
  })
  assert.equal(recipe.ingredients[6].group, 'creamy dijon')
  assert.deepEqual(recipe.tags, ['Instagram', 'Photo recipe'])
})

test('parses every recipe and method from a multi-card noodle carousel', () => {
  const card = (title, secondIngredient) => [
    title,
    '2 packs Maggi + tastemaker',
    secondIngredient,
    '1. Heat the pan',
    '2. Add the ingredients',
    '3. Add water and boil',
    '4. Add noodles and cook',
    '5. Garnish and serve',
    '@samplecook',
  ].join('\n')
  const recipe = extractRecipeFromPhotoText([
    '7 Mago) Recipes\nSwipe for recipes —\n@samplecook',
    card('Cheesy Masala\nMagg', '%. cup grated cheese'),
    card('Tandoori\nMaggi', 'J 1tbsp tandoori masala'),
    card('Maggi\nPakoda', '½ cup besan'),
    card('Schezwan\nMaggi', '/* Y% cup mixed veggies'),
    card('Butter Garlic\nMaqaqi', '4-5 garlic cloves, sliced'),
    card('Monsoon Maggi\nSoup Bowl', 'Ya cup green peas'),
    card('Paneer\nMaggi', '½ cup paneer, cubed'),
    'Save this for your next rainy day\nFollow for more!',
  ], 'https://www.instagram.com/p/sample-maggi-cards/')

  assert.equal(recipe.title, '7 Maggi Recipes')
  assert.deepEqual([...new Set(recipe.ingredients.map((item) => item.group))], [
    'Cheesy Masala Maggi',
    'Tandoori Maggi',
    'Maggi Pakoda',
    'Schezwan Maggi',
    'Butter Garlic Maggi',
    'Monsoon Maggi Soup Bowl',
    'Paneer Maggi',
  ])
  assert.equal(recipe.ingredients.length, 14)
  assert.equal(recipe.steps.length, 35)
  assert.ok(recipe.steps.every((step) => step.includes(' — ')))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '½ CUP' && item.name === 'grated cheese'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '¼ CUP' && item.name === 'mixed veggies'))
  assert.doesNotMatch(recipe.steps.join(' '), /samplecook|Follow for more/i)
})

test('pairs quantity-first OCR columns and joins split ingredient names', () => {
  const recipe = extractRecipeFromPhotoText([
    '8 SAUCES FOR MEAL PREP\nHEALTHY · HIGH PROTEIN',
    'ROASTED RED PEPPER PROTEIN SAUCE\n½ CUP\nGREEK\nYOGURT\nPINCH\nSALT\n2 TSP\nARLIC\nOWDER\n1 TSP\nOLIVE OIL\nSWEET + SMOKY\n1 TBSP\nWATER\nO @samplecook\n½ CUP\nROASTED\nRED PEPPERS\n1 TBSF\nLEMOI\nJUICE\n1TSP\nPAPRIKA',
  ], 'https://www.instagram.com/p/SAUCES1/')

  assert.equal(recipe.title, 'ROASTED RED PEPPER PROTEIN SAUCE')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '½ CUP', name: 'greek yogurt' },
    { quantity: 'pinch', name: 'salt' },
    { quantity: '½ TSP', name: 'garlic powder' },
    { quantity: '1 TSP', name: 'olive oil' },
    { quantity: '1 TBSP', name: 'water' },
    { quantity: '½ CUP', name: 'roasted red peppers' },
    { quantity: '1 TBSP', name: 'lemon juice' },
    { quantity: '1 TSP', name: 'paprika' },
  ])
})

test('keeps inline and unmeasured ingredients separate in quantity-first photo cards', () => {
  const recipe = extractRecipeFromPhotoText([
    'JALAPEÑO LIME YOGURT SAUCE\nV2 CUP\nGREEK\nYOGURT\nPINCH\nSALT\n1 TBSP\nCORIANDER\nFRESH + ZESTY\n1TBSP\nWATER\nLIME\nZEST\n@chef\n1 WHOLE\nJALAPEÑO\n1TBSP\nLIME JUICE\nVe TSP\nGARLIC\nPOWDER',
    'PERI PERI GARLIC YOGURT SAUCE\n½ CUP\nGREEK\nYOGURT\nPINCH\nSALT\n2 GARLIC\nCLOVES\nFIERY + CREAMY\n1 TSP\nHONEY\n1 TBSP\nWATER\n@chef\n1 TSP\nPERI PERI\nSEASONING',
  ], 'https://www.instagram.com/p/SAUCES2/')

  assert.deepEqual(recipe.ingredients.slice(0, 8).map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '½ CUP', name: 'greek yogurt' },
    { quantity: 'pinch', name: 'salt' },
    { quantity: '1 TBSP', name: 'coriander' },
    { quantity: '1 TBSP', name: 'water' },
    { quantity: '', name: 'lime zest' },
    { quantity: '1 WHOLE', name: 'jalapeño' },
    { quantity: '1 TBSP', name: 'lime juice' },
    { quantity: '½ TSP', name: 'garlic powder' },
  ])
  assert.deepEqual(recipe.ingredients.slice(8, 12).map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '½ CUP', name: 'greek yogurt' },
    { quantity: 'pinch', name: 'salt' },
    { quantity: '2', name: 'garlic cloves' },
    { quantity: '1 TSP', name: 'honey' },
  ])
})

test('repairs common handwriting OCR mistakes in social recipe cards', () => {
  const text = `Crowd Lavorites
Honey Garlic
* Jd tbsp honey
SH| thsp Soy Sauce
* | 4sp garlic
Sweet Chili
* 2 tbsp Sweet chili sauce
° | tbsp Soy Sauce
* | tsp garlic
O tsp sesame oil
Garlic Parmesan
* A thsp olive oil
° tbsp grated parmesan
eo | tsp garlic
Teriyaki
oF tbsp Soy Sauce
* | tbsp brown Sugar
o | tsp ginger
IK: | tsp garlic`
  const cards = splitPhotoTextCards(text)
  const recipe = extractRecipeFromPhotoText(cards, 'https://www.instagram.com/p/example')

  assert.equal(cards.length, 4)
  assert.deepEqual([...new Set(recipe.ingredients.map((item) => item.group))], ['Honey Garlic', 'Sweet Chili', 'Garlic Parmesan', 'Teriyaki'])
  assert.ok(recipe.ingredients.length >= 10)
  assert.ok(recipe.ingredients.some((item) => item.quantity === '2 TBSP' && item.name === 'honey'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '1 TSP' && item.name === 'sesame oil'))
})

test('ignores noisy photo-only cover OCR before carousel recipe cards', () => {
  const noisyCover = `pos
2
vw
AY
LN
3 A 20 tn
8
[a
EA
Es.`
  const card = `Crowd Lavorites
Honey Garlic
* Jd tbsp honey
SH| thsp Soy Sauce
* | 4sp garlic
Sweet Chili
* 2 tbsp Sweet chili sauce
° | tbsp Soy Sauce
* | tsp garlic
O tsp sesame oil`
  const recipe = extractRecipeFromPhotoText([noisyCover, ...splitPhotoTextCards(card)], 'https://www.instagram.com/p/example')

  assert.equal(recipe.title, 'Honey Garlic')
  assert.ok(recipe.ingredients.length >= 6)
  assert.ok(recipe.ingredients.every((item) => !/vw ay ln|20 tn|ea es/i.test(item.name)))
})

test('repairs additional sparse-text OCR artifacts in handwritten cards', () => {
  const text = `Fresh & zesty
Lemon Herb
© A tbsp lemon Juice
o | tbsp olive oil
O || tsp parsley
Mediterranean Garlic
* 2d thsp olive oil
© A) tsp garlic
ON tsp oregano
Smoky Cajun
C Al tbsp olive oil
eB|Etsp garlic
e | tsp Cajun seasoning 2`
  const recipe = extractRecipeFromPhotoText(splitPhotoTextCards(text), 'https://www.instagram.com/p/example')

  assert.ok(recipe.ingredients.some((item) => item.quantity === '2 TBSP' && item.name === 'lemon juice'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '1 TSP' && item.name === 'parsley'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '2 TBSP' && item.name === 'olive oil'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '1 TSP' && item.name === 'oregano'))
  assert.ok(recipe.ingredients.some((item) => item.quantity === '1 TSP' && item.name === 'cajun seasoning'))
})

test('does not mark a social video thumbnail as a photo post', () => {
  const html = [
    '<meta property="og:type" content="video.other">',
    '<meta property="og:image" content="https://cdn.example/video-thumbnail.jpg">',
    '<meta property="og:video" content="https://cdn.example/video.mp4">',
  ].join('')

  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/example/')

  assert.equal(recipe.postMedia, 'video')
})

test('recognizes an Instagram reel as video even when only a thumbnail is exposed', () => {
  const html = '<meta property="og:image" content="https://cdn.example/reel-thumbnail.jpg">'
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/example/')

  assert.equal(recipe.postMedia, 'video')
})

test('reads the largest embedded YouTube video thumbnail', () => {
  const caption = 'Tomato eggs&#10;Ingredients:&#10;2 eggs&#10;1 tomato&#10;Directions:&#10;1. Whisk the eggs.&#10;2. Fry with the tomato.'
  const html = [
    `<meta property="og:description" content="${caption}">`,
    `<script type="application/json">${JSON.stringify({
      videoDetails: {
        thumbnail: {
          thumbnails: [
            { url: 'https://i.example/small.jpg', width: 120, height: 90 },
            { url: 'https://i.example/maxres.jpg', width: 1280, height: 720 },
          ],
        },
      },
    })}</script>`,
  ].join('')
  const recipe = extractRecipe(html, 'https://www.youtube.com/shorts/example')

  assert.equal(recipe.imageUrl, 'https://i.example/maxres.jpg')
})

test('uses declared page artwork before embedded thumbnail candidates', () => {
  const caption = 'Quick toast&#10;Ingredients:&#10;2 slices bread&#10;Directions:&#10;1. Toast the bread.'
  const html = [
    `<meta property="og:description" content="${caption}">`,
    '<link rel="image_src" href="/quick-toast.jpg">',
    `<script type="application/json">${JSON.stringify({ video: { cover: 'https://cdn.example/video-cover.jpg' } })}</script>`,
  ].join('')
  const recipe = extractRecipe(html, 'https://recipes.example/quick-toast')

  assert.equal(recipe.imageUrl, 'https://recipes.example/quick-toast.jpg')
})

test('reads nested Schema.org recipe data', () => {
  const html = `<script type="application/ld+json">${JSON.stringify({
    '@context': 'https://schema.org',
    '@graph': [{
      '@type': 'Recipe',
      name: 'Tomato toast',
      image: { url: '/toast.jpg' },
      totalTime: 'PT15M',
      recipeYield: '2 servings',
      recipeIngredient: ['2 slices sourdough', '1 large tomato'],
      recipeInstructions: [{ '@type': 'HowToSection', itemListElement: [{ '@type': 'HowToStep', text: 'Toast the bread.' }, { '@type': 'HowToStep', text: 'Add tomato.' }] }],
    }],
  })}</script>`
  const recipe = extractRecipe(html, 'https://recipes.example/post')

  assert.equal(recipe.title, 'Tomato toast')
  assert.equal(recipe.time, 15)
  assert.equal(recipe.servings, 2)
  assert.equal(recipe.imageUrl, 'https://recipes.example/toast.jpg')
  assert.deepEqual(recipe.steps, ['Toast the bread.', 'Add tomato.'])
})

test('keeps caption line breaks and accepts directions as a section heading', () => {
  const caption = [
    'Sample Cook on Instagram: &quot;Creamy lemon pasta',
    'Ingredients',
    '• 250g spaghetti',
    '• 2 tbsp olive oil',
    '• 3 cloves garlic',
    'Directions',
    '1) Boil the spaghetti.',
    '2) Fry the garlic in olive oil.',
    '3) Toss everything with lemon juice.',
    '#pasta&quot;',
  ].join('&#10;')
  const html = `<meta property="og:description" content="${caption}">`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/lemon-pasta/')

  assert.equal(recipe.ingredients.length, 3)
  assert.equal(recipe.ingredients[0].quantity, '250g')
  assert.equal(recipe.ingredients[0].name, 'spaghetti')
  assert.deepEqual(recipe.steps, [
    'Boil the spaghetti.',
    'Fry the garlic in olive oil.',
    'Toss everything with lemon juice.',
  ])
})

test('reads ingredients and instructions embedded in a recipe description', () => {
  const html = `<script type="application/ld+json">${JSON.stringify({
    '@context': 'https://schema.org',
    '@type': 'Recipe',
    name: 'Crispy potatoes',
    description: 'Ingredients:\n- 500g potatoes\n- 2 tbsp olive oil\nInstructions:\n1. Boil the potatoes.\n2. Roast until crisp.',
  })}</script>`
  const recipe = extractRecipe(html, 'https://recipes.example/crispy-potatoes')

  assert.equal(recipe.title, 'Crispy potatoes')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '500g', name: 'potatoes' },
    { quantity: '2 tbsp', name: 'olive oil' },
  ])
  assert.deepEqual(recipe.steps, ['Boil the potatoes.', 'Roast until crisp.'])
})

test('accepts emoji after informal ingredient and preparation headings', () => {
  const recipe = extractRecipe([
    '<meta name="description" content="Dinner tonight&#10;',
    'WHAT YOU&apos;LL NEED 👇&#10;• 1 cup rice&#10;• 2 cups water&#10;',
    'PREPARATION ⬇️&#10;1) Rinse the rice.&#10;2) Cook until tender.">',
  ].join(''), 'https://social.example/rice')

  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '1 cup', name: 'rice' },
    { quantity: '2 cups', name: 'water' },
  ])
  assert.deepEqual(recipe.steps, ['Rinse the rice.', 'Cook until tender.'])
})

test('infers a numbered ingredient list before a preparation heading', () => {
  const caption = [
    'Sample Cook on Instagram: &quot;Low fat paneer Wrap Recipe 👇',
    '',
    'Protein 36g',
    'Calories 400',
    '',
    '1. 100g Low Fat paneer',
    '2. 100g Skyr',
    '3. 50g Boiled sweet corn',
    '4. 5ml oil',
    '5. Half onion rings',
    '',
    'Preparation of this recipe',
    '',
    '1. Spread some oil in a tawa.',
    '2. Keep the flame low.',
    '3. Add the grated paneer and sweet corn.&quot;',
  ].join('&#10;')
  const html = `<meta property="og:title" content="${caption}">`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/sample-paneer-wrap/')

  assert.equal(recipe.title, 'Low fat paneer Wrap Recipe')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '100g', name: 'Low Fat paneer' },
    { quantity: '100g', name: 'Skyr' },
    { quantity: '50g', name: 'Boiled sweet corn' },
    { quantity: '5ml', name: 'oil' },
    { quantity: 'Half', name: 'onion rings' },
  ])
  assert.deepEqual(recipe.steps, [
    'Spread some oil in a tawa.',
    'Keep the flame low.',
    'Add the grated paneer and sweet corn.',
  ])
})

test('keeps unrelated caption content out of ingredients and preparation steps', () => {
  const caption = [
    'Sample Cook on Instagram: &quot;My easiest weekday dinner',
    'This one has been a family favourite for years.',
    'Macros per serving: 420 calories and 28g protein',
    '1. First reason to try it',
    '2. Second reason to try it',
    '',
    'Ingredients',
    '1. 250g paneer',
    '2. 1 large onion',
    '3. 2 tsp garam masala',
    '',
    'Preparation',
    '1. Dice the paneer and onion.',
    '2. Fry the onion until golden.',
    '3. Add the paneer and garam masala.',
    'Save this recipe for later!',
    'Follow me for more weekday dinners.',
    '#paneer #dinner&quot;',
  ].join('&#10;')
  const recipe = extractRecipe(`<meta property="og:description" content="${caption}">`, 'https://www.instagram.com/reel/weekday-paneer/')

  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '250g', name: 'paneer' },
    { quantity: '1 large', name: 'onion' },
    { quantity: '2 tsp', name: 'garam masala' },
  ])
  assert.deepEqual(recipe.steps, [
    'Dice the paneer and onion.',
    'Fry the onion until golden.',
    'Add the paneer and garam masala.',
  ])
})

test('selects the ingredient list nearest preparation when its heading is omitted', () => {
  const caption = [
    'Three reasons this wrap is great',
    '1. It is quick',
    '2. It is filling',
    '3. It travels well',
    '4. It is high in protein',
    '5. It uses simple ingredients',
    '',
    'Shopping list below',
    '1. 2 tortillas',
    '2. 100g paneer',
    '3. 1 tomato',
    '4. Salt to taste',
    '',
    'Preparation of this recipe',
    '1. Warm the tortillas.',
    '2. Fill and roll them.',
    'Share this post with a friend.',
  ].join('&#10;')
  const recipe = extractRecipe(`<meta name="description" content="${caption}">`, 'https://social.example/wrap')

  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '2', name: 'tortillas' },
    { quantity: '100g', name: 'paneer' },
    { quantity: '1', name: 'tomato' },
    { quantity: '', name: 'Salt to taste' },
  ])
  assert.deepEqual(recipe.steps, ['Warm the tortillas.', 'Fill and roll them.'])
})

test('imports emoji lists and step labels from a TikTok caption', () => {
  const caption = [
    'Weeknight sesame noodles',
    'INGREDIENTS:',
    '✅ 200g noodles',
    '✅ 1 tbsp sesame oil',
    '✅ 2 tsp soy sauce',
    'COOKING INSTRUCTIONS:',
    'Step 1: Boil the noodles.',
    'Step 2: Toss with sesame oil and soy sauce.',
    'Follow me for more recipes.',
  ].join('&#10;')
  const html = [
    '<meta property="og:title" content="Maya Meals on TikTok">',
    `<meta property="og:description" content="${caption}">`,
  ].join('')
  const recipe = extractRecipe(html, 'https://vm.tiktok.com/example/')

  assert.equal(recipe.title, 'Weeknight sesame noodles')
  assert.equal(recipe.source, 'Maya Meals · TikTok')
  assert.equal(recipe.tags[0], 'TikTok')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '200g', name: 'noodles' },
    { quantity: '1 tbsp', name: 'sesame oil' },
    { quantity: '2 tsp', name: 'soy sauce' },
  ])
  assert.deepEqual(recipe.steps, ['Boil the noodles.', 'Toss with sesame oil and soy sauce.'])
})

test('uses the richest recipe metadata exposed by a YouTube page', () => {
  const ingredientsOnly = 'Ingredients:&#10;• 2 eggs&#10;• 1 tomato'
  const fullCaption = [
    'Fast tomato eggs',
    'You will need:',
    '🔸 2 eggs',
    '🔸 1 tomato',
    'Method:',
    '1️⃣Whisk the eggs.',
    '2️⃣Cook the tomato.',
    '3️⃣Fold in the eggs.',
    '#breakfast',
  ].join('&#10;')
  const html = [
    '<meta name="author" content="Kitchen Channel">',
    `<meta property="og:description" content="${ingredientsOnly}">`,
    `<meta name="twitter:description" content="${fullCaption}">`,
  ].join('')
  const recipe = extractRecipe(html, 'https://youtu.be/example')

  assert.equal(recipe.source, 'Kitchen Channel · YouTube')
  assert.equal(recipe.ingredients.length, 2)
  assert.deepEqual(recipe.steps, ['Whisk the eggs.', 'Cook the tomato.', 'Fold in the eggs.'])
})

test('imports a recipe from a creator-pinned Instagram comment embedded in page data', () => {
  const pinnedComment = [
    'Spicy paneer toast',
    'Ingredients:',
    '• 200g paneer',
    '• 2 slices sourdough',
    '• 1 tsp chilli flakes',
    'Directions:',
    '1. Crumble and season the paneer.',
    '2. Toast the sourdough.',
    '3. Spoon the paneer over the toast.',
  ].join('\n')
  const html = [
    '<meta property="og:title" content="Sample Kitchen on Instagram">',
    '<meta property="og:image" content="https://cdn.example/paneer-toast.jpg">',
    `<script type="application/json">${JSON.stringify({
      media: {
        owner: { username: 'priya_kitchen' },
        pinned_comment: {
          is_pinned: true,
          user: { username: 'priya_kitchen' },
          text: pinnedComment,
        },
      },
    })}</script>`,
  ].join('')
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/paneer-toast/')

  assert.equal(recipe.title, 'Spicy paneer toast')
  assert.equal(recipe.source, 'priya_kitchen · Instagram')
  assert.equal(recipe.tags[0], 'Instagram')
  assert.equal(recipe.tags[1], 'Pinned comment')
  assert.equal(recipe.imageUrl, 'https://cdn.example/paneer-toast.jpg')
  assert.deepEqual(recipe.ingredients.map(({ quantity, name }) => ({ quantity, name })), [
    { quantity: '200g', name: 'paneer' },
    { quantity: '2 slices', name: 'sourdough' },
    { quantity: '1 tsp', name: 'chilli flakes' },
  ])
  assert.deepEqual(recipe.steps, [
    'Crumble and season the paneer.',
    'Toast the sourdough.',
    'Spoon the paneer over the toast.',
  ])
})

test('prefers a complete pinned-comment recipe over a partial caption', () => {
  const html = [
    '<meta property="og:description" content="Ingredients:&#10;• 2 eggs">',
    `<script type="application/json">${JSON.stringify({
      pinnedComment: {
        pinned: true,
        author: { username: 'Breakfast Lab' },
        text: 'Tomato eggs\nIngredients:\n• 2 eggs\n• 1 tomato\nDirections:\n1. Whisk the eggs.\n2. Fry with the tomato.',
      },
    })}</script>`,
  ].join('')
  const recipe = extractRecipe(html, 'https://www.youtube.com/shorts/example')

  assert.equal(recipe.title, 'Tomato eggs')
  assert.equal(recipe.tags[1], 'Pinned comment')
  assert.equal(recipe.ingredients.length, 2)
  assert.equal(recipe.steps.length, 2)
})

test('reads a YouTube pinned-comment renderer with rich text runs', () => {
  const html = `<script type="application/json">${JSON.stringify({
    contents: {
      commentThreadRenderer: {
        comment: {
          commentRenderer: {
            authorText: { simpleText: 'Kitchen Channel' },
            contentText: { runs: [{ text: 'One-pan eggs\nIngredients:\n2 eggs\n1 tomato\nDirections:\n1. Whisk the eggs.\n2. Cook with the tomato.' }] },
            pinnedCommentBadge: { label: 'Pinned by Kitchen Channel' },
          },
        },
      },
    },
  })}</script>`
  const recipe = extractRecipe(html, 'https://www.youtube.com/watch?v=example')

  assert.equal(recipe.title, 'One-pan eggs')
  assert.equal(recipe.source, 'Kitchen Channel · YouTube')
  assert.equal(recipe.tags[1], 'Pinned comment')
  assert.equal(recipe.ingredients.length, 2)
  assert.deepEqual(recipe.steps, ['Whisk the eggs.', 'Cook with the tomato.'])
})

test('does not import an ordinary viewer comment as the video recipe', () => {
  const html = `<script type="application/json">${JSON.stringify({
    comments: [{
      user: { username: 'random_viewer' },
      text: 'Ingredients:\n1 cup rice\nDirections:\n1. Cook the rice.',
    }],
  })}</script>`
  const recipe = extractRecipe(html, 'https://www.instagram.com/reel/no-public-recipe/')

  assert.equal(recipe.ingredients.length, 0)
  assert.equal(recipe.steps.length, 0)
})

test('labels supported social sources without assuming Instagram', () => {
  const caption = 'Ingredients:\n- 1 cup rice\nInstructions:\n1. Cook the rice.'
  const platforms = [
    ['https://www.facebook.com/watch/example', 'Facebook'],
    ['https://pin.it/example', 'Pinterest'],
    ['https://www.threads.net/@cook/post/example', 'Threads'],
    ['https://x.com/cook/status/1', 'X'],
    ['https://www.reddit.com/r/recipes/comments/example', 'Reddit'],
    ['https://www.lemon8-app.com/example', 'Lemon8'],
  ]

  for (const [url, platform] of platforms) {
    const recipe = extractRecipe(`<meta itemprop="description" content="${caption.replaceAll('\n', '&#10;')}">`, url)
    assert.equal(recipe.source, platform)
    assert.equal(recipe.tags[0], platform)
  }
})

test('parses a complete recipe shared as text without requiring a URL', () => {
  const recipe = extractRecipeFromText([
    'Sample Cook on Pinterest: Quick soup',
    'Ingredients:',
    '- 2 cups stock',
    '- 1 large carrot',
    'Directions:',
    '1. Simmer the stock.',
    '2. Add the carrot and cook until tender.',
    'https://pin.it/example',
  ].join('\n'))

  assert.equal(recipe.source, 'Sample Cook · Pinterest')
  assert.equal(recipe.sourceUrl, undefined)
  assert.equal(recipe.ingredients.length, 2)
  assert.deepEqual(recipe.steps, ['Simmer the stock.', 'Add the carrot and cook until tender.'])
})

test('separates instructions embedded in an ingredient section and drops nutrition', () => {
  const caption = [
    'Sample Cook on Instagram: &quot;Green egg salad',
    '',
    'Recipe by @samplecook',
    '',
    'Ingredients:',
    'To a bowl add 4 chopped boiled eggs and set it aside',
    'Now to a blender add a hard boiled egg',
    'A handful of mint leaves',
    'Handful of coriander leaves',
    '2 garlic cloves',
    '1 green chilli',
    '1 tbsp yogurt (20g - don&apos;t add more, you want the chutney to be thick)',
    '1 tsp salt',
    'Blend it well',
    '',
    'Now take the bowl of chopped eggs',
    'add half a chopped onion (50g)',
    'Half a chopped tomato (60g)',
    'Half a cucumber (60g)',
    'Pour the green chutney over it and mix everything really well.',
    'And your green egg salad is ready',
    '',
    'Nutrition:',
    'Calories - 460',
    'Protein - 36g',
    'Carbs - 15g',
    'Fats - 27g&quot;',
  ].join('&#10;')
  const recipe = extractRecipe(`<meta property="og:title" content="${caption}">`, 'https://www.instagram.com/reel/sample-green-egg-salad/')
  const ingredientNames = recipe.ingredients.map(({ name }) => name)

  assert.equal(recipe.ingredients.length, 11)
  assert.ok(ingredientNames.includes('chopped boiled eggs'))
  assert.ok(ingredientNames.includes('hard boiled egg'))
  assert.ok(ingredientNames.includes('mint leaves'))
  assert.ok(ingredientNames.includes('chopped onion (50g)'))
  assert.equal(ingredientNames.some((name) => /Nutrition|Calories|Protein|Carbs|Fats/i.test(name)), false)
  assert.deepEqual(recipe.steps, [
    'To a bowl add 4 chopped boiled eggs and set it aside',
    "Now to a blender add a hard boiled egg A handful of mint leaves Handful of coriander leaves 2 garlic cloves 1 green chilli 1 tbsp yogurt (20g - don't add more, you want the chutney to be thick) 1 tsp salt",
    'Blend it well',
    'Now take the bowl of chopped eggs',
    'add half a chopped onion (50g) Half a chopped tomato (60g) Half a cucumber (60g)',
    'Pour the green chutney over it and mix everything really well.',
    'And your green egg salad is ready',
  ])
})

test('drops a numbered social call to action after cooking directions', () => {
  const caption = [
    'Chicken wraps',
    'Ingredients:',
    '250g chicken',
    '2 tortillas',
    'Instructions:',
    '1. Marinate the chicken.',
    '2. Cook it through.',
    '3. Fill and fold the tortillas.',
    '4. Follow @cook for more recipes.',
    'Macros per wrap:',
    'Protein: 40g',
  ].join('\n')
  const recipe = extractRecipeFromText(caption)

  assert.deepEqual(recipe.steps, ['Marinate the chicken.', 'Cook it through.', 'Fill and fold the tortillas.'])
})

test('drops an orphaned trailing step marker left by social metadata', () => {
  const recipe = extractRecipeFromText([
    'Chicken wraps',
    'Ingredients:',
    '250g chicken',
    '2 tortillas',
    'Instructions:',
    '1. Marinate the chicken.',
    '2. Cook it through.',
    '3. Fill and fold the tortillas. 4.',
  ].join('\n'))

  assert.deepEqual(recipe.steps, ['Marinate the chicken.', 'Cook it through.', 'Fill and fold the tortillas.'])
})

test('gives combined pasted recipe text a useful fallback title', () => {
  const recipe = extractRecipeFromText('Ingredients:\n2 eggs\n1 tomato\nDirections:\n1. Whisk the eggs.\n2. Add the tomato.')

  assert.equal(recipe.title, 'Pasted recipe')
  assert.equal(recipe.ingredients.length, 2)
  assert.equal(recipe.steps.length, 2)
})
