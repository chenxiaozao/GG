function channels(hex: string) {
  return [0, 2, 4].map((offset) =>
    parseInt(hex.slice(offset + 1, offset + 3), 16),
  );
}

export function luminance(hex: string) {
  return channels(hex)
    .map((channel) => {
      const value = channel / 255;
      return value <= 0.04045
        ? value / 12.92
        : ((value + 0.055) / 1.055) ** 2.4;
    })
    .reduce(
      (sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index],
      0,
    );
}

export function contrastRatio(first: string, second: string) {
  const a = luminance(first),
    b = luminance(second);
  return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
}

export function contrastText(background: string) {
  return contrastRatio("#ffffff", background) >= 4.5 ? "#ffffff" : "#000000";
}

// Keep the chosen hue when possible, without yellow-on-white or navy-on-navy text.
export function readableAccent(
  accent: string,
  surfaces: string[],
  ink: string,
) {
  const original = channels(accent),
    target = channels(ink);
  for (let step = 0; step <= 20; step++) {
    const amount = step / 20;
    const result =
      "#" +
      original
        .map((value, index) =>
          Math.round(value * (1 - amount) + target[index] * amount)
            .toString(16)
            .padStart(2, "0"),
        )
        .join("");
    if (surfaces.every((surface) => contrastRatio(result, surface) >= 4.5))
      return result;
  }
  return ink;
}
