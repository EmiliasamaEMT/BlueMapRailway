export function fitView(bounds, width, height, padding = 24) {
  const safeWidth = Math.max(1, width);
  const safeHeight = Math.max(1, height);
  const minX = Number(bounds?.minX ?? -128);
  const minZ = Number(bounds?.minZ ?? -128);
  const maxX = Number(bounds?.maxX ?? 128);
  const maxZ = Number(bounds?.maxZ ?? 128);
  const contentWidth = Math.max(16, maxX - minX);
  const contentHeight = Math.max(16, maxZ - minZ);
  const availableWidth = Math.max(1, safeWidth - padding * 2);
  const availableHeight = Math.max(1, safeHeight - padding * 2);
  const scale = Math.min(availableWidth / contentWidth, availableHeight / contentHeight);
  const viewWidth = safeWidth / Math.max(scale, 0.0001);
  const viewHeight = safeHeight / Math.max(scale, 0.0001);
  const centerX = (minX + maxX) / 2;
  const centerZ = (minZ + maxZ) / 2;
  return { x: centerX - viewWidth / 2, y: centerZ - viewHeight / 2, w: viewWidth, h: viewHeight };
}

export function zoomView(view, point, factor, minSize = 4, maxSize = 200000) {
  const safeFactor = Math.max(0.05, Number(factor) || 1);
  const width = Math.max(minSize, Math.min(maxSize, view.w * safeFactor));
  const height = Math.max(minSize, Math.min(maxSize, view.h * safeFactor));
  const ratioX = view.w === 0 ? 0.5 : (point.x - view.x) / view.w;
  const ratioY = view.h === 0 ? 0.5 : (point.z - view.y) / view.h;
  return {
    x: point.x - width * ratioX,
    y: point.z - height * ratioY,
    w: width,
    h: height,
  };
}

export function centerView(view, x, z) {
  return { ...view, x: x - view.w / 2, y: z - view.h / 2 };
}

export function panView(view, dx, dz) {
  return { ...view, x: view.x + dx, y: view.y + dz };
}

export function screenToWorld(view, rect, clientX, clientY) {
  return {
    x: view.x + ((clientX - rect.left) / Math.max(1, rect.width)) * view.w,
    z: view.y + ((clientY - rect.top) / Math.max(1, rect.height)) * view.h,
  };
}

export function worldToScreen(view, width, height, x, z) {
  return {
    x: ((x - view.x) / view.w) * width,
    y: ((z - view.y) / view.h) * height,
  };
}
