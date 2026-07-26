import { boxesIntersect, distanceToLineSquared, lineBounds } from "./geometry.mjs";

export class RailSpatialIndex {
  constructor(cellSize = 128) {
    this.cellSize = Math.max(16, Number(cellSize) || 128);
    this.cells = new Map();
    this.lines = [];
  }

  rebuild(lines = []) {
    this.cells.clear();
    this.lines = Array.from(lines);
    this.lines.forEach((line, index) => {
      const bounds = lineBounds(line.points);
      if (!bounds) {
        return;
      }
      for (const key of this.keysForBounds(bounds)) {
        const indexes = this.cells.get(key) || [];
        indexes.push(index);
        this.cells.set(key, indexes);
      }
    });
  }

  queryBox(bounds) {
    const candidates = new Set();
    for (const key of this.keysForBounds(bounds)) {
      for (const index of this.cells.get(key) || []) {
        candidates.add(index);
      }
    }
    return Array.from(candidates, (index) => this.lines[index]).filter((line) => {
      const boundsForLine = lineBounds(line.points);
      return boundsForLine && boxesIntersect(boundsForLine, bounds);
    });
  }

  nearest(x, z, tolerance) {
    const radius = Math.max(0, tolerance);
    const candidates = this.queryBox({
      minX: x - radius,
      minZ: z - radius,
      maxX: x + radius,
      maxZ: z + radius,
    });
    let nearest = null;
    let nearestDistance = radius * radius;
    for (const line of candidates) {
      const distance = distanceToLineSquared(line, x, z);
      if (distance <= nearestDistance) {
        nearest = line;
        nearestDistance = distance;
      }
    }
    return nearest;
  }

  keysForBounds(bounds) {
    const keys = [];
    const minX = Math.floor(bounds.minX / this.cellSize);
    const minZ = Math.floor(bounds.minZ / this.cellSize);
    const maxX = Math.floor(bounds.maxX / this.cellSize);
    const maxZ = Math.floor(bounds.maxZ / this.cellSize);
    for (let x = minX; x <= maxX; x += 1) {
      for (let z = minZ; z <= maxZ; z += 1) {
        keys.push(`${x}:${z}`);
      }
    }
    return keys;
  }
}
