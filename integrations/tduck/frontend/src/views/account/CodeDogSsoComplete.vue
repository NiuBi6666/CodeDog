<template>
  <main class="codedog-sso-page" aria-live="polite">
    <div class="codedog-sso-spinner" aria-hidden="true"></div>
    <h1>正在进入问卷与作业</h1>
    <p>正在安全同步 CodeDog 登录状态...</p>
  </main>
</template>

<script>
export default {
  name: 'CodeDogSsoComplete',
  created() {
    sessionStorage.setItem('codedogEmbedded', window.self !== window.top ? '1' : '0')
    this.$api
      .get('/user/current/detail')
      .then((response) =>
        this.$store.dispatch('user/login', {
          ...response.data,
          token: 'cookie-session'
        })
      )
      .then(() => {
        this.$store.dispatch('global/loginExpired', false)
        this.$router.replace('/home')
      })
      .catch(() => {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        window.location.replace('https://codedog.online/login?redirect=%2Fquestionnaire')
      })
  }
}
</script>

<style scoped>
.codedog-sso-page {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: center;
  gap: 12px;
  color: #1f2937;
  background: #f7f9f8;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
.codedog-sso-page h1 {
  margin: 8px 0 0;
  font-size: 22px;
  font-weight: 650;
}
.codedog-sso-page p {
  margin: 0;
  color: #667085;
}
.codedog-sso-spinner {
  width: 30px;
  height: 30px;
  border: 3px solid #d7e4dc;
  border-top-color: #247a4d;
  border-radius: 50%;
  animation: codedog-spin 0.8s linear infinite;
}
@keyframes codedog-spin {
  to { transform: rotate(360deg); }
}
</style>
