<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { BarChart, LineChart, PieChart, ScatterChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { getInstanceByDom, init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, LineChart, PieChart, ScatterChart, GridComponent, LegendComponent, TitleComponent, TooltipComponent, CanvasRenderer])

const API_BASE = '/api'
const savedUser = JSON.parse(localStorage.getItem('movieUser') || 'null')

const state = reactive({
  user: savedUser,
  activeView: 'recommend',
  authMode: 'login',
  topN: 5,
  loading: false,
  movieLoading: false,
  ratingLoading: false,
  authLoading: false,
  submitting: false,
  llmLoading: false,
  statsLoading: false,
  detailLoading: false,
  recommendationRefreshing: false,
  suggestionLoading: false,
  error: '',
  message: '',
  recommendations: [],
  movies: [],
  movieSuggestions: [],
  movieGenres: [],
  selectedMovieDetail: null,
  preferences: [],
  myRatings: [],
  llmStatus: {
    enabled: false,
    configured: false,
    provider: 'deepseek',
    model: 'deepseek-v4-flash',
  },
  stats: {
    overview: {},
    genres: [],
    topRated: [],
    userProfile: {},
    recommendationProfile: {},
  },
})

const authForm = reactive({
  username: savedUser?.username || '',
  password: '',
  age: '',
  gender: '',
})

const movieQuery = reactive({
  keyword: '',
  initial: 'all',
  genre: 'all',
  page: 1,
  jumpPage: 1,
  pageSize: 12,
  totalPages: 0,
  totalItems: 0,
})

const ratingForm = reactive({
  movieId: '',
  movieTitle: '',
  score: 4.5,
})

const ratingFilters = reactive({
  sort: 'recent',
  genre: 'all',
  minScore: 'all',
})

const llmForm = reactive({
  queryText: '根据我的推荐结果，帮我挑一部适合今晚看的电影',
  responseText: '',
  relatedMovies: [],
  history: [],
})

const llmTemplates = [
  '根据我的评分推荐一部电影',
  '解释当前推荐第一名为什么适合我',
  '我想看轻松一点的电影',
  '推荐一部高分科幻片',
  '避开我不感兴趣的类型推荐一部电影',
]

const genreChartRef = ref(null)
const topMovieChartRef = ref(null)
const scoreChartRef = ref(null)
const userGenreChartRef = ref(null)
const recGenreChartRef = ref(null)
const recScoreChartRef = ref(null)

const navItems = [
  { key: 'recommend', label: '推荐结果' },
  { key: 'movies', label: '电影库与评分' },
  { key: 'ratings', label: '我的评分' },
  { key: 'profile', label: '我的画像' },
  { key: 'dashboard', label: '数据看板' },
  { key: 'llm', label: '智能问答' },
]

const alphabetFilters = ['all', ...'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')]

