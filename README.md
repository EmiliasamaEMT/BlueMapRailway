# BlueMapRailway

BlueMapRailway 鏄竴涓潰鍚?Paper 26.1.2 鏈嶅姟绔殑 BlueMap 闄勫睘鎻掍欢锛岀洰鏍囨槸鍦?BlueMap 缃戦〉鍦板浘涓婃樉绀哄師鐗?Minecraft 鐨勯搧璺綉缁溿€?
褰撳墠鏀寔璇嗗埆 4 绉嶅師鐗堥搧杞細

- 鏅€氶搧杞細`RAIL`
- 鍔ㄥ姏閾佽建锛歚POWERED_RAIL`
- 鎺㈡祴閾佽建锛歚DETECTOR_RAIL`
- 婵€娲婚搧杞細`ACTIVATOR_RAIL`

## 褰撳墠鐘舵€?
椤圭洰鐩墠澶勪簬鏃╂湡 MVP 闃舵锛屽凡缁忓叿澶囧熀纭€閾捐矾锛?
- 绛夊緟 `BlueMapAPI` 鍙敤鍚庡惎鍔ㄩ搧璺鐩栧眰鏈嶅姟銆?- 鑷姩鎵弿閰嶇疆涓栫晫涓殑宸插姞杞藉尯鍧椼€?- 璇诲彇 Bukkit `Rail.Shape`锛岀敓鎴愬唴閮ㄩ搧璺妭鐐广€?- 鐢熸垚閾佽矾杩為€氬垎閲?`RailComponent` 鍜岀ǔ瀹?component ID銆?- 灏嗚繛缁搧杞ㄥ悎骞朵负 BlueMap `LineMarker`銆?- 鏀寔閫氳繃 `routes.yml` 鎸?component ID 缁欑嚎璺懡鍚嶃€佹敼鑹插拰璋冩暣绾垮銆?- BlueMap 鍥惧眰鍜?SVG 浼氭寜绾胯矾鍒嗙粍锛屾湭褰掔被绾胯矾杩涘叆鏈垎绫诲垎缁勩€?- 鏀寔 `/railmap route ...` 绠＄悊鍛戒护锛屽噺灏戞墜鍐欑嚎璺厤缃垚鏈€?- 鏀寔 `stations.yml` 瀹氫箟绔欑偣鍖哄煙锛屽苟鍦?BlueMap/SVG 涓樉绀虹珯鐐广€?- 鏀寔鍘嗗彶鎵弿缂撳瓨锛屽凡鎵弿杩囩殑閾佽建鍖哄潡鍗歌浇鍚庝粛鍙户缁樉绀恒€?- 鏀寔鐩戝惉鏂板姞杞藉尯鍧楋紝骞跺皢鐜╁閲嶆柊鍔犺浇鍒扮殑鍖哄潡鑷姩绾冲叆鍘嗗彶缂撳瓨銆?- 鏀寔瀹氭湡澶囦唤 `routes.yml`銆乣stations.yml`銆乣edits.yml` 鍜屽彲閫夌殑 `config.yml`锛岄粯璁ゆ瘡 24 灏忔椂鐢熸垚涓€涓?zip 澶囦唤鍖呫€?- 鎵弿瀹屾垚鍚庡鍑哄湴鐞嗗瀷 SVG 绾胯矾鍥撅紝鏂逛究缃戦〉灞曠ず鎴栦簩娆″姞宸ャ€?- 鐩戝惉閾佽建鏀剧疆銆佺牬鍧忋€佺墿鐞嗗拰绾㈢煶鍙樺寲锛屽苟寤惰繜瑙﹀彂閲嶆壂銆?- 鎻愪緵绠＄悊鍛樺懡浠ゆ煡鐪嬬姸鎬併€侀噸杞介厤缃拰瑙﹀彂閲嶆壂銆?
椤圭洰褰撳墠姝ｅ湪鍚戝妯″潡缁撴瀯婕旇繘锛?
- `core/`锛氬钩鍙版棤鍏崇殑鏍稿績妯″瀷銆佺畻娉曞拰閰嶇疆鎶借薄
- `paper/`锛氬綋鍓嶆寮忓彲鐢ㄧ殑 Paper 鎻掍欢瀹炵幇
- `fabric/`锛氶鐣欎腑鐨?Fabric 閫傞厤灞傞鏋?
褰撳墠姝ｅ紡鍙戝竷涓庝娇鐢ㄧ殑浠嶇劧鏄?`paper` 鐗堟湰鎻掍欢銆?
## 绠＄悊鍛樺懡浠?
```text
/railmap status
/railmap debug
/railmap log [琛屾暟]
/railmap backup
/railmap reload
/railmap rescan
/railmap route list
/railmap route info <id>
/railmap route status [id]
/railmap route create <id> <鍚嶇О>
/railmap route rename <id> <鍚嶇О>
/railmap route color <id> <#RRGGBB>
/railmap route width <id> <瀹藉害>
/railmap route auto-match <id> <true|false>
/railmap route assign-nearest <id> [鍗婂緞]
/railmap route anchor-nearest <id> [鍗婂緞]
/railmap station list
/railmap station info <id>
/railmap station add <id> <鍚嶇О> [鍗婂緞]
/railmap station set-area-here <id> [鍗婂緞]
/railmap station remove <id>
```

