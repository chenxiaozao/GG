import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import ts from "typescript";

// Test the actual TypeScript helper in memory; no duplicate implementation or generated file.
const source = await readFile("src/utils/colors.ts", "utf8");
const compiled = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020,
  },
}).outputText;
const colors = {};
new Function("exports", compiled)(colors);
assert.equal(colors.contrastRatio("#000000", "#ffffff"), 21);
assert.equal(colors.contrastText("#ffffff"), "#000000");
assert.equal(colors.contrastText("#000000"), "#ffffff");
let combinations = 0;
const backgrounds = [
  "#ffffff",
  "#f5f6f8",
  "#f7f1e7",
  "#777777",
  "#999999",
  "#111827",
  "#000000",
];
for (let red = 0; red <= 255; red += 51) {
  for (let green = 0; green <= 255; green += 51) {
    for (let blue = 0; blue <= 255; blue += 51) {
      const accent =
        "#" +
        [red, green, blue]
          .map((channel) => channel.toString(16).padStart(2, "0"))
          .join("");
      assert.ok(
        colors.contrastRatio(colors.contrastText(accent), accent) >= 4.5,
        accent,
      );
      for (const background of backgrounds) {
        const dark = colors.contrastText(background) === "#ffffff";
        const surfaces = dark
          ? ["#1b2533", "#222f40", background]
          : ["#ffffff", "#f7f8fa", background];
        const foreground = colors.readableAccent(
          accent,
          surfaces,
          dark ? "#ffffff" : "#000000",
        );
        for (const surface of surfaces)
          assert.ok(
            colors.contrastRatio(foreground, surface) >= 4.5,
            `${accent} / ${surface} / ${foreground}`,
          );
        combinations++;
      }
    }
  }
}
console.log(
  JSON.stringify({
    sampledAccents: 216,
    foregroundSurfaceCombinations: combinations,
    passed: true,
    note: "算法抽样验证，不等于全站所有组件 WCAG 验收。",
  }),
);