const currentUserId = computed(() => state.user?.userId || null)
const hasRecommendations = computed(() => state.recommendations.length > 0)
const hasMyRatings = computed(() => state.myRatings.length > 0)
const wantMovies = computed(() => state.preferences.filter(item => item.status === 'WANT'))
const favoriteMovies = computed(() => state.preferences.filter(item => item.status === 'FAVORITE'))
const dislikedMovies = computed(() => state.preferences.filter(item => item.status === 'DISLIKE'))
const ratingGenres = computed(() => {
  const genres = new Set()
  for (const rating of state.myRatings) {
    String(rating.genres || '')
      .split('|')
      .filter(Boolean)
      .forEach(genre => genres.add(genre))
  }
  return [...genres].sort((a, b) => a.localeCompare(b))
})
const filteredRatings = computed(() => {
  let items = [...state.myRatings]
  if (ratingFilters.genre !== 'all') {
    items = items.filter(rating => String(rating.genres || '').split('|').includes(ratingFilters.genre))
  }
  if (ratingFilters.minScore !== 'all') {
    const minScore = Number(ratingFilters.minScore)
    items = items.filter(rating => Number(rating.score || 0) >= minScore)
  }
  const sorters = {
    recent: (a, b) => Number(b.timestamp || 0) - Number(a.timestamp || 0),
    scoreDesc: (a, b) => Number(b.score || 0) - Number(a.score || 0),
    scoreAsc: (a, b) => Number(a.score || 0) - Number(b.score || 0),
    title: (a, b) => String(a.title || '').localeCompare(String(b.title || '')),
  }
  return items.sort(sorters[ratingFilters.sort] || sorters.recent)
})
const ratingSummary = computed(() => {
  const ratings = state.myRatings
  const count = ratings.length
  const average = count
    ? ratings.reduce((sum, rating) => sum + Number(rating.score || 0), 0) / count
    : 0
  const genreCounts = new Map()
  for (const rating of ratings) {
    for (const genre of String(rating.genres || '').split('|').filter(Boolean)) {
      genreCounts.set(genre, (genreCounts.get(genre) || 0) + 1)
    }
  }
  const topGenre = [...genreCounts.entries()].sort((a, b) => b[1] - a[1])[0]?.[0] || '暂无'
  const latest = ratings.reduce((max, rating) => Math.max(max, Number(rating.timestamp || 0)), 0)
  return { count, average, topGenre, latest }
})
const llmContextItems = computed(() => [
  `当前用户：${state.user ? `${state.user.username} (#${state.user.userId})` : '未登录'}`,
  `已评分：${state.myRatings.length} 部`,
  `当前推荐：${state.recommendations.length} 条`,
  `想看/收藏/不感兴趣：${wantMovies.value.length}/${favoriteMovies.value.length}/${dislikedMovies.value.length}`,
  `LLM 状态：${state.llmStatus.enabled && state.llmStatus.configured ? 'DeepSeek 已启用' : '离线降级模式'}`,
])
const selectedMovieLabel = computed(() => {
  if (!ratingForm.movieId) return '尚未选择电影'
  return `${ratingForm.movieTitle || '电影'} (#${ratingForm.movieId})`
})
const canGoPrev = computed(() => movieQuery.page > 1)
const canGoNext = computed(() => movieQuery.totalPages === 0 || movieQuery.page < movieQuery.totalPages)
const shouldShowMovieDetail = computed(() => state.selectedMovieDetail && ['recommend', 'movies'].includes(state.activeView))

function clearFeedback() {
  state.error = ''
  state.message = ''
}

function setError(error) {
  state.error = error instanceof Error ? error.message : String(error)
}

function formatNumber(value) {
  return Number(value || 0).toLocaleString('zh-CN')
}

function formatDate(timestamp) {
  if (!timestamp) return '暂无'
  return new Date(Number(timestamp) * 1000).toLocaleString('zh-CN')
}

function preferenceLabel(status) {
  const labels = {
    WANT: '想看',
    FAVORITE: '收藏',
    DISLIKE: '不感兴趣',
  }
  return labels[status] || '未标记'
}

function distributionPercent(value) {
  const total = Number(state.selectedMovieDetail?.ratingCount || 0)
  if (!total) return 0
  return Math.round((Number(value || 0) / total) * 100)
}

function ratingImpact(score) {
  const value = Number(score || 0)
  if (value >= 4) return '高分，会增强相似类型和相似用户偏好的推荐'
  if (value <= 2) return '低分，会降低类似电影在推荐中的优先级'
  return '中性评分，会作为个性化排序的参考信号'
}

async function requestJson(url, options = {}) {
  const response = await fetch(url, options)
  const payload = await response.json().catch(() => ({}))
  if (!response.ok) {
    throw new Error(payload.message || `HTTP ${response.status}`)
  }
  if (payload.code !== 0) {
    throw new Error(payload.message || '接口返回异常')
  }
  return payload
}

function saveUser(user) {
  state.user = user
  localStorage.setItem('movieUser', JSON.stringify(user))
}

function logout() {
  localStorage.removeItem('movieUser')
  state.user = null
  state.recommendations = []
  state.myRatings = []
  state.preferences = []
  state.stats.userProfile = {}
  state.stats.recommendationProfile = {}
  llmForm.responseText = ''
  llmForm.relatedMovies = []
  state.message = '已退出当前用户'
}

function switchView(view) {
  state.activeView = view
  if (!['recommend', 'movies'].includes(view)) {
    state.selectedMovieDetail = null
  }
  clearFeedback()
}

async function submitAuth() {
  state.authLoading = true
  clearFeedback()
  try {
    const body = {
      username: authForm.username.trim(),
      password: authForm.password,
      age: authForm.age === '' ? null : Number(authForm.age),
      gender: authForm.gender || null,
    }
    const endpoint = state.authMode === 'register' ? 'register' : 'login'
    const payload = await requestJson(`${API_BASE}/user/${endpoint}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    saveUser(payload.data)
    authForm.password = ''
    state.message = state.authMode === 'register' ? '注册成功，已进入系统' : '登录成功'
    await Promise.all([fetchRecommendations(), fetchMyRatings(), fetchPreferences(), fetchDashboard()])
  } catch (error) {
    setError(error)
  } finally {
    state.authLoading = false
  }
}

async function fetchRecommendations() {
  if (!currentUserId.value) {
    state.message = '请先登录或注册用户'
    return
  }
  state.loading = true
  clearFeedback()
  try {
    const params = new URLSearchParams({
      userId: String(currentUserId.value),
      topN: String(state.topN),
    })
    const payload = await requestJson(`${API_BASE}/recommend/movie?${params}`)
    state.recommendations = Array.isArray(payload.data) ? payload.data : []
    if (state.recommendations.length === 0) {
      state.message = '当前用户暂时没有推荐结果'
    }
    if (state.activeView === 'dashboard') {
      await fetchRecommendationStats()
    }
  } catch (error) {
    state.recommendations = []
    setError(error)
  } finally {
    state.loading = false
  }
}

async function triggerRecommendationRefresh() {
  if (!currentUserId.value) return
  state.recommendationRefreshing = true
  const params = new URLSearchParams({
    userId: String(currentUserId.value),
    topN: String(state.topN),
  })
  await requestJson(`${API_BASE}/recommend/refresh?${params}`, { method: 'POST' })
  window.setTimeout(pollRecommendationRefresh, 1200)
}

async function pollRecommendationRefresh() {
  if (!currentUserId.value) return
  const params = new URLSearchParams({ userId: String(currentUserId.value) })
  try {
    const payload = await requestJson(`${API_BASE}/recommend/refresh/status?${params}`)
    if (payload.refreshing) {
      window.setTimeout(pollRecommendationRefresh, 1200)
      return
    }
    state.recommendationRefreshing = false
    await Promise.all([fetchRecommendations(), fetchRecommendationStats()])
    state.message = '推荐结果和推荐理由已更新'
  } catch (error) {
    state.recommendationRefreshing = false
    setError(error)
  }
}

async function fetchMyRatings() {
  if (!currentUserId.value) {
    state.myRatings = []
    return
  }
  state.ratingLoading = true
  try {
    const params = new URLSearchParams({ userId: String(currentUserId.value) })
    const payload = await requestJson(`${API_BASE}/rating/user?${params}`)
    state.myRatings = Array.isArray(payload.data) ? payload.data : []
  } catch (error) {
    state.myRatings = []
    setError(error)
  } finally {
    state.ratingLoading = false
  }
}

async function updateRatingInline(rating) {
  if (!isValidRating(rating.score)) {
    state.error = '评分必须在 0.5 到 5.0 之间，并且只能以 0.5 为步长'
    return
  }
  clearFeedback()
  try {
    await requestJson(`${API_BASE}/rating/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(currentUserId.value),
        movieId: Number(rating.movieId),
        score: Number(rating.score),
      }),
    })
    state.message = '评分已修改，正在后台更新推荐结果'
    await Promise.all([fetchMyRatings(), fetchUserStats()])
    await triggerRecommendationRefresh()
  } catch (error) {
    setError(error)
  }
}

async function deleteRating(rating) {
  if (!currentUserId.value) return
  clearFeedback()
  try {
    const params = new URLSearchParams({
      userId: String(currentUserId.value),
      movieId: String(rating.movieId),
    })
    await requestJson(`${API_BASE}/rating?${params}`, { method: 'DELETE' })
    state.message = `已删除《${rating.title || rating.movieId}》的评分，正在后台更新推荐结果`
    await Promise.all([fetchMyRatings(), fetchUserStats()])
    await triggerRecommendationRefresh()
  } catch (error) {
    setError(error)
  }
}

async function fetchPreferences() {
  if (!currentUserId.value) {
    state.preferences = []
    return
  }
  try {
    const params = new URLSearchParams({ userId: String(currentUserId.value) })
    const payload = await requestJson(`${API_BASE}/movie/preference?${params}`)
    state.preferences = Array.isArray(payload.data) ? payload.data : []
  } catch (error) {
    state.preferences = []
    setError(error)
  }
}

