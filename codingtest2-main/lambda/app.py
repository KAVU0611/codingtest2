import json
import html

HTML_PAGE = """
<!doctype html>
<html lang="ja">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>電卓 (Lambda Function URL)</title>
  <style>
    body { font-family: system-ui, sans-serif; margin: 40px; }
    .wrap { max-width: 420px; }
    label { display:block; margin-top: 12px; }
    input, select, button { font-size: 16px; padding: 8px; }
    .row { display:flex; gap: 8px; align-items: center; }
    .row > * { flex: 1; }
    .result { margin-top: 16px; font-weight: bold; }
    .note { color: #555; margin-top: 8px; font-size: 12px; }
  </style>
</head>
<body>
  <div class="wrap">
    <h1>電卓</h1>
    <div class="row">
      <input id="a" type="number" placeholder="A" />
      <select id="op">
        <option value="add">+</option>
        <option value="sub">-</option>
        <option value="mul">×</option>
        <option value="div">÷</option>
      </select>
      <input id="b" type="number" placeholder="B" />
    </div>
    <div class="row" style="margin-top: 12px;">
      <button id="calc">計算</button>
      <button id="clear">クリア</button>
    </div>
    <div id="out" class="result"></div>
    <div class="note">このページは AWS Lambda Function URL から配信され、計算は同じ URL に対してクエリで行います。</div>
  </div>
  <script>
    const out = document.getElementById('out');
    document.getElementById('calc').addEventListener('click', async () => {
      const a = document.getElementById('a').value;
      const b = document.getElementById('b').value;
      const op = document.getElementById('op').value;
      out.textContent = '計算中...';
      try {
        const url = new URL(window.location.href);
        url.search = new URLSearchParams({ a, b, op }).toString();
        const res = await fetch(url.toString(), { headers: { 'Accept': 'application/json' } });
        const data = await res.json();
        if (!res.ok || data.error) throw new Error(data.error || '計算エラー');
        out.textContent = `${data.expression} = ${data.result}`;
      } catch (e) {
        out.textContent = 'エラー: ' + e.message;
      }
    });
    document.getElementById('clear').addEventListener('click', () => {
      document.getElementById('a').value = '';
      document.getElementById('b').value = '';
      out.textContent = '';
      history.replaceState(null, '', window.location.pathname);
    });
  </script>
</body>
</html>
"""


def _calc(a, b, op):
    if op == 'add':
        return a + b, f"{a} + {b}"
    if op == 'sub':
        return a - b, f"{a} - {b}"
    if op == 'mul':
        return a * b, f"{a} * {b}"
    if op == 'div':
        if b == 0:
            raise ValueError('division by zero')
        return a / b, f"{a} / {b}"
    raise ValueError('unsupported operation')


def _resp(status, body, content_type='application/json'):
    headers = {
        'Content-Type': content_type,
        'Access-Control-Allow-Origin': '*',
    }
    if content_type == 'text/html; charset=utf-8':
        return { 'statusCode': status, 'headers': headers, 'body': body }
    return { 'statusCode': status, 'headers': headers, 'body': json.dumps(body) }


def lambda_handler(event, context):
    # If called without params (e.g., browser GET), serve the HTML page.
    params = (event or {}).get('queryStringParameters') or {}
    method = (event or {}).get('requestContext', {}).get('http', {}).get('method', 'GET')
    accept = ''
    for h in ('headers','multiValueHeaders'):
        if h in (event or {}):
            headers = event[h] or {}
            accept = headers.get('accept') or headers.get('Accept') or accept
    if method == 'GET' and not params:
        return _resp(200, HTML_PAGE, 'text/html; charset=utf-8')

    # If parameters provided, try to calculate
    try:
        a = float(params.get('a'))
        b = float(params.get('b'))
        op = params.get('op')
        result, expr = _calc(a, b, op)
        body = { 'ok': True, 'result': result, 'expression': expr }
        return _resp(200, body)
    except (TypeError, ValueError) as e:
        return _resp(400, { 'ok': False, 'error': str(e) })
    except Exception as e:
        return _resp(500, { 'ok': False, 'error': 'internal error' })
