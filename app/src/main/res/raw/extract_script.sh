D=@DEST@
rel="Android/data/com.devolverdigital.ballxpit/files"
ok=0
mkdir -p "$D" || { echo "Não foi possível criar $D"; exit 2; }
for R in /storage/emulated/0 /mnt/pass_through/0/emulated/0 /mnt/user/0/emulated/0 /data/media/0; do
  S="$R/$rel"
  [ -d "$S" ] || continue
  echo "Tentando $S"
  (
    cd "$S" || exit 3
    find . -type d | while IFS= read -r d; do mkdir -p "$D/$d" || exit 1; done || exit 1
    find . -type f | while IFS= read -r f; do cat "$f" > "$D/$f" || { echo "falha: $f"; exit 1; }; done || exit 1
  ) && { ok=1; echo "OK via $R"; break; }
done
if [ "$ok" != 1 ]; then
  id
  ls -ld "/storage/emulated/0/$rel"
  exit 1
fi
@MOVE@
