# Blueprint 01 Review Coverage

Baseline SHA: `d693acd7c52f285b6ba475fdd3712a10e419d4e1`  
Authorized implementation branch: `autopilot/epics-features-user-stories`

## Coverage boundary

The exact baseline tree contains **256 tracked blobs**. Of these, this ledger classifies **224 UTF-8/text candidates**, plus 30 PNGs, 1 DOCX and 1 JAR binary blob. Blueprint 01 reported 218 selected text files / 36,411 lines from its source-informed review. This S01 inventory deliberately does not assume that earlier selected set is identical to the full current text set.

Per-file blob identity is recorded below. Exact line counts and a bounded baseline static scan are being completed against the immutable baseline tree. The static scan checks private-key markers, common hard-coded credential/token signatures, and unresolved merge markers; it is screening evidence, not semantic or runtime certification.

Semantic status values below reflect only the Blueprint 01 review statement: a small named set received detailed tracing, while the rest remain targeted/gap work. Runtime coverage is not attributed to individual files merely because a workflow was green.

| Path | Blob SHA | Bytes | Lines | Subsystem | Inventory | Static scan | Semantic review | Runtime coverage |
|---|---|---:|---:|---|---|---|---|---|
| `.github/workflows/android-verify.yml` | `9f7cc7dec31f8a274c0366e181da7f923f700f85` | 1054 | 36 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.github/workflows/phase1-device-verify.yml` | `b9db84cb29c028263afd0f3bc26e26a7f6a57c9a` | 7062 | 184 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.github/workflows/phase3-device-certification.yml` | `7eddaa0dafd07c5383a1e67692707d67da8ac535` | 6726 | 186 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.github/workflows/phase4-index-measurement.yml` | `8912f4d89e89b2814f67290f722f175aa72ffe41` | 7484 | 204 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.github/workflows/reliability-emulator-verify.yml` | `9e77fa2a7cd2b62946df6d516d62f048faf41472` | 11077 | 266 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.github/workflows/verified-integration-correctness.yml` | `f959030f074234e93a9f903b730e6ea91d73b0a0` | 8520 | 193 | build/verification | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `.gitignore` | `25773b1367be563d0592c830d732e5fc5b16be8f` | 192 | 34 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `README.md` | `718b918d391745be81871f82c6d96ecc44391149` | 9089 | 87 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `UI/TestQr.kt` | `17ac326927c7006eaf1ac32089feee7b5f3fda89` | 643 | 60 | auxiliary tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `UI/UI/clean_overlay.py` | `74cd5e487e13438279cd34daf79c12847da27c42` | 1505 | 34 | auxiliary tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `UI/generate_playstore_screenshots.py` | `09581556cc7b4097fc0d81c4187680273cdc9623` | 3608 | 98 | auxiliary tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/build.gradle.kts` | `a62ee6ca4da2ca659cb4300b89c84e7aa48e1e95` | 13074 | 344 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/patch_qr.sh` | `be14f31c7d2d56db782001e8877953a6be106b19` | 494 | 2 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/proguard-rules.pro` | `4520c221351179b5597d91212da2cf80db2c093a` | 1209 | 40 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/schemas/com.photobook.app.data.db.PhotoBookDatabase/12.json` | `564fe3875883f9cda5b215b3cf61ef8f26329ad8` | 11421 | 118 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/feature/metadata/ExifMetadataServiceInstrumentedTest.kt` | `0b65185380a15417b1f0d5796558d94323646b81` | 10545 | 261 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/feature/phototextsearch/MediaStorePhotoTextLayoutSourceInstrumentedTest.kt` | `f39f8a5c6d5aef46a8c16f9db8aa3c38b51cb1b9` | 5312 | 147 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/feature/phototextsearch/PhotoTextMatcherPerformanceInstrumentedTest.kt` | `f14d128cc59a5a361383f65d383f826e885edacb` | 2733 | 73 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/ml/LocalOcrEngineInstrumentedTest.kt` | `5e295592742c905eb0d4939cda57d7da41d752e2` | 3267 | 76 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/util/BitmapOrientationInstrumentedTest.kt` | `8132e71c3fa60e7fdb82a46acf392166b081fc80` | 3828 | 115 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/verification/ArchiveAccessBoundaryInstrumentedTest.kt` | `973c2dce48c4fec0c09211bf9b4e6669ed640dd7` | 8969 | 228 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/verification/IndexPersistenceInstrumentedTest.kt` | `987f7bdb9c16afa1601127cc0ecf283b3e41350a` | 8157 | 208 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/verification/OfflineAndDatabaseInvariantTest.kt` | `12b5b11e8fad3e1ecfe54692445a57820a91c361` | 1728 | 51 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/verification/RoomMigrationInfrastructureTest.kt` | `f402445585847cd2bbc23faa7218037ad157cfba` | 1162 | 38 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/androidTest/java/com/photobook/app/verification/SearchCompletionInstrumentedTest.kt` | `fdae92526786c71fcb1c8b0d1f316b3f04408a0d` | 8241 | 198 | android tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/benchmark/AndroidManifest.xml` | `06dd166c6dc89068931e3727e1365b4222c0d015` | 299 | 9 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/AndroidManifest.xml` | `129e3d464e0100169447115eece30ffa58d9b3f1` | 3193 | 78 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/assets/cities_min.csv` | `927fb93c9f7f2b002691a6fa7825e802b57c82f6` | 382 | 9 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/MainActivity.kt` | `fb013053db172ff7a9d7530ec1fff6b99e47297e` | 35816 | 913 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/PhotoBookApplication.kt` | `2e64888039df2b31ab0714cf76c9fd3d9200e03a` | 2188 | 69 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/ArchiveDao.kt` | `6d96007971e8b8610b5f1ffcbbb00e98d71e4db3` | 4406 | 156 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/ArchiveDecisionEntity.kt` | `860b96c77da50ce4a10df7d2cb650d582dfa2700` | 858 | 35 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/PhotoBookDatabase.kt` | `22ecb51651a20561c1edcfcbbb7d34abdd7e277b` | 477 | 20 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/PhotoDao.kt` | `9a74add2e2d9660fa9dd5ffc298df83aa2dad284` | 6211 | 213 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/PhotoEntity.kt` | `5e565b182678e7cf2130dc32fffc41de37d9b822` | 4550 | 152 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/PhotoFtsEntity.kt` | `df68e241260378de877c49a9eb9405972960400e` | 885 | 39 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/PhotoTagCodec.kt` | `80a0509fdd4b4b6b9ff187fc2b356fa3cbc2939b` | 1508 | 50 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/VaultDao.kt` | `1cfa11a137faa751b23cda6b7366c122cbf2ca28` | 977 | 27 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/db/VaultEntity.kt` | `b368d2b00de7e53ba7e2e4aa844d0e98b89eb215` | 494 | 22 | persistence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/geo/CityDatabase.kt` | `b7f277b3248bf092b2f77381b77cf4af6567e916` | 1200 | 36 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/geo/OfflineGeocoder.kt` | `72b4182d751f2acc59d965bc02d15a7d7d5274bc` | 2150 | 62 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/index/IndexBuilder.kt` | `1c80a49631b0d391c8f04aa10a89d1fc3100cb5b` | 6462 | 170 | index | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/index/IndexCommitCoordinator.kt` | `8a53ef30fe7ca3ed792601c37ab2d4c72f6fa758` | 670 | 21 | index | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/index/IndexPersistence.kt` | `19649f3d89a0e80d948d587f294b991c81ae236b` | 16162 | 388 | index | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/index/PhotoIndex.kt` | `96b2f42d8c981cf09933bd5265986d4fc1c514d5` | 22117 | 570 | index | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/model/GeoResult.kt` | `0bd1b030de35f5130b0e71704d61920e178b8d50` | 132 | 7 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/model/IntelligenceStatus.kt` | `c06e4885e623a51f9b7b4d564384f9665d72bd0a` | 630 | 21 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/model/MLTag.kt` | `09195c586ed883d45d5da072bbff1d513fc91d7b` | 162 | 9 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/model/PhotoRecord.kt` | `3087d20001b6c899402167e01f8d44282c7a398a` | 1794 | 63 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/model/RawPhotoData.kt` | `0e5552621aa6b3cb060a3d0ba3b661a2407ae99a` | 376 | 16 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/source/ExifExtractor.kt` | `ff33732d75e2c62082787204dc3dd4d82f2b2c4e` | 2034 | 50 | media scanning | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/data/source/MediaStoreScanner.kt` | `39bc06e0b86f0d814ec13d3cfd572b0c89903b2a` | 6860 | 170 | media scanning | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/di/AppModule.kt` | `5c1befdc2ca3950ba3296d64615d9528a5890a51` | 11132 | 305 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/di/MLModule.kt` | `5443c639ff287f8b02953fb5f91edfa425a051e4` | 555 | 22 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/archive/ArchiveClassifier.kt` | `50d7865ce5c8c64200b939d2bfb464625f2a7010` | 8483 | 238 | archives | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/archive/ArchivePagination.kt` | `66d9455875bb75f20a4a3fe10e1ad2dcc4b982a8` | 1366 | 39 | archives | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/archive/ArchiveService.kt` | `1601b5bad06c2b6ac12ab1c658cde2507b2db78d` | 23411 | 602 | archives | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/ExtractedTextResult.kt` | `c5fd88711788548de919e00a7f6dbc41e7310f20` | 269 | 7 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/NormalizedTextRegion.kt` | `a0d048137c53dd313d51a2ada181a5ee850cbcd1` | 1069 | 35 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/OnDevicePhotoTextExtractor.kt` | `91b9f1f525c1cec4362b821b2789f9b0fffee4c3` | 11744 | 322 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/PhotoTextCopyCoordinator.kt` | `6507d16ee1363fd31e3e9b82eaee75f30989b60f` | 2830 | 95 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/PhotoTextExtractor.kt` | `75a95e343ad9148a9c47e83604c11e1646792978` | 304 | 12 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/copytext/PhotoTextFormatter.kt` | `a3bdb250d53f8a0275a36d2796c264215470d5b7` | 968 | 37 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/declutter/DeclutterModels.kt` | `7cb5bac499135fb6003cd2a541e1ac459a3db4f1` | 849 | 34 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/BlurScoreComputer.kt` | `65acfb99d5747289bfbc0af506d66fda6800583f` | 4026 | 123 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/BurstBestShotPicker.kt` | `6c99fc7ce36e066b6b1f3f6d09da691046c95a21` | 3388 | 82 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/DuplicateHash.kt` | `efa08617af1c9c581814cac09cddeb0f0de3fe56` | 511 | 17 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/DuplicatePhotoFinder.kt` | `cb7da0166409ad203c58c816db14e263c7ee1af3` | 21037 | 543 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/DuplicatePhotoGroup.kt` | `7ff9e544f1d1341dc3401eb5523cc0c4d44a923b` | 459 | 22 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/duplicates/PerceptualHashComputer.kt` | `caa036d2787c9d6b0627ff0f174a83bfb76019f4` | 3319 | 97 | cleanup/duplicates | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/editor/PhotoEditService.kt` | `507758347ec879997c51c4960c539b80e48c1744` | 15340 | 429 | viewer/editor | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/memories/MemoryCurator.kt` | `5b21121f252efa179113d3bfa19faf63fff0e42e` | 7460 | 207 | memories/widget | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/memories/MemoryStory.kt` | `d621e84de59aa024104eb62057c4ca3cfffef09d` | 364 | 15 | memories/widget | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/metadata/ExifMetadataService.kt` | `8e5770c7cc1ca589f9bba8914b31c23cff091119` | 37063 | 887 | safe sharing/metadata | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/notes/PhotoNoteStore.kt` | `f9df6f080eb346a1c0fe2f9f84564b40a0b62314` | 3871 | 108 | notes | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/pdf/PdfExportLayout.kt` | `a78d2cd36bab3ea078926f99150450d099fd58c4` | 3158 | 99 | pdf export | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/pdf/PdfExportResult.kt` | `679fb8bc03cd85e1601de4feae83474cd26d17ec` | 595 | 27 | pdf export | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/pdf/PdfExportService.kt` | `5e5671ac580216d723a8e40452080fd54db1603b` | 15231 | 428 | pdf export | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/phototextsearch/PhotoTextCoordinateMapper.kt` | `e47238d658270b81e715e4c8c52784c5ac8d94e7` | 3119 | 104 | in-photo search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/phototextsearch/PhotoTextLayoutRepository.kt` | `b76df5a3c107a1ef7ccdba34527738da31ccc177` | 15372 | 404 | in-photo search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/phototextsearch/PhotoTextMatcher.kt` | `6a04b066a62652aaeb98bac22dca8b4141e51190` | 4069 | 115 | in-photo search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/phototextsearch/PhotoTextSearchController.kt` | `ad173554da87fb8c84ea002f02248f67a3654329` | 9127 | 280 | in-photo search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/phototextsearch/PhotoTextSearchModels.kt` | `5b17c8b420236d0d827daf023415798c45900dbb` | 1931 | 87 | in-photo search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrBitmapEncoder.kt` | `025db4c4833d333b33ab3b960ea2eb079c9e2a1e` | 1203 | 38 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrPayloadHash.kt` | `aa15643e51089f4fe8a650e518faf6a9ce4e153f` | 328 | 12 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrPreviewDecoder.kt` | `c71c69b6f95cbe860cce2b224489c7ce9546a5d3` | 2629 | 71 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrReceivedImageStore.kt` | `89b4a5af7d1c553dcc71ee996cd32fd8fc4cd5b4` | 6654 | 187 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrShareEncoder.kt` | `0e05bd0c01c764fd899c14a7a1243eec7cdaf983` | 12576 | 341 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrTransferAssembler.kt` | `ffb68eb2c10c4eecedf1708ede1c95f27736b0c6` | 7565 | 217 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/qrshare/QrTransferProtocol.kt` | `86e844c1edb51ee9a5cee18fa7f6620866804165` | 6258 | 181 | qr transfer | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/trash/TrashService.kt` | `e376b065f1dfa3b9ed9d0994da717f264ba8a505` | 5375 | 124 | trash | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/vault/VaultAuthCrypto.kt` | `2498d78a97d58b17a185cf7af390d22a0ac34fbf` | 29578 | 753 | vault | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/vault/VaultAuthenticationCoordinator.kt` | `36c46f94620adbbe1a4b93706b39b8a18d9edcae` | 9095 | 235 | vault | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/feature/vault/VaultService.kt` | `6c4140b6bb1e635b4b861f8cbc3684179b54376c` | 35389 | 911 | vault | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/ArchiveFoodSignals.kt` | `8a5dc5932cf969933d507f0aab8e0ded93a607c9` | 3221 | 107 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/CompactLocalIntelligence.kt` | `e6d43e4bf82f4223dfc76036d22d51e8e59be5ae` | 4838 | 127 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/LabelMapping.kt` | `c43e09ef37666be84ab613b67cbb18d000670e06` | 7344 | 264 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/LocalOcrEngine.kt` | `a0c1bb1e6ae22c5d3e8e8dc2e4bc73400ef72f7d` | 8454 | 215 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/LocalSemanticImageLabeler.kt` | `fcfdac4931bc572867382e037f81cc9ffdd8c3c4` | 13926 | 342 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/MLTagger.kt` | `a305a4b2b4bd06f71b4597e593e8a4c92766b33f` | 12049 | 300 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/OnDeviceIntelligence.kt` | `73c6c938b8eb9fff9d2c1a5fda0d9d17d0e3d652` | 1227 | 38 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ml/TaggingWorker.kt` | `07342e97a9ee950b81d24707a4a2c3685a4182f1` | 14740 | 351 | offline intelligence | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/FilterEngine.kt` | `f802f2a13c2fc27a4d013c72263b6a66fd6adabb` | 3005 | 89 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/FilterFactory.kt` | `beaf32ecfc219a529a43637903c4188fa9449201` | 10403 | 245 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/OcrIndexReadiness.kt` | `3178f451f9ba3152648bfa5f01c5b6b4fb0e292b` | 635 | 16 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/OcrQueryMatcher.kt` | `6919dbdadc06a2f257e7b3f017bcda0e01e5cd83` | 1435 | 43 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/PhotoSource.kt` | `3b1b41cbf0a5cacba604d2ac70e18f83caa89414` | 2543 | 80 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/QueryParser.kt` | `215ae9bd7fa49140e4131454828d0fcb3432b747` | 1640 | 52 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/QueryToken.kt` | `a1345e8ef94bf9ec2e3fb4abc11c8cdab69ab4e9` | 901 | 36 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/SearchEngineV2.kt` | `3f6acd0b4dbf4e5100383cbb1b28d3ec90300265` | 6860 | 167 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/SearchRanker.kt` | `21f3393406e02556adbb2af25b7c232358c0fb23` | 5314 | 147 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/SuggestionEngine.kt` | `0a51dafc6024a68d11befef9ad3c3fe85036bbf0` | 1505 | 49 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/search/TokenClassifier.kt` | `cbab2dd36d2e4a957366e866a4b11ffbf636b81a` | 4259 | 113 | search | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/EmptyState.kt` | `e268eea6118230a6e7703a388789ca8f38641986` | 10039 | 268 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/PhotoGrid.kt` | `3e9be37a7524f0421c342771e07f538c2f8ba77d` | 8680 | 219 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/PhotoTextSearchControls.kt` | `90cf71c6f786153b805f27f7538b09fa96336240` | 17778 | 491 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/PhotoTextSearchDialogInsets.kt` | `69260fe4efaaa607dfd6f72387bd4730ddb9f875` | 7787 | 184 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/PhotoTextSearchOverlay.kt` | `25965d838385c3a857d2b4c76e7d5d788c824862` | 5888 | 152 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/PhotoThumbnail.kt` | `4915d90c49beb019178f89575655cb461eb33844` | 4592 | 122 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/SearchBar.kt` | `5ab24b1dea713b1f104b0b3e84e378efd6c048b5` | 3517 | 98 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/component/SuggestionDropdown.kt` | `6a2a626d88ff3a30aa31223d8fd1f418d096ce44` | 3397 | 80 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/model/HomeFeedMode.kt` | `5707c53194f183553c95b2e87be58a5882283f27` | 147 | 9 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/model/TimelineMark.kt` | `93b36e94bce67986bdad7b8d548d29fcd79218db` | 160 | 9 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/ArchivesScreen.kt` | `2b099ed2f2f93e1394b4f8869dbaa072785629cb` | 25387 | 643 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/DeclutterSwipeScreen.kt` | `9842e8b114f5c3c1b00f0e96e0cdb17adeb61a49` | 12829 | 302 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/MainScreen.kt` | `f3834c9795e32e9180c169ccc58ed2a6cba4295b` | 35553 | 867 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/MemoryStoryViewerScreen.kt` | `d26afa9e99d336e80d2ccf3728fe073684e6f721` | 9402 | 224 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/OnboardingScreen.kt` | `d19e106631942aebccf55f108316948ea259693a` | 2555 | 73 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/PhotoReelsScreen.kt` | `0fe9727f3a0ed5fab8f3017ad34619ca4e8de9e6` | 18569 | 371 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/PhotoSearchRevealRequestGate.kt` | `e23089d5dc16bee18446781121f15eb4abb834ce` | 608 | 19 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/PhotoViewerScreen.kt` | `ce004b04f7d78dc53b252691a66e31de74e881db` | 110264 | 2441 | viewer/editor | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/QrReceiveScannerScreen.kt` | `fb3c0c4cb1e500b0a00036f17d71f7c907812dc8` | 16720 | 408 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/QrShareSheet.kt` | `c3d22fafe509d0e8ae2221505ec985ebb77e4ed1` | 8753 | 216 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/TrashScreen.kt` | `c67370f87966ecc7cd5c1cead2543cd9726bcf51` | 7675 | 157 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/screen/VaultBottomSheet.kt` | `c308bf937c7abd06676cb4f0c03525fa6dff47cd` | 18585 | 467 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/theme/Color.kt` | `21d95dee95cd623339fb892f7c4614efc1cbfed5` | 712 | 16 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/theme/Theme.kt` | `6aad6b4b56bcd95466b364b95674f7a3aaf63fef` | 1602 | 50 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/theme/Type.kt` | `c9e7fdaf5c4674d4badb7814f440bc1ae33c5ac7` | 1830 | 63 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/viewmodel/MainViewModel.kt` | `0bd78501c1e6603b2a6f09e7de4efc2e06b6b6e5` | 77954 | 2010 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/viewmodel/PhotoViewerViewModel.kt` | `7a105d6dcf58beb391f8aef3ba4f15b9127be611` | 335 | 14 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/ui/viewmodel/SearchResultsPagingSource.kt` | `6f9d2e2fea5186b03fe5c840598e93ce9d9e9b4a` | 2333 | 69 | ui/navigation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/BitmapOrientation.kt` | `e6454a975e4d770eb4549ca5248db995877af8af` | 1771 | 53 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/Constants.kt` | `8b6e3b87eccb039ba0de03e6cad6b2de3824598f` | 955 | 24 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/DateUtils.kt` | `00d765b8ca58edba5610a6233b1d9724e8e3a282` | 3421 | 80 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/LocalDiagnostics.kt` | `456f38e2cc7931f1cee9f78525e4fbe04c6a59e8` | 3580 | 103 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | DETAILED_BASELINE_REVIEW | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/PerformanceProfiler.kt` | `2717c5f21c1bcfabdd857556ee11603c9c1be2cf` | 1958 | 55 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/util/PermissionUtils.kt` | `6c803c48c8a02094721aeca6e3aa552499f8dc96` | 2173 | 58 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/widget/OnThisDayWidgetProvider.kt` | `2f6215fd05faf8f0619a5024d30fd3ea9bd0536c` | 4446 | 110 | memories/widget | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/worker/ArchiveRetentionWorker.kt` | `7dff89c9483c6c709ec99b80c1b6a70135be99a7` | 2019 | 58 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/worker/ArchiveScanWorker.kt` | `d96b12fd2d86329dcd36e04da4cbc8a750776a85` | 2403 | 64 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/java/com/photobook/app/worker/TrashPurgeWorker.kt` | `5af0e0bd1fe7fcf49ad9ca7d00128926481ca453` | 1571 | 41 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/drawable/ic_launcher_background.xml` | `9dd403c8f1588b0a52db0cda5860757fb4163d28` | 140 | 3 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | `531e2c19e256e1a243d25c8fec734931104735bd` | 472 | 12 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/drawable/widget_on_this_day_bg.xml` | `11a64ede9326c316088392e4ffa30e9a612432b3` | 300 | 9 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/layout/widget_on_this_day.xml` | `fb4f25e1ada54216e6d3214ae62f079b0c7b3e40` | 1949 | 56 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | `6b78462d615bfe7003b31d0534dcad416b75ad25` | 273 | 5 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | `6b78462d615bfe7003b31d0534dcad416b75ad25` | 273 | 5 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/values/colors.xml` | `c8cd280f77403083e3b6449eff22ab0fb84b808f` | 625 | 13 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/values/strings.xml` | `538893b415484f8bdd9f70f2344e796e834d9d05` | 19147 | 259 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/values/themes.xml` | `1b5cc1854fa8d6fa77b2686c996bb0b9ee58cb10` | 108 | 3 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/xml/backup_rules.xml` | `d348ebd18ef7f41118eb6e6c62e92df8bf942787` | 492 | 10 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/xml/data_extraction_rules.xml` | `ac01a2af935a7af6be3c669f1866f31e4018b9dd` | 921 | 20 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/xml/file_paths.xml` | `c26dc8532737f3e09f478de08cb72e2c364db095` | 381 | 15 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/main/res/xml/on_this_day_widget_info.xml` | `c17c44a5ef3bf2f0dda1ee550e31f85b6042a3d2` | 468 | 10 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/data/db/PhotoEntityTest.kt` | `a3b89474cb9453990384039d4d1652845493569f` | 2400 | 77 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/data/db/PhotoTagCodecTest.kt` | `75bd9cb5ed2e33944b5ceeb9fa39da35cf744387` | 675 | 24 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/data/index/PhotoIndexV2ParityTest.kt` | `84540f94e5fc5a5b3a2e7331643de1a0c5f7b4e2` | 9530 | 230 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/data/source/MediaStoreScannerProjectionTest.kt` | `3e0297e0266c19eaf919bfb5fc9b8b19b12b6e28` | 1259 | 34 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/archive/ArchiveClassifierTest.kt` | `24cdabaf74d683f77b569e2eb513d9358d29a70b` | 13214 | 451 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/archive/ArchivePaginationTest.kt` | `321a1b7af673771fa68a6e1440345ebe538911a5` | 2888 | 87 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/copytext/PhotoTextCopyCoordinatorTest.kt` | `3c1a72737663a20cf503cb531e27ed262caf38a3` | 2559 | 82 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/copytext/PhotoTextFormatterTest.kt` | `3b581fe1e031bf224741df33be8d6c1e7e561fe7` | 720 | 27 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/duplicates/DuplicateHashTest.kt` | `9708f3293767190fc464d990862450cd29c29e1a` | 554 | 19 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/editor/PhotoEditStateTest.kt` | `e554ce8761d45a996e53e2ad13447b441d46dc8a` | 1022 | 38 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/pdf/PdfExportLayoutTest.kt` | `098450d3261e16cc5c8acada05f6794d29d6dca7` | 2951 | 97 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/phototextsearch/PhotoTextCoordinateMapperTest.kt` | `f3b17d6f827edcb8e2ef7a42be349596456d5502` | 2260 | 73 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/phototextsearch/PhotoTextMatcherTest.kt` | `1601efb5510f2e09352c9c528699585705f14330` | 4546 | 140 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/phototextsearch/PhotoTextSearchControllerTest.kt` | `ad8290b0e2dbf12dda1e7e67ba20e5989ffff8ae` | 6005 | 155 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/qrshare/QrPreviewDecoderTest.kt` | `87be5136ae153b8d3eb2568eac8080abb074f624` | 891 | 28 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/qrshare/QrTransferAssemblerTest.kt` | `fb1a28c0bb20a8a0c39bdabe724d92e3d8662f17` | 2364 | 67 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/feature/qrshare/QrTransferProtocolTest.kt` | `0957f021cbc989ca58f81e6802481be7c3abd135` | 2099 | 70 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/ml/ArchiveFoodSignalsTest.kt` | `498579a56121f96f6a70578ccceb4b2a886cf777` | 5464 | 192 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/ml/LocalSemanticScoreAggregationTest.kt` | `762626b251e648f0c239c49e9dea8fc8076c73a1` | 1637 | 53 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/FilterEngineTest.kt` | `e17a5cebd211b5b9a63b1be32d362259d697d908` | 11269 | 345 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/OcrIndexReadinessTest.kt` | `87c98e9fe25bdfa86392c70c9e9aa500babc553e` | 2317 | 73 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/OcrTextSearchTest.kt` | `85ad959f0db43e124067d39bf00fa0c957fbc10e` | 4991 | 156 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/QueryParserTest.kt` | `6c88cc3ad6ecd64f2d276265b3b3e70a17c12263` | 880 | 32 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/SearchEngineV2ParityTest.kt` | `3fe890293c1717b1f5b21d8883271cb7cc20e741` | 9611 | 238 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/search/TokenClassifierTest.kt` | `26bcf515bce40b62eb6058faf82b327a85bd0b8f` | 1316 | 38 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/ui/component/PhotoTextSearchDialogInsetsTest.kt` | `00ce31e6bc4e46b3648747e2a3dcfe284aa6e199` | 5440 | 179 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/ui/screen/PhotoSearchRevealRequestGateTest.kt` | `e12c93f69049abedf07ade0e1c6a95778c8df7ad` | 1469 | 49 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `app/src/test/java/com/photobook/app/ui/viewmodel/LimitedAccessReconcileTest.kt` | `aece8fc0ad08eb2c42d7578c7eed189e49d96018` | 5264 | 149 | unit tests | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `baselineprofile/build.gradle.kts` | `cc456ecb2b72a91839393d37641c1f606b3cae92` | 1485 | 59 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `baselineprofile/src/main/AndroidManifest.xml` | `8072ee00dbf16d9161b7464ef3d2194a7d659bcc` | 52 | 2 | resources/package | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `baselineprofile/src/main/java/com/photobook/app/baselineprofile/BaselineProfileGenerator.kt` | `105159a16bb5cf026dd2a318b62092b4fe46797f` | 2523 | 78 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `baselineprofile/src/main/java/com/photobook/app/baselineprofile/BenchmarkMediaSeeder.kt` | `89b3c73b82a200a525d52e1cd7cbf9b1cb40978e` | 7545 | 189 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `baselineprofile/src/main/java/com/photobook/app/baselineprofile/PhotoBookMacrobenchmark.kt` | `21d9e4c9f649edf4bd5537db7e3cc00a32282ae2` | 23672 | 611 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `build.gradle.kts` | `ed486b6836240db73ce4042c34b0b5b400461d54` | 447 | 9 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/README.md` | `06ad01aeb2dbed0064543e1ba6997688de7b3486` | 2209 | 45 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/claude.md` | `e202efc6f0bc8c671ad09b725bf9dbd65219a6dc` | 4660 | 44 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/gemini.md` | `0bec841a2b5cab86cd9a870997966703fb4a98ab` | 4330 | 40 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/jules.md` | `34146795c1615fad58897094d0774819184c3983` | 4565 | 47 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/performance.md` | `f07febb143f0c27f68bb1d6c0603e900b6927dd4` | 3591 | 30 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/phase0-verification.md` | `6eebe9322c7ba0586eba7fb89ecca4edefa8ac5a` | 10399 | 178 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/phase4-index-startup-plan.md` | `579bb2cc76a693e7fc50ee3ad457be3c1982bfb7` | 3438 | 64 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/phase5-device-evidence-template.md` | `dc5023457f3c0f3d14fa5adc6bc078089c9634d8` | 7582 | 195 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/phase5-physical-device-certification-plan.md` | `0672e836a7d258fc6d73639ab96cc9a1f5688a94` | 7768 | 131 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `docs/security.md` | `10188ccda17dd2df481d86eaba5f858a9a370b44` | 3337 | 29 | documentation | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `finish_release.sh` | `133267f2dcd2ee82aac84b1796fc43f27ba00c1c` | 4636 | 137 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `gradle.properties` | `b4eecc410e10dd712ec08130fb85ab5a8b2ee677` | 177 | 5 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `gradle/wrapper/gradle-wrapper.properties` | `e98323e7071c55a6bd74029ba8d5f07a2b7dad0a` | 281 | 8 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `gradlew` | `ef07e0162b183eb9d19a2c9ba7035c283af9f8dd` | 8728 | 251 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `gradlew.bat` | `5eed7ee8452842305a18a4eb967442683808226a` | 2937 | 94 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `keystore.properties.example` | `cde94f7eb0a8795ccd56ea7c4f9fdcb6fca4ba28` | 286 | 6 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `settings.gradle.kts` | `2cc08940dcaca86fe63369df38cf3c9142fbfdad` | 445 | 22 | project/build | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/README.md` | `9aa5ae074819a4dc2eed179630b342d311fe0723` | 2296 | 39 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/extract_phase4_timings.py` | `707c0b1b480b16f8250d3cb377678f1745bffd92` | 7274 | 180 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/generate_media_fixtures.py` | `f9b3ffb1f9d1eb42c4bafe645336d74bbdffc951` | 11836 | 353 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/phase0_device_stress.sh` | `48360f93b1179332ab6bba4e8ed4421e1fe9c604` | 3513 | 100 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/report_artifact_sizes.py` | `0f7947b4b0a5bf591d848fe44fefc4f1e5b2a721` | 4029 | 119 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/run_phase0_local.sh` | `224dcb02a2d1919c338fb3f0dbda95e1d652e1e5` | 3080 | 108 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/run_phase3_device.sh` | `9040dc921c2fa86859c52fe51928da6b57ec8afe` | 22475 | 570 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/run_phase5_physical_device.sh` | `12c4c5935407c10717a6137b7728ca777a69603d` | 957 | 24 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |
| `tools/benchmark/test_phase0_tools.py` | `f13cb338b1780a053128389cf85ac617653918a2` | 12629 | 299 | verification tooling | INVENTORIED | SCANNED_NO_CREDENTIAL_PATTERN | REVIEW_GAP | NOT_FRESHLY_ATTRIBUTED_PER_FILE |

## S01 line-count progress

- Exact line counts and the bounded baseline static screen are complete for **224/224** baseline text candidates.
- Static screening checks private-key markers, common hard-coded credential/token signatures, and unresolved merge markers. No result is promoted to semantic/runtime proof.
- The final batch produced no static-review flags.

## Binary/model/dependency provenance gap

PNG screenshots, the DOCX planning artifact, the Gradle wrapper JAR, generated/pinned model artifacts, dependency/native-library contents and final packaged artifacts are not semantically certified by this text ledger. S31 owns binary/model/dependency provenance and package inspection. Historical screenshots are not current visual acceptance evidence.

## Story review rule

Before accepting a story that changes a subsystem, inspect every changed production file plus direct callers/tests, record the result in story evidence, and update this ledger or link that evidence. Never promote INVENTORIED or a prior green workflow to whole-file semantic/runtime proof.


## S02 verification-subsystem semantic review

Story S02 reviewed the changed verification paths plus their direct execution/evidence boundary:

| S02 path | S02 review result |
|---|---|
| `.github/workflows/android-verify.yml` | Semantically reviewed: `autopilot/**` routing, read-only permissions, exact event-SHA checkout, concurrency, Phase-0 gate and fail-closed artifact presence. |
| `.github/workflows/autopilot-targeted-verify.yml` | New S02 workflow semantically reviewed and executed at exact source `03b63c544e016f809618c4509d2cf5f9c6788a4b`; targeted correctness/privacy run succeeded. |
| `tools/benchmark/run_phase0_local.sh` | Semantically reviewed: existing full gate preserved, including `assembleRelease` before `verifyApkSize`; evidence unit tests added to host self-test phase. |
| `tools/benchmark/build_ci_evidence_manifest.py` | New S02 evidence validator semantically reviewed; requires source/workflow/environment/artifact identities and non-zero green instrumentation evidence. |
| `tools/benchmark/test_ci_evidence_manifest.py` | New independent negative/positive tests reviewed; missing fixture and zero-test evidence fail closed, valid XML/direct instrumentation evidence succeeds. |

S02 runtime evidence is linked in `VALIDATION.md`. The failed broad/performance and UTP-offline attempts are retained there rather than hidden. Binary/dependency provenance remains S31 scope.


## S05 navigation-shell semantic review

| S05 path | Review result |
|---|---|
| `app/src/main/java/com/photobook/app/ui/screen/MainScreen.kt` | Semantically reviewed after final diff: Photos is the saveable default, Albums/Tools destinations preserve existing callbacks, automatic search autofocus is removed, and the decorative PRO badge is removed. |
| `app/src/main/java/com/photobook/app/MainActivity.kt` | Direct caller reviewed: existing search/viewer/selection/Vault/Trash/Archives/duplicate/memory callbacks remain wired through the shell. |
| `app/src/main/java/com/photobook/app/ui/component/SearchBar.kt` | Direct focus behavior reviewed: IME display occurs only from explicit focus behavior; S05 no longer requests focus automatically from MainScreen. |
| `app/src/androidTest/java/com/photobook/app/verification/MainNavigationInstrumentedTest.kt` | Focused API-35 hosted-emulator regression reviewed and executed; verifies shell navigation, absent PRO, no automatic IME, and scroll-reachable Tools journey. |
| `.github/workflows/autopilot-targeted-verify.yml` | Existing correctness/privacy suite preserved and extended to execute the focused S05 navigation regression; no existing gate removed or weakened. |

Runtime evidence and artifact identities are recorded in `VALIDATION.md`. Physical/OEM evidence remains unavailable and is not inferred from hosted emulation.


## S08 targeted review closure

Reviewed production/direct-call/test scope: `ui/model/AlbumCatalog.kt`, `ui/viewmodel/MainViewModel.kt`, `ui/screen/MainScreen.kt`, `MainActivity.kt`, `ui/model/AlbumCatalogTest.kt`, and `verification/MainNavigationInstrumentedTest.kt`. S08 changes are bounded to local personalization and retain existing offline/privacy/media-safety behavior.


## S09 targeted review closure

Reviewed changed production/direct-call/test scope: `util/ThumbnailDecodePolicy.kt`, `util/PerformanceProfiler.kt`, `ui/component/PhotoThumbnail.kt`, `ui/component/PhotoGrid.kt`, `di/AppModule.kt`, `PhotoBookApplication.kt`, `ThumbnailDecodePolicyTest.kt`, and `PerformanceProfilerTest.kt`. Existing Coil cache policies and low-memory trimming were preserved; no full-resolution gallery decode path was introduced.


## S10 targeted review closure

Reviewed changed production/direct-call/test scope: `ui/component/PhotoGrid.kt`, `ui/component/GridContinuityPolicy.kt`, `ui/screen/MainScreen.kt`, `ui/viewmodel/MainViewModel.kt`, `MainActivity.kt`, and `GridContinuityPolicyTest.kt`. Paging remains bounded and viewer presentation still overlays the retained Photos shell; S10 does not retain the full library in Compose state.


## S11 targeted review closure

Reviewed changed production/direct-call/test scope: `ui/viewmodel/StartupReadinessPolicy.kt`, `ui/viewmodel/MainViewModel.kt`, `MainActivity.kt`, and `StartupReadinessPolicyTest.kt`. Access-generation checks and limited-access filtering remain before any persisted-row publication; enrichment scheduling stays asynchronous and separate from browse readiness.


## S16 targeted review closure

Reviewed changed/direct-call/test scope: `feature/phototextsearch/PhotoTextSearchController.kt`, `PhotoTextMatcher.kt`, `PhotoTextCoordinateMapper.kt`, `ui/screen/PhotoViewerScreen.kt`, `PhotoReelsScreen.kt`, `VaultBottomSheet.kt`, and `PhotoTextSearchControllerTest.kt`. The S16 production behavior already had request/session/query stale-result fencing and one-layout reuse; focused tests added lifecycle-close/dispose proof without broadening Vault plaintext lifetime or changing geometry/insets/reveal semantics.


## S17 targeted review closure

Reviewed production/direct-call/test scope: `ml/TaggingWorker.kt`, `TaggingRetryPolicy.kt`, `MLTagger.kt`, `OnDeviceIntelligence.kt`, `IntelligenceSchedulingPolicy.kt`, `data/index/IndexPersistence.kt`, `data/db/PhotoDao.kt`, `data/model/IntelligenceStatus.kt`, `ui/viewmodel/MainViewModel.kt`, `IntelligenceSchedulingPolicyTest.kt`, `OcrIndexReadinessTest.kt`, and `TaggingWorkerPolicyTest.kt`. The change centralizes the existing retry bound without widening network, access, foreground scheduling, or terminal-status behavior.


## S18 targeted review closure

Reviewed changed/direct-call/test scope: `feature/editor/EditTransform.kt`, `PhotoEditService.kt`, `EditorOutputPublisher.kt`, `ui/screen/PhotoViewerScreen.kt`, `PhotoEditStateTest.kt`, and `EditTransformTest.kt`. Preview/export now consume one transform pipeline, including EXIF-normalized source pixels, rotation, custom/preset crop geometry and tone/filter matrices. Rendered previews are bounded and cancellation-safe; original media and S03 publication journaling are unchanged.


## S19 targeted review closure

Reviewed changed/direct-call/test scope: `feature/metadata/ExifMetadataService.kt`, `SafeShareIntentFactory.kt`, `MainActivity.kt`, `ui/screen/PhotoViewerScreen.kt`, and `ExifMetadataServiceInstrumentedTest.kt`. Current branch already failed closed on safe-share errors; S19 adds bounded/cancellation-safe preparation and one read-only share-intent boundary without any original-media fallback.


## S23 targeted review closure

Reviewed changed/direct-call/test scope: `feature/trash/TrashService.kt`, `MainActivity.kt`, `ui/screen/TrashScreen.kt`, and `feature/trash/TrashListUiStateTest.kt`. Final S23 review confirms bounded MediaStore paging with stable ordering and one-item lookahead, truthful provider expiry, distinct ready/unsupported/error presentation, archive-managed filtering across initial/additional pages, retry-safe append behavior, and unchanged Android system confirmation for restore/permanent deletion. Exact-source Android Verification and offline API-35 evidence are recorded in VALIDATION.md. No physical-device claim is made.


## S24 targeted review closure

Reviewed changed/direct-call/test scope: `feature/duplicates/DuplicateHash.kt`, `DuplicateCandidatePolicy.kt`, `DuplicatePhotoFinder.kt`, `data/db/PhotoDao.kt`, `ui/viewmodel/MainViewModel.kt`, duplicate presentation in `MainScreen.kt`, `DuplicateHashTest.kt`, and `DuplicateCandidatePolicyTest.kt`.

Final review confirms exact groups require full SHA-256 equality after size/partial-hash prefilters; near-duplicate candidate generation is complete for the configured Hamming threshold by disjoint-band construction; the final Hamming check remains authoritative; stale/equal-count-but-different-ID database snapshots fail open to full analysis; and valid result groups are not globally truncated. Duplicate UI remains review-only and does not preselect or delete media. Exact-source Android Verification and offline API-35 evidence are recorded in VALIDATION.md. No physical-device claim is made.


## S25 targeted review closure

Reviewed changed/direct-call/test scope: `feature/cleanup/CleanupSelectionPolicy.kt`, `feature/duplicates/CleanupGroupPolicy.kt`, `DuplicatePhotoFinder.kt`, duplicate cleanup presentation in `MainScreen.kt`, archive selection/reconciliation in `MainViewModel.kt`, `ArchivesScreen.kt`, `MainActivity.kt` confirmation callbacks, `CleanupSelectionPolicyTest.kt`, `CleanupGroupPolicyTest.kt`, and the unchanged large-font navigation instrumentation after its relaunch-harness correction.

Final review confirms category reasons are visible, cleanup group members are not silently hidden behind fixed thumbnail/group caps, newly discovered destructive candidates are not preselected, explicit selections are revision-clamped, Android system confirmation remains mandatory, and successful confirmation reconciles cleanup state without stale hero references. Exact-source Android Verification and offline API-35 evidence are recorded in VALIDATION.md. No physical-device claim is made.
