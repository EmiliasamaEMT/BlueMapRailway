import test from 'node:test';
import assert from 'node:assert/strict';
import { RailSpatialIndex } from '../../core/src/main/resources/web/assets/spatial-index.mjs';
import { RailwayMapView } from '../../core/src/main/resources/web/assets/map-view.mjs';

test('huge diagonal and overview remain bounded and preserve crossing geometry', () => {
  const index = new RailSpatialIndex(16);
  const huge = { points:[[-3e7,64,-3e7],[3e7,64,3e7]] };
  index.rebuild([huge]);
  assert.equal(index.cells.size,0);
  assert.deepEqual(index.queryBox({minX:-1,minZ:-1,maxX:1,maxZ:1}),[huge]);
  assert.deepEqual(index.queryBox({minX:-3e7,minZ:-3e7,maxX:3e7,maxZ:3e7}),[huge]);
  assert.deepEqual(index.queryBox({minX:NaN,minZ:0,maxX:1,maxZ:1}),[]);
});

test('visibility is evaluated before nearest selection, including world isolation', () => {
  const index = new RailSpatialIndex();
  const visible = {world:'a',points:[[0,0,2],[100,0,2]]};
  const hidden = {world:'b',points:[[0,0,0],[100,0,0]]};
  index.rebuild([visible,hidden]);
  assert.equal(index.nearest(50,0,8,line=>line.world==='a'),visible);
});

test('queries preserve source order across cells and replacement discards old entries', () => {
  const index = new RailSpatialIndex(16);
  const a={points:[[30,0,0],[40,0,0]]}, b={points:[[0,0,0],[35,0,0]]};
  index.rebuild([a,b]);
  assert.deepEqual(index.queryBox({minX:0,minZ:0,maxX:45,maxZ:1}),[a,b]);
  index.rebuild([]);
  assert.deepEqual(index.queryBox({minX:0,minZ:0,maxX:45,maxZ:1}),[]);
});

test('render invalidations coalesce and view changes do not rebuild object overlays', () => {
  const original=globalThis.requestAnimationFrame;
  let callback, schedules=0;
  globalThis.requestAnimationFrame = fn => { callback=fn; schedules++; return schedules; };
  try {
    const map=Object.create(RailwayMapView.prototype);
    Object.assign(map,{frame:null,dirty:new Set(),destroyed:false,store:{getState:()=>({view:{x:0,y:0,w:10,h:10}})},overlay:{setAttribute(){}}});
    const calls=[];
    for(const method of ['drawBackground','drawRails','drawMasks','drawStations','drawSelection','drawDraft']) map[method]=()=>calls.push(method);
    map.renderView(); map.renderView(); map.renderSelection();
    assert.equal(schedules,1);
    callback();
    assert.deepEqual(calls,['drawBackground','drawRails','drawSelection','drawDraft']);
  } finally { globalThis.requestAnimationFrame=original; }
});

test('viewport drawing skips distant lines but keeps crossing segments and global style order', () => {
  const map=Object.create(RailwayMapView.prototype);
  const line=(color,points)=>({color,points,world:'world',lineWidth:3});
  const lines=[line('red',[[1000,0,1000],[1100,0,1100]]),line('blue',[[0,0,0],[10,0,10]]),line('red',[[-1000,0,5],[1000,0,5]])];
  const state={world:'world',view:{x:0,y:0,w:10,h:10},layers:{unclassified:true},data:{lines}};
  Object.assign(map,{width:1000,height:1000,store:{getState:()=>state},index:new RailSpatialIndex(),styles:new Map([['red|3',0],['blue|3',1]])});
  map.index.rebuild(lines);
  const strokes=[];let points=0;
  const context={beginPath(){},moveTo(){points++},lineTo(){points++},stroke(){strokes.push(this.strokeStyle)}};
  map.prepareContext=()=>context;
  map.drawRails();
  assert.deepEqual(strokes,['red','blue']);
  assert.equal(points,4);
});

test('obsolete background loads cannot replace the new image or redraw after destruction', () => {
  const oldImage=globalThis.Image, oldWindow=globalThis.window;
  const images=[];
  globalThis.Image=class {constructor(){images.push(this)}};
  globalThis.window={location:{origin:'http://localhost'}};
  try {
    let state={token:'',data:{background:{imageUrl:'/a.png'}}};
    const map=Object.create(RailwayMapView.prototype);
    let draws=0;
    Object.assign(map,{store:{getState:()=>state},backgroundKey:'',destroyed:false,invalidate:()=>draws++});
    map.ensureBackgroundImage();
    state={token:'',data:{background:{imageUrl:'/b.png'}}};
    map.ensureBackgroundImage();
    images[1].onload();images[0].onload();
    assert.equal(map.backgroundImage,images[1]);assert.equal(draws,1);
    map.destroyed=true;images[1].onload();assert.equal(draws,1);
  } finally { globalThis.Image=oldImage;globalThis.window=oldWindow; }
});
