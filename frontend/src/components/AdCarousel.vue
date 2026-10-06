<template>
  <div v-if="slides.length" class="glass-card ad-carousel">
    <n-carousel
      autoplay
      :interval="5000"
      :show-arrow="slides.length > 1"
      dot-placement="bottom"
      class="ad-track"
    >
      <div
        v-for="s in slides"
        :key="s.id"
        class="ad-slide"
        :class="{ clickable: !!s.linkUrl }"
        :title="s.linkUrl ? '点击打开链接' : (s.title || '')"
        @click="open(s)"
      >
        <img :src="s.imageUrl" :alt="s.title || '轮播图'" />
        <div v-if="s.title" class="ad-title">{{ s.title }}</div>
      </div>
    </n-carousel>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api'

const slides = ref([])

onMounted(async () => {
  try {
    const res = await api.get('/carousel')
    slides.value = res.data || []
  } catch { /* 广告位加载失败静默处理，不影响上传 */ }
})

function open(s) {
  if (s.linkUrl) window.open(s.linkUrl, '_blank', 'noopener')
}
</script>

<style scoped>
.ad-carousel { overflow: hidden; }
.ad-track { height: 200px; }
.ad-slide {
  position: relative;
  width: 100%;
  height: 200px;
  background: rgba(0, 0, 0, 0.25);
}
.ad-slide.clickable { cursor: pointer; }
.ad-slide img { width: 100%; height: 100%; object-fit: cover; display: block; }
.ad-title {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 26px 16px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 6px rgba(0, 0, 0, 0.6);
  background: linear-gradient(180deg, transparent, rgba(0, 0, 0, 0.55));
}

@media (max-width: 720px) {
  .ad-track, .ad-slide { height: 150px; }
}
</style>
