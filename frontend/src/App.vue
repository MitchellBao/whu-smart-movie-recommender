<script setup>
import { computed, onMounted, reactive } from 'vue'

const API_BASE = '/api'
const savedUser = JSON.parse(localStorage.getItem('movieUser') || 'null')

const state = reactive({
  user: savedUser,
  authMode: 'login',
  topN: 5,
  loading: false,
  movieLoading: false,
  authLoading: false,
  submitting: false,
  error: '',
  message: '',
  recommendations: [],
  movies: [],
})

const authForm = reactive({
  username: savedUser?.username || '',
  password: '',
  age: '',
  gender: '',
})

const movieQuery = reactive({
  keyword: '',
  limit: 12,
})

const ratingForm = reactive({
  movieId: '',
  movieTitle: '',
  score: 4.5,
})

const currentUserId = computed(() => state.user?.userId || null)
const hasRecommendations = computed(() => state.recommendations.length > 0)
const selectedMovieLabel = computed(() => {
  if (!ratingForm.movieId) return '未选择电影'
  return `${ratingForm.movieTitle || '电影'} (#${ratingForm.movieId})`
})

function setError(error) {
  state.error = error instanceof Error ? error.message : String(error)
}

function clearFeedback() {
  state.error = ''
  state.message = ''
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
  state.message = '已退出当前用户'
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
    await fetchRecommendations()
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
  } catch (error) {
    state.recommendations = []
    setError(error)
  } finally {
    state.loading = false
  }
}

async function searchMovies() {
  state.movieLoading = true
  clearFeedback()
  try {
    const params = new URLSearchParams({
      keyword: movieQuery.keyword,
      limit: String(movieQuery.limit),
    })
    const payload = await requestJson(`${API_BASE}/movie/search?${params}`)
    state.movies = Array.isArray(payload.data) ? payload.data : []
  } catch (error) {
    state.movies = []
    setError(error)
  } finally {
    state.movieLoading = false
  }
}

function chooseMovie(movie) {
  ratingForm.movieId = movie.movieId
  ratingForm.movieTitle = movie.title
  state.message = `已选择《${movie.title}》`
}

async function submitRating() {
  if (!currentUserId.value) {
    state.error = '请先登录或注册用户'
    return
  }
  if (!ratingForm.movieId) {
    state.error = '请先从电影浏览中选择一部电影'
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
    state.message = '评分已提交，推荐结果已刷新'
    await fetchRecommendations()
  } catch (error) {
    setError(error)
  } finally {
    state.submitting = false
  }
}

onMounted(async () => {
  await searchMovies()
  if (currentUserId.value) {
    await fetchRecommendations()
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
        <h2>用户入口</h2>
        <button v-if="state.user" type="button" class="ghost-button" @click="logout">退出</button>
      </div>
      <form v-if="!state.user" class="auth-form" @submit.prevent="submitAuth">
        <div class="segmented">
          <button
            type="button"
            :class="{ selected: state.authMode === 'login' }"
            @click="state.authMode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            :class="{ selected: state.authMode === 'register' }"
            @click="state.authMode = 'register'"
          >
            注册
          </button>
        </div>
        <label>
          <span>用户名</span>
          <input v-model="authForm.username" type="text" autocomplete="username" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="authForm.password" type="password" autocomplete="current-password" />
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
      <p v-else class="hint">系统会使用当前用户 ID 请求推荐和提交评分，不需要你手动记 userId。</p>
    </section>

    <section class="toolbar" aria-label="推荐参数">
      <label>
        <span>推荐数量</span>
        <input v-model.number="state.topN" type="number" min="1" max="20" />
      </label>
      <button type="button" :disabled="state.loading || !state.user" @click="fetchRecommendations">
        {{ state.loading ? '加载中' : '刷新推荐' }}
      </button>
      <p class="toolbar-note">推荐结果来自 MySQL 中的 MovieLens 评分数据。</p>
    </section>

    <p v-if="state.error" class="notice error">{{ state.error }}</p>
    <p v-else-if="state.message" class="notice">{{ state.message }}</p>

    <section class="content-grid">
      <div class="recommend-panel">
        <div class="section-heading">
          <h2>推荐结果</h2>
          <span>{{ state.recommendations.length }} 条</span>
        </div>

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
              <small>score</small>
            </div>
          </li>
        </ol>
      </div>

      <aside class="rating-panel">
        <div class="section-heading">
          <h2>电影浏览与评分</h2>
        </div>

        <form class="movie-search" @submit.prevent="searchMovies">
          <label>
            <span>搜索电影或类型</span>
            <input v-model="movieQuery.keyword" type="search" placeholder="例如 Matrix / Sci-Fi" />
          </label>
          <button type="submit" :disabled="state.movieLoading">
            {{ state.movieLoading ? '搜索中' : '搜索' }}
          </button>
        </form>

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
            <span>评分</span>
            <input v-model.number="ratingForm.score" type="number" min="1" max="5" step="0.5" />
          </label>
          <button type="submit" :disabled="state.submitting || !state.user">
            {{ state.submitting ? '提交中' : '提交评分并刷新推荐' }}
          </button>
        </form>
      </aside>
    </section>
  </main>
</template>
