import { mkdir, writeFile, access } from "node:fs/promises";
import path from "node:path";

// This identifies records requiring human review; shared images are not automatically false.
const root = path.resolve("../tmp/catalog-audit");
await mkdir(root, { recursive: true });
const products = await fetch("http://127.0.0.1:8080/api/products")
  .then((r) => r.json())
  .then((r) => r.data);
const images = new Map();
for (const product of products) {
  const entries = images.get(product.image) || [];
  entries.push({
    id: product.id,
    name: product.name,
    sourceUrl: product.sourceUrl,
  });
  images.set(product.image, entries);
}
const sharedImages = [...images]
  .filter(([, entries]) => entries.length > 1)
  .map(([image, products]) => ({ image, products }));
const genericSources = products
  .filter((product) => {
    try {
      const url = new URL(product.sourceUrl);
      return (
        !["http:", "https:"].includes(url.protocol) ||
        url.pathname === "/" ||
        url.pathname === ""
      );
    } catch {
      return true;
    }
  })
  .map((product) => ({
    id: product.id,
    name: product.name,
    sourceUrl: product.sourceUrl,
  }));
const illustrationImages = products
  .filter((x) => /\.svg(?:$|\?)/.test(x.image || ""))
  .map((x) => ({ id: x.id, name: x.name, image: x.image }));
const mediaCounts = {};
const missingLocalImages = [];
for (const product of products) {
  const kind = product.imageKind || "UNREVIEWED";
  mediaCounts[kind] = (mediaCounts[kind] || 0) + 1;
  if (product.image?.startsWith("/products/")) {
    const local = path.resolve("public", product.image.slice(1));
    if (!local.startsWith(path.resolve("public") + path.sep)) continue;
    try {
      await access(local);
    } catch {
      missingLocalImages.push({
        id: product.id,
        name: product.name,
        image: product.image,
      });
    }
  }
}
const report = {
  generatedAt: new Date().toISOString(),
  activeProducts: products.length,
  sharedImages,
  genericSources,
  illustrationImages,
  mediaCounts,
  missingLocalImages,
  reviewedModelImages: products
    .filter((x) => x.imageKind === "MODEL_PHOTO")
    .map((x) => ({
      id: x.id,
      name: x.name,
      sourceUrl: x.sourceUrl,
      image: x.image,
      checkedAt: x.imageCheckedAt,
    })),
  referenceImages: products
    .filter((x) => x.imageKind === "REFERENCE")
    .map((x) => ({
      id: x.id,
      name: x.name,
      image: x.image,
      note: x.imageNote,
    })),
  unreviewedImages: products
    .filter((x) => !x.imageKind || x.imageKind === "UNREVIEWED")
    .map((x) => ({
      id: x.id,
      name: x.name,
      image: x.image,
      sourceUrl: x.sourceUrl,
    })),
  note: "共享图片、品牌首页和示意图需要逐型号复核，不能凭此报告宣称全部参数或图片已验证。",
};
await writeFile(
  path.join(root, "report.json"),
  JSON.stringify(report, null, 2),
);
console.log(
  JSON.stringify({
    activeProducts: products.length,
    sharedImageGroups: sharedImages.length,
    genericSourceRecords: genericSources.length,
    illustrationRecords: illustrationImages.length,
    mediaCounts,
    missingLocalImages: missingLocalImages.length,
  }),
);