杩欎簺鍛戒护鍙潰鍚戞湇鍔″櫒绠＄悊鍛樸€傛彃浠剁殑姝ｅ紡宸ヤ綔鏂瑰紡鏄嚜鍔ㄧ淮鎶?BlueMap 鍥惧眰锛屼笉渚濊禆鐜╁鎵嬪姩鎵弿銆?
## 鏋勫缓涓庡彂甯?
Paper 鏈嶅姟绔彃浠剁殑鍙戝竷褰㈠紡鏄竴涓?`.jar` 鏂囦欢銆傛瀯寤哄悗鐨?`BlueMapRailway-鐗堟湰鍙?jar` 鏀惧叆鏈嶅姟绔?`plugins/` 鐩綍鍗冲彲浣跨敤銆?
鏈湴鏋勫缓锛?
```bash
./gradlew build
```

Windows 涓嬩篃鍙互浣跨敤锛?
```powershell
.\gradlew.bat build
```

鏋勫缓浜х墿浣嶄簬锛?
```text
build/libs/
```

鍏朵腑鏍圭洰褰?`build/libs/` 浼氳嚜鍔ㄥ悓姝ュ綋鍓?`paper` 妯″潡鐨勬寮忔瀯寤轰骇鐗╋紝淇濇寔涓庢棦鏈夊彂甯冩祦绋嬪吋瀹广€?
鍒涘缓 GitHub Release锛?
```bash
git tag v0.1.0
git push origin v0.1.0
```

鎺ㄩ€?`v*` tag 鍚庯紝GitHub Actions 浼氳嚜鍔ㄦ瀯寤?`BlueMapRailway-0.1.0.jar` 骞跺彂甯冨埌 GitHub Release銆?
## 閰嶇疆璇存槑

榛樿閰嶇疆妯℃澘浣嶄簬 `paper/src/main/resources/config.yml`銆?
鏃х増鏈崌绾ф椂閫氬父涓嶉渶瑕佸垹闄?`plugins/BlueMapRailway/config.yml`銆傛彃浠跺惎鍔ㄥ拰 `/railmap reload` 鏃朵細鑷姩琛ラ綈鏂扮増鏈己澶辩殑榛樿閰嶇疆椤癸紝骞朵笖涓嶄細瑕嗙洊浣犲凡缁忎慨鏀硅繃鐨勫€硷紱濡傛灉宸茬粡閰嶇疆浜?`worlds`锛屼篃涓嶄細寮鸿鎶婇粯璁?`world` 鍔犲洖鍘汇€?
```yaml
worlds:
  world:
    enabled: true
    scan-radius: -1
```