async function fetchLlmStatus() {
  try {
    const payload = await requestJson(`${API_BASE}/llm/status`)
    state.llmStatus = {
      enabled: Boolean(payload.enabled),
      configured: Boolean(payload.configured),
      provider: payload.provider || 'deepseek',
      model: payload.model || 'deepseek-v4-flash',
    }
  } catch {
    state.llmStatus = {
      enabled: false,
      configured: false,
      provider: 'deepseek',
      model: 'deepseek-v4-flash',
    }
  }
}

async function searchMovies(resetPage = true) {
  if (resetPage) {
    movieQuery.page = 1
  }
  state.movieLoading = true
  clearFeedback()
  try {
    const params = new URLSearchParams({
      keyword: movieQuery.keyword,
      initial: movieQuery.initial,
      genre: movieQuery.genre,
      page: String(movieQuery.page),
      pageSize: String(movieQuery.pageSize),
    })
    const payload = await requestJson(`${API_BASE}/movie/page?${params}`)
    const pageData = payload.data || {}
    state.movies = Array.isArray(pageData.items) ? pageData.items : []
    movieQuery.page = pageData.page || movieQuery.page
    movieQuery.jumpPage = movieQuery.page
    movieQuery.pageSize = pageData.pageSize || movieQuery.pageSize
    movieQuery.totalPages = pageData.totalPages || 0
    movieQuery.totalItems = pageData.totalItems || 0
  } catch (error) {
    state.movies = []
    setError(error)
  } finally {
    state.movieLoading = false
  }
}

async function fetchMovieSuggestions() {
  const keyword = movieQuery.keyword.trim()
  if (!keyword) {
    state.movieSuggestions = []
    return
  }
  state.suggestionLoading = true
  try {
    const params = new URLSearchParams({
      keyword,
      limit: '8',
    })
    const payload = await requestJson(`${API_BASE}/movie/suggest?${params}`)
    state.movieSuggestions = Array.isArray(payload.data) ? payload.data : []
  } catch {
    state.movieSuggestions = []
  } finally {
    state.suggestionLoading = false
  }
}

async function fetchMovieGenres() {
  try {
    const payload = await requestJson(`${API_BASE}/movie/genres`)
    state.movieGenres = Array.isArray(payload.data) ? payload.data : []
  } catch {
    state.movieGenres = []
  }
}

async function applySuggestion(movie) {
  movieQuery.keyword = movie.title
  state.movieSuggestions = []
  await searchMovies(true)
}

async function applyInitialFilter(initial) {
  movieQuery.initial = initial
  await searchMovies(true)
}

async function applyGenreFilter(genre) {
  movieQuery.genre = genre
  await searchMovies(true)
}

async function resetMovieFilters() {
  movieQuery.keyword = ''
  movieQuery.initial = 'all'
  movieQuery.genre = 'all'
  state.movieSuggestions = []
  await searchMovies(true)
}

async function changeMoviePage(delta) {
  const nextPage = movieQuery.page + delta
  if (nextPage < 1) return
  if (movieQuery.totalPages > 0 && nextPage > movieQuery.totalPages) return
  movieQuery.page = nextPage
  await searchMovies(false)
}

async function jumpMoviePage() {
  const totalPages = movieQuery.totalPages || 1
  const targetPage = Math.trunc(Number(movieQuery.jumpPage))
  if (!Number.isFinite(targetPage)) {
    state.error = '请输入有效页码'
    return
  }
  movieQuery.page = Math.min(Math.max(targetPage, 1), totalPages)
  await searchMovies(false)
}

function chooseMovie(movie) {
  ratingForm.movieId = movie.movieId
  ratingForm.movieTitle = movie.title
  state.message = `已选择《${movie.title}》`
  fetchMovieDetail(movie.movieId)
}

async function fetchMovieDetail(movieId) {
  state.detailLoading = true
  clearFeedback()
  try {
    const params = new URLSearchParams({ movieId: String(movieId) })
    if (currentUserId.value) {
      params.set('userId', String(currentUserId.value))
    }
    const payload = await requestJson(`${API_BASE}/movie/detail?${params}`)
    state.selectedMovieDetail = payload.data || null
  } catch (error) {
    state.selectedMovieDetail = null
    setError(error)
  } finally {
    state.detailLoading = false
  }
}

function rateFromDetail() {
  if (!state.selectedMovieDetail) return
  ratingForm.movieId = state.selectedMovieDetail.movieId
  ratingForm.movieTitle = state.selectedMovieDetail.title
  ratingForm.score = state.selectedMovieDetail.userScore || ratingForm.score || 4.5
  state.activeView = 'movies'
  state.message = `正在给《${ratingForm.movieTitle}》评分`
}

async function setMoviePreference(movieId, status) {
  if (!currentUserId.value) {
    state.error = '请先登录或注册用户'
    return
  }
  clearFeedback()
  try {
    await requestJson(`${API_BASE}/movie/preference`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(currentUserId.value),
        movieId: Number(movieId),
        status,
      }),
    })
    await fetchPreferences()
    if (state.selectedMovieDetail?.movieId === movieId) {
      state.selectedMovieDetail.preferenceStatus = status
    }
    if (status === 'DISLIKE') {
      state.recommendations = state.recommendations.filter(movie => movie.movieId !== movieId)
    }
    state.message = `已标记为${preferenceLabel(status)}`
  } catch (error) {
    setError(error)
  }
}

async function removeMoviePreference(movieId) {
  if (!currentUserId.value) return
  clearFeedback()
  try {
    const params = new URLSearchParams({
      userId: String(currentUserId.value),
      movieId: String(movieId),
    })
    await requestJson(`${API_BASE}/movie/preference?${params}`, { method: 'DELETE' })
    await fetchPreferences()
    if (state.selectedMovieDetail?.movieId === movieId) {
      state.selectedMovieDetail.preferenceStatus = null
    }
    state.message = '已取消标记'
  } catch (error) {
    setError(error)
  }
}

async function markNotInterested(movie) {
  await setMoviePreference(movie.movieId, 'DISLIKE')
}

function closeMovieDetail() {
  state.selectedMovieDetail = null
}

function editRating(rating) {
  ratingForm.movieId = rating.movieId
  ratingForm.movieTitle = rating.title || `电影 #${rating.movieId}`
  ratingForm.score = Number(rating.score)
  state.activeView = 'movies'
  state.selectedMovieDetail = null
  state.message = `正在修改《${ratingForm.movieTitle}》的评分`
}

