<template>
  <div class="auth-shell">
    <!-- 左侧品牌区 -->
    <aside class="auth-brand">
      <div class="brand-top">
        <div class="brand-mark">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="1.8" stroke-linecap="round">
            <circle cx="12" cy="12" r="8.4" />
            <path d="M8.4 9.2a4.5 4.5 0 0 1 3.2-1.6" />
            <circle cx="9.4" cy="14.3" r="1.15" fill="#fff" stroke="none" />
            <circle cx="14.8" cy="15.6" r="1.5" fill="#fff" stroke="none" />
          </svg>
        </div>
        <span class="brand-name">火星图床</span>
      </div>

      <div class="brand-main">
        <span class="brand-kicker">Free Image Hosting</span>
        <h1 class="brand-h1">
          把图片交给 <span class="gradient-text">GitHub</span><br />
          让 jsDelivr 送达全球
        </h1>
        <p class="brand-lead">
          零成本搭建属于你的图床：图片直存 GitHub 仓库，通过 jsDelivr 全球节点秒级分发，
          支持一键复制 CDN / Markdown / HTML 链接。
        </p>
        <ul class="brand-features">
          <li><span class="feature-dot">🚀</span> 免注册即可上传体验</li>
          <li><span class="feature-dot">🗂️</span> 仓库直连存储，数据完全由你掌控</li>
          <li><span class="feature-dot">⚡</span> 全球 CDN 加速，链接永久可用</li>
          <li><span class="feature-dot">🛡️</span> 登录后独立上传记录与仓库管理</li>
        </ul>
      </div>

      <div class="brand-foot">Spring Boot 3 · Vue 3 · MyBatis-Plus · SQLite</div>
    </aside>

    <!-- 右侧表单区 -->
    <main class="auth-panel">
      <div class="auth-card">
        <header class="auth-head">
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </header>
        <slot />
      </div>
    </main>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' }
})
</script>
