<template>
  <main class="codedog-login-redirect">
    <p>正在返回 CodeDog 登录...</p>
  </main>
</template>

<script>
export default {
  name: 'CodeDogLoginRedirect',
  created() {
    window.location.replace('https://codedog.online/questionnaire')
  }
}
</script>

<style scoped>
.codedog-login-redirect {
  min-height: 100vh;
  display: grid;
  place-content: center;
  color: #667085;
  background: #f7f9f8;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
}
</style>
