# VeyrionID 1.1.0

Plugin Paper 1.21.1 untuk Kartu Identitas Veyrion, dengan tampilan kartu di layar (HUD) dan item kartu fisik lewat **ItemsAdder**.

## Pasang
1. `mvn package` -> taruh `target/VeyrionID-1.1.0.jar` di `plugins/` (Java 21).
2. Salin folder `itemsadder/contents/veyrion` ke `plugins/ItemsAdder/contents/`.
3. Jalankan `/iazip` (atau restart) supaya resource pack (font kartu + tekstur item) ter-generate.
4. Jalankan `/ki reload`.

## Perintah
| Perintah | Siapa | Fungsi |
|---|---|---|
| `/ki daftar <pemain> <nama> <umur> <DD/MM/YYYY> <ras>` | OP / trusted | Daftarkan pemain & beri kartu fisik (`_` = spasi) |
| `/ki trust <pemain>` | OP | Izinkan pemain mendaftarkan orang lain |
| `/ki untrust <pemain>` | OP | Cabut izin |
| `/ki trustlist` | OP | Daftar pemain trusted |
| `/ki kartu <pemain>` | OP / trusted | Beri ulang kartu fisik |
| `/ki lihat [pemain]` | semua (pemain lain: OP) | Tampilkan kartu di layar |
| `/ki tunjuk` | semua | Tunjukkan kartu ke pemain terdekat |
| `/ki hapus <pemain>` / `/ki reload` | OP | Hapus data / muat ulang config |

Contoh: `/ki daftar Steve Kynaro_Karamazov 17 11/01/2009 Elf`

Interaksi: klik kanan kartu = lihat; klik kanan pemain sambil pegang kartu = tunjukkan. **Sneak** menutup tampilan kartu.

## Mengubah posisi / ukuran kartu
- Horizontal: `hud.offset-x` di `config.yml`.
- Vertikal / ukuran: ubah konstanta di atas `tools/generate_assets.py`, jalankan ulang, lalu `/iazip`.
  `python3 tools/generate_assets.py Kartu_Identitas_Veyrion.png itemsadder/contents`
- Di HUD nama dipotong 24 karakter & ditampilkan huruf kapital (font pixel hanya ASCII).