`scan-radius: -1` 琛ㄧず鍙壂鎻忚涓栫晫褰撳墠宸插姞杞界殑鍖哄潡銆傝缃负闈炶礋鏁版椂锛屼細浠ヤ笘鐣屽嚭鐢熺偣涓轰腑蹇冩壂鎻忔寚瀹氬崐寰勫唴鐨勫凡鍔犺浇鍖哄潡銆傚綋鍓嶇増鏈笉浼氫富鍔ㄥ姞杞芥湭鍔犺浇鍖哄潡锛岄伩鍏嶅惎鍔ㄦ椂閫犳垚鏈嶅姟鍣ㄥ帇鍔涖€?
```yaml
scanner:
  chunks-per-tick: 1
  update-debounce-ticks: 40
```

`chunks-per-tick` 鎺у埗姣?tick 鎵弿澶氬皯鍖哄潡銆俙update-debounce-ticks` 鎺у埗閾佽建鍙樺寲鍚庡欢杩熷涔呭悎骞惰Е鍙戦噸鎵€?
```yaml
cache:
  enabled: true
  file: cache/rail-cache.yml
  scan-newly-loaded-chunks: true
  chunk-load-debounce-ticks: 100
```

鍘嗗彶鎵弿缂撳瓨榛樿寮€鍚€傛彃浠朵細鑷姩缂撳瓨宸茬粡鎵弿鍒伴搧杞ㄧ殑鍖哄潡锛涗箣鍚庡嵆浣胯繖浜涘尯鍧椾笉鍐嶈鐜╁鍔犺浇锛屼篃浼氱户缁娇鐢ㄧ紦瀛樺弬涓?BlueMap 瑕嗙洊灞傚拰 SVG 杈撳嚭銆俙scan-newly-loaded-chunks` 寮€鍚悗锛岀帺瀹舵垨鏈嶅姟鍣ㄥ姞杞芥柊 chunk 鏃讹紝鎻掍欢浼氭妸璇?chunk 鍔犲叆寤惰繜鎵弿闃熷垪锛岀敤浜庤ˉ鍏ㄥ巻鍙茬紦瀛樸€?
```yaml
logging:
  console-info: false
  file:
    enabled: true
    path: logs/latest.log
```

甯歌 info 榛樿涓嶅埛鏈嶅姟鍣ㄥ悗鍙帮紝鑰屾槸鍐欏叆 `plugins/BlueMapRailway/logs/latest.log`銆傚彲鐢?`/railmap log [琛屾暟]` 鏌ヨ鏈€杩戞棩蹇楋紱璀﹀憡鍜岄敊璇粛浼氳繘鍏ユ帶鍒跺彴銆?
```yaml
backup:
  enabled: true
  interval-hours: 24
  directory: backups
  include-config: true
  max-files: 0
```

绾胯矾绠＄悊鐩稿叧鏁版嵁鏂囦欢閫氬父閮戒笉澶э紝閫傚悎鐩存帴鍋氳交閲忓揩鐓с€傞粯璁や細姣?24 灏忔椂鑷姩妫€鏌ユ槸鍚﹂渶瑕佸浠斤紝骞舵妸 `routes.yml`銆乣stations.yml`銆乣edits.yml` 浠ュ強鍙€夌殑 `config.yml` 鎵撴垚 zip 淇濆瓨鍒?`plugins/BlueMapRailway/backups/`銆俙max-files` 鐢ㄤ簬闄愬埗淇濈暀鐨勫巻鍙插浠芥暟閲忥紱`0` 琛ㄧず鏃犻檺淇濈暀銆傞粯璁ゅ€煎氨鏄?`0`銆?濡傞渶绔嬪嵆鐢熸垚涓€浠藉浠斤紝鍙墽琛?`/railmap backup`銆?
```yaml
admin-web:
  enabled: false
  host: 127.0.0.1
  port: 8765
  token: change-me
  background:
    image: admin-web/background.png
    world: world
    center-x: 0.0
    center-z: 0.0
    pixels-per-block: 1.0
