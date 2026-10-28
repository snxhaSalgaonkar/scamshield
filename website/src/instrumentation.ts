// Runs once when the server starts: download/load the similarity model and embed the phrase bank
// before the first phone request, so /api/analyze answers within its 2.5 s budget.
export async function register() {
  if (process.env.NEXT_RUNTIME !== 'nodejs') return
  const { loadModel } = await import('./lib/semantic/similarity')
  loadModel().catch(() => {
    // Ignored here: the route retries the load and answers 503 if it still fails.
  })
}