function isValidRating(score) {
  const value = Number(score)
  return Number.isFinite(value) && value >= 0.5 && value <= 5 && Number.isInteger(value * 2)
}

async function submitRating() {
  if (!currentUserId.value) {
    state.error = '请先登录或注册用户'
    return
  }
  if (!ratingForm.movieId) {
    state.error = '请先从电影库中选择一部电影'
    return
  }
  if (!isValidRating(ratingForm.score)) {
    state.error = '评分必须在 0.5 到 5.0 之间，并且只能以 0.5 为步长'
    return
  }
  state.submitting = true
  clearFeedback()
  try {
    await requestJson(`${API_BASE}/rating/submit`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(currentUserId.value),
        movieId: Number(ratingForm.movieId),
        score: Number(ratingForm.score),
      }),
    })
    state.message = '评分已保存，正在后台更新推荐结果和 DeepSeek 推荐理由'
    await Promise.all([fetchMyRatings(), fetchUserStats()])
    await triggerRecommendationRefresh()
  } catch (error) {
    setError(error)
  } finally {
    state.submitting = false
  }
}

async function askLlm() {
  if (!currentUserId.value) {
    state.error = '请先登录或注册用户'
    return
  }
  if (!llmForm.queryText.trim()) {
    state.error = '请输入想问的问题'
    return
  }
  state.llmLoading = true
  clearFeedback()
  try {
    const payload = await requestJson(`${API_BASE}/llm/query`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userId: Number(currentUserId.value),
        queryText: llmForm.queryText.trim(),
        topN: Number(state.topN),
      }),
    })
    llmForm.responseText = payload.responseText || '当前没有返回回答'
    llmForm.relatedMovies = Array.isArray(payload.relatedMovies) ? payload.relatedMovies : []
    llmForm.history.unshift({
      question: llmForm.queryText.trim(),
      answer: llmForm.responseText,
      relatedMovies: llmForm.relatedMovies,
      createdAt: Date.now(),
    })
    llmForm.history = llmForm.history.slice(0, 8)
  } catch (error) {
    setError(error)
  } finally {
    state.llmLoading = false
  }
}

function useLlmTemplate(template) {
  llmForm.queryText = template
}

function viewRelatedMovie(movie) {
  if (!movie?.movieId) return
  state.activeView = 'recommend'
  fetchMovieDetail(movie.movieId)
}

async function prepareLlmContext() {
  if (!currentUserId.value) return
  await Promise.all([
    fetchRecommendations(),
    fetchMyRatings(),
    fetchPreferences(),
    fetchUserStats(),
  ])
}

async function fetchOverviewStats() {
  const [overview, genres, topRated] = await Promise.all([
    requestJson(`${API_BASE}/stats/overview`),
    requestJson(`${API_BASE}/stats/genres`),
    requestJson(`${API_BASE}/stats/top-rated?limit=10`),
  ])
  state.stats.overview = overview.data || {}
  state.stats.genres = Array.isArray(genres.data) ? genres.data : []
  state.stats.topRated = Array.isArray(topRated.data) ? topRated.data : []
}

async function fetchUserStats() {
  if (!currentUserId.value) return
  const params = new URLSearchParams({ userId: String(currentUserId.value) })
  const payload = await requestJson(`${API_BASE}/stats/user?${params}`)
  state.stats.userProfile = payload.data || {}
}

async function fetchRecommendationStats() {
  if (!currentUserId.value) return
  const params = new URLSearchParams({ userId: String(currentUserId.value) })
  const payload = await requestJson(`${API_BASE}/stats/recommendation?${params}`)
  state.stats.recommendationProfile = payload.data || {}
}

async function fetchDashboard() {
  state.statsLoading = true
  try {
    await fetchOverviewStats()
    if (currentUserId.value) {
      await Promise.all([fetchUserStats(), fetchRecommendationStats()])
    }
    await renderCharts()
  } catch (error) {
    setError(error)
  } finally {
    state.statsLoading = false
  }
}

function chartInstance(el) {
  if (!el) return null
  return getInstanceByDom(el) || init(el)
}

function setChart(el, option) {
  const chart = chartInstance(el)
  if (chart) chart.setOption(option, true)
}

async function renderCharts() {
  if (state.activeView !== 'dashboard') return
  await nextTick()
  const genreData = state.stats.genres.slice(0, 10)
  const topRated = state.stats.topRated
  const userScores = state.stats.userProfile.scoreDistribution || []
  const userGenres = state.stats.userProfile.genrePreference || []
  const recGenres = state.stats.recommendationProfile.genreDistribution || []
  const recScores = state.recommendations || []

  setChart(genreChartRef.value, doughnutOption('电影类型分布', genreData, '#0f766e'))
  setChart(topMovieChartRef.value, horizontalBarOption(
    '热门电影 Top10',
    topRated.map(item => item.title),
    topRated.map(item => item.ratingCount),
    '#2563eb',
  ))
  setChart(scoreChartRef.value, barOption('我的评分分布', userScores.map(item => item.name), userScores.map(item => item.value), '#d97706'))
  setChart(userGenreChartRef.value, roseOption('我的类型偏好', userGenres, '#7c3aed'))
  setChart(recGenreChartRef.value, pieOption('推荐类型分布', recGenres, '#dc2626'))
  setChart(recScoreChartRef.value, scatterOption(
    `推荐分分布（当前 ${recScores.length} 条）`,
    recScores.map(item => item.title),
    recScores.map(item => Number(item.score || 0)),
    '#0891b2',
  ))
}

function baseTitle(title) {
  return { text: title, left: 8, top: 4, textStyle: { fontSize: 14, fontWeight: 700 } }
}

function chartPalette(color) {
  return [color, '#2563eb', '#d97706', '#7c3aed', '#dc2626', '#0891b2', '#65a30d', '#be185d', '#475569', '#9333ea']
}

function doughnutOption(title, data, color) {
  return {
    title: baseTitle(title),
    color: chartPalette(color),
    tooltip: { trigger: 'item' },
    legend: { type: 'scroll', bottom: 8, left: 12, right: 12 },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '48%'],
      data,
      label: { formatter: '{b}' },
    }],
  }
}

function pieOption(title, data, color) {
  return {
    title: baseTitle(title),
    color: chartPalette(color),
    tooltip: { trigger: 'item' },
    legend: { type: 'scroll', bottom: 8, left: 12, right: 12 },
    series: [{
      type: 'pie',
      radius: '66%',
      center: ['50%', '48%'],
      data,
      label: { formatter: '{b}' },
    }],
  }
}

