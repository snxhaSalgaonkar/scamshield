// Runs once when the server starts: download/load the similarity model and embed the phrase bank
// before the first phone request, so /api/analyze answers within its 2.5 s budget.
export async function register() {
  if (process.env.NEXT_RUNTIME !== 'nodejs') return
  try {
    const { loadModel } = await import('./lib/semantic/similarity')
    loadModel().catch((err) => {
      console.warn('Preloading similarity model failed (will retry on demand):', err)
    })
  } catch (err) {
    console.warn('Failed to load similarity model in instrumentation:', err)
  }
}
