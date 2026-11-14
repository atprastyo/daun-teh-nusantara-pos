# Product photos

One optional photo per saved product. Photos never belong to sale snapshots,
payments, receipts or the durable sale outbox. Photo edits have their own revisions;
replacing a picture does not change the product's price/catalog revision.

## Development backend setup

1. The project must already have the reviewed `supabase/schema.sql` installed.
   Do not rerun that initial schema over an existing database.
2. Review and run `supabase/product-photos.sql` **once**, as a trusted SQL operator
   in the development project. Supabase Storage must be available. This optional
   feature setup adds new photo objects/policies and does not rewrite existing
   product, sale or Room tables. It is not executed by Android or by this change.
3. It creates a **private** `product-photos` bucket, 204,800-byte upload limit and
   JPEG-only MIME allowlist. Keep it private. Review any existing broad policies on
   `storage.objects`: PostgreSQL permissive policies combine with OR.
4. Install the updated Android build on all sync clients **before publishing the
   first photo**. Older clients do not understand the new `product_photos` feed rows
   and will stop at an unsupported page; they must not skip those rows manually.
5. Use the existing HTTPS project URL/public key configuration. No new key, service
   secret, dependency version or public bucket is needed.

Server identity remains manually provisioned. Upload requires live Owner/Manager
access and an approved role-bound device for that brand. Storage uses JWT/RLS; the
publication RPC additionally checks the current device UUID/secret, live role,
feed epoch, expected photo version and stable operation UUID. Cashiers can read
published photos for their authorized brand but cannot upload/publish/remove them.

## In the app

- Sign in online as an approved Owner/Manager and open Katalog → Product.
- Create/save the product first, then choose **Tambah foto** or **Ganti foto**.
- Android Photo Picker selects a single image; older devices use its document-picker
  fallback. No broad gallery/storage permission is requested.
- Preparation automatically centers/crops square, applies EXIF orientation and
  compresses to **512×512 JPEG**, at most **200 KiB**. Source input is capped at
  **16 MiB / 40 megapixels**. There is no interactive crop/zoom editor in this version.
  Rendering into a new bitmap strips source EXIF/GPS metadata.
- The app uploads to a fresh immutable `<brand>/<product>/<uuid>.jpg` path, then
  publishes the reference through `set_product_photo`. It waits for scoped pull to
  apply the acknowledged feed sequence before enabling another catalog action.
- **Hapus foto** publishes a versioned tombstone. It does not physically delete the
  original Storage object. Product cards show a placeholder when no photo exists.
- A failed load has a **Muat foto** retry. An uncertain upload/save keeps its command
  UUID/path/bytes for explicit retry in the ViewModel. Revalidation of the same
  account/outlet retains that pending command; the activity-level picker survives
  the brief outlet-selection transition on return from the system picker.

## Offline and failure behavior

Photos download when their cards are displayed. Successful JPEGs are atomically
cached in private app cache storage, keyed by backend/brand/outlet/user/device/role
and immutable path. A global **32 MiB LRU budget** bounds disk use; the OS may also
remove cache files. Only photos already downloaded and still retained are available
offline. Current authorization/one-month offline lease gates every cache read.
A missing image, network failure, invalid file or cache eviction shows a placeholder;
checkout and original paid receipts remain usable. Binary image transfer runs outside
the authorization mutex so a slow download/upload does not hold local checkout access.

Cache paths contain no JWT, device secret or signed URL. Cache isolation is not
hardware/root protection or image encryption. Logout/role/outlet changes stop old
in-memory image presentation; unrelated accounts cannot read an old scope's cache.

## Consistency and retention

`product_photos` is a new public RLS table with one row per product. Its own version,
tombstone and full-row CATALOG events use the existing feed lock/triggers. Photo
reference, operation dedupe, feed event and audit commit together. Room applies the
reference to the existing versioned catalog metadata and cursor in one transaction;
Room remains initial **v1 / 14 tables**, with no migration or destructive fallback.

Storage upload and SQL publication are separate transactions. An interrupted upload,
conflict, process death or replacement can leave unused objects. Photo form/command
state is memory-only, not a durable photo outbox. After process loss refresh the
catalog before selecting again. Old/unpublished blobs are intentionally retained;
a trusted operator must review unreferenced paths and clean them through Storage
API/Dashboard according to an agreed retention policy. Do not delete Storage metadata
rows directly as a substitute for deleting object bytes. No automatic garbage
collector or live cleanup/deployment is included.

## Checks and remaining live acceptance

- `npm test --prefix supabase` executes photo setup/RLS/RPC/feed/audit/idempotence
  against isolated PostgreSQL with a Storage-schema fixture. It tests actual SQL
  policies, not the hosted Storage HTTP service, byte limits or MIME enforcement.
- Android tests cover image preparation/bounds, cache persistence/isolation/corruption,
  photo-only feed persistence without repricing, and a blocked photo transfer that
  must not hold checkout's authority lookup. Native UI tests cover photo actions,
  offline disablement and missing-image retry.
- Before release: exercise the real picker/cloud providers, rotation/permissions,
  real Storage uploads, bad MIME/oversized HTTP uploads, expired JWTs, two-device
  replacement/removal and lost acknowledgments. Include cached/offline/missing-photo
  checkout on phone/tablet and signed/minified build verification. Hosted byte backup
  and retention remain separate from database backup.

Official references checked:
- https://developer.android.com/training/data-storage/shared/photo-picker
- https://developer.android.com/reference/android/media/ExifInterface
- https://supabase.com/docs/guides/storage/security/access-control
- https://supabase.com/docs/guides/storage/buckets/fundamentals
- https://supabase.com/docs/guides/storage/serving/downloads
- https://supabase.com/docs/reference/self-hosting-storage/upload-a-new-object
