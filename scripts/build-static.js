import fs from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const dest = path.join(root, 'dist');
await fs.rm(dest, { recursive: true, force: true });
await fs.mkdir(dest, { recursive: true });
for (const name of ['index.html', 'app.js', 'style.css', 'sw.js', 'icon.svg', 'manifest.webmanifest']) {
  await fs.copyFile(path.join(root, 'public', name), path.join(dest, name));
}
await fs.copyFile(path.join(root, 'lib/geo.js'), path.join(dest, 'geo.js'));
await fs.copyFile(path.join(root, 'lib/guide.js'), path.join(dest, 'guide.js'));
await fs.copyFile(path.join(root, 'data/places.js'), path.join(dest, 'places.js'));
await fs.copyFile(path.join(root, 'data/restaurants.js'), path.join(dest, 'restaurants.js'));
console.log('Static site built:', dest);
