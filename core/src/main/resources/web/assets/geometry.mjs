export function normalizeBox(a, b, snap = true) {
  const values = snap
    ? [Math.floor(a.x), Math.floor(a.z), Math.floor(b.x), Math.floor(b.z)]
    : [a.x, a.z, b.x, b.z];
  return {
    minX: Math.min(values[0], values[2]),
    minZ: Math.min(values[1], values[3]),
    maxX: Math.max(values[0], values[2]),
    maxZ: Math.max(values[1], values[3]),
  };
}

export function pointInBox(x, z, box) {
  return x >= box.minX && x <= box.maxX && z >= box.minZ && z <= box.maxZ;
}

export function boxesIntersect(a, b) {
  return a.minX <= b.maxX && a.maxX >= b.minX && a.minZ <= b.maxZ && a.maxZ >= b.minZ;
}

export function lineBounds(points) {
  let minX = Infinity;
  let minZ = Infinity;
  let maxX = -Infinity;
  let maxZ = -Infinity;
  for (const point of points || []) {
    minX = Math.min(minX, Number(point[0]));
    minZ = Math.min(minZ, Number(point[2]));
    maxX = Math.max(maxX, Number(point[0]));
    maxZ = Math.max(maxZ, Number(point[2]));
  }
  return Number.isFinite(minX) ? { minX, minZ, maxX, maxZ } : null;
}

export function distanceToSegmentSquared(px, pz, ax, az, bx, bz) {
  const dx = bx - ax;
  const dz = bz - az;
  if (dx === 0 && dz === 0) {
    return (px - ax) ** 2 + (pz - az) ** 2;
  }
  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (pz - az) * dz) / (dx * dx + dz * dz)));
  const x = ax + t * dx;
  const z = az + t * dz;
  return (px - x) ** 2 + (pz - z) ** 2;
}

export function distanceToLineSquared(line, x, z) {
  const points = line?.points || [];
  let best = Infinity;
  for (let index = 1; index < points.length; index += 1) {
    best = Math.min(best, distanceToSegmentSquared(
      x,
      z,
      Number(points[index - 1][0]),
      Number(points[index - 1][2]),
      Number(points[index][0]),
      Number(points[index][2]),
    ));
  }
  return best;
}

export function segmentIntersectsBox(ax, az, bx, bz, box) {
  if (pointInBox(ax, az, box) || pointInBox(bx, bz, box)) {
    return true;
  }
  const minX = Math.min(ax, bx);
  const maxX = Math.max(ax, bx);
  const minZ = Math.min(az, bz);
  const maxZ = Math.max(az, bz);
  if (maxX < box.minX || minX > box.maxX || maxZ < box.minZ || minZ > box.maxZ) {
    return false;
  }
  return segmentsIntersect(ax, az, bx, bz, box.minX, box.minZ, box.maxX, box.minZ)
    || segmentsIntersect(ax, az, bx, bz, box.maxX, box.minZ, box.maxX, box.maxZ)
    || segmentsIntersect(ax, az, bx, bz, box.maxX, box.maxZ, box.minX, box.maxZ)
    || segmentsIntersect(ax, az, bx, bz, box.minX, box.maxZ, box.minX, box.minZ);
}

export function lineIntersectsBox(line, box) {
  const points = line?.points || [];
  for (let index = 1; index < points.length; index += 1) {
    if (segmentIntersectsBox(
      Number(points[index - 1][0]),
      Number(points[index - 1][2]),
      Number(points[index][0]),
      Number(points[index][2]),
      box,
    )) {
      return true;
    }
  }
  return false;
}

function segmentsIntersect(ax, az, bx, bz, cx, cz, dx, dz) {
  const d1 = direction(cx, cz, dx, dz, ax, az);
  const d2 = direction(cx, cz, dx, dz, bx, bz);
  const d3 = direction(ax, az, bx, bz, cx, cz);
  const d4 = direction(ax, az, bx, bz, dx, dz);
  return ((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
    && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0));
}

function direction(ax, az, bx, bz, cx, cz) {
  return (cx - ax) * (bz - az) - (cz - az) * (bx - ax);
}
