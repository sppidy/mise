// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

import { Fragment, useEffect, useMemo, useRef, useState } from 'react'
import {
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Camera,
  ClipboardPaste,
  Check,
  CheckCircle2,
  ChefHat,
  ChevronLeft,
  ChevronRight,
  Clock3,
  Folder,
  FolderPlus,
  Code2,
  Heart,
  Home,
  ImagePlus,
  Link2,
  ListChecks,
  Minus,
  Monitor,
  Moon,
  Plus,
  Search,
  Server,
  ShoppingBasket,
  Sparkles,
  Sun,
  Trash2,
  Upload,
  Utensils,
  X,
} from 'lucide-react'
import { api, importPreviewFromError } from './api'
import type { AppState, GroceryItem, Ingredient, Recipe, View } from './types'
import { makeId } from './utils'

type Theme = 'system' | 'light' | 'dark'

const sprite = '/assets/recipe-sprite.png'
const projectSourceUrl = import.meta.env.VITE_SOURCE_URL || 'https://github.com/sppidy/mise'
const legalUrl = (document: string) => `/legal/${document}`
const browserImageUrl = (value?: string) => value && (/^https?:\/\//i.test(value) || value.startsWith('/')) ? value : undefined
const imagePosition: Record<Recipe['imagePosition'], string> = {
  tl: '0% 0%',
  tr: '100% 0%',
  bl: '0% 100%',
  br: '100% 100%',
}

const materializeRecipe = (partial: Partial<Recipe>, collection: string): Recipe => {
  const positions: Recipe['imagePosition'][] = ['tl', 'tr', 'bl', 'br']
  return {
    id: makeId(),
    title: partial.title || 'Untitled recipe',
    description: partial.description || 'A recipe saved to your recipe box.',
    source: partial.source || 'Added by you',
    sourceUrl: partial.sourceUrl,
    imageUrl: partial.imageUrl,
    postImageUrls: partial.postImageUrls,
    postMedia: partial.postMedia,
    time: partial.time || 30,
    servings: partial.servings || 4,
    difficulty: partial.difficulty || 'Easy',
    collection,
    favorite: false,
    imagePosition: positions[Math.floor(Math.random() * positions.length)],
    tags: partial.tags || ['Homemade'],
    ingredients: partial.ingredients || [],
    steps: partial.steps || [],
    createdAt: new Date().toISOString(),
  }
}

const importErrorMessage = (message: string) => /no ingredients or directions|no recipe (?:was )?found/i.test(message)
  ? 'No recipe was found in the caption or public pinned comments. Paste the creator’s pinned comment and Mise can parse it.'
  : message

const emptyState: AppState = { recipes: [], grocery: [], collections: [] }

const navItems: { id: View; label: string; icon: typeof Home }[] = [
  { id: 'home', label: 'Home', icon: Home },
  { id: 'recipes', label: 'All recipes', icon: BookOpen },
  { id: 'favorites', label: 'Favorites', icon: Heart },
  { id: 'collections', label: 'Collections', icon: Folder },
  { id: 'grocery', label: 'Grocery list', icon: ShoppingBasket },
]

function Logo() {
  return (
    <div className="brand" aria-label="Mise home">
      <span className="brand-mark"><span /></span>
      <span>mise</span>
    </div>
  )
}

function Sidebar({ view, setView, count, connected }: { view: View; setView: (view: View) => void; count: number; connected: boolean }) {
  return (
    <aside className="sidebar">
      <Logo />
      <nav className="sidebar-nav" aria-label="Main navigation">
        <p className="nav-label">Your kitchen</p>
        {navItems.map((item) => {
          const Icon = item.icon
          return (
            <button className={view === item.id ? 'nav-item active' : 'nav-item'} key={item.id} onClick={() => setView(item.id)}>
              <Icon size={19} strokeWidth={1.8} />
              <span>{item.label}</span>
              {item.id === 'recipes' && <small>{count}</small>}
            </button>
          )
        })}
      </nav>
      <div className="sidebar-foot">
        <div className={connected ? 'server-pill' : 'server-pill offline'}><Server size={15} /><span>{connected ? 'Saved on your server' : 'Saved in this browser'}</span><i /></div>
        <p>Self-hosted.<br />No ads. No subscription.</p>
        <a className="source-code-link" href={projectSourceUrl} target="_blank" rel="noreferrer"><Code2 size={14} /> Source code</a>
      </div>
    </aside>
  )
}

function MobileNav({ view, setView }: { view: View; setView: (view: View) => void }) {
  return (
    <nav className="mobile-nav" aria-label="Mobile navigation">
      {navItems.filter((item) => item.id !== 'favorites').map((item) => {
        const Icon = item.icon
        return (
          <button key={item.id} className={view === item.id ? 'active' : ''} onClick={() => setView(item.id)}>
            <Icon size={21} />
            <span>{item.label.replace('All ', '')}</span>
          </button>
        )
      })}
    </nav>
  )
}

function Header({ title, onImport, onSettings, onTheme, dark, search, setSearch }: { title: string; onImport: () => void; onSettings: () => void; onTheme: () => void; dark: boolean; search: string; setSearch: (value: string) => void }) {
  return (
    <header className="topbar">
      <div className="mobile-logo"><Logo /></div>
      <div className="page-title">{title}</div>
      <label className="search-box">
        <Search size={18} />
        <input aria-label="Search recipes" placeholder="Search your recipes" value={search} onChange={(event) => setSearch(event.target.value)} />
        <kbd>⌘ K</kbd>
      </label>
      <button className="primary compact" onClick={onImport}><Plus size={18} /> Import recipe</button>
      <button className="header-icon" aria-label={dark ? 'Use light mode' : 'Use dark mode'} onClick={onTheme}>{dark ? <Sun size={17} /> : <Moon size={17} />}</button>
      <button className="avatar" aria-label="Server settings" title="Connect to your server" onClick={onSettings}><Server size={17} /></button>
    </header>
  )
}

function RecipeImage({ recipe, className = '' }: { recipe: Recipe; className?: string }) {
  const image = browserImageUrl(recipe.imageUrl)
  const source = image || sprite
  return <div className={`recipe-image ${className}`} role="img" aria-label={recipe.title} style={{ backgroundImage: `url(${JSON.stringify(source)})`, backgroundPosition: image ? 'center' : imagePosition[recipe.imagePosition], backgroundSize: image ? 'cover' : '200% 200%' }} />
}

function RecipeCard({ recipe, onOpen, onFavorite }: { recipe: Recipe; onOpen: () => void; onFavorite: () => void }) {
  return (
    <article className="recipe-card" onClick={onOpen} tabIndex={0} onKeyDown={(event) => event.key === 'Enter' && onOpen()}>
      <div className="card-media">
        <RecipeImage recipe={recipe} />
        <button
          className={recipe.favorite ? 'favorite active' : 'favorite'}
          aria-label={recipe.favorite ? 'Remove from favorites' : 'Add to favorites'}
          onClick={(event) => { event.stopPropagation(); onFavorite() }}
        >
          <Heart size={18} fill={recipe.favorite ? 'currentColor' : 'none'} />
        </button>
        <span className="time-badge"><Clock3 size={14} /> {recipe.time} min</span>
      </div>
      <div className="card-copy">
        <div className="eyebrow">{recipe.collection}</div>
        <h3>{recipe.title}</h3>
        <div className="card-meta"><span>{recipe.source}</span><i /> <span>{recipe.difficulty}</span></div>
      </div>
    </article>
  )
}

function RecipeGrid({ recipes, onOpen, onFavorite, emptyCopy = 'No recipes found.' }: { recipes: Recipe[]; onOpen: (recipe: Recipe) => void; onFavorite: (id: string) => void; emptyCopy?: string }) {
  if (!recipes.length) {
    return <div className="empty-panel"><Utensils size={28} /><h3>{emptyCopy}</h3><p>Try another search or import something delicious.</p></div>
  }
  return <div className="recipe-grid">{recipes.map((recipe) => <RecipeCard key={recipe.id} recipe={recipe} onOpen={() => onOpen(recipe)} onFavorite={() => onFavorite(recipe.id)} />)}</div>
}

function HomeView({ recipes, search, onImport, onOpen, onFavorite, setView }: { recipes: Recipe[]; search: string; onImport: () => void; onOpen: (recipe: Recipe) => void; onFavorite: (id: string) => void; setView: (view: View) => void }) {
  const filtered = recipes.filter((recipe) => `${recipe.title} ${recipe.tags.join(' ')} ${recipe.collection}`.toLowerCase().includes(search.toLowerCase()))
  return (
    <>
      <section className="welcome-row">
        <div>
          <span className="kicker"><Sparkles size={15} /> Your recipe box</span>
          <h1>What are we<br /><em>cooking?</em></h1>
          <p>Save recipes from across the web or add your own.<br />Keep them in this browser or on your server.</p>
        </div>
        <div className="hero-plate" aria-hidden="true">
          <div className="plate-food" />
          <span className="herb herb-one">☘</span>
          <span className="herb herb-two">⌇</span>
          <div className="hero-note"><span>Tonight's pick</span><strong>{recipes[0]?.title || 'Your next favorite'}</strong><small><Clock3 size={13} /> {recipes[0]?.time || 25} minutes</small></div>
        </div>
      </section>

      <section className="import-strip" onClick={onImport}>
        <div className="import-icon"><Link2 size={24} /></div>
        <div><strong>Bring a recipe in</strong><span>Paste a public recipe link. Mise will look for ingredients and directions.</span></div>
        <button className="light-button">Import a link <ArrowRight size={17} /></button>
      </section>

      <section className="section-block">
        <div className="section-heading"><div><span className="kicker">Back to the good stuff</span><h2>{search ? 'Search results' : 'Recently saved'}</h2></div><button className="text-button" onClick={() => setView('recipes')}>View all <ArrowUpRight size={16} /></button></div>
        <RecipeGrid recipes={filtered.slice(0, 4)} onOpen={onOpen} onFavorite={onFavorite} />
      </section>
    </>
  )
}

function RecipesView({ recipes, search, onOpen, onFavorite }: { recipes: Recipe[]; search: string; onOpen: (recipe: Recipe) => void; onFavorite: (id: string) => void }) {
  const [filter, setFilter] = useState('All')
  const filters = ['All', 'Quick', 'Vegetarian', 'Baking', 'Favorites']
  const visible = recipes.filter((recipe) => {
    const matchesSearch = `${recipe.title} ${recipe.tags.join(' ')} ${recipe.collection}`.toLowerCase().includes(search.toLowerCase())
    const matchesFilter = filter === 'All' || (filter === 'Quick' && recipe.time <= 30) || (filter === 'Favorites' && recipe.favorite) || recipe.tags.includes(filter)
    return matchesSearch && matchesFilter
  })
  return (
    <section className="page-section">
      <div className="page-hero"><div><span className="kicker">Your personal cookbook</span><h1>All recipes</h1><p>{recipes.length} recipes, ready when you are.</p></div></div>
      <div className="filter-row">{filters.map((item) => <button key={item} onClick={() => setFilter(item)} className={filter === item ? 'chip active' : 'chip'}>{item}</button>)}</div>
      <RecipeGrid recipes={visible} onOpen={onOpen} onFavorite={onFavorite} />
    </section>
  )
}

function CollectionsView({ state, onOpen, setView }: { state: AppState; onOpen: (recipe: Recipe) => void; setView: (view: View) => void }) {
  const icons = [ChefHat, Clock3, BookOpen]
  return (
    <section className="page-section">
      <div className="page-hero"><div><span className="kicker">A place for everything</span><h1>Collections</h1><p>Group recipes around the way you actually cook.</p></div><button className="outline-button" onClick={() => setView('recipes')}>Browse all recipes</button></div>
      <div className="collection-grid">
        {state.collections.map((collection, index) => {
          const Icon = icons[index % icons.length]
          const recipes = state.recipes.filter((recipe) => recipe.collection === collection)
          return <button className={`collection-card tone-${index % 3}`} key={collection} onClick={() => recipes[0] && onOpen(recipes[0])}><span className="collection-icon"><Icon size={24} /></span><span><strong>{collection}</strong><small>{recipes.length} {recipes.length === 1 ? 'recipe' : 'recipes'}</small></span><ChevronRight size={20} /></button>
        })}
      </div>
      <div className="section-heading small"><div><span className="kicker">Curated by you</span><h2>Collection highlights</h2></div></div>
      <RecipeGrid recipes={state.recipes.slice(0, 3)} onOpen={onOpen} onFavorite={() => {}} />
    </section>
  )
}

function GroceryView({ items, onToggle, onAdd, onClear }: { items: GroceryItem[]; onToggle: (id: string) => void; onAdd: (name: string) => void; onClear: () => void }) {
  const [name, setName] = useState('')
  const groups = useMemo(() => [...new Set(items.map((item) => item.category))], [items])
  const checked = items.filter((item) => item.checked).length
  const add = () => { if (name.trim()) { onAdd(name.trim()); setName('') } }
  return (
    <section className="page-section grocery-page">
      <div className="page-hero grocery-head"><div><span className="kicker">One calm trip</span><h1>Grocery list</h1><p>{items.length - checked} items left to pick up.</p></div><div className="grocery-progress"><span style={{ width: `${items.length ? (checked / items.length) * 100 : 0}%` }} /></div></div>
      <div className="grocery-layout">
        <div className="grocery-list">
          <form className="add-item" onSubmit={(event) => { event.preventDefault(); add() }}><Plus size={19} /><input value={name} onChange={(event) => setName(event.target.value)} placeholder="Add an item…" aria-label="Grocery item" /><button>Add</button></form>
          {groups.map((group) => (
            <div className="grocery-group" key={group}><h3>{group}</h3>{items.filter((item) => item.category === group).map((item) => <button className={item.checked ? 'grocery-item checked' : 'grocery-item'} key={item.id} onClick={() => onToggle(item.id)}><span className="check-box">{item.checked && <Check size={15} />}</span><span>{item.name}</span><small>{item.amount}</small></button>)}</div>
          ))}
          {!items.length && <div className="empty-panel"><ShoppingBasket size={28} /><h3>Your list is clear</h3><p>Add ingredients from a recipe or type one above.</p></div>}
          {checked > 0 && <button className="text-button clear-button" onClick={onClear}><Trash2 size={15} /> Clear {checked} checked</button>}
        </div>
        <aside className="grocery-tip"><span><ListChecks size={24} /></span><h3>Cook smarter</h3><p>Open any recipe and add every ingredient to this list in one tap.</p></aside>
      </div>
    </section>
  )
}

function ImportModal({ collections, initialUrl = '', initialError = '', initialPreview, onClose, onAdd }: { collections: string[]; initialUrl?: string; initialError?: string; initialPreview?: Partial<Recipe>; onClose: () => void; onAdd: (recipe: Recipe) => void }) {
  const [mode, setMode] = useState<'link' | 'photo' | 'paste' | 'manual'>('link')
  const [url, setUrl] = useState(initialUrl)
  const [pastedText, setPastedText] = useState('')
  const [title, setTitle] = useState('')
  const [minutes, setMinutes] = useState('30')
  const [collection, setCollection] = useState(collections[0] || 'Unsorted')
  const [ingredients, setIngredients] = useState('')
  const [steps, setSteps] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(importErrorMessage(initialError))
  const [photoName, setPhotoName] = useState('')
  const [photoProgress, setPhotoProgress] = useState(0)
  const [photoRecipe, setPhotoRecipe] = useState<Partial<Recipe> | null>(null)
  const [linkPreview, setLinkPreview] = useState<Partial<Recipe> | undefined>(initialPreview)
  const linkPreviewImage = browserImageUrl(linkPreview?.imageUrl)
  const photoInput = useRef<HTMLInputElement>(null)

  const finish = (partial: Partial<Recipe>) => {
    const finalRecipe = materializeRecipe({ ...partial, time: partial.time || Number(minutes) || 30 }, collection)
    onAdd(finalRecipe)
  }

  const importLink = async () => {
    if (!url.trim()) return setError('Paste a recipe link first.')
    setBusy(true); setError('')
    try {
      finish(await api.importRecipe(url.trim()))
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Could not import this recipe.'
      setLinkPreview(importPreviewFromError(error))
      setError(importErrorMessage(message))
    } finally { setBusy(false) }
  }

  const addManual = () => {
    if (!title.trim()) return setError('Give your recipe a name.')
    const ingredientRows: Ingredient[] = ingredients.split('\n').filter(Boolean).map((line) => ({ id: makeId(), quantity: '', name: line.trim() }))
    finish({ title: title.trim(), time: Number(minutes) || 30, ingredients: ingredientRows, steps: steps.split('\n').filter(Boolean).map((step) => step.replace(/^\d+[.)]\s*/, '').trim()) })
  }

  const importText = () => {
    if (!pastedText.trim()) return setError('Paste the ingredients and directions first.')
    setError('')
    try {
      const parsed = api.importSharedText(pastedText.trim(), url.trim())
      finish({ ...parsed, imageUrl: parsed.imageUrl || linkPreview?.imageUrl })
    }
    catch (error) { setError(error instanceof Error ? error.message : 'Could not find ingredients or directions in that text.') }
  }

  const analyzePhoto = async (file?: File) => {
    if (!file) return
    if (!file.type.startsWith('image/')) return setError('Choose a JPG, PNG, or WebP recipe photo.')
    setBusy(true); setError(''); setPhotoRecipe(null); setPhotoName(file.name); setPhotoProgress(0)
    try {
      const extractedText = await api.analyzePhoto(file, ({ progress = 0 }) => setPhotoProgress(Math.round(progress * 100)))
      setPastedText(extractedText)
      try {
        const parsed = api.importSharedText(extractedText)
        setPhotoRecipe({ ...parsed, source: 'Recipe photo', sourceUrl: undefined, tags: ['Photo import', ...(parsed.tags || []).filter((tag) => tag !== 'Imported')] })
      } catch {
        setMode('paste')
        setError('Text was found, but the recipe sections were unclear. Review the scan below and add “Ingredients” and “Directions” headings.')
      }
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Mise could not analyze that photo.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="import-modal" role="dialog" aria-modal="true" aria-labelledby="import-title">
        <button className="close-button" onClick={onClose} aria-label="Close"><X size={20} /></button>
        <span className="modal-icon"><Upload size={23} /></span>
        <p className="kicker">Import a recipe</p>
        <h2 id="import-title">Bring a recipe into Mise</h2>
        <p className="modal-intro">Import a link, scan a recipe photo, paste text, or add it yourself.</p>
        <div className="tab-switch"><button className={mode === 'link' ? 'active' : ''} onClick={() => setMode('link')}><Link2 size={16} /> Link</button><button className={mode === 'photo' ? 'active' : ''} onClick={() => setMode('photo')}><Camera size={16} /> Photo</button><button className={mode === 'paste' ? 'active' : ''} onClick={() => setMode('paste')}><ClipboardPaste size={16} /> Text</button><button className={mode === 'manual' ? 'active' : ''} onClick={() => setMode('manual')}><BookOpen size={16} /> Manual</button></div>
        {mode === 'link' ? (
          <div className="modal-form">
            <label>Recipe URL<div className="url-input"><Link2 size={18} /><input type="url" autoFocus placeholder="https://example.com/my-favorite-recipe" value={url} onChange={(event) => { setUrl(event.target.value); setLinkPreview(undefined) }} onKeyDown={(event) => event.key === 'Enter' && importLink()} /></div></label>
            <label>Save to<select value={collection} onChange={(event) => setCollection(event.target.value)}>{collections.map((item) => <option key={item}>{item}</option>)}</select></label>
            <button className="primary wide" onClick={importLink} disabled={busy}>{busy ? <><span className="spinner" /> Reading the recipe…</> : <>Import recipe <ArrowRight size={18} /></>}</button>
            <p className="privacy-note"><Server size={14} /> Fetched by your server, or directly by Android when disconnected.</p>
          </div>
        ) : mode === 'photo' ? (
          <div className="modal-form photo-form">
            <input ref={photoInput} className="photo-input" type="file" accept="image/jpeg,image/png,image/webp" onChange={(event) => analyzePhoto(event.target.files?.[0])} />
            <button className={photoRecipe ? 'photo-drop has-result' : 'photo-drop'} type="button" onClick={() => photoInput.current?.click()} disabled={busy}>
              {busy ? <span className="spinner dark" /> : photoRecipe ? <CheckCircle2 size={30} /> : <ImagePlus size={31} />}
              <strong>{busy ? 'Reading your recipe…' : photoRecipe ? 'Recipe text found' : 'Choose a recipe photo'}</strong>
              <span>{busy ? `${photoProgress || '…'}% · keep this screen open` : photoName || 'Take a photo or pick one from your gallery'}</span>
            </button>
            {photoRecipe && <div className="photo-result"><span><strong>{photoRecipe.title}</strong><small>{photoRecipe.ingredients?.length || 0} ingredients · {photoRecipe.steps?.length || 0} directions</small></span><button type="button" onClick={() => photoInput.current?.click()}>Choose another</button></div>}
            <label>Save to<select value={collection} onChange={(event) => setCollection(event.target.value)}>{collections.map((item) => <option key={item}>{item}</option>)}</select></label>
            <button className="primary wide" onClick={() => photoRecipe && finish(photoRecipe)} disabled={!photoRecipe || busy}><Camera size={18} /> Add scanned recipe</button>
            <p className="privacy-note"><Sparkles size={14} /> Photos are analyzed on your device; clear, straight-on text works best.</p>
          </div>
        ) : mode === 'paste' ? (
          <div className="modal-form paste-form">
            <label>Caption or pinned comment <span>paste everything together</span><textarea autoFocus rows={12} value={pastedText} onChange={(event) => setPastedText(event.target.value)} placeholder={'Creamy tomato pasta\n\nIngredients:\n250g pasta\n2 tomatoes\n1 tbsp olive oil\n\nDirections:\n1. Boil the pasta.\n2. Cook the tomatoes in olive oil.\n3. Toss together and serve.'} /></label>
            {linkPreviewImage && <div className="link-cover-preview"><span style={{ backgroundImage: `url(${JSON.stringify(linkPreviewImage)})` }} /><div><strong>Cover photo found</strong><small>It will be saved with this recipe.</small></div></div>}
            <label>Save to<select value={collection} onChange={(event) => setCollection(event.target.value)}>{collections.map((item) => <option key={item}>{item}</option>)}</select></label>
            <button className="primary wide" onClick={importText}><ClipboardPaste size={18} /> Parse and save</button>
            <p className="privacy-note"><Sparkles size={14} /> Include “Ingredients” and “Directions” headings for the cleanest result.</p>
          </div>
        ) : (
          <div className="modal-form manual-form">
            <div className="field-row"><label>Recipe name<input autoFocus value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Sunday roast" /></label><label>Minutes<input type="number" min="1" value={minutes} onChange={(event) => setMinutes(event.target.value)} /></label></div>
            <label>Save to<select value={collection} onChange={(event) => setCollection(event.target.value)}>{collections.map((item) => <option key={item}>{item}</option>)}</select></label>
            <label>Ingredients <span>one per line</span><textarea rows={4} value={ingredients} onChange={(event) => setIngredients(event.target.value)} placeholder={'2 cups flour\n3 ripe bananas\n1 pinch salt'} /></label>
            <label>Directions <span>one step per line</span><textarea rows={4} value={steps} onChange={(event) => setSteps(event.target.value)} placeholder={'Mix the dry ingredients.\nFold in the bananas.\nBake until golden.'} /></label>
            <button className="primary wide" onClick={addManual}>Save recipe <Check size={18} /></button>
          </div>
        )}
        {error && <div className="form-error"><span>{error}</span>{mode === 'link' && <button type="button" onClick={() => { setMode('paste'); setError('') }}><ClipboardPaste size={14} /> Paste pinned comment</button>}</div>}
      </div>
    </div>
  )
}

function SharedCollectionPicker({ recipe, collections, onClose, onSave }: { recipe: Partial<Recipe>; collections: string[]; onClose: () => void; onSave: (collection: string) => void }) {
  const options = collections.length ? collections : ['Unsorted']
  const [selected, setSelected] = useState(options[0])
  const [creating, setCreating] = useState(false)
  const [newCollection, setNewCollection] = useState('')
  const destination = creating ? newCollection.trim() : selected

  return (
    <div className="modal-backdrop" role="presentation">
      <div className="collection-picker" role="dialog" aria-modal="true" aria-labelledby="collection-picker-title">
        <button className="close-button" onClick={onClose} aria-label="Cancel shared import"><X size={20} /></button>
        <span className="modal-icon collection"><FolderPlus size={23} /></span>
        <p className="kicker">Recipe ready</p>
        <h2 id="collection-picker-title">Which list should it go in?</h2>
        <p className="modal-intro"><strong>{recipe.title || 'Shared recipe'}</strong> has been analyzed but is not saved yet.</p>
        <div className="collection-options">
          {options.map((collection) => <button key={collection} className={!creating && selected === collection ? 'active' : ''} onClick={() => { setCreating(false); setSelected(collection) }}><Folder size={18} /><span>{collection}</span>{!creating && selected === collection && <Check size={17} />}</button>)}
          <button className={creating ? 'active new' : 'new'} onClick={() => setCreating(true)}><FolderPlus size={18} /><span>New list</span>{creating && <Check size={17} />}</button>
        </div>
        {creating && <label className="new-collection-field">List name<input autoFocus value={newCollection} maxLength={50} onChange={(event) => setNewCollection(event.target.value)} onKeyDown={(event) => event.key === 'Enter' && destination && onSave(destination)} placeholder="e.g. Weekend dinners" /></label>}
        <button className="primary wide" disabled={!destination} onClick={() => destination && onSave(destination)}><Folder size={18} /> Save to {destination || 'new list'}</button>
      </div>
    </div>
  )
}

function RecipeDetail({ recipe, onClose, onFavorite, onGrocery, onCook, onDelete }: { recipe: Recipe; onClose: () => void; onFavorite: () => void; onGrocery: () => void; onCook: () => void; onDelete: () => void }) {
  const [servings, setServings] = useState(recipe.servings)
  const [checked, setChecked] = useState<string[]>([])
  return (
    <div className="drawer-backdrop" onMouseDown={(event) => event.currentTarget === event.target && onClose()}>
      <article className="recipe-drawer">
        <div className="drawer-media"><RecipeImage recipe={recipe} /><button className="drawer-close" onClick={onClose} aria-label="Close"><X size={20} /></button><div className="drawer-actions"><button onClick={onFavorite}><Heart size={18} fill={recipe.favorite ? 'currentColor' : 'none'} /></button><button onClick={onDelete} title="Delete recipe"><Trash2 size={18} /></button></div></div>
        <div className="drawer-content">
          <div className="eyebrow">{recipe.collection}</div>
          <h1>{recipe.title}</h1>
          <p className="recipe-description">{recipe.description}</p>
          <div className="detail-meta"><span><Clock3 size={17} /><strong>{recipe.time}</strong> min</span><span><ChefHat size={17} /><strong>{recipe.difficulty}</strong></span><span className="servings-control"><Utensils size={17} /><button onClick={() => setServings(Math.max(1, servings - 1))}><Minus size={13} /></button><strong>{servings}</strong><button onClick={() => setServings(servings + 1)}><Plus size={13} /></button></span></div>
          <div className="source-line">Saved from <a href={recipe.sourceUrl} target="_blank" rel="noreferrer">{recipe.source} {recipe.sourceUrl && <ArrowUpRight size={13} />}</a></div>
          <section className="ingredients-section">
            <div className="detail-heading"><h2>Ingredients</h2><button onClick={onGrocery}><ShoppingBasket size={16} /> Add to list</button></div>
            {recipe.ingredients.length ? <div className="ingredient-list">{recipe.ingredients.map((ingredient, index) => <Fragment key={ingredient.id}>{ingredient.group && ingredient.group !== recipe.ingredients[index - 1]?.group && <h3 className="ingredient-group">{ingredient.group}</h3>}<button className={checked.includes(ingredient.id) ? 'ingredient checked' : 'ingredient'} onClick={() => setChecked((items) => items.includes(ingredient.id) ? items.filter((id) => id !== ingredient.id) : [...items, ingredient.id])}><span className="round-check">{checked.includes(ingredient.id) && <Check size={13} />}</span><strong>{ingredient.quantity}</strong><span>{ingredient.name}</span></button></Fragment>)}</div> : <p className="muted-empty">No ingredients were found on the source page. You can still use cook mode for the saved directions.</p>}
          </section>
          <section className="directions-preview"><h2>Directions</h2>{recipe.steps.map((step, index) => <div key={index}><span>{index + 1}</span><p>{step}</p></div>)}{!recipe.steps.length && <p className="muted-empty">No directions were found. The original source link is saved above.</p>}</section>
        </div>
        <footer className="drawer-footer"><button className="primary wide" disabled={!recipe.steps.length} onClick={onCook}><ChefHat size={19} /> Start cooking</button></footer>
      </article>
    </div>
  )
}

function CookMode({ recipe, onExit }: { recipe: Recipe; onExit: () => void }) {
  const [step, setStep] = useState(0)
  const [seconds, setSeconds] = useState(300)
  const [running, setRunning] = useState(false)
  useEffect(() => {
    if (!running || seconds <= 0) return
    const timer = window.setInterval(() => setSeconds((value) => value - 1), 1000)
    return () => window.clearInterval(timer)
  }, [running, seconds])
  const minutes = String(Math.floor(seconds / 60)).padStart(2, '0')
  const remainingSeconds = String(seconds % 60).padStart(2, '0')
  return (
    <div className="cook-mode">
      <header><Logo /><div className="cook-progress"><span>Step {step + 1} of {recipe.steps.length}</span><div><i style={{ width: `${((step + 1) / recipe.steps.length) * 100}%` }} /></div></div><button onClick={onExit}>Exit cook mode <X size={18} /></button></header>
      <main>
        <aside><RecipeImage recipe={recipe} /><div><span className="kicker">Cooking</span><h2>{recipe.title}</h2></div><div className="mini-timer"><Clock3 size={20} /><div><span>Kitchen timer</span><strong>{minutes}:{remainingSeconds}</strong></div><button onClick={() => setRunning(!running)}>{running ? 'Pause' : seconds === 300 ? 'Start' : 'Resume'}</button></div></aside>
        <section className="cook-step"><span className="step-number">{String(step + 1).padStart(2, '0')}</span><p>{recipe.steps[step]}</p><div className="cook-controls"><button className="round-nav" disabled={step === 0} onClick={() => setStep(step - 1)}><ChevronLeft /></button>{step === recipe.steps.length - 1 ? <button className="primary finish" onClick={onExit}><CheckCircle2 /> Finish cooking</button> : <button className="primary next" onClick={() => setStep(step + 1)}>Next step <ArrowRight /></button>}</div></section>
      </main>
    </div>
  )
}

function SettingsModal({ theme, onTheme, onClose, onSave }: { theme: Theme; onTheme: (theme: Theme) => void; onClose: () => void; onSave: (url: string) => Promise<void> }) {
  const [serverUrl, setServerUrl] = useState(api.getServerUrl())
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const save = async () => {
    setSaving(true)
    setError('')
    try {
      await onSave(serverUrl)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Could not connect to that server')
      setSaving(false)
    }
  }
  return (
    <div className="modal-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <div className="settings-modal" role="dialog" aria-modal="true" aria-labelledby="settings-title">
        <button className="close-button" onClick={onClose} aria-label="Close"><X size={20} /></button>
        <span className="modal-icon server"><Server size={23} /></span>
        <p className="kicker">Your data, your home</p>
        <h2 id="settings-title">Server connection</h2>
        <p className="modal-intro">Leave this blank to use the server that delivered this page.</p>
        <div className="connection-note"><i /><div><strong>{serverUrl || window.location.origin}</strong><span>Recipes sync through this server</span></div></div>
        <div className="modal-form">
          <label>Self-hosted server URL<input type="url" value={serverUrl} onChange={(event) => setServerUrl(event.target.value)} placeholder="https://recipes.example.com" /></label>
          <label>Appearance<div className="theme-options">
            <button className={theme === 'system' ? 'active' : ''} onClick={() => onTheme('system')}><Monitor size={16} /> System</button>
            <button className={theme === 'light' ? 'active' : ''} onClick={() => onTheme('light')}><Sun size={16} /> Light</button>
            <button className={theme === 'dark' ? 'active' : ''} onClick={() => onTheme('dark')}><Moon size={16} /> Dark</button>
          </div></label>
          <button className="primary wide" disabled={saving} onClick={save}><Check size={18} /> {saving ? 'Connecting…' : 'Save connection'}</button>
          {error && <p className="form-error" role="alert"><span>{error}</span></p>}
          <p className="privacy-note"><Server size={14} /> Use HTTPS when connecting outside your home network.</p>
          <p className="legal-note">Created by Ramshouriesh (sppidy). Copyright © 2026 Mise contributors. Mise is free software under AGPL-3.0-or-later and comes without a warranty.</p>
          <div className="project-links">
            <a href={projectSourceUrl} target="_blank" rel="noreferrer">Source code <ArrowUpRight size={13} /></a>
            <a href={legalUrl('LICENSE')} target="_blank" rel="noreferrer">License <ArrowUpRight size={13} /></a>
            <a href={legalUrl('PRIVACY.md')} target="_blank" rel="noreferrer">Privacy <ArrowUpRight size={13} /></a>
            <a href={legalUrl('THIRD_PARTY_NOTICES.md')} target="_blank" rel="noreferrer">Notices <ArrowUpRight size={13} /></a>
          </div>
        </div>
      </div>
    </div>
  )
}

function Toast({ children }: { children: string }) {
  return <div className="toast"><CheckCircle2 size={18} />{children}</div>
}

export default function App() {
  const [state, setState] = useState<AppState>(emptyState)
  const [loaded, setLoaded] = useState(false)
  const [serverConnected, setServerConnected] = useState(false)
  const [theme, setTheme] = useState<Theme>(() => (localStorage.getItem('mise-theme') as Theme) || 'system')
  const [systemDark, setSystemDark] = useState(() => window.matchMedia('(prefers-color-scheme: dark)').matches)
  const [view, setView] = useState<View>('home')
  const [search, setSearch] = useState('')
  const [importOpen, setImportOpen] = useState(false)
  const [settingsOpen, setSettingsOpen] = useState(false)
  const [sharedUrl, setSharedUrl] = useState('')
  const [sharedImportError, setSharedImportError] = useState('')
  const [sharedImportPreview, setSharedImportPreview] = useState<Partial<Recipe> | null>(null)
  const [pendingSharedRecipe, setPendingSharedRecipe] = useState<Partial<Recipe> | null>(null)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [cookingId, setCookingId] = useState<string | null>(null)
  const [toast, setToast] = useState('')
  const selected = state.recipes.find((recipe) => recipe.id === selectedId) || null
  const cooking = state.recipes.find((recipe) => recipe.id === cookingId) || null
  const dark = theme === 'dark' || (theme === 'system' && systemDark)

  useEffect(() => { api.load().then(({ state: saved, connected }) => { setState(saved); setServerConnected(connected); setLoaded(true) }) }, [])
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const update = () => setSystemDark(media.matches)
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])
  useEffect(() => {
    document.documentElement.dataset.theme = dark ? 'dark' : 'light'
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#111a17' : '#f5f3eb')
  }, [dark])
  useEffect(() => {
    if (!loaded) return
    const timer = window.setTimeout(() => { void api.save(state).then(setServerConnected) }, 250)
    return () => window.clearTimeout(timer)
  }, [state, loaded])
  useEffect(() => {
    const handler = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
        event.preventDefault()
        const input = document.querySelector<HTMLInputElement>('.search-box input')
        input?.focus()
      }
      if (event.key === 'Escape') { setImportOpen(false); setSelectedId(null) }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [])
  const notify = (message: string) => { setToast(message); window.setTimeout(() => setToast(''), 2600) }
  const changeTheme = (next: Theme) => { setTheme(next); localStorage.setItem('mise-theme', next) }
  const updateRecipes = (updater: (recipes: Recipe[]) => Recipe[]) => setState((current) => ({ ...current, recipes: updater(current.recipes) }))
  const toggleFavorite = (id: string) => updateRecipes((recipes) => recipes.map((recipe) => recipe.id === id ? { ...recipe, favorite: !recipe.favorite } : recipe))
  const addRecipe = (recipe: Recipe) => { updateRecipes((recipes) => [recipe, ...recipes.filter((item) => !recipe.sourceUrl || item.sourceUrl !== recipe.sourceUrl)]); setImportOpen(false); setSharedUrl(''); setSharedImportError(''); setSharedImportPreview(null); setSelectedId(recipe.id); notify('Recipe saved to your box') }
  const saveSharedRecipe = (collection: string) => {
    if (!pendingSharedRecipe) return
    const recipe = materializeRecipe(pendingSharedRecipe, collection)
    setState((current) => ({
      ...current,
      collections: current.collections.includes(collection) ? current.collections : [...current.collections, collection],
      recipes: [recipe, ...current.recipes.filter((item) => !recipe.sourceUrl || item.sourceUrl !== recipe.sourceUrl)],
    }))
    setPendingSharedRecipe(null)
    setView('recipes')
    setSelectedId(recipe.id)
    notify(`Recipe saved to ${collection}`)
  }
  const deleteRecipe = (id: string) => { updateRecipes((recipes) => recipes.filter((recipe) => recipe.id !== id)); setSelectedId(null); notify('Recipe deleted') }
  const addToGrocery = (recipe: Recipe) => {
    const additions = recipe.ingredients.map((ingredient) => ({ id: makeId(), name: ingredient.name, amount: ingredient.quantity, checked: false, category: 'From recipes' }))
    setState((current) => ({ ...current, grocery: [...current.grocery, ...additions] })); notify(`${additions.length} ingredients added to your list`)
  }
  const addGroceryItem = (name: string) => setState((current) => ({ ...current, grocery: [{ id: makeId(), name, amount: '', checked: false, category: 'Other' }, ...current.grocery] }))
  const title = navItems.find((item) => item.id === view)?.label || 'Home'
  const viewRecipes = view === 'favorites' ? state.recipes.filter((recipe) => recipe.favorite && `${recipe.title} ${recipe.tags.join(' ')}`.toLowerCase().includes(search.toLowerCase())) : state.recipes

  if (!loaded) return <div className="splash"><Logo /><span className="spinner dark" /><p>Opening your recipe box…</p></div>
  if (cooking) return <CookMode recipe={cooking} onExit={() => setCookingId(null)} />

  return (
    <div className="app-shell">
      <Sidebar view={view} setView={setView} count={state.recipes.length} connected={serverConnected} />
      <div className="main-shell">
        <Header title={title} onImport={() => setImportOpen(true)} onSettings={() => setSettingsOpen(true)} onTheme={() => changeTheme(dark ? 'light' : 'dark')} dark={dark} search={search} setSearch={setSearch} />
        <main className="content">
          {view === 'home' && <HomeView recipes={state.recipes} search={search} onImport={() => setImportOpen(true)} onOpen={(recipe) => setSelectedId(recipe.id)} onFavorite={toggleFavorite} setView={setView} />}
          {view === 'recipes' && <RecipesView recipes={state.recipes} search={search} onOpen={(recipe) => setSelectedId(recipe.id)} onFavorite={toggleFavorite} />}
          {view === 'favorites' && <section className="page-section"><div className="page-hero"><div><span className="kicker">Keepers, all together</span><h1>Favorites</h1><p>The recipes you reach for most.</p></div></div><RecipeGrid recipes={viewRecipes} onOpen={(recipe) => setSelectedId(recipe.id)} onFavorite={toggleFavorite} emptyCopy="No favorites yet" /></section>}
          {view === 'collections' && <CollectionsView state={state} onOpen={(recipe) => setSelectedId(recipe.id)} setView={setView} />}
          {view === 'grocery' && <GroceryView items={state.grocery} onAdd={addGroceryItem} onToggle={(id) => setState((current) => ({ ...current, grocery: current.grocery.map((item) => item.id === id ? { ...item, checked: !item.checked } : item) }))} onClear={() => setState((current) => ({ ...current, grocery: current.grocery.filter((item) => !item.checked) }))} />}
        </main>
      </div>
      <MobileNav view={view} setView={setView} />
      <button className="mobile-import" onClick={() => setImportOpen(true)} aria-label="Import recipe"><Plus size={25} /></button>
      {importOpen && <ImportModal collections={state.collections} initialUrl={sharedUrl} initialError={sharedImportError} initialPreview={sharedImportPreview || undefined} onClose={() => { setImportOpen(false); setSharedUrl(''); setSharedImportError(''); setSharedImportPreview(null) }} onAdd={addRecipe} />}
      {pendingSharedRecipe && <SharedCollectionPicker recipe={pendingSharedRecipe} collections={state.collections} onClose={() => setPendingSharedRecipe(null)} onSave={saveSharedRecipe} />}
      {settingsOpen && <SettingsModal theme={theme} onTheme={changeTheme} onClose={() => setSettingsOpen(false)} onSave={async (url) => { const remote = await api.connect(url, state); setState(remote); setServerConnected(true); setSettingsOpen(false); notify('Server connected') }} />}
      {selected && <RecipeDetail recipe={selected} onClose={() => setSelectedId(null)} onFavorite={() => toggleFavorite(selected.id)} onGrocery={() => addToGrocery(selected)} onCook={() => { setCookingId(selected.id); setSelectedId(null) }} onDelete={() => deleteRecipe(selected.id)} />}
      {toast && <Toast>{toast}</Toast>}
    </div>
  )
}
