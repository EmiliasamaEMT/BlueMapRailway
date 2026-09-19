import { boxesIntersect, distanceToLineSquared, lineBounds } from "./geometry.mjs";

const MAX_CELLS = 256;
const valid = b => b && [b.minX,b.minZ,b.maxX,b.maxZ].every(Number.isFinite) && b.minX <= b.maxX && b.minZ <= b.maxZ;

// Cached bounds and capped cell membership keep long diagonals and overview queries bounded.
export class RailSpatialIndex {
  constructor(cellSize = 128) {
    this.cellSize = Math.max(16, Number(cellSize) || 128);
    this.cells = new Map();
    this.lines = [];
    this.bounds = [];
    this.large = [];
  }
  rebuild(lines = []) {
    this.cells.clear();
    this.lines = Array.from(lines);
    this.bounds = this.lines.map(line => lineBounds(line.points));
    this.large = [];
    this.bounds.forEach((bounds, index) => {
      if (!valid(bounds)) return;
      const keys = this.keysForBounds(bounds);
      if (!keys) { this.large.push(index); return; }
      for (const key of keys) {
        if (!this.cells.has(key)) this.cells.set(key, []);
        this.cells.get(key).push(index);
      }
    });
  }
  queryBox(bounds) {
    if (!valid(bounds)) return [];
    const keys = this.keysForBounds(bounds);
    if (!keys) return this.lines.filter((_, i) => valid(this.bounds[i]) && boxesIntersect(this.bounds[i], bounds));
    const candidates = new Set(this.large);
    for (const key of keys) for (const i of this.cells.get(key) || []) candidates.add(i);
    return [...candidates].sort((a,b) => a-b)
      .filter(i => boxesIntersect(this.bounds[i], bounds)).map(i => this.lines[i]);
  }
  nearest(x, z, tolerance, accept = () => true) {
    const radius = Math.max(0, tolerance);
    let nearest = null, distance = radius * radius;
    for (const line of this.queryBox({minX:x-radius,minZ:z-radius,maxX:x+radius,maxZ:z+radius})) {
      if (!accept(line)) continue;
      const next = distanceToLineSquared(line,x,z);
      // Equal distances intentionally prefer the later source line.
      if (next <= distance) { nearest = line; distance = next; }
    }
    return nearest;
  }
  keysForBounds(bounds) {
    if (!valid(bounds)) return [];
    const x0=Math.floor(bounds.minX/this.cellSize), x1=Math.floor(bounds.maxX/this.cellSize);
    const z0=Math.floor(bounds.minZ/this.cellSize), z1=Math.floor(bounds.maxZ/this.cellSize);
    if ((x1-x0+1)*(z1-z0+1)>MAX_CELLS) return null;
    const keys=[];
    for(let dx=0;dx<=x1-x0;dx++) for(let dz=0;dz<=z1-z0;dz++) keys.push(`${x0+dx}:${z0+dz}`);
    return keys;
  }
}