```

绠＄悊缃戦〉榛樿鍏抽棴銆傚紑鍚悗鍙闂?`http://127.0.0.1:8765/`銆傞〉闈㈤粯璁よ繘鍏ュ彧璇绘祻瑙堟ā寮忥紝鍙樉绀哄湴鍥句互鍙婂凡鏈夌嚎璺€佺珯鐐瑰垪琛紱杈撳叆 token 鍚庢墠浼氬垏鎹㈠埌瀹屾暣绠＄悊妯″紡锛屽彲杩涜绾胯矾褰掔被銆佹閫夌嚎璺€佹暣鏉＄嚎璺殣钘忋€佹閫夌珯鐐硅寖鍥村拰淇濆瓨绾胯矾瑁佸垏瑙勫垯銆傜嚎璺鍒囪鍒欑殑鑼冨洿妗嗛粯璁や笉浼氶摵鍦ㄥ湴鍥句笂锛屽彧鏈夊湪鍙充晶鐐瑰嚮瀵瑰簲瑙勫垯鏃舵墠浼氭樉绀恒€傛彃浠跺唴缃竴寮?`-5000..5000`銆佹瘡鍍忕礌 1 鏍笺€佷腑蹇冨潗鏍?`0,0` 鐨勯粯璁ゅ簳鍥撅紱濡傛灉 `plugins/BlueMapRailway/admin-web/background.png` 瀛樺湪锛屽垯浼樺厛浣跨敤鏈嶅姟鍣ㄦ枃浠跺す閲岀殑鑷畾涔夊簳鍥撅紝涓嶄細琚唴缃簳鍥捐鐩栥€?
```yaml
routes:
  auto-match:
    enabled: true
    anchor-radius: 16.0
    min-bounds-overlap-ratio: 0.35
```

绾胯矾鑷姩寤剁画榛樿寮€鍚€傞€氳繃 `/railmap route assign-nearest` 缁戝畾绾胯矾鏃讹紝鎻掍欢浼氳褰曢敋鐐瑰拰鑼冨洿锛涗箣鍚庡鏋滅嚎璺皬骞呭欢闀垮鑷?component ID 鍙樺寲锛屼細灏濊瘯鎸夌┖闂翠綅缃嚜鍔ㄨ拷鍔犳柊鐨?component ID銆?
```yaml
stations:
  marker-set-label: 绔欑偣
  default-radius: 24.0
  default-y-radius: 6
  bounds:
    enabled: true
    color: "#fb7185"
    line-width: 2
    depth-test-enabled: false
  internal-tracks:
    label: 绔欏唴杞ㄩ亾
    default-hidden: false
```

绔欑偣鍥惧眰榛樿鏄剧ず涓?`绔欑偣`锛屽苟浼氱敾鍑虹珯鐐?box 鐨勫彲瑙嗗寲杈规銆備娇鐢?`/railmap station add` 鏃讹紝浼氫互鐜╁褰撳墠浣嶇疆涓轰腑蹇冨垱寤轰竴涓?box 鍖哄煙锛沗default-radius` 鏄粯璁ゆ按骞冲崐寰勶紝`default-y-radius` 鏄粯璁や笂涓嬮珮搴︺€傜┛杩囩珯鐐瑰尯鍩熺殑閾佽矾浼氬湪 BlueMap 娓叉煋鏃舵媶鍑虹珯鍐呭皬娈碉紝杩涘叆 `绔欏唴杞ㄩ亾` 鍥惧眰锛屼笉鍐嶆樉绀哄湪涓荤嚎鍥惧眰涓€?
```yaml
filters:
  hide-short-lines: true
  short-line-max-points: 3
  short-line-max-length: 6.0
  hide-fragmented-plain-rail-below-min-y: true
  min-y: 50
  fragmented-line-max-points: 8
  fragmented-line-max-length: 32.0
```

杩囨护鍣ㄥ垎涓ょ被锛歚hide-short-lines` 鐢ㄤ簬鍏ㄥ眬灞忚斀闆剁鐭搧璺紱`hide-fragmented-plain-rail-below-min-y` 鐢ㄤ簬灞忚斀鐤戜技搴熷純鐭夸簳鐨勫湴涓嬫櫘閫氶搧杞ㄣ€?
```yaml
export:
  svg:
    enabled: true
    directory: export
    file: railways.svg
    title: BlueMap Railway
    padding: 16.0
    non-scaling-stroke: true
```