function roseOption(title, data, color) {
  return {
    title: baseTitle(title),
    color: chartPalette(color),
    tooltip: { trigger: 'item' },
    legend: { type: 'scroll', bottom: 8, left: 12, right: 12 },
    series: [{
      type: 'pie',
      radius: ['18%', '68%'],
      center: ['50%', '48%'],
      roseType: 'area',
      data,
      label: { formatter: '{b}' },
    }],
  }
}

function scatterOption(title, labels, values, color) {
  return {
    title: baseTitle(title),
    color: [color],
    tooltip: { trigger: 'axis' },
    grid: { left: 48, right: 18, top: 52, bottom: 92 },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: {
        rotate: 35,
        width: 86,
        overflow: 'truncate',
      },
    },
    yAxis: { type: 'value', scale: true },
    series: [{
      type: 'scatter',
      symbolSize: value => Math.max(10, Math.min(26, Number(value) * 4)),
      data: values,
    }],
  }
}

function barOption(title, labels, values, color) {
  return {
    title: baseTitle(title),
    color: [color],
    tooltip: { trigger: 'axis' },
    grid: { left: 42, right: 18, top: 52, bottom: 42 },
    xAxis: { type: 'category', data: labels, axisLabel: { rotate: labels.length > 6 ? 30 : 0 } },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: values, barMaxWidth: 26 }],
  }
}

function horizontalBarOption(title, labels, values, color) {
  return {
    title: baseTitle(title),
    color: [color],
    tooltip: { trigger: 'axis' },
    grid: { left: 132, right: 24, top: 52, bottom: 24 },
    xAxis: { type: 'value' },
    yAxis: {
      type: 'category',
      data: labels,
      inverse: true,
      axisLabel: {
        width: 118,
        overflow: 'truncate',
      },
    },
    series: [{ type: 'bar', data: values, barMaxWidth: 18 }],
  }
}

watch(
  () => state.activeView,
  async view => {
    if (view === 'movies' && state.movies.length === 0) {
      await searchMovies()
    }
    if (view === 'ratings') {
      await fetchMyRatings()
    }
    if (view === 'profile') {
      await Promise.all([fetchMyRatings(), fetchPreferences(), fetchUserStats()])
    }
    if (view === 'llm') {
      await prepareLlmContext()
    }
    if (view === 'dashboard') {
      await fetchDashboard()
    }
  },
)

watch(
  () => [state.stats.genres, state.stats.topRated, state.stats.userProfile, state.stats.recommendationProfile],
  () => renderCharts(),
  { deep: true },
)

window.addEventListener('resize', () => {
  for (const el of [genreChartRef.value, topMovieChartRef.value, scoreChartRef.value, userGenreChartRef.value, recGenreChartRef.value, recScoreChartRef.value]) {
    const chart = el && getInstanceByDom(el)
    if (chart) chart.resize()
  }
})

