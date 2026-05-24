<script setup>
import { computed, onMounted, reactive, ref } from 'vue'

const API_BASE = '/api'

const state = reactive({
  userId: 1,
  topN: 5,
  loading: false,
  submitting: false,
  error: '',
  message: '',
  recommendations: [],
})

const ratingForm = reactive({
  movieId: 1,
  score: 4.5,
})

const hasRecommendations = computed(() => state.recommendations.length > 0)

function normalizeError(error) {
  if (error instanceof Error) {
    return error.message
  }
  return String(error)
}

async function fetchRecommendations() {
  state.loading = true
  state.error = ''
  state.message = ''
  try {
    const params = new URLSearchParams({
      userId: String(state.userId),
      topN: String(state.topN),
    })
    const response = await fetch(`${API_BASE}/recommend/movie?${params}`)
    if (!response.ok) {
      throw new Error(`推荐接口请求失败：HTTP ${response.status}`)
    }
    const payload = await response.json()
    if (payload.code !== 0) {
      throw new Error(payload.message || '推荐接口返回异常')
    }
    state.recommendations = Array.isArray(payload.data) ? payload.data : []
    if (state.recommendations.length === 0) {
      state.message = '当前用户暂时没有推荐结果'
    }
  } catch (error) {
    state.recommendations = []
    state.error = normalizeError(error)
  } finally {
    state.loading = false
  }
}

async function submitRating() {
  state.submitting = true
  state.error = ''
  state.message = ''
  try {
    const response = await fetch(`${API_BASE}/rating/submit`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        userId: Number(state.userId),
        movieId: Number(ratingForm.movieId),
        score: Number(ratingForm.score),
      }),
    })
    if (!response.ok) {
      throw new Error(`评分接口请求失败：HTTP ${response.status}`)
    }
    const payload = await response.json()
    if (payload.code !== 0) {
      throw new Error(payload.message || '评分提交失败')
    }
    state.message = '评分已提交，推荐结果已刷新'
    await fetchRecommendations()
  } catch (error) {
    state.error = normalizeError(error)
  } finally {
    state.submitting = false
  }
}

onMounted(fetchRecommendations)
</script>

<template>
  <main class="app-shell">
    <header class="topbar">
      <div>
        <p class="eyebrow">WHU Smart Movie Recommender</p>
        <h1>智能电影推荐系统</h1>
      </div>
      <div class="status-pill" :class="{ active: hasRecommendations }">
        {{ hasRecommendations ? '后端已联通' : '等待数据' }}
      </div>
    </header>

    <section class="toolbar" aria-label="推荐参数">
      <label>
        <span>用户 ID</span>
        <input v-model.number="state.userId" type="number" min="1" />
      </label>
      <label>
        <span>推荐数量</span>
        <input v-model.number="state.topN" type="number" min="1" max="20" />
      </label>
      <button type="button" :disabled="state.loading" @click="fetchRecommendations">
        {{ state.loading ? '加载中' : '获取推荐' }}
      </button>
    </section>

    <section class="content-grid">
      <div class="recommend-panel">
        <div class="section-heading">
          <h2>推荐结果</h2>
          <span>{{ state.recommendations.length }} 条</span>
        </div>

        <p v-if="state.error" class="notice error">{{ state.error }}</p>
        <p v-else-if="state.message" class="notice">{{ state.message }}</p>

        <div v-if="state.loading" class="empty">正在从后端读取推荐结果...</div>
        <div v-else-if="!hasRecommendations" class="empty">暂无推荐结果</div>
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
          <h2>提交评分</h2>
        </div>
        <form class="rating-form" @submit.prevent="submitRating">
          <label>
            <span>电影 ID</span>
            <input v-model.number="ratingForm.movieId" type="number" min="1" />
          </label>
          <label>
            <span>评分</span>
            <input v-model.number="ratingForm.score" type="number" min="0.5" max="5" step="0.5" />
          </label>
          <button type="submit" :disabled="state.submitting">
            {{ state.submitting ? '提交中' : '提交并刷新' }}
          </button>
        </form>
        <p class="hint">
          评分会写入 MySQL 的 ratings 表，并触发后端刷新该用户的推荐缓存。
        </p>
      </aside>
    </section>
  </main>
</template>
