import { readFile } from "node:fs/promises";
import { createPinia, setActivePinia } from "pinia";
import ts from "typescript";
import assert from "node:assert/strict";
setActivePinia(createPinia());
let source = await readFile("src/stores/comparison.ts", "utf8");
source = source.replace(
  /from\s+['"]pinia['"]/,
  "from " +
    JSON.stringify(
      new URL("../node_modules/pinia/dist/pinia.mjs", import.meta.url).href,
    ),
);
const { useComparison } = await import(
  "data:text/javascript;base64," +
    Buffer.from(
      ts.transpile(source, { module: ts.ModuleKind.ESNext }),
    ).toString("base64")
);
const s = useComparison();
const p = (id, categoryId = 1, type = "ROBOT") => ({
  id,
  categoryId,
  type,
  name: "设备" + id,
  categoryName: "清洁",
});
s.toggle(p(1));
assert.equal(s.items.length, 1);
s.toggle(p(2, 2));
assert.equal(s.items.length, 1);
assert.match(s.message, /清空/);
s.toggle(p(2));
s.toggle(p(3));
s.toggle(p(4));
assert.equal(s.items.length, 4);
s.toggle(p(5));
assert.equal(s.items.length, 4);
assert.match(s.message, /最多/);
s.toggle(p(2));
assert.equal(s.items.length, 3);
s.remove(1);
assert.equal(s.items.length, 2);
s.clear();
assert.equal(s.items.length, 0);
s.toggle(p(10, 1, "ACCESSORY"));
assert.equal(s.items.length, 0);
s.toggle(p(-1));
assert.equal(s.items.length, 0);
console.log(
  "PASS comparison selection, same-category, limit, toggle, remove, clear and invalid data",
);
