# Publikasi Kebijakan Privasi

17 November 2026. Konten publik berada di PRIVACY_POLICY.md dan privacy-policy.html.
Pengelola: Daun Teh Nusantara; pengembang: Asterfed; kontak yang diberikan pengelola:
asterinovasiteknologi@gmail.com. HTML sudah mandiri (tanpa JavaScript, font eksternal,
analitik, atau formulir pengiriman data). Belum diunggah/dipublikasikan oleh agen.

## GitHub Pages

1. Buat repositori khusus untuk dokumen publik, misalnya daun-teh-nusantara-privacy.
   Tidak perlu memublikasikan kode POS atau dokumen operasional/internal.
2. Upload docs/privacy-policy.html dari proyek ini ke ROOT repositori tersebut,
   dengan nama tetap privacy-policy.html. Markdown dapat disertakan sebagai sumber.
3. Buka Settings → Pages → Deploy from a branch; pilih main dan /(root), lalu Save.
4. Setelah deployment selesai, buka URL publik HTTPS:
   https://USERNAME.github.io/NAMA-REPO/privacy-policy.html
5. Uji dari mode incognito/perangkat yang tidak login GitHub. Pastikan halaman
   berhasil dibaca, tautan email benar, serta bagian penghapusan mudah ditemukan.
   Gunakan URL GitHub Pages, bukan halaman kode github.com/.../blob/... atau URL raw.
6. Masukkan URL tersebut ke Privacy Policy di Play Console dan tambahkan akses
   Kebijakan Privasi di dalam aplikasi. URL final belum diberikan, sehingga versi
   aplikasi saat ini belum diubah untuk membuka halaman tersebut.

GitHub Pages project site dapat menerbitkan HTML statis tanpa generator situs baru.
Panduan: https://docs.github.com/en/pages/quickstart

## Pemeriksaan isi sebelum submit

- Nama aplikasi/developer harus cocok dengan listing Play Store dan pengelola sebenarnya.
- Pastikan email benar-benar dipantau untuk pertanyaan dan permintaan privasi.
- Tinjau praktik produksi nyata: akses dukungan pengembang, wilayah Supabase, logging
  infrastruktur, cadangan dan retensi. Dokumen tidak mengarang lokasi server atau
  jangka retensi tertentu; versi saat ini menyatakan transaksi/audit tidak otomatis
  dihapus. Tetapkan SOP penanganan permintaan, bukan sekadar alamat email di halaman.
- Perubahan foto saat ini merupakan tombstone: berkas Storage lama tidak otomatis
  dihapus. Jangan menjanjikan bahwa tombol hapus foto menghapus semua salinan.
- Aplikasi hanya menyediakan teks tes printer saat ini. Jangan mengklaim sudah
  mencetak struk pelanggan melalui Bluetooth secara otomatis.
- Logout, nonaktifkan keanggotaan dan penghapusan akun/data adalah hal berbeda.
  Penutupan akun dapat memerlukan penanganan referensi transaksi/audit dan data
  penyedia oleh administrator; tidak ada penghapusan akun otomatis dalam aplikasi.

## Google Play dan penghapusan akun

Google Play memerlukan URL kebijakan yang aktif/publik dan akses kebijakan di aplikasi.
Saat ini aplikasi belum memiliki tautan kebijakan privasi di Setelan. Jangan menandai
pekerjaan integrasi tersebut selesai hanya karena file HTML sudah dibuat.

Aplikasi mempunyai fitur Owner/Manager membuat akun staf, sehingga deklarasi akun
harus ditinjau berdasarkan alur nyata, bukan dijawab “tidak ada akun” karena tidak
ada pendaftaran publik. Jika persyaratan penghapusan berlaku pada distribusi aplikasi,
siapkan jalur permintaan dari dalam aplikasi dan dari web serta proses penghapusan
sebenarnya. Label “brand privat” tidak otomatis membuktikan pengecualian aplikasi
permanently private/enterprise yang dijelaskan Google. Tidak ada klaim pengecualian
atau kelulusan review Play Store dalam dokumen ini.

Bagian web permintaan berada pada:
https://USERNAME.github.io/NAMA-REPO/privacy-policy.html#penghapusan-data
Bagian ini memuat kontak email dan informasi permintaan tanpa wajib memasang ulang
aplikasi, tetapi tautan dari aplikasi dan SOP pemrosesan tetap pekerjaan terpisah.

Isi Data Safety sesuai build/operasi yang benar-benar dirilis. Tinjau data akun,
identitas perangkat aplikasi, aktivitas/audit, data transaksi dan unggahan foto.
Bedakan pemrosesan lokal, pengiriman ke server, penyedia layanan, serta ekspor yang
dipilih pengguna menurut definisi Google. Jangan mengisi “tidak mengumpulkan data”
untuk aplikasi yang mengirim akun/transaksi/perangkat/foto ke backend.

Referensi resmi, diperiksa 17 November 2026:
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/9859455
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://support.google.com/googleplay/android-developer/answer/10787469

## Pemeliharaan

Markdown dan HTML memuat isi kebijakan yang sama; perbarui keduanya serta tanggal
pembaruan jika praktik berubah. Tidak ada backend/form baru yang dibuat untuk halaman
ini. Membuat file tidak menerbitkan GitHub Pages, mengirim email, atau mengubah
Play Console. Tinjau kembali sebelum menambahkan SDK, analytics, pembayaran gateway,
fitur cetak struk, koleksi data baru, atau penghapusan akun otomatis.
