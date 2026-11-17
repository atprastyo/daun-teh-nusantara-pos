# Kebijakan Privasi

Terakhir diperbarui: **17 November 2026**

Aplikasi: **Daun Teh Nusantara POS (Daun Teh Nusantara)**

[Permintaan penghapusan akun dan data](#penghapusan-data)

<a id="tentang"></a>

## 1. Tentang aplikasi dan pengelola

Aplikasi Daun Teh Nusantara POS (“Aplikasi”) digunakan untuk operasional penjualan dan pengelolaan outlet Daun Teh Nusantara. Aplikasi dikelola oleh Daun Teh Nusantara dan dikembangkan oleh Asterfed.

Kebijakan ini menjelaskan data yang diproses melalui Aplikasi, tujuan penggunaannya, pihak yang dapat mengaksesnya, serta cara menghubungi pengelola mengenai privasi. Akses ditujukan kepada Owner, Manager, Cashier, dan pihak lain yang diberi kewenangan oleh pengelola; Aplikasi bukan layanan pendaftaran usaha publik.

<a id="data"></a>

## 2. Data yang diproses

- Data akun dan akses: nama, alamat email, identitas akun, peran, penugasan merek/outlet, serta status akses. Akun dapat disiapkan oleh administrator atau melalui fitur pengelolaan staf sesuai kewenangan.

- Data autentikasi: kata sandi yang Anda masukkan diproses untuk masuk, membuat akun staf, atau mengganti kata sandi melalui layanan autentikasi. Kata sandi tidak disimpan sebagai teks biasa di penyimpanan lokal atau tabel aplikasi. Token sesi dan kredensial pendaftaran perangkat dipakai untuk mempertahankan akses yang sah.

- Data perangkat: identitas perangkat yang dibuat Aplikasi, produsen/model perangkat untuk peninjauan persetujuan, status pendaftaran/persetujuan, serta informasi keamanan dan waktu yang diperlukan untuk membatasi akses offline. Identitas tersebut berbeda dari IMEI atau nomor seri perangkat.

- Data operasional: produk dan pilihan tambahan, kuantitas, harga, waktu transaksi, outlet, identitas petugas, jumlah pembayaran tunai atau QRIS manual, uang diterima/kembalian, struk, dan status sinkronisasi. Aplikasi tidak meminta nomor kartu pembayaran, PIN bank, atau kredensial rekening untuk pencatatan pembayaran ini.

- Foto produk: gambar yang Anda pilih dan unggah untuk katalog. Pilih gambar yang memang diperlukan untuk operasional dan hindari menyertakan data pribadi yang tidak relevan.

- Riwayat aktivitas: tindakan pengelolaan katalog, akses staf, perangkat, dan alasan perubahan yang dicatat untuk audit serta penelusuran operasional.

- Pengaturan lokal: preferensi tampilan dan pilihan printer. Saat fitur printer digunakan, Aplikasi mengakses nama/alamat perangkat Bluetooth yang telah dipasangkan. Pilihan printer disimpan secara lokal untuk akun dan outlet terkait.

- Komunikasi dukungan: alamat email, isi pesan, dan informasi yang Anda berikan ketika meminta bantuan atau mengajukan permintaan privasi. Jangan mengirim kata sandi, PIN, kode autentikasi, atau token akses melalui email.

Koneksi ke layanan internet juga mengirimkan informasi teknis jaringan seperti alamat IP. Penyedia infrastruktur dapat memproses metadata permintaan untuk menjalankan dan mengamankan layanan. Hal ini tidak berarti Aplikasi mengakses lokasi GPS Anda.

<a id="tujuan"></a>

## 3. Tujuan penggunaan data

- Mengautentikasi pengguna dan menerapkan kewenangan sesuai peran serta outlet.

- Meninjau dan menyetujui perangkat, menjaga keamanan akun, serta membatasi akses offline.

- Mencatat penjualan, menampilkan struk/riwayat, menyinkronkan data, dan menyusun laporan usaha.

- Mengelola katalog, foto produk, staf, dan pengaturan operasional.

- Menyediakan ekspor data, pemulihan operasional, penelusuran masalah, dan dukungan pengguna.

- Menangani permintaan privasi, penyalahgunaan, sengketa, dan kewajiban yang berlaku.

Aplikasi tidak menampilkan iklan atau menggunakan SDK pelacakan iklan pihak ketiga. Data tidak digunakan oleh Aplikasi untuk iklan yang dipersonalisasi atau disediakan kepada jaringan periklanan.

<a id="penyimpanan"></a>

## 4. Penyimpanan lokal, server, dan akses offline

Data operasional tertentu disimpan pada perangkat agar transaksi dan riwayat tetap dapat digunakan ketika koneksi terbatas, dengan izin akses offline yang masih berlaku. Data yang perlu dibagikan antarperangkat dikirim ke server saat sinkronisasi dapat dilakukan, termasuk melalui pekerjaan latar belakang.

Layanan Supabase digunakan untuk autentikasi, basis data, penyimpanan foto produk, dan fungsi server. Lokasi infrastruktur mengikuti wilayah proyek serta layanan pendukung yang dipilih pengelola atau digunakan penyedia. Pemrosesan dapat melibatkan infrastruktur di luar Indonesia.

Keluar dari akun mengakhiri izin masuk dan akses offline lokal, tetapi tidak otomatis menghapus struk, antrean transaksi, data usaha, atau seluruh data pendaftaran perangkat. Menghapus aplikasi/data perangkat juga tidak otomatis menghapus data yang sudah tersimpan di server.

<a id="akses-pihak-lain"></a>

## 5. Akses dan pengungkapan kepada pihak lain

- Owner, Manager, atau administrator yang berwenang dapat mengakses data sesuai peran dan penugasan operasional. Data tidak menjadi informasi publik hanya karena disimpan di server.

- Penyedia layanan seperti Supabase memproses data untuk menyediakan fungsi autentikasi, penyimpanan, dan layanan server. Pihak yang ditugaskan pengelola untuk dukungan/pemeliharaan dapat menerima akses terbatas sesuai kebutuhan tugasnya.

- Jika Anda mengekspor laporan atau arsip melalui fitur Aplikasi, data dikirim ke lokasi atau penyedia penyimpanan yang Anda pilih. Berkas dapat memuat data usaha dan identitas terkait; pembagian selanjutnya bergantung pada pilihan serta pengamanan tujuan tersebut. Berkas ekspor tidak otomatis dienkripsi oleh Aplikasi.

- Informasi dapat diungkapkan bila diperlukan untuk memenuhi kewajiban yang berlaku atau menangani permintaan yang sah dari pihak berwenang.

Halaman kebijakan ini dapat dihosting di GitHub Pages. GitHub dapat memproses metadata akses halaman menurut kebijakannya; hosting halaman kebijakan tidak berarti basis data transaksi Aplikasi disimpan di GitHub. Permintaan melalui email juga diproses oleh penyedia layanan email yang digunakan.

Informasi penyedia tersedia di Kebijakan Privasi Supabase (https://supabase.com/privacy) dan Pernyataan Privasi GitHub (https://docs.github.com/en/site-policy/privacy-policies/github-general-privacy-statement). Kebijakan ini tetap menjelaskan pengelolaan data Aplikasi oleh Daun Teh Nusantara.

<a id="izin"></a>

## 6. Izin dan fitur perangkat

- Internet dan status jaringan digunakan untuk autentikasi, sinkronisasi, dan akses layanan server.

- Pemilih foto/berkas Android digunakan ketika Anda memilih foto produk. Fitur ini mengakses berkas yang Anda pilih, bukan seluruh galeri. Gambar diproses menjadi salinan foto produk; metadata EXIF dari sumber tidak diteruskan melalui hasil pemrosesan tersebut.

- Bluetooth/Perangkat di Sekitar digunakan atas tindakan Anda untuk menampilkan perangkat berpasangan dan menjalankan tes printer. Versi saat ini menyediakan tes teks printer, bukan pengiriman otomatis struk penjualan ke printer.

- Verifikasi kunci perangkat ditangani oleh Android. Aplikasi menerima hasil verifikasi, bukan PIN, pola, atau kata sandi kunci layar Anda.

Aplikasi tidak meminta izin lokasi GPS, kontak, mikrofon, atau SMS, dan tidak mengumpulkan IMEI, nomor seri ponsel, atau Advertising ID melalui fitur yang dijelaskan di sini. Anda dapat mengelola izin melalui pengaturan Android; penolakan izin dapat membatasi fitur terkait.

<a id="keamanan"></a>

## 7. Perlindungan data

Komunikasi dengan server menggunakan HTTPS. Hak akses diperiksa melalui autentikasi, peran/outlet, dan persetujuan perangkat. Penyimpanan lokal berada dalam ruang aplikasi yang dikelola Android; token sesi dan kredensial pendaftaran perangkat dilindungi menggunakan enkripsi berbasis Android Keystore.

Perlindungan tersebut tidak sama dengan jaminan bahwa seluruh basis data lokal atau berkas ekspor dienkripsi oleh Aplikasi. Pengguna dan pengelola perlu menjaga perangkat, akun, serta salinan ekspor dari akses yang tidak berwenang.

<a id="retensi"></a>

## 8. Retensi data

Data akun dan akses disimpan selama diperlukan untuk penyediaan akses, pengelolaan staf, keamanan, dan penanganan permintaan pengguna. Transaksi serta catatan audit tidak memiliki penghapusan otomatis dalam versi Aplikasi ini dan dapat tetap dipertahankan setelah akses akun dinonaktifkan untuk pembukuan, penelusuran operasional, penyelesaian sengketa, atau kewajiban yang berlaku.

Penghapusan foto dari tampilan katalog tidak otomatis menghapus seluruh salinan berkas terdahulu di penyimpanan. Jika cadangan dibuat, retensi serta penanganannya mengikuti pengaturan layanan dan kebijakan operasional pengelola. Penghapusan pada data aktif dan salinan cadangan dapat memerlukan proses yang berbeda.

Pengelola meninjau permintaan penghapusan sesuai keterkaitan data dan kebutuhan retensinya. Anda dapat meminta penjelasan mengenai data yang masih dipertahankan, tujuan, serta kriteria retensinya melalui kontak privasi.

<a id="penghapusan-data"></a>

## 9. Permintaan akses, koreksi, dan penghapusan akun/data

Anda dapat mengajukan permintaan akses, koreksi, atau penghapusan akun dan data terkait Aplikasi dengan mengirim email ke asterinovasiteknologi@gmail.com. Gunakan subjek “Permintaan Privasi Daun Teh Nusantara POS” atau “Permintaan Penghapusan Akun Daun Teh Nusantara POS”. Anda tidak perlu memasang ulang Aplikasi untuk mengirim permintaan.

Cantumkan nama, email akun yang terkait, outlet bila relevan, dan jenis permintaan. Pengelola dapat meminta informasi tambahan yang proporsional untuk memverifikasi bahwa permintaan berasal dari pihak yang berhak. Jangan sertakan kata sandi atau token akses.

Pengelola akan menilai dan menindaklanjuti permintaan, serta menjelaskan bila ada data yang perlu dipertahankan berikut alasannya. Menonaktifkan akun tidak sama dengan menghapus akun/data. Catatan transaksi atau audit yang masih diperlukan dapat tetap disimpan sebagaimana dijelaskan pada bagian Retensi Data.

Versi Aplikasi saat ini belum memiliki tombol penghapusan akun mandiri. Permintaan ditangani melalui kontak privasi dan administrator yang berwenang.

<a id="anak"></a>

## 10. Pengguna anak

Aplikasi ditujukan bagi pengguna yang diberi akses untuk operasional usaha, bukan sebagai layanan yang ditujukan kepada anak-anak. Jika Anda mengetahui data anak yang tidak semestinya diproses melalui Aplikasi, hubungi kontak privasi agar pengelola dapat meninjaunya.

<a id="perubahan"></a>

## 11. Perubahan kebijakan

Kebijakan ini dapat diperbarui jika fitur, penyedia layanan, atau praktik pengelolaan data berubah. Versi terbaru dipublikasikan pada halaman ini dengan tanggal pembaruan. Perubahan yang memerlukan pemberitahuan atau persetujuan tambahan akan ditangani sesuai ketentuan yang berlaku.

<a id="kontak"></a>

## 12. Kontak privasi

Pengelola: Daun Teh Nusantara.

Pengembang: Asterfed.

Email pertanyaan privasi dan permintaan terkait data: asterinovasiteknologi@gmail.com.

