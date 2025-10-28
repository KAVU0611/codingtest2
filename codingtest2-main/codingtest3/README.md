# codingtest3 (Java Playlist App)

機能
- プレイリスト全件の曲一覧表示（標準: アルバム名→曲順）
- アルバム名/アーティスト名/曲名プレフィックスでの絞り込み
- 演奏時間での並び替え（オプション）
- アルバムの新規追加（CUI対話）
- 簡易Web UI（ブラウザで一覧・絞り込み）

構成
- CLI: `com.example.playlist.PlaylistApp`
- Web: `com.example.playlist.WebServer` (ポート `8080`)
- 既定のプレイリストフォルダ: 実行ディレクトリの `playlist`。`--playlist` で変更可能。

ビルド（javac）
```
# Windows PowerShell 例
$root = "codingtest3"
$classes = "$root/.run/classes"
New-Item -ItemType Directory -Force $classes | Out-Null
$files = Get-ChildItem -Recurse -Filter *.java "$root/src/main/java" | % { $_.FullName }
javac -encoding UTF-8 -d $classes $files
```

実行例（一覧表示）
```
# 指定フォルダのTSVを表示
java -cp codingtest3/.run/classes com.example.playlist.PlaylistApp list --playlist "C:\\Users\\kavu1\\Downloads\\codingtest\\codingtest\\playlist\\playlist"
# フィルタ
... --album "Oasis" --artist "Jackson 5" --title-prefix "I "
# 並び替え（演奏時間）
... --sort duration
# 再帰読み込み
... --recursive
```

実行例（アルバム追加）
```
java -cp codingtest3/.run/classes com.example.playlist.PlaylistApp add-album --playlist "<保存先フォルダ>" --name "<アルバム名>"
```

Webサーバ（任意）
```
java -cp codingtest3/.run/classes com.example.playlist.WebServer
# ブラウザでアクセス: http://localhost:8080
# 右上のPlaylist dirにプレイリストのパスを入力してReload
```

GitHub へのプッシュ（codingtest3 リポジトリ）
```
cd codingtest3
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/<your-account>/codingtest3.git
git push -u origin main
```
