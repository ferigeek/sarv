<template>
  <main class="login-view" data-testid="login-view">
    <section class="panel login-box">
      <h1 class="login-brand">SARV</h1>
      <p class="login-hint">sign in or create an account to continue.</p>
    </section>
  </main>
</template>

<style scoped>
.login-view {
  height: 100%;
  display: grid;
  place-items: center;
}

.login-box {
  padding: var(--sarv-space-6) var(--sarv-space-7);
  text-align: center;
  border-color: var(--sarv-green-dark);
  box-shadow: var(--sarv-glow);
}

.login-brand {
  font-size: 2rem;
  letter-spacing: 0.35em;
  color: var(--sarv-green);
  text-shadow: var(--sarv-glow);
}

.login-hint {
  margin-top: var(--sarv-space-3);
  color: var(--sarv-text-dim);
}
</style>