SVG 瀵煎嚭榛樿寮€鍚€傛瘡娆℃壂鎻忓畬鎴愬悗浼氱敓鎴愶細

```text
plugins/BlueMapRailway/export/railways.svg
```

杩欐槸鍦扮悊鍨?SVG锛屼細鎸?Minecraft `x/z` 鍧愭爣鎶曞奖鍒颁簩缁村钩闈紝骞朵繚鐣欓搧杞ㄧ被鍨嬨€佷笘鐣屽悕鍜岄€氱數鐘舵€佺瓑鏁版嵁灞炴€с€?
绾胯矾鍛藉悕鍜岄厤鑹蹭綅浜庢彃浠惰繍琛岀洰褰曠敓鎴愮殑 `routes.yml`锛?
```yaml
routes:
  main-line:
    name: "涓荤嚎"
    color: "#22c55e"
    line-width: 6
    auto-match: true
    components:
      - "world:component:example"
    anchors:
      - world: "world"
        x: 120
        y: 64
        z: -30
    bounds:
      world: "world"
      min: [100, 60, -80]
      max: [900, 75, 20]
```

鍙€氳繃 `/railmap debug` 鏌ョ湅鎵弿鍒扮殑 component ID锛屽啀濉叆 `routes.yml`銆?
涔熷彲浠ョ珯鍦ㄩ搧璺梺杈规墽琛岋細

```text
/railmap route assign-nearest main-line 16
```

鎻掍欢浼氭妸鍗婂緞鍐呮渶杩戠殑 component 缁戝畾鍒版寚瀹氱嚎璺紝骞舵帓闃熼噸鎵€?
璁剧疆浜?`color` 鐨勭嚎璺細鏁翠綋鍚岃壊娓叉煋锛屼笉鍐嶆寜鏅€氶搧杞ㄣ€佸姩鍔涢搧杞ㄧ瓑绫诲瀷鎷嗛鑹层€傛湭璁剧疆 route 棰滆壊鐨勭嚎璺粛鎸夐搧杞ㄧ被鍨嬩娇鐢ㄩ粯璁ら鑹层€?
绔欑偣鍖哄煙浣嶄簬鎻掍欢杩愯鐩綍鐢熸垚鐨?`stations.yml`锛?
```yaml
stations:
  spawn:
    name: "鍑虹敓鐐圭珯"
    world: "world"
    area:
      type: box
      min: [120, 60, -30]
      max: [170, 75, 20]
```

涔熷彲浠ュ湪娓告垙鍐呯珯鍒扮珯鐐逛腑蹇冩墽琛岋細

```text
/railmap station add spawn 鍑虹敓鐐圭珯 24
```

BlueMap 涓凡鍛藉悕绾胯矾浼氫互绾胯矾鍚嶄綔涓虹嫭绔嬪浘灞傛樉绀猴紝鏈綊绫荤嚎璺繘鍏?`Railways - 鏈垎绫籤锛岀珯鐐?POI 鍜岃寖鍥磋竟妗嗚繘鍏?`绔欑偣` 鍥惧眰锛岀珯鐐硅寖鍥村唴鐨勯搧璺皬娈佃繘鍏?`绔欏唴杞ㄩ亾` 鍥惧眰銆?
## 鏂囨。

- [浣跨敤鏂囨。](docs/浣跨敤鏂囨。.md)
- [Fabric 版配置使用说明](docs/Fabric版配置使用说明.md)
- [鎶€鏈璁(docs/鎶€鏈璁?md)
- [鏈潵灞曟湜锛氱嚎璺鐞哴(docs/鏈潵灞曟湜-绾胯矾绠＄悊.md)
- [鏈潵灞曟湜锛欶abric 鏀寔](docs/鏈潵灞曟湜-Fabric鏀寔.md)
- [杩唬璁板綍](docs/杩唬璁板綍.md)

鍚庣画姣忔瀹炵幇杈冨ぇ鍔熻兘鏃讹紝閮藉簲鍚屾鏇存柊鐩稿叧鏂囨。銆?
