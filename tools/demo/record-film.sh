#!/usr/bin/env bash
# 시안으로 만든 주행 재현 영상(차 안 시점)을 만든다. 사용: tools/demo/record-film.sh <출력.mp4> [fps]
# 필요: Google Chrome(WebGL, macOS Metal), node 22 이상, ffmpeg, macOS say(한국어 음성 Yuna), 인터넷(three.js 를 jsdelivr 에서 받음).
# 실제 앱 화면이 아니라 시안과 연출이다. 센터 화면 바탕은 pleos/ 의 에뮬레이터 캡처다.
set -eu
out="${1:?출력 파일이 필요합니다}"
fps="${2:-30}"
cd "$(dirname "$0")/../.."
work=$(mktemp -d)
python3 -m http.server 8799 >/dev/null 2>&1 &
server=$!
trap 'kill $server 2>/dev/null; rm -rf "$work"' EXIT
sleep 1
node tools/demo/record-film.mjs http://127.0.0.1:8799 "$work" "$fps"

# 음성: 재현 화면의 VOICE 시각에 맞춰 앱 문구를 합성해 겹친다
inputs=(); filters=""; n=0
while IFS=$'\t' read -r at text; do
  say -v Yuna -r 190 -o "$work/v$n.aiff" "$text"
  inputs+=(-i "$work/v$n.aiff")
  ms=$(python3 -c "print(int(float('$at') * 1000))")
  filters+="[$((n + 1)):a]adelay=${ms}|${ms}[a$n];"
  n=$((n + 1))
done < <(python3 -c "import json,sys; [print(f'{a}\t{t}') for a,t in json.load(open('$work/voice.json'))]")
mix=$(python3 -c "print(''.join(f'[a{i}]' for i in range($n)))")
ffmpeg -y -loglevel error -framerate "$fps" -i "$work/frames/f%05d.jpg" "${inputs[@]}" \
  -filter_complex "${filters}${mix}amix=inputs=$n:normalize=0,apad[aout]" \
  -map 0:v -map "[aout]" -c:v libx264 -pix_fmt yuv420p -crf 18 -c:a aac -b:a 160k -t "$(cat "$work/end.txt")" "$out"
echo "$out"
