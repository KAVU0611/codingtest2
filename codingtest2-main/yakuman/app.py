import json

HTML = """
<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>役満 ランキング（2択で決める）</title>
  <style>
    :root { --bg:#0b0d10; --fg:#e8eef2; --muted:#9fb3c8; --card:#13171c; --accent:#5dd3ff; }
    html,body { margin:0; height:100%; }
    body { background: var(--bg); color: var(--fg); font-family: system-ui, -apple-system, Segoe UI, Roboto, "Hiragino Kaku Gothic ProN", Meiryo, sans-serif; }
    .container { max-width: 1000px; margin: 24px auto; padding: 0 16px; }
    h1 { font-size: 22px; margin: 0 0 12px; }
    p { color: var(--muted); margin: 8px 0 16px; }
    .board { display: grid; grid-template-columns: 1fr 120px 1fr; gap: 12px; align-items: start; }
    .card { background: var(--card); border: 1px solid #1d232b; border-radius: 10px; padding: 16px; min-height: 160px; display: flex; flex-direction: column; justify-content: space-between; }
    .name { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
    .desc { color: var(--muted); font-size: 14px; }
    .center { text-align:center; color: var(--muted); display:flex; align-items:center; justify-content:center; height:100%; }
    .final { grid-column: 1 / -1; background: var(--card); border: 1px solid #1d232b; border-radius: 10px; padding: 16px; }
    .actions { display:flex; gap: 8px; justify-content: center; margin-top:12px; }
    button { background:#1a222c; color: var(--fg); border:1px solid #273242; border-radius: 8px; padding: 10px 14px; cursor:pointer; font-size:14px; }
    button.primary { background: #193243; border-color:#2c5369; color:#cdefff; }
    button:hover { filter: brightness(1.05); }
    .layout { display:grid; grid-template-columns: 2fr 1fr; gap:16px; margin-top: 16px; }
    .panel { background: var(--card); border: 1px solid #1d232b; border-radius: 10px; padding: 14px; }
    .panel h2 { font-size: 16px; margin:0 0 8px; }
    .list { display:grid; gap: 6px; }
    .row { display:flex; justify-content: space-between; gap:8px; font-size: 14px; }
    .muted { color: var(--muted); }
    .controls { display:flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
    textarea { width: 100%; min-height: 90px; background:#0f141a; color:var(--fg); border:1px solid #273242; border-radius:8px; padding:8px; }
    @media (max-width: 860px) { .layout { grid-template-columns: 1fr; } .board { grid-template-columns: 1fr; } .vs{ display:none; } }
  </style>
</head>
<body>
  <div class="container">
    <h1>役満 ランキング（2択で決める）</h1>
    <p>2つの役満から「強い/価値が高い」と感じる方を選んでいくと、Elo方式で総合ランキングができます。各ユーザー/セッションごとに独立し、タブ/ウィンドウを閉じると結果はリセットされます。</p>

    <div class="layout">
      <div>
        <div class="board">
          <div class="card" id="leftCard">
            <div>
              <div class="name" id="leftName"></div>
              <div class="desc" id="leftDesc"></div>
            </div>
          </div>
          <div class="center" id="center">VS</div>
          <div class="card" id="rightCard">
            <div>
              <div class="name" id="rightName"></div>
              <div class="desc" id="rightDesc"></div>
            </div>
          </div>
          <div id="final" class="final" style="display:none"></div>
        </div>
        <div class="actions" id="actions">
          <button id="pickLeft" class="primary">左が上</button>
          <button id="pickRight" class="primary">右が上</button>
        </div>
      </div>

      <div class="panel">
        <h2>進捗と上位</h2>
        <div class="row"><span class="muted">比較回数</span><span id="count">0</span></div>
        <div class="row"><span class="muted">K係数</span><span id="kval">24</span></div>
        <div class="row"><span class="muted">候補の選び方</span><span id="pairMode">バランス</span></div>
        <div style="height:8px"></div>
        <div class="list" id="topList"></div>
        <div style="height:10px"></div>
        <div class="controls">
          <button id="reset">リセット</button>
          <button id="export">エクスポート</button>
          <button id="import">インポート</button>
        </div>
        <div style="height:8px"></div>
        <textarea id="io" placeholder="ここにJSONを出力/貼り付け"></textarea>
      </div>
    </div>
  </div>

  <script>
    const YAKUMAN = [
      { id:'kokushi', name:'国士無双', desc:'老頭牌と字牌の十三面子（1・9と字牌1枚ずつ+1枚）' },
      { id:'suanko', name:'四暗刻', desc:'暗刻4つと面子1つ（単騎待ちは倍役満ルールも）' },
      { id:'daisangen', name:'大三元', desc:'白・發・中の三元牌すべてを刻子/槓子で揃える' },
      { id:'shosushi', name:'小四喜', desc:'風牌4種のうち3つを刻子/槓子 + 残り1つを雀頭' },
      { id:'daisushi', name:'大四喜', desc:'風牌4種すべてを刻子/槓子で揃える' },
      { id:'tsuiso', name:'字一色', desc:'手牌が字牌のみで構成' },
      { id:'ryuiso', name:'緑一色', desc:'索子の緑色牌のみで構成（23468索と發）' },
      { id:'chinroto', name:'清老頭', desc:'1と9のみで構成' },
      { id:'churen', name:'九蓮宝燈', desc:'同一色で1112345678999 + いずれか1枚' },
      { id:'sukantsu', name:'四槓子', desc:'槓子を4つ作る' },
      { id:'tenhou', name:'天和', desc:'親の第一ツモで和了' },
      { id:'chihou', name:'地和', desc:'子の第一ツモで和了' },
      { id:'renhou', name:'人和', desc:'子の第一巡での他家の捨て牌で和了（採用ルール依存）' },
      { id:'daisuishi', name:'大車輪（ローカル）', desc:'数牌2-8索の順子のみ（ローカル役満）' }
    ];

    // Persistent state
    const KEY = 'yakuman-elo-state-v2';
    function allPairs(items){
      const pairs = [];
      for (let i=0;i<items.length;i++){
        for (let j=i+1;j<items.length;j++) pairs.push([items[i], items[j]]);
      }
      // shuffle
      for (let i=pairs.length-1;i>0;i--){ const j=Math.floor(Math.random()*(i+1)); [pairs[i],pairs[j]]=[pairs[j],pairs[i]]; }
      return pairs;
    }
    function loadState() {
      try {
        const s = JSON.parse(sessionStorage.getItem(KEY));
        if (s && s.items && s.ratings && Array.isArray(s.pairs)) return s;
      } catch {}
      const items = YAKUMAN.map(x => x.id);
      const ratings = Object.fromEntries(items.map(id => [id, 1500]));
      const games = Object.fromEntries(items.map(id => [id, 0]));
      const pairs = allPairs(items);
      return { items, ratings, games, count: 0, k: 24, pairs, pairIndex: 0, done:false };
    }
    function saveState() { sessionStorage.setItem(KEY, JSON.stringify(state)); }

    let state = loadState();

    function expected(ra, rb) {
      return 1 / (1 + Math.pow(10, (rb - ra) / 400));
    }
    function updateElo(a, b, result, k) {
      const ra = state.ratings[a];
      const rb = state.ratings[b];
      const ea = expected(ra, rb);
      const eb = 1 - ea;
      let sa = 0.5, sb = 0.5;
      if (result === 'A') { sa = 1; sb = 0; }
      else if (result === 'B') { sa = 0; sb = 1; }
      state.ratings[a] = ra + k * (sa - ea);
      state.ratings[b] = rb + k * (sb - eb);
      state.games[a] = (state.games[a]||0) + 1;
      state.games[b] = (state.games[b]||0) + 1;
      state.count += 1;
    }

    function remainingPairs() { return state.pairs.length - (state.pairIndex||0); }

    let current = null;
    function setPair(pair) {
      current = pair;
      const [a,b] = pair;
      const A = YAKUMAN.find(x => x.id === a);
      const B = YAKUMAN.find(x => x.id === b);
      document.getElementById('leftName').textContent = A.name;
      document.getElementById('leftDesc').textContent = A.desc;
      document.getElementById('rightName').textContent = B.name;
      document.getElementById('rightDesc').textContent = B.desc;
      document.getElementById('center').textContent = `VS\n(${state.pairIndex+1}/${state.pairs.length})`;
    }

    function topList(n=10) {
      const arr = state.items.map(id => ({ id, r: state.ratings[id], g: state.games[id]||0 }));
      arr.sort((x,y) => y.r - x.r);
      return arr.slice(0, n);
    }

    function renderStats() {
      document.getElementById('count').textContent = String(state.count);
      document.getElementById('kval').textContent = String(state.k);
      document.getElementById('pairMode').textContent = '全組合せ方式';
      const list = document.getElementById('topList');
      list.innerHTML = '';
      const top = topList(12);
      top.forEach((t, i) => {
        const yak = YAKUMAN.find(x => x.id === t.id);
        const row = document.createElement('div');
        row.className = 'row';
        const left = document.createElement('div');
        left.textContent = `${i+1}. ${yak.name}`;
        const right = document.createElement('div');
        right.textContent = `${Math.round(t.r)}  (${t.g})`;
        right.className = 'muted';
        row.appendChild(left); row.appendChild(right);
        list.appendChild(row);
      });
    }

    function showFinal() {
      state.done = true; saveState();
      const final = document.getElementById('final');
      const center = document.getElementById('center');
      const actions = document.getElementById('actions');
      document.getElementById('leftCard').style.display = 'none';
      document.getElementById('rightCard').style.display = 'none';
      actions.style.display = 'none';
      center.style.display = 'none';
      const all = state.items.map(id => ({ id, r: state.ratings[id], g: state.games[id]||0 }))
        .sort((x,y)=>y.r-x.r);
      const rows = all.map((t,i)=>{
        const y = YAKUMAN.find(x=>x.id===t.id);
        return `<div class="row"><div>${i+1}. ${y.name}</div><div class="muted">${Math.round(t.r)} (${t.g})</div></div>`;
      }).join('');
      final.innerHTML = `<h2 style="margin-top:0">最終結果</h2><div class="list">${rows}</div>`;
      final.style.display = 'block';
    }

    function nextRound() {
      if ((state.pairIndex||0) >= state.pairs.length) { showFinal(); return; }
      setPair(state.pairs[state.pairIndex]);
      renderStats();
    }

    function recordResult(choice) {
      const [a,b] = current;
      updateElo(a,b, choice==='left' ? 'A' : 'B', state.k);
      state.pairIndex = (state.pairIndex||0) + 1;
      saveState();
      nextRound();
    }
    document.getElementById('pickLeft').addEventListener('click', () => recordResult('left'));
    document.getElementById('pickRight').addEventListener('click', () => recordResult('right'));

    document.getElementById('reset').addEventListener('click', () => {
      if (!confirm('すべての結果をリセットしますか？')) return;
      sessionStorage.removeItem(KEY); state = loadState();
      // restore UI
      document.getElementById('leftCard').style.display = '';
      document.getElementById('rightCard').style.display = '';
      document.getElementById('actions').style.display = '';
      document.getElementById('center').style.display = '';
      document.getElementById('final').style.display = 'none';
      nextRound();
    });
    document.getElementById('export').addEventListener('click', () => {
      const payload = { ratings: state.ratings, games: state.games, count: state.count, k: state.k };
      document.getElementById('io').value = JSON.stringify(payload, null, 2);
    });
    document.getElementById('import').addEventListener('click', () => {
      try {
        const t = document.getElementById('io').value.trim();
        const d = JSON.parse(t);
        if (!d || !d.ratings) throw new Error('形式が不正です');
        state.ratings = { ...state.ratings, ...d.ratings };
        state.games = { ...state.games, ...d.games };
        state.count = d.count || state.count; state.k = d.k || state.k;
        if (Array.isArray(d.pairs)) state.pairs = d.pairs;
        if (typeof d.pairIndex === 'number') state.pairIndex = d.pairIndex;
        saveState();
        nextRound();
      } catch (e) { alert('インポート失敗: ' + e.message); }
    });

    if (state.done) { showFinal(); } else { nextRound(); }
  </script>
</body>
</html>
"""


def lambda_handler(event, context):
    return {
        "statusCode": 200,
        "headers": {"Content-Type": "text/html; charset=utf-8", "Access-Control-Allow-Origin": "*"},
        "body": HTML,
    }
