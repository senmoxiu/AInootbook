const fs = require('node:fs')
const path = require('node:path')

const rootDir = path.resolve(__dirname, '..')
const targets = ['node_modules/.vite', 'node_modules/.cache']

for (const relativePath of targets) {
  const targetPath = path.join(rootDir, relativePath)
  if (fs.existsSync(targetPath)) {
    fs.rmSync(targetPath, { recursive: true, force: true })
    console.log(`removed ${relativePath}`)
  } else {
    console.log(`skip ${relativePath}`)
  }
}
