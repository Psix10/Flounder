export function startRead(read, handlers) {
  let active = true
  void Promise.resolve()
    .then(() => active ? read() : undefined)
    .then(value => {
      if (active) handlers.success(value)
    })
    .catch(error => {
      if (active) handlers.failure(error)
    })
    .finally(() => {
      if (active) handlers.finished()
    })
  return () => { active = false }
}
