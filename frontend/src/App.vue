<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { BarChart } from 'echarts/charts'
import { GridComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { getInstanceByDom, init, use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'

use([BarChart, GridComponent, TitleComponent, TooltipComponent, CanvasRenderer])

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
  error: '',
  message: '',
  recommendations: [],
  movies: [],
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

const llmForm = reactive({
  queryText: '根据我的推荐结果，帮我挑一部适合今晚看的电影',
  responseText: '',
  relatedMovies: [],
})

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
  { key: 'dashboard', label: '数据看板' },
  { key: 'llm', label: '智能问答' },
]

const currentUserId = computed(() => state.user?.userId || null)
const hasRecommendations = computed(() => state.recommendations.length > 0)
const hasMyRatings = computed(() => state.myRatings.length > 0)
const selectedMovieLabel = computed(() => {
  if (!ratingForm.movieId) return '尚未选择电影'
  return `${ratingForm.movieTitle || '电影'} (#${ratingForm.movieId})`
})
const canGoPrev = computed(() => movieQuery.page > 1)
const canGoNext = computed(() => movieQuery.totalPages === 0 || movieQuery.page < movieQuery.totalPages)

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
  state.stats.userProfile = {}
  state.stats.recommendationProfile = {}
  llmForm.responseText = ''
  llmForm.relatedMovies = []
  state.message = '已退出当前用户'
}

function switchView(view) {
  state.activeView = view
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
    await Promise.all([fetchRecommendations(), fetchMyRatings(), fetchDashboard()])
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
}

function editRating(rating) {
  ratingForm.movieId = rating.movieId
  ratingForm.movieTitle = rating.title || `电影 #${rating.movieId}`
  ratingForm.score = Number(rating.score)
  state.activeView = 'movies'
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
    state.message = '评分已保存，推荐已根据最新偏好重新计算'
    await Promise.all([fetchRecommendations(), fetchMyRatings(), fetchDashboard()])
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
      }),
    })
    llmForm.responseText = payload.responseText || '当前没有返回回答'
    llmForm.relatedMovies = Array.isArray(payload.relatedMovies) ? payload.relatedMovies : []
  } catch (error) {
    setError(error)
  } finally {
    state.llmLoading = false
  }
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
  const recScores = state.stats.recommendationProfile.scoreRanking || []

  setChart(genreChartRef.value, barOption('电影类型分布', genreData.map(item => item.name), genreData.map(item => item.value), '#0f766e'))
  setChart(topMovieChartRef.value, horizontalBarOption(
    '热门电影 Top10',
    topRated.map(item => item.title),
    topRated.map(item => item.ratingCount),
    '#2563eb',
  ))
  setChart(scoreChartRef.value, barOption('我的评分分布', userScores.map(item => item.name), userScores.map(item => item.value), '#d97706'))
  setChart(userGenreChartRef.value, barOption('我的类型偏好', userGenres.map(item => item.name), userGenres.map(item => item.value), '#7c3aed'))
  setChart(recGenreChartRef.value, barOption('推荐类型分布', recGenres.map(item => item.name), recGenres.map(item => item.value), '#dc2626'))
  setChart(recScoreChartRef.value, horizontalBarOption(
    '推荐分 Top10',
    recScores.map(item => item.title),
    recScores.map(item => Number(item.score || 0).toFixed(2)),
    '#0891b2',
  ))
}

function barOption(title, labels, values, color) {
  return {
    title: { text: title, left: 8, top: 4, textStyle: { fontSize: 14, fontWeight: 700 } },
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
    title: { text: title, left: 8, top: 4, textStyle: { fontSize: 14, fontWeight: 700 } },
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
  await searchMovies()
  await fetchOverviewStats()
  if (currentUserId.value) {
    await Promise.all([fetchRecommendations(), fetchMyRatings(), fetchUserStats(), fetchRecommendationStats()])
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
      <div v-if="state.loading" class="empty">正在读取推荐结果...</div>
      <div v-else-if="!hasRecommendations" class="empty">登录后可查看个性化推荐</div>
      <ol v-else class="movie-list">
        <li v-for="movie in state.recommendations" :key="movie.movieId" class="movie-item">
          <div class="rank">#{{ movie.movieId }}</div>
          <div class="movie-main">
            <h3>{{ movie.title || '未知电影' }}</h3>
            <p class="genres">{{ movie.genres || '未知类型' }}</p>
            <p class="reason">{{ movie.reason }}</p>
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
          <input v-model="movieQuery.keyword" type="search" placeholder="例如 Matrix / Sci-Fi" />
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
          <p class="section-note">这里展示当前用户已经评价过的电影，可点击“修改”回到电影库更新评分。</p>
        </div>
        <button type="button" class="ghost-button" :disabled="!state.user || state.ratingLoading" @click="fetchMyRatings">
          {{ state.ratingLoading ? '加载中' : '刷新' }}
        </button>
      </div>
      <div v-if="!state.user" class="empty">登录后可查看自己的评分记录</div>
      <div v-else-if="state.ratingLoading" class="empty">正在读取评分记录...</div>
      <div v-else-if="!hasMyRatings" class="empty">还没有评分记录，先到电影库选择一部电影评分</div>
      <div v-else class="rating-list">
        <div v-for="rating in state.myRatings" :key="rating.movieId" class="rating-item">
          <div>
            <strong>{{ rating.title || `电影 #${rating.movieId}` }}</strong>
            <span>{{ rating.genres || '未知类型' }}</span>
          </div>
          <div class="rating-score">{{ Number(rating.score).toFixed(1) }}</div>
          <button type="button" class="ghost-button" @click="editRating(rating)">修改</button>
        </div>
      </div>
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
          <span v-for="movie in llmForm.relatedMovies" :key="movie.movieId">{{ movie.title }}</span>
        </div>
      </div>
    </section>
  </main>
</template>