onMounted(async () => {
  await fetchLlmStatus()
  await fetchMovieGenres()
  await searchMovies()
  await fetchOverviewStats()
  if (currentUserId.value) {
    await Promise.all([fetchRecommendations(), fetchMyRatings(), fetchPreferences(), fetchUserStats(), fetchRecommendationStats()])
  }
})
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">WHU Smart Movie Recommender</p>
        <h1>智能电影推荐系统</h1>
      </div>
      <div class="status-pill" :class="{ active: state.user }">
        {{ state.user ? `当前用户：${state.user.username} (#${state.user.userId})` : '未登录' }}
      </div>
    </header>

    <section class="auth-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">用户入口</p>
          <h2>登录后系统会自动使用当前用户 ID</h2>
        </div>
        <button v-if="state.user" type="button" class="ghost-button" @click="logout">退出</button>
      </div>
      <form v-if="!state.user" class="auth-form" @submit.prevent="submitAuth">
        <div class="segmented">
          <button type="button" :class="{ selected: state.authMode === 'login' }" @click="state.authMode = 'login'">登录</button>
          <button type="button" :class="{ selected: state.authMode === 'register' }" @click="state.authMode = 'register'">注册</button>
        </div>
        <label>
          <span>用户名</span>
          <input v-model="authForm.username" type="text" autocomplete="username" required />
        </label>
        <label>
          <span>密码</span>
          <input v-model="authForm.password" type="password" autocomplete="current-password" required />
        </label>
        <label v-if="state.authMode === 'register'">
          <span>年龄</span>
          <input v-model="authForm.age" type="number" min="1" max="120" />
        </label>
        <label v-if="state.authMode === 'register'">
          <span>性别</span>
          <select v-model="authForm.gender">
            <option value="">不填写</option>
            <option value="male">男</option>
            <option value="female">女</option>
          </select>
        </label>
        <button type="submit" :disabled="state.authLoading">
          {{ state.authLoading ? '处理中' : state.authMode === 'register' ? '注册并进入' : '登录' }}
        </button>
      </form>
      <p v-else class="hint">推荐、评分、我的评分、数据看板和智能问答都会使用当前登录用户，不需要手动输入 userId。</p>
    </section>

    <nav class="app-nav" aria-label="主菜单">
      <button
        v-for="item in navItems"
        :key="item.key"
        type="button"
        :class="{ selected: state.activeView === item.key }"
        @click="switchView(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <p v-if="state.error" class="notice error">{{ state.error }}</p>
    <p v-else-if="state.message" class="notice">{{ state.message }}</p>

    <section v-if="shouldShowMovieDetail" class="detail-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">电影详情</p>
          <h2>{{ state.selectedMovieDetail.title }}</h2>
          <p class="section-note">{{ state.selectedMovieDetail.genres || '未知类型' }}</p>
        </div>
        <button type="button" class="ghost-button" @click="closeMovieDetail">关闭</button>
      </div>
      <div class="detail-grid">
        <div class="detail-stat">
          <span>上映年份</span>
          <strong>{{ state.selectedMovieDetail.releaseYear || '未知' }}</strong>
        </div>
        <div class="detail-stat">
          <span>平均评分</span>
          <strong>{{ Number(state.selectedMovieDetail.averageScore || 0).toFixed(2) }}</strong>
        </div>
        <div class="detail-stat">
          <span>评分人数</span>
          <strong>{{ formatNumber(state.selectedMovieDetail.ratingCount) }}</strong>
        </div>
        <div class="detail-stat">
          <span>我的评分</span>
          <strong>{{ state.selectedMovieDetail.ratedByCurrentUser ? Number(state.selectedMovieDetail.userScore).toFixed(1) : '未评分' }}</strong>
        </div>
        <div class="detail-stat">
          <span>我的标记</span>
          <strong>{{ preferenceLabel(state.selectedMovieDetail.preferenceStatus) }}</strong>
        </div>
      </div>
      <div class="detail-actions">
        <button type="button" @click="rateFromDetail">{{ state.selectedMovieDetail.ratedByCurrentUser ? '修改评分' : '给这部电影评分' }}</button>
        <button type="button" class="ghost-button" @click="setMoviePreference(state.selectedMovieDetail.movieId, 'WANT')">想看</button>
        <button type="button" class="ghost-button" @click="setMoviePreference(state.selectedMovieDetail.movieId, 'FAVORITE')">收藏</button>
        <button type="button" class="ghost-button danger" @click="setMoviePreference(state.selectedMovieDetail.movieId, 'DISLIKE')">不感兴趣</button>
        <button v-if="state.selectedMovieDetail.preferenceStatus" type="button" class="ghost-button" @click="removeMoviePreference(state.selectedMovieDetail.movieId)">取消标记</button>
      </div>
      <div v-if="state.selectedMovieDetail.scoreDistribution?.length" class="distribution-panel">
        <p class="filter-title">该电影评分分布</p>
        <div
          v-for="bucket in state.selectedMovieDetail.scoreDistribution"
          :key="bucket.name"
          class="distribution-row"
        >
          <span>{{ bucket.name }}</span>
          <div class="distribution-track">
            <div class="distribution-fill" :style="{ width: `${distributionPercent(bucket.value)}%` }"></div>
          </div>
          <strong>{{ bucket.value }}</strong>
        </div>
      </div>
      <div v-if="state.selectedMovieDetail.similarMovies?.length" class="similar-section">
        <p class="filter-title">相似电影</p>
        <div class="similar-list">
          <button
            v-for="movie in state.selectedMovieDetail.similarMovies"
            :key="movie.movieId"
            type="button"
            @click="fetchMovieDetail(movie.movieId)"
          >
            <strong>{{ movie.title }}</strong>
            <span>{{ movie.genres }}</span>
            <small>{{ movie.reason }}</small>
          </button>
        </div>
      </div>
    </section>

    <section v-if="state.activeView === 'recommend'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">个性化推荐</p>
          <h2>推荐结果</h2>
          <p class="section-note">推荐分是算法排序分，不等同于用户 0.5-5.0 的评分。</p>
        </div>
        <span>{{ state.recommendations.length }} 条</span>
      </div>
      <section class="toolbar" aria-label="推荐参数">
        <label>
          <span>推荐数量</span>
          <input v-model.number="state.topN" type="number" min="1" max="20" />
        </label>
        <button type="button" :disabled="state.loading || !state.user" @click="fetchRecommendations">
          {{ state.loading ? '加载中' : '刷新推荐' }}
        </button>
        <p class="toolbar-note">同一用户、同一批评分数据和同一推荐数量下，推荐排序会保持稳定；提交新评分后会重新计算。</p>
      </section>
      <div v-if="state.recommendationRefreshing" class="refresh-banner">
        <span class="spinner"></span>
        <div>
          <strong>正在更新推荐结果和 DeepSeek 理由</strong>
          <p>旧推荐会继续显示，后台完成后会自动替换为最新推荐。</p>
        </div>
      </div>
      <div v-if="state.loading" class="empty">正在读取推荐结果...</div>
      <div v-else-if="!hasRecommendations" class="empty">登录后可查看个性化推荐</div>
      <ol v-else class="movie-list">
        <li v-for="movie in state.recommendations" :key="movie.movieId" class="movie-item">
          <div class="rank">#{{ movie.movieId }}</div>
          <div class="movie-main">
            <button type="button" class="link-title" @click="fetchMovieDetail(movie.movieId)">
              {{ movie.title || '未知电影' }}
            </button>
            <p class="genres">{{ movie.genres || '未知类型' }}</p>
            <div v-if="movie.reasonTags?.length" class="reason-tags">
              <span v-for="tag in movie.reasonTags" :key="tag">{{ tag }}</span>
            </div>
            <ul v-if="movie.reasonPoints?.length" class="reason-list">
              <li v-for="point in movie.reasonPoints" :key="point">{{ point }}</li>
            </ul>
            <p class="reason">{{ movie.reason }}</p>
            <button type="button" class="inline-danger" @click="markNotInterested(movie)">不感兴趣</button>
          </div>
          <div class="score">
            <span>{{ Number(movie.score).toFixed(2) }}</span>
            <small>推荐分</small>
          </div>
        </li>
      </ol>
    </section>

    <section v-if="state.activeView === 'movies'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">电影库</p>
          <h2>搜索、浏览与评分</h2>
        </div>
        <span>{{ formatNumber(movieQuery.totalItems) }} 部</span>
      </div>
      <form class="movie-search" @submit.prevent="searchMovies(true)">
        <label>
          <span>搜索电影或类型</span>
          <input v-model="movieQuery.keyword" type="search" placeholder="例如 Matrix / Sci-Fi" @input="fetchMovieSuggestions" />
        </label>
        <label>
          <span>每页</span>
          <select v-model.number="movieQuery.pageSize" @change="searchMovies(true)">
            <option :value="8">8</option>
            <option :value="12">12</option>
            <option :value="20">20</option>
            <option :value="30">30</option>
          </select>
        </label>
        <button type="submit" :disabled="state.movieLoading">
          {{ state.movieLoading ? '搜索中' : '搜索' }}
        </button>
      </form>

      <div v-if="state.movieSuggestions.length" class="suggestion-list">
        <button
          v-for="movie in state.movieSuggestions"
          :key="movie.movieId"
          type="button"
          @click="applySuggestion(movie)"
        >
          <strong>{{ movie.title }}</strong>
          <span>{{ movie.genres }}</span>
        </button>
      </div>

      <section class="filter-panel">
        <div>
          <span class="filter-title">首字母</span>
          <div class="chip-row">
            <button
              v-for="letter in alphabetFilters"
              :key="letter"
              type="button"
              class="filter-chip"
              :class="{ selected: movieQuery.initial === letter }"
              @click="applyInitialFilter(letter)"
            >
              {{ letter === 'all' ? '全部' : letter }}
            </button>
          </div>
        </div>
        <div>
          <span class="filter-title">类型</span>
          <div class="chip-row genre-row">
            <button
              type="button"
              class="filter-chip"
              :class="{ selected: movieQuery.genre === 'all' }"
              @click="applyGenreFilter('all')"
            >
              全部
            </button>
            <button
              v-for="genre in state.movieGenres"
              :key="genre"
              type="button"
              class="filter-chip"
              :class="{ selected: movieQuery.genre === genre }"
              @click="applyGenreFilter(genre)"
            >
              {{ genre }}
            </button>
          </div>
        </div>
        <button type="button" class="ghost-button" @click="resetMovieFilters">重置筛选</button>
      </section>

      <div class="pager">
        <button type="button" class="icon-button" :disabled="!canGoPrev || state.movieLoading" @click="changeMoviePage(-1)">上一页</button>
        <span>第 {{ movieQuery.page }} / {{ movieQuery.totalPages || 1 }} 页</span>
        <button type="button" class="icon-button" :disabled="!canGoNext || state.movieLoading" @click="changeMoviePage(1)">下一页</button>
      </div>

      <form class="jump-form" @submit.prevent="jumpMoviePage">
        <label>
          <span>跳转页码</span>
          <input v-model.number="movieQuery.jumpPage" type="number" min="1" :max="movieQuery.totalPages || 1" />
        </label>
        <button type="submit" class="ghost-button" :disabled="state.movieLoading">跳转</button>
      </form>

      <div class="movie-and-rating">
        <div class="movie-browser">
          <button
            v-for="movie in state.movies"
            :key="movie.movieId"
            type="button"
            class="browse-item"
            :class="{ selected: movie.movieId === ratingForm.movieId }"
            @click="chooseMovie(movie)"
          >
            <strong>{{ movie.title }}</strong>
            <span>{{ movie.genres }}</span>
          </button>
        </div>

        <form class="rating-form" @submit.prevent="submitRating">
          <p class="selected-movie">{{ selectedMovieLabel }}</p>
          <label>
            <span>用户评分</span>
            <input v-model.number="ratingForm.score" type="number" min="0.5" max="5" step="0.5" />
          </label>
          <p class="rating-help">满分 5.0，最低 0.5，只允许 0.5 的倍数。重复评价同一部电影会修改原评分，不会新增重复记录。</p>
          <button type="submit" :disabled="state.submitting || !state.user">
            {{ state.submitting ? '提交中' : '保存评分并更新推荐' }}
          </button>
        </form>
      </div>
    </section>

    <section v-if="state.activeView === 'ratings'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">用户画像来源</p>
          <h2>我的评分</h2>
          <p class="section-note">这里展示当前用户已经评价过的电影，可筛选、排序、快速改分或删除评分。</p>
        </div>
        <button type="button" class="ghost-button" :disabled="!state.user || state.ratingLoading" @click="fetchMyRatings">
          {{ state.ratingLoading ? '加载中' : '刷新' }}
        </button>
      </div>
      <div v-if="!state.user" class="empty">登录后可查看自己的评分记录</div>
      <div v-else-if="state.ratingLoading" class="empty">正在读取评分记录...</div>
      <div v-else-if="!hasMyRatings" class="empty">还没有评分记录，先到电影库选择一部电影评分</div>
      <template v-else>
        <div class="metric-grid rating-summary">
          <div class="metric-card">
            <span>已评分</span>
            <strong>{{ ratingSummary.count }}</strong>
          </div>
          <div class="metric-card">
            <span>平均评分</span>
            <strong>{{ ratingSummary.average.toFixed(2) }}</strong>
          </div>
          <div class="metric-card">
            <span>最高频类型</span>
            <strong>{{ ratingSummary.topGenre }}</strong>
          </div>
          <div class="metric-card">
            <span>最近评分</span>
            <strong class="date-text">{{ formatDate(ratingSummary.latest) }}</strong>
          </div>
        </div>
        <section class="filter-panel rating-filter-panel">
          <div>
            <span class="filter-title">排序</span>
            <select v-model="ratingFilters.sort">
              <option value="recent">最近评分优先</option>
              <option value="scoreDesc">评分从高到低</option>
              <option value="scoreAsc">评分从低到高</option>
              <option value="title">电影标题 A-Z</option>
            </select>
          </div>
          <div>
            <span class="filter-title">类型</span>
            <select v-model="ratingFilters.genre">
              <option value="all">全部类型</option>
              <option v-for="genre in ratingGenres" :key="genre" :value="genre">{{ genre }}</option>
            </select>
          </div>
          <div>
            <span class="filter-title">最低分</span>
            <select v-model="ratingFilters.minScore">
              <option value="all">全部评分</option>
              <option value="4">只看 4.0 以上</option>
              <option value="3">只看 3.0 以上</option>
              <option value="2">只看 2.0 以上</option>
            </select>
          </div>
        </section>
        <div v-if="!filteredRatings.length" class="empty">当前筛选条件下没有评分记录</div>
        <div v-else class="rating-list">
          <div v-for="rating in filteredRatings" :key="rating.movieId" class="rating-item expanded">
            <div class="rating-main">
              <strong>{{ rating.title || `电影 #${rating.movieId}` }}</strong>
              <span>{{ rating.genres || '未知类型' }}</span>
              <small>{{ ratingImpact(rating.score) }}</small>
            </div>
            <label class="inline-score">
              <span>评分</span>
              <input v-model.number="rating.score" type="number" min="0.5" max="5" step="0.5" />
            </label>
            <div class="rating-actions">
              <button type="button" class="ghost-button" @click="updateRatingInline(rating)">保存</button>
              <button type="button" class="ghost-button" @click="editRating(rating)">去电影库</button>
              <button type="button" class="ghost-button danger" @click="deleteRating(rating)">删除</button>
            </div>
          </div>
        </div>
      </template>
    </section>

    <section v-if="state.activeView === 'profile'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">个性化闭环</p>
          <h2>我的画像</h2>
          <p class="section-note">汇总评分、类型偏好、想看/收藏/不感兴趣，用来解释系统如何理解当前用户。</p>
        </div>
        <button type="button" class="ghost-button" :disabled="!state.user" @click="Promise.all([fetchMyRatings(), fetchPreferences(), fetchUserStats()])">刷新画像</button>
      </div>
      <div v-if="!state.user" class="empty">登录后可查看用户画像</div>
      <template v-else>
        <div class="metric-grid">
          <div class="metric-card">
            <span>已评分</span>
            <strong>{{ formatNumber(state.stats.userProfile.ratingCount) }}</strong>
          </div>
          <div class="metric-card">
            <span>平均评分</span>
            <strong>{{ Number(state.stats.userProfile.averageScore || 0).toFixed(2) }}</strong>
          </div>
          <div class="metric-card">
            <span>想看</span>
            <strong>{{ wantMovies.length }}</strong>
          </div>
          <div class="metric-card">
            <span>收藏</span>
            <strong>{{ favoriteMovies.length }}</strong>
          </div>
          <div class="metric-card">
            <span>不感兴趣</span>
            <strong>{{ dislikedMovies.length }}</strong>
          </div>
        </div>

        <div class="profile-grid">
          <section class="profile-block">
            <p class="filter-title">喜欢的类型</p>
            <div class="reason-tags">
              <span v-for="genre in state.stats.userProfile.genrePreference || []" :key="genre.name">
                {{ genre.name }} · {{ genre.value }}
              </span>
            </div>
          </section>
          <section class="profile-block">
            <p class="filter-title">最近评分电影</p>
            <div v-if="!hasMyRatings" class="empty compact">暂无评分记录</div>
            <div v-else class="compact-list">
              <button
                v-for="rating in state.myRatings.slice(0, 6)"
                :key="rating.movieId"
                type="button"
                @click="viewRelatedMovie(rating)"
              >
                <span>{{ rating.title }}</span>
                <strong>{{ Number(rating.score).toFixed(1) }}</strong>
              </button>
            </div>
          </section>
          <section class="profile-block">
            <p class="filter-title">想看 / 收藏 / 不感兴趣</p>
            <div v-if="!state.preferences.length" class="empty compact">暂无显式偏好标记</div>
            <div v-else class="compact-list">
              <button
                v-for="item in state.preferences.slice(0, 8)"
                :key="`${item.status}-${item.movieId}`"
                type="button"
                @click="viewRelatedMovie(item)"
              >
                <span>{{ item.title }}</span>
                <strong>{{ preferenceLabel(item.status) }}</strong>
              </button>
            </div>
          </section>
        </div>
      </template>
    </section>

    <section v-if="state.activeView === 'dashboard'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">数据可视化</p>
          <h2>数据看板</h2>
          <p class="section-note">展示系统数据规模、电影类型分布、热门电影、当前用户评分画像和推荐画像。</p>
        </div>
        <button type="button" class="ghost-button" :disabled="state.statsLoading" @click="fetchDashboard">
          {{ state.statsLoading ? '更新中' : '刷新看板' }}
        </button>
      </div>

      <div class="metric-grid">
        <div class="metric-card">
          <span>电影总数</span>
          <strong>{{ formatNumber(state.stats.overview.movieCount) }}</strong>
        </div>
        <div class="metric-card">
          <span>用户总数</span>
          <strong>{{ formatNumber(state.stats.overview.userCount) }}</strong>
        </div>
        <div class="metric-card">
          <span>评分总数</span>
          <strong>{{ formatNumber(state.stats.overview.ratingCount) }}</strong>
        </div>
        <div class="metric-card">
          <span>平均评分</span>
          <strong>{{ Number(state.stats.overview.averageScore || 0).toFixed(2) }}</strong>
        </div>
      </div>

      <div class="chart-grid">
        <div ref="genreChartRef" class="chart-box"></div>
        <div ref="topMovieChartRef" class="chart-box"></div>
        <div ref="scoreChartRef" class="chart-box"></div>
        <div ref="userGenreChartRef" class="chart-box"></div>
        <div ref="recGenreChartRef" class="chart-box"></div>
        <div ref="recScoreChartRef" class="chart-box"></div>
      </div>
    </section>

    <section v-if="state.activeView === 'llm'" class="view-panel">
      <div class="section-heading">
        <div>
          <p class="step-label">智能问答</p>
          <h2>向 LLM 询问推荐理由</h2>
        </div>
        <span class="llm-status" :class="{ active: state.llmStatus.enabled && state.llmStatus.configured }">
          {{
            state.llmStatus.enabled && state.llmStatus.configured
              ? `DeepSeek 已启用：${state.llmStatus.model}`
              : 'DeepSeek 离线模式'
          }}
        </span>
      </div>
      <p class="section-note llm-note">LLM 只负责解释和问答，不参与推荐排序；未配置密钥时会自动返回离线回答。</p>
      <section class="llm-context">
        <div>
          <p class="filter-title">当前会参考</p>
          <div class="reason-tags">
            <span v-for="item in llmContextItems" :key="item">{{ item }}</span>
          </div>
        </div>
        <div>
          <p class="filter-title">常用问题</p>
          <div class="template-row">
            <button
              v-for="template in llmTemplates"
              :key="template"
              type="button"
              class="ghost-button"
              @click="useLlmTemplate(template)"
            >
              {{ template }}
            </button>
          </div>
        </div>
      </section>
      <form class="llm-form" @submit.prevent="askLlm">
        <textarea
          v-model="llmForm.queryText"
          rows="3"
          placeholder="例如：我想看烧脑科幻片，哪一部更适合我？"
        />
        <button type="submit" :disabled="state.llmLoading || !state.user">
          {{ state.llmLoading ? '生成中' : '提问' }}
        </button>
      </form>
      <div v-if="llmForm.responseText" class="llm-answer">
        <p>{{ llmForm.responseText }}</p>
        <div v-if="llmForm.relatedMovies.length" class="related-movies">
          <button v-for="movie in llmForm.relatedMovies" :key="movie.movieId" type="button" @click="viewRelatedMovie(movie)">
            查看《{{ movie.title }}》
          </button>
        </div>
      </div>
      <section v-if="llmForm.history.length" class="chat-history">
        <p class="filter-title">本次问答历史</p>
        <article v-for="item in llmForm.history" :key="item.createdAt" class="chat-item">
          <strong>问：{{ item.question }}</strong>
          <p>答：{{ item.answer }}</p>
          <div v-if="item.relatedMovies.length" class="related-movies">
            <button
              v-for="movie in item.relatedMovies"
              :key="`${item.createdAt}-${movie.movieId}`"
              type="button"
              @click="viewRelatedMovie(movie)"
            >
              查看《{{ movie.title }}》
            </button>
          </div>
        </article>
      </section>
    </section>
  </main>
</template>
