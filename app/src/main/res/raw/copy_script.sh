S=@SRC@
rel="Android/data/com.devolverdigital.ballxpit/files"
ok=0
for R in /storage/emulated/0 /mnt/pass_through/0/emulated/0 /mnt/user/0/emulated/0 /data/media/0; do
  D="$R/$rel"
  [ -d "$R" ] || continue
  echo "Tentando $R"
  mkdir -p "$D" || continue
  (
    cd "$S" || exit 3
    find . -type d | while IFS= read -r d; do mkdir -p "$D/$d" || exit 1; done || exit 1
    find . -type f | while IFS= read -r f; do cat "$f" > "$D/$f" || { echo "falha: $f"; exit 1; }; done || exit 1
  ) && { ok=1; echo "OK via $R"; break; }
done
if [ "$ok" != 1 ]; then
  id
  ls -ld "/storage/emulated/0/Android/data/com.devolverdigital.ballxpit/files"
  exit 1
fi
@MOVE@